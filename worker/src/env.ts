import type { WalletDO } from "./wallet";
import type { DeviceDO } from "./device";
import type { IpGateDO } from "./ipgate";

export interface Env {
  GROQ_API_KEY: string;
  /**
   * Signs the device tokens handed out by `/v1/register`. Set with
   * `wrangler secret put TOKEN_SECRET`. Rotating it logs every device out of
   * its token - they re-register by device key and land on the same wallet, so
   * it is safe, just one extra round trip each.
   */
  TOKEN_SECRET?: string;

  /** Optional. Bound in wrangler.jsonc to enable the per-IP daily cap. */
  RATE?: KVNamespace;
  /** Optional. Cloudflare Workers AI, used when Groq is out of quota. */
  AI?: Ai;

  WALLET: DurableObjectNamespace<WalletDO>;
  DEVICE: DurableObjectNamespace<DeviceDO>;
  /** New wallets per network per day - see ipgate.ts. Optional so older configs still run. */
  IP_GATE?: DurableObjectNamespace<IpGateDO>;
  /** Limit for IP_GATE. "0" disables it. */
  NEW_ACCOUNTS_PER_IP?: string;

  /**
   * "true" once every shipped build sends a device token. Until then a request
   * without one still goes through the old uncharged path, so builds already
   * on test devices keep working while the app side lands.
   */
  REQUIRE_AUTH?: string;

  /**
   * Fixoo Premium (Google Play subscription). `PLAY_SERVICE_ACCOUNT` is the
   * JSON key of a service account granted access in Play Console - a secret,
   * set with `wrangler secret put`. The other two are plain vars.
   */
  PLAY_SERVICE_ACCOUNT?: string;
  PLAY_PACKAGE_NAME?: string;
  PREMIUM_PRODUCT_ID?: string;
  PREMIUM_DAILY_CAP?: string;

  /** Comma-separated AdMob rewarded ad unit ids accepted by `/v1/admob/ssv`. Empty = any. */
  REWARDED_AD_UNITS?: string;

  /**
   * Public Firebase web config, used to read the `coin_config/app` document.
   * Not secrets - the same values ship inside the app. Without them the
   * `wrangler.jsonc` vars are the only config.
   */
  FIREBASE_PROJECT_ID?: string;
  FIREBASE_API_KEY?: string;

  // Coin economy defaults - overridden live by Firestore, see config.ts.
  TRIAL_DAYS?: string;
  TRIAL_DAILY_CAP?: string;
  REWARD_PER_AD?: string;
  DAILY_AD_CAP?: string;
  WELCOME_BONUS?: string;
  COST_FIXOO?: string;
  COST_VOICE?: string;
  COST_SCAN?: string;
  COST_INSIGHT?: string;
  DAY_OFFSET_MINUTES?: string;

  /**
   * Local development only (`.dev.vars`), never set in production:
   * `GROQ_URL` points the proxy at a mock, `SSV_TEST_KEY` is an extra P-256
   * SPKI (base64) accepted for `key_id=test` so the SSV path can be exercised
   * without AdMob. Both are ignored unless `DEV_MODE` is "true".
   */
  DEV_MODE?: string;
  GROQ_URL?: string;
  SSV_TEST_KEY?: string;
  /** Dev only: skip the Workers AI fallback so the "busy" path can be tested locally. */
  DEV_NO_FALLBACK?: string;
}
