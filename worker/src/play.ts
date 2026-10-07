/**
 * Google Play subscriptions: verifying a purchase token and acknowledging it.
 *
 * Fixoo Premium is sold through **Google Play Billing**, not a direct Google
 * Pay / UPI link: Play policy requires Play Billing for digital subscriptions
 * inside a Play app, and Play's own checkout already offers UPI / Google Pay /
 * cards in India. The app hands us the purchase token; nothing about premium
 * is decided on the device.
 *
 * Auth is a Google Cloud service account (JSON key in the `PLAY_SERVICE_ACCOUNT`
 * secret) that has been granted access to the app in Play Console. We sign a
 * JWT with its private key using Web Crypto and trade it for an OAuth token -
 * no Google SDK, which would not run on Workers anyway.
 *
 * Docs: purchases.subscriptionsv2.get and purchases.subscriptions.acknowledge
 * (https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2).
 */

import type { Env } from "./env";
import { b64url } from "./token";

const SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications";

interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

let cachedToken: { value: string; expiresAt: number } | null = null;

async function accessToken(env: Env): Promise<string> {
  if (cachedToken && Date.now() < cachedToken.expiresAt - 60_000) return cachedToken.value;
  if (!env.PLAY_SERVICE_ACCOUNT) throw new Error("PLAY_SERVICE_ACCOUNT is not set");
  const sa = JSON.parse(env.PLAY_SERVICE_ACCOUNT) as ServiceAccount;
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";

  const now = Math.floor(Date.now() / 1000);
  const enc = new TextEncoder();
  const header = b64url(enc.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = b64url(
    enc.encode(JSON.stringify({ iss: sa.client_email, scope: SCOPE, aud: tokenUri, iat: now, exp: now + 3600 }))
  );
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, enc.encode(`${header}.${claims}`));
  const jwt = `${header}.${claims}.${b64url(sig)}`;

  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  });
  if (!res.ok) throw new Error(`oauth ${res.status}: ${await res.text()}`);
  const body = (await res.json()) as { access_token: string; expires_in: number };
  cachedToken = { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return body.access_token;
}

export interface Entitlement {
  /** True when the subscription currently grants premium. */
  active: boolean;
  /** When the current paid period ends (epoch ms); 0 if unknown. */
  expiresAt: number;
  /** Whether it will renew (false once the user cancels - premium still runs to expiresAt). */
  autoRenewing: boolean;
  productId: string;
  /** The account id the app set as obfuscatedAccountId at purchase, if any. */
  accountId: string | null;
  needsAcknowledge: boolean;
  state: string;
  test: boolean;
}

/** States in which the user has paid for the current period. CANCELED still runs to its expiry. */
const ENTITLED = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);

export async function verifySubscription(env: Env, purchaseToken: string): Promise<Entitlement> {
  // Local testing only: `dev-premium-<anything>` stands in for a real token so
  // the whole premium path can be exercised with `wrangler dev`, no Play
  // Console product needed. Ignored unless DEV_MODE is "true".
  if (env.DEV_MODE === "true" && purchaseToken.startsWith("dev-premium-")) {
    return {
      active: true,
      expiresAt: Date.now() + 30 * 86_400_000,
      autoRenewing: true,
      productId: env.PREMIUM_PRODUCT_ID ?? "fixoo_premium_monthly",
      accountId: null,
      needsAcknowledge: false,
      state: "SUBSCRIPTION_STATE_ACTIVE",
      test: true,
    };
  }

  const pkg = env.PLAY_PACKAGE_NAME;
  if (!pkg) throw new Error("PLAY_PACKAGE_NAME is not set");
  const res = await fetch(`${API}/${pkg}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`, {
    headers: { Authorization: `Bearer ${await accessToken(env)}` },
  });
  if (res.status === 404 || res.status === 410) {
    return { active: false, expiresAt: 0, autoRenewing: false, productId: "", accountId: null, needsAcknowledge: false, state: "NOT_FOUND", test: false };
  }
  // A token Play does not recognise comes back as 400 "Invalid Value", not
  // 404 (seen 2026-10-07 once the service account had access). It is a final
  // answer about the token, so it is "not active" rather than a Play outage -
  // otherwise the app would re-send the same bad token on every launch.
  if (res.status === 400) {
    const body = await res.text();
    if (/"reason":\s*"invalid"/.test(body)) {
      return { active: false, expiresAt: 0, autoRenewing: false, productId: "", accountId: null, needsAcknowledge: false, state: "INVALID_TOKEN", test: false };
    }
    throw new Error(`subscriptionsv2 400: ${body}`);
  }
  if (!res.ok) throw new Error(`subscriptionsv2 ${res.status}: ${await res.text()}`);

  const sub = (await res.json()) as {
    subscriptionState?: string;
    acknowledgementState?: string;
    testPurchase?: unknown;
    externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
    lineItems?: Array<{ productId?: string; expiryTime?: string; autoRenewingPlan?: { autoRenewEnabled?: boolean } }>;
  };
  const items = sub.lineItems ?? [];
  const wanted = env.PREMIUM_PRODUCT_ID ?? "fixoo_premium_monthly";
  const item = items.find((i) => i.productId === wanted) ?? items[0];
  const expiresAt = item?.expiryTime ? Date.parse(item.expiryTime) : 0;
  const state = sub.subscriptionState ?? "UNKNOWN";
  return {
    active: ENTITLED.has(state) && expiresAt > Date.now() && (item?.productId ?? "") === wanted,
    expiresAt,
    autoRenewing: item?.autoRenewingPlan?.autoRenewEnabled ?? false,
    productId: item?.productId ?? "",
    accountId: sub.externalAccountIdentifiers?.obfuscatedExternalAccountId ?? null,
    needsAcknowledge: sub.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
    state,
    test: sub.testPurchase !== undefined,
  };
}

/**
 * Must happen within three days of purchase, or Google refunds the user and
 * revokes the subscription. Done right after the entitlement is recorded.
 */
export async function acknowledgeSubscription(env: Env, productId: string, purchaseToken: string): Promise<void> {
  const pkg = env.PLAY_PACKAGE_NAME;
  const res = await fetch(
    `${API}/${pkg}/purchases/subscriptions/${productId}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${await accessToken(env)}`, "Content-Type": "application/json" },
      body: "{}",
    }
  );
  if (!res.ok && res.status !== 400) throw new Error(`acknowledge ${res.status}: ${await res.text()}`);
}
