/**
 * FixMoney AI proxy + AI coin wallet.
 *
 * The app used to call Groq directly, which meant shipping the Groq key inside
 * the APK. This Worker holds the key instead, and - since every AI call already
 * passes through here - it is also where AI coins are enforced. The app only
 * ever *displays* a balance; charging and crediting happen here, so a patched
 * APK cannot mint coins.
 *
 * Routes:
 *   POST /v1/register     device key (+ optional old token) -> device token + wallet
 *   GET  /v1/wallet       balance, trial, ads left today, prices
 *   POST /v1/chat         AI call; reserve -> model -> commit / release
 *   POST /v1/spend        charge a feature that has no model call behind it
 *   GET  /v1/admob/ssv    AdMob's signed reward callback - the only coin source
 *   POST /v1/premium/verify  a Google Play subscription token -> Fixoo Premium
 *
 * There is no login. Identity is a device token issued by /v1/register - see
 * token.ts and device.ts for why that is enough here.
 */

import type { Env } from "./env";
import { isFeature, loadCoinConfig } from "./config";
import { bearer, sha256Hex, signToken, verifyToken } from "./token";
import { runChat, type AiOutcome } from "./ai";
import { verifySsv } from "./ssv";
import { acknowledgeSubscription, verifySubscription } from "./play";
import type { WalletView } from "./wallet";
import { networkOf } from "./ipgate";

export { WalletDO } from "./wallet";
export { DeviceDO } from "./device";
export { IpGateDO } from "./ipgate";

/**
 * Per-IP cap for the legacy (token-less) path only. Groq's free tier stops at
 * 1,000 requests a day for the whole org, so without this one abuser could
 * spend the entire allowance. Authenticated calls are bounded by coins and the
 * trial cap instead.
 */
const DAILY_PER_IP = 80;

/** Groq rejects runaway prompts anyway; failing here is cheaper and clearer. */
const MAX_BODY_BYTES = 128 * 1024;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    const url = new URL(request.url);

    try {
      switch (`${request.method} ${url.pathname}`) {
        case "POST /v1/register":
          return await register(request, env);
        case "GET /v1/wallet":
          return await wallet(request, env);
        case "POST /v1/chat":
          return await chat(request, env);
        case "POST /v1/spend":
          return await spend(request, env);
        case "GET /v1/admob/ssv":
          return await admobSsv(url, env);
        case "POST /v1/premium/verify":
          return await premiumVerify(request, env);
        default:
          return err(404, "Unknown route");
      }
    } catch (e) {
      console.error("unhandled", (e as Error).stack ?? e);
      return err(500, "Internal error");
    }
  },
} satisfies ExportedHandler<Env>;

// ---------------------------------------------------------------- identity

function walletStub(env: Env, accountId: string) {
  return env.WALLET.get(env.WALLET.idFromName(accountId));
}

/** The account a request belongs to, or null when it carries no valid token. */
async function accountOf(request: Request, env: Env): Promise<string | null> {
  const token = bearer(request);
  if (!token || !env.TOKEN_SECRET) return null;
  return verifyToken(env.TOKEN_SECRET, token);
}

/**
 * Finds or creates the wallet for this install and hands back a device token.
 *
 * Order matters: a valid token wins over the device key. A token restored onto
 * a new phone (Android Auto Backup, iCloud Keychain) therefore keeps its
 * wallet even though the device key there is new. Only with no token does the
 * device key decide - which is what makes a plain uninstall/reinstall land on
 * the same wallet and the same trial.
 */
async function register(request: Request, env: Env): Promise<Response> {
  if (!env.TOKEN_SECRET) return err(500, "Proxy is missing TOKEN_SECRET");
  const body = await readJson(request);
  if (!body) return err(400, "Body must be JSON");
  const deviceKey = typeof body.deviceKey === "string" ? body.deviceKey : "";
  if (deviceKey.length < 16 || deviceKey.length > 256) return err(400, "deviceKey is required");

  // Salted so the stored name cannot be matched against an ANDROID_ID seen elsewhere.
  const deviceHash = await sha256Hex(`${env.TOKEN_SECRET}:device:${deviceKey}`);
  const device = env.DEVICE.get(env.DEVICE.idFromName(deviceHash));

  const fromToken = await accountOf(request, env);
  let accountId: string;
  if (fromToken) {
    accountId = fromToken;
    // Link the device if it has never been seen; never re-point one that has,
    // or a token could be used to hand a fresh trial to an old device.
    await device.claim(fromToken);
  } else {
    const existing = await device.accountId();
    if (existing) {
      accountId = existing;
    } else {
      // A brand-new wallet: the only case that hands out a free trial, so the
      // only one counted per network. Refused here, the app simply carries on
      // without a token on the old path while REQUIRE_AUTH is off.
      if (!(await takeNewAccountSlot(request, env))) {
        log("register_refused", { reason: "ip_limit", network: networkOf(request) });
        return err(429, "Too many new accounts from this network today. Try again tomorrow.", "IP_LIMIT");
      }
      accountId = (await device.claim(crypto.randomUUID())).accountId;
    }
  }

  const view = await walletStub(env, accountId).init(accountId, await loadCoinConfig(env));
  log("register", { account: short(accountId), via: fromToken ? "token" : "device", coins: view.coins, trial: view.trialActive, trialEnds: new Date(view.trialEndsAt).toISOString(), bonus: view.welcomeBonusGranted ?? 0 });
  const token = fromToken ? bearer(request)! : await signToken(env.TOKEN_SECRET, accountId, Date.now());
  return json(200, { token, accountId, wallet: view });
}

