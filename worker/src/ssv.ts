/**
 * AdMob rewarded-ad server-side verification (SSV).
 *
 * This is the **only** way coins are ever created. When a user finishes a
 * rewarded video, AdMob's own servers call `/v1/admob/ssv` with the details,
 * signed with Google's ECDSA P-256 key. The app reporting "the user watched it"
 * is never trusted - a patched APK could send that a thousand times.
 *
 * Callback shape (https://developers.google.com/admob/android/ssv):
 *   ?ad_network=..&ad_unit=..&custom_data=..&reward_amount=..&reward_item=..
 *    &timestamp=..&transaction_id=..&user_id=..&signature=..&key_id=..
 * `signature` and `key_id` are always the last two parameters; everything
 * before `&signature=` is the signed content, verified byte for byte as sent.
 */

import type { Env } from "./env";
import { fromB64url } from "./token";

const KEYS_URL = "https://www.gstatic.com/admob/reward/verifier-keys.json";
const KEYS_TTL_MS = 24 * 60 * 60 * 1000;

interface KeyCache {
  at: number;
  keys: Map<string, CryptoKey>;
}
let cache: KeyCache | null = null;

async function importSpki(base64: string): Promise<CryptoKey> {
  const der = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey("spki", der, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
}

async function googleKeys(): Promise<Map<string, CryptoKey>> {
  if (cache && Date.now() - cache.at < KEYS_TTL_MS) return cache.keys;
  const res = await fetch(KEYS_URL);
  if (!res.ok) throw new Error(`verifier keys ${res.status}`);
  const body = (await res.json()) as { keys: Array<{ keyId: number | string; base64: string }> };
  const keys = new Map<string, CryptoKey>();
  for (const k of body.keys) keys.set(String(k.keyId), await importSpki(k.base64));
  cache = { at: Date.now(), keys };
  return keys;
}

/**
 * ECDSA signatures arrive DER-encoded (`SEQUENCE { INTEGER r, INTEGER s }`);
 * WebCrypto wants the raw 64-byte `r || s`. Each integer may carry a leading
 * zero byte (when its top bit is set) or be shorter than 32 bytes.
 */
function derToRaw(der: Uint8Array): Uint8Array | null {
  let i = 0;
  if (der[i++] !== 0x30) return null;
  let len = der[i++];
  if (len & 0x80) i += len & 0x7f; // long-form length; never needed for P-256 but tolerated
  const out = new Uint8Array(64);
  for (let part = 0; part < 2; part++) {
    if (der[i++] !== 0x02) return null;
    len = der[i++];
    let int = der.subarray(i, i + len);
    i += len;
    while (int.length > 32 && int[0] === 0) int = int.subarray(1);
    if (int.length > 32) return null;
    out.set(int, part * 32 + (32 - int.length));
  }
  return out;
}

export interface SsvReward {
  userId: string;
  transactionId: string;
  adUnit: string;
}

export type SsvResult = { ok: true; reward: SsvReward | null } | { ok: false; error: string };

/**
 * Verifies the callback URL. `reward` is null for AdMob's console "Verify URL"
 * test ping, which is validly signed but carries no user.
 */
export async function verifySsv(url: URL, env: Env): Promise<SsvResult> {
  const query = url.search.startsWith("?") ? url.search.slice(1) : url.search;
  const cut = query.indexOf("&signature=");
  if (cut < 0) return { ok: false, error: "missing signature" };
  const content = query.slice(0, cut);

  const params = url.searchParams;
  const signature = params.get("signature");
  const keyId = params.get("key_id");
  if (!signature || !keyId) return { ok: false, error: "missing signature or key_id" };

  let key: CryptoKey | undefined;
  if (env.DEV_MODE === "true" && keyId === "test" && env.SSV_TEST_KEY) {
    key = await importSpki(env.SSV_TEST_KEY);
  } else {
    try {
      key = (await googleKeys()).get(keyId);
    } catch (e) {
      return { ok: false, error: `could not fetch verifier keys: ${(e as Error).message}` };
    }
  }
  if (!key) return { ok: false, error: "unknown key_id" };

  let raw: Uint8Array | null;
  try {
    raw = derToRaw(fromB64url(signature));
  } catch {
    raw = null;
  }
  if (!raw) return { ok: false, error: "malformed signature" };

  // Google signs the *decoded* query (its own verifier uses java.net.URI's
  // getQuery()), so "reward_item=Fixoo%20Coins" was signed as "Fixoo Coins".
  // Checking the raw form failed AdMob's console "Verify URL" with a 400 the
  // moment the reward item had a space in it. The raw form is still tried, for
  // callbacks with nothing to decode and for the local test signer.
  let decoded: string | null;
  try {
    decoded = decodeURIComponent(content);
  } catch {
    decoded = null;
  }
  const verifies = (text: string) =>
    crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, key!, raw!, new TextEncoder().encode(text));
  const valid = (decoded !== null && (await verifies(decoded))) || (decoded !== content && (await verifies(content)));
  if (!valid) return { ok: false, error: "bad signature" };

  const userId = params.get("user_id") ?? "";
  const transactionId = params.get("transaction_id") ?? "";
  const adUnit = params.get("ad_unit") ?? "";
  if (!userId || !transactionId) return { ok: true, reward: null };

  const allowed = (env.REWARDED_AD_UNITS ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (allowed.length > 0 && !allowed.includes(adUnit)) {
    return { ok: false, error: "ad unit not accepted" };
  }
  return { ok: true, reward: { userId, transactionId, adUnit } };
}
