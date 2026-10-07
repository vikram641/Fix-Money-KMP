/**
 * Every number the coin economy runs on, read from `wrangler.jsonc` vars.
 *
 * They live on the server rather than in the app so they can be tuned without a
 * release - the reward per video in particular has to be set from real AdMob
 * eCPM data, which does not exist until after launch. The app only ever
 * *displays* these; it never decides anything from them.
 */

import type { Env } from "./env";

/** Features that cost coins. The app sends one of these in `X-Feature`. */
export const FEATURES = ["fixoo", "voice", "scan", "insight"] as const;
export type Feature = (typeof FEATURES)[number];

export interface CoinConfig {
  trialDays: number;
  /** AI uses per day allowed during the trial, to protect the shared Groq quota. */
  trialDailyCap: number;
  rewardPerAd: number;
  dailyAdCap: number;
  /** Granted once, the first time a wallet is seen after its trial has ended. */
  welcomeBonus: number;
  /**
   * AI uses per day for a Fixoo Premium subscriber. Premium means "no coins",
   * not "no limit": every call still spends the shared Groq quota, so a fair-use
   * ceiling protects everyone else. The app says "fair use", never "unlimited".
   */
  premiumDailyCap: number;
  costs: Record<Feature, number>;
  /**
   * Minutes added to UTC before cutting a calendar day. 330 = IST, so "resets
   * tomorrow" means the user's midnight rather than 05:30 in the morning.
   */
  dayOffsetMinutes: number;
}

function num(raw: string | undefined, fallback: number): number {
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}

export function coinConfig(env: Env): CoinConfig {
  return {
    trialDays: num(env.TRIAL_DAYS, 5),
    trialDailyCap: num(env.TRIAL_DAILY_CAP, 30),
    rewardPerAd: num(env.REWARD_PER_AD, 5),
    dailyAdCap: num(env.DAILY_AD_CAP, 10),
    welcomeBonus: num(env.WELCOME_BONUS, 10),
    premiumDailyCap: num(env.PREMIUM_DAILY_CAP, 100),
    costs: {
      fixoo: num(env.COST_FIXOO, 1),
      voice: num(env.COST_VOICE, 1),
      scan: num(env.COST_SCAN, 1),
      // The Home insight is local arithmetic with no AI call behind it, so it
      // costs nothing to serve. Charging for it is a product choice; 0 by default.
      insight: num(env.COST_INSIGHT, 0),
    },
    dayOffsetMinutes: num(env.DAY_OFFSET_MINUTES, 330),
  };
}

/**
 * Firestore field name -> config setter. These names are what goes in the
 * `coin_config/app` document, so treat them as a published contract like the
 * feature-flag keys: renaming one silently falls back to the `wrangler.jsonc`
 * value.
 */
const FIRESTORE_FIELDS: Record<string, (c: CoinConfig, n: number) => void> = {
  trial_days: (c, n) => (c.trialDays = Math.min(n, 365)),
  trial_daily_cap: (c, n) => (c.trialDailyCap = Math.min(n, 1000)),
  reward_per_ad: (c, n) => (c.rewardPerAd = Math.min(n, 100)),
  daily_ad_cap: (c, n) => (c.dailyAdCap = Math.min(n, 50)),
  welcome_bonus: (c, n) => (c.welcomeBonus = Math.min(n, 1000)),
  premium_daily_cap: (c, n) => (c.premiumDailyCap = Math.min(n, 1000)),
  cost_fixoo: (c, n) => (c.costs.fixoo = Math.min(n, 100)),
  cost_voice: (c, n) => (c.costs.voice = Math.min(n, 100)),
  cost_scan: (c, n) => (c.costs.scan = Math.min(n, 100)),
  cost_insight: (c, n) => (c.costs.insight = Math.min(n, 100)),
};

/** How long a fetched copy is trusted before Firestore is asked again. */
const REMOTE_TTL_MS = 5 * 60_000;
let remote: { at: number; values: Record<string, number> } | null = null;

/**
 * The coin config as it should be applied **right now**: the `wrangler.jsonc`
 * vars, overlaid with whatever the Firestore document `coin_config/app` says.
 *
 * This is what lets the trial length, prices and rewards change from the
 * Firebase console without a deploy - the same console the feature flags
 * already live in. Read over Firestore's REST API with the public web key,
 * like the app reads its flags; the rule that makes it readable:
 *
 *   match /coin_config/{doc} { allow read: if true; allow write: if false; }
 *
 * Fetched by the front Worker only, cached for five minutes per isolate, and
 * handed to the wallet objects as an argument - ten thousand wallets must not
 * mean ten thousand Firestore reads. A failed fetch keeps the last good copy,
 * and with no copy at all the vars apply: the economy never goes blank.
 *
 * Values are clamped to sane maxima so a typo in the console (an extra zero)
 * cannot hand out 50,000 coins per video.
 */
export async function loadCoinConfig(env: Env): Promise<CoinConfig> {
  const cfg = coinConfig(env);
  const now = Date.now();
  if ((!remote || now - remote.at > REMOTE_TTL_MS) && env.FIREBASE_PROJECT_ID && env.FIREBASE_API_KEY) {
    try {
      const url =
        `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}` +
        `/databases/(default)/documents/coin_config/app?key=${env.FIREBASE_API_KEY}`;
      const res = await fetch(url, { signal: AbortSignal.timeout(2_000) });
      if (res.ok) {
        const doc = (await res.json()) as { fields?: Record<string, Record<string, unknown>> };
        const values: Record<string, number> = {};
        for (const [name, typed] of Object.entries(doc.fields ?? {})) {
          // Accept a number typed as int, double or string - whoever edits the
          // console will produce all three.
          const raw = typed.integerValue ?? typed.doubleValue ?? typed.stringValue;
          const n = Math.floor(Number(raw));
          if (Number.isFinite(n) && n >= 0) values[name] = n;
        }
        remote = { at: now, values };
      } else {
        // 404 = no document yet, 403 = rule not published: vars apply. Retry later.
        console.warn("coin_config fetch", res.status);
        remote = { at: now, values: remote?.values ?? {} };
      }
    } catch (e) {
      console.warn("coin_config fetch failed", (e as Error).message);
      remote = { at: now, values: remote?.values ?? {} };
    }
  }
  for (const [name, n] of Object.entries(remote?.values ?? {})) FIRESTORE_FIELDS[name]?.(cfg, n);
  return cfg;
}

export function isFeature(value: string | null): value is Feature {
  return value !== null && (FEATURES as readonly string[]).includes(value);
}

/** `yyyy-mm-dd` of the user's local day, given the configured offset. */
export function dayKey(nowMs: number, offsetMinutes: number): string {
  return new Date(nowMs + offsetMinutes * 60_000).toISOString().slice(0, 10);
}