/** Defaults to 20 a day; `NEW_ACCOUNTS_PER_IP` = "0" turns the limit off. */
async function takeNewAccountSlot(request: Request, env: Env): Promise<boolean> {
  const limit = Number(env.NEW_ACCOUNTS_PER_IP ?? "20");
  if (!env.IP_GATE || !Number.isFinite(limit) || limit <= 0) return true;
  const day = new Date().toISOString().slice(0, 10);
  return env.IP_GATE.get(env.IP_GATE.idFromName(`${networkOf(request)}:${day}`)).take(limit);
}

async function wallet(request: Request, env: Env): Promise<Response> {
  const accountId = await accountOf(request, env);
  if (!accountId) return err(401, "Missing or invalid device token", "UNAUTHORIZED");
  const cfg = await loadCoinConfig(env);
  const stub = walletStub(env, accountId);
  // A subscription whose paid period has passed is re-checked with Play here:
  // that is how a monthly renewal (or a cancellation) is noticed, without
  // real-time developer notifications. One Play call per expiry, not per request.
  const recheck = await stub.premiumToRecheck();
  if (recheck) {
    try {
      const ent = await verifySubscription(env, recheck.token);
      await stub.setPremium(
        { until: ent.active ? ent.expiresAt : 0, autoRenewing: ent.autoRenewing, productId: recheck.productId, token: recheck.token },
        cfg
      );
      log("premium_recheck", { account: short(accountId), active: ent.active, state: ent.state, until: ent.expiresAt ? new Date(ent.expiresAt).toISOString() : null });
    } catch (e) {
      // Play unreachable: leave it as expired; the next refresh tries again.
      console.warn("premium recheck failed", (e as Error).message);
    }
  }
  const view = await stub.get(cfg);
  if (!view) return err(401, "Unknown wallet - register again", "UNAUTHORIZED");
  if (view.welcomeBonusGranted !== undefined) log("trial_ended", { account: short(accountId), bonus: view.welcomeBonusGranted, coins: view.coins });
  return json(200, { wallet: view });
}

// ---------------------------------------------------------------- AI calls

async function chat(request: Request, env: Env): Promise<Response> {
  if (!env.GROQ_API_KEY) return err(500, "Proxy is missing its API key");

  const raw = await request.text();
  if (raw.length > MAX_BODY_BYTES) return err(413, "Body too large");
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(raw);
  } catch {
    return err(400, "Body must be JSON");
  }
  if (!Array.isArray(body.messages) || body.messages.length === 0) {
    return err(400, "messages[] is required");
  }

  const accountId = await accountOf(request, env);
  if (!accountId) {
    if (env.REQUIRE_AUTH === "true" || bearer(request)) {
      return err(401, "Missing or invalid device token", "UNAUTHORIZED");
    }
    return legacyChat(request, env, body);
  }

  const feature = request.headers.get("X-Feature");
  if (!isFeature(feature)) return err(400, "X-Feature must be one of fixoo, voice, scan, insight");
  const requestId = request.headers.get("X-Request-Id") ?? "";
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(requestId)) return err(400, "X-Request-Id is required");

  const cfg = await loadCoinConfig(env);
  const stub = walletStub(env, accountId);
  const reserved = await stub.reserve(requestId, feature, cfg);
  if (!reserved.ok) {
    log("refused", { account: short(accountId), feature, reason: reserved.reason, coins: reserved.view?.coins });
    return refusal(reserved.reason, reserved.view);
  }
  const paidBy = reserved.view.premiumActive ? "premium" : reserved.view.trialActive ? "trial" : "coins";
  log("reserved", { account: short(accountId), feature, paidBy, available: reserved.view.available });

  let outcome: AiOutcome;
  try {
    outcome = await runChat(env, body);
  } catch (e) {
    await stub.release(requestId, cfg);
    throw e;
  }

  if (outcome.kind === "ok") {
    const view = (await stub.commit(requestId, cfg)) ?? reserved.view;
    log("used", {
      account: short(accountId),
      feature,
      paidBy,
      tier: outcome.tier,
      coinsLeft: view.coins,
      ...(paidBy === "premium" ? { premiumUsesLeft: view.premiumUsesLeftToday } : {}),
      ...(paidBy === "trial" ? { trialUsesLeft: view.trialUsesLeftToday } : {}),
    });
    return withWallet(
      new Response(outcome.body, { status: 200, headers: { "Content-Type": "application/json" } }),
      view
    );
  }

  const view = (await stub.release(requestId, cfg)) ?? reserved.view;
  log("released", { account: short(accountId), feature, why: outcome.kind, coins: view.coins });
  if (outcome.kind === "busy") {
    return withWallet(
      json(503, {
        error: "AI is busy right now. Try again in about 30 seconds - you were not charged.",
        code: "AI_BUSY",
        charged: false,
        retryAfterSeconds: 30,
      }),
      view
    );
  }
  return withWallet(
    cors(new Response(outcome.body, { status: outcome.status, headers: { "Content-Type": "application/json" } })),
    view
  );
}

/**
 * Charges a feature that has no model call behind it (the Home insight).
 * Reserve and commit in one go - there is nothing that can fail in between.
 * With the default price of 0 this just answers with the wallet.
 */
async function spend(request: Request, env: Env): Promise<Response> {
  const accountId = await accountOf(request, env);
  if (!accountId) return err(401, "Missing or invalid device token", "UNAUTHORIZED");
  const body = await readJson(request);
  const feature = typeof body?.feature === "string" ? body.feature : null;
  const requestId = typeof body?.requestId === "string" ? body.requestId : "";
  if (!isFeature(feature)) return err(400, "feature must be one of fixoo, voice, scan, insight");
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(requestId)) return err(400, "requestId is required");

  const cfg = await loadCoinConfig(env);
  const stub = walletStub(env, accountId);
  const reserved = await stub.reserve(requestId, feature, cfg);
  if (!reserved.ok) return refusal(reserved.reason, reserved.view);
  const view = (await stub.commit(requestId, cfg)) ?? reserved.view;
  return json(200, { wallet: view });
}

function refusal(reason: "NO_COINS" | "TRIAL_LIMIT" | "PREMIUM_LIMIT" | "NO_WALLET", view?: WalletView): Response {
  switch (reason) {
    case "PREMIUM_LIMIT":
      return json(429, {
        error: "You've reached today's fair-use limit for Fixoo Premium. It resets at midnight.",
        code: "PREMIUM_LIMIT",
        wallet: view,
      });
    case "NO_COINS":
      return json(402, { error: "Out of Fixoo Coins. Watch a video to get more.", code: "NO_COINS", wallet: view });
    case "TRIAL_LIMIT":
      return json(429, {
        error: "You've reached today's free AI limit. It resets tomorrow.",
        code: "TRIAL_LIMIT",
        wallet: view,
      });
    case "NO_WALLET":
      return err(401, "Unknown wallet - register again", "UNAUTHORIZED");
  }
}

/**
 * The pre-coins behaviour, for builds that do not send a device token yet.
 * Uncharged, bounded only by the per-IP cap. Removed by setting REQUIRE_AUTH.
 */
async function legacyChat(request: Request, env: Env, body: Record<string, unknown>): Promise<Response> {
  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  if (await overQuota(env, ip)) {
    return err(429, "Daily limit reached for this device. Try again tomorrow.");
  }
  const outcome = await runChat(env, body);
  switch (outcome.kind) {
    case "ok":
      return cors(new Response(outcome.body, { status: 200, headers: { "Content-Type": "application/json" } }));
    case "busy":
      // Old builds map 429 to their "quota exceeded" message; keep speaking that.
      return err(429, "AI is busy right now. Try again shortly.");
    case "rejected":
      return cors(
        new Response(outcome.body, { status: outcome.status, headers: { "Content-Type": "application/json" } })
      );
  }
}

/**
 * KV is eventually consistent, so a burst can slip a few over the line. Fine:
 * this exists to stop one client draining the org's quota, not to meter.
 */
async function overQuota(env: Env, ip: string): Promise<boolean> {
  if (!env.RATE) return false;
  const key = `${ip}:${new Date().toISOString().slice(0, 10)}`;
  const used = Number((await env.RATE.get(key)) ?? "0");
  if (used >= DAILY_PER_IP) return true;
  await env.RATE.put(key, String(used + 1), { expirationTtl: 60 * 60 * 48 });
  return false;
}

// ---------------------------------------------------------------- rewards

/**
 * AdMob calls this after a rewarded video completes. The SSV `user_id` is the
 * account id the app set on the ad request.
 *
 * Answers 200 for anything validly signed - including duplicates and capped
 * views - because a non-200 makes AdMob retry, and a retry would be refused
 * again anyway. Only a bad signature gets an error.
 */
async function admobSsv(url: URL, env: Env): Promise<Response> {
  const result = await verifySsv(url, env);
  if (!result.ok) {
    console.warn("ssv rejected:", result.error);
    return err(400, result.error);
  }
  if (!result.reward) return json(200, { ok: true, note: "verified test callback" });

  const { userId, transactionId } = result.reward;
  if (!/^[0-9a-f-]{36}$/.test(userId)) return json(200, { ok: true, note: "unknown user" });

  const outcome = await walletStub(env, userId).creditAd(transactionId, await loadCoinConfig(env));
  log("ad_reward", { account: short(userId), credited: outcome.credited, reason: outcome.reason ?? null, coins: outcome.view?.coins });
  return json(200, { ok: true, credited: outcome.credited, reason: outcome.reason ?? null });
}

// ---------------------------------------------------------------- premium

/**
 * The app calls this right after a Google Play purchase, and on launch for any
 * purchase Play still reports (restore after reinstall / on a new phone).
 *
 * Everything is checked with Google, not taken from the app: the token must be
 * a real subscription for our product, currently paid for. The purchase is
 * then acknowledged - Google refunds anything left unacknowledged for three
 * days - and the expiry stored is Play's.
 *
 * A token bought under a *different* account id (obfuscatedAccountId) is still
 * honoured for the caller: that is a reinstall or a new phone without backup,
 * where the same Google account legitimately owns the subscription.
 */
async function premiumVerify(request: Request, env: Env): Promise<Response> {
  const accountId = await accountOf(request, env);
  if (!accountId) return err(401, "Missing or invalid device token", "UNAUTHORIZED");
  const body = await readJson(request);
  const purchaseToken = typeof body?.purchaseToken === "string" ? body.purchaseToken : "";
  if (purchaseToken.length < 10 || purchaseToken.length > 4096) return err(400, "purchaseToken is required");

  let ent;
  try {
    ent = await verifySubscription(env, purchaseToken);
  } catch (e) {
    console.error("premium verify failed", (e as Error).message);
    return err(502, "Couldn't confirm the purchase with Google Play. Try again shortly.", "PLAY_UNAVAILABLE");
  }
  log("premium_verify", {
    account: short(accountId),
    active: ent.active,
    state: ent.state,
    test: ent.test,
    boughtBy: ent.accountId ? short(ent.accountId) : null,
    until: ent.expiresAt ? new Date(ent.expiresAt).toISOString() : null,
  });
  if (!ent.active) {
    return json(402, { error: "This subscription isn't active.", code: "PREMIUM_INACTIVE", state: ent.state });
  }

  const cfg = await loadCoinConfig(env);
  const view = await walletStub(env, accountId).setPremium(
    { until: ent.expiresAt, autoRenewing: ent.autoRenewing, productId: ent.productId, token: purchaseToken },
    cfg
  );
  if (!view) return err(401, "Unknown wallet - register again", "UNAUTHORIZED");

  if (ent.needsAcknowledge) {
    try {
      await acknowledgeSubscription(env, ent.productId, purchaseToken);
    } catch (e) {
      // Not fatal: the app re-sends unacknowledged purchases on next launch.
      console.error("acknowledge failed", (e as Error).message);
    }
  }
  return json(200, { wallet: view });
}

// ---------------------------------------------------------------- helpers

/**
 * One line per coin event, readable in `wrangler dev` and in the dashboard's
 * Workers Logs (observability is on). Account ids are shortened - enough to
 * follow one user through a session, not a full identifier in the logs.
 */
function log(event: string, fields: Record<string, unknown>): void {
  console.log(`[coins] ${event} ${JSON.stringify(fields)}`);
}

function short(accountId: string): string {
  return accountId.slice(0, 8);
}

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const v = await request.json();
    return v && typeof v === "object" ? (v as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

/** Every authenticated AI answer carries the balance, so the app never polls for it. */
function withWallet(res: Response, view: WalletView): Response {
  res.headers.set("X-Coins", String(view.coins));
  res.headers.set("X-Coins-Available", String(view.available));
  res.headers.set("X-Trial-Ends-At", String(view.trialEndsAt));
  res.headers.set("X-Trial-Uses-Left", String(view.trialUsesLeftToday));
  return cors(res);
}

function cors(res: Response): Response {
  res.headers.set("Access-Control-Allow-Origin", "*");
  res.headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Feature, X-Request-Id");
  res.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  return res;
}

function json(status: number, body: unknown): Response {
  return cors(new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } }));
}

function err(status: number, message: string, code?: string): Response {
  return json(status, code ? { error: message, code } : { error: message });
}
