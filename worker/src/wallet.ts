import { DurableObject } from "cloudflare:workers";
import type { Env } from "./env";
import { dayKey, type CoinConfig, type Feature } from "./config";

/**
 * One user's coins. One instance per wallet, named by account id.
 *
 * A Durable Object rather than a row in one shared database: an instance
 * handles one request at a time, so "is there a coin, and take it" can never
 * race with itself, and ten thousand users are ten thousand independent
 * instances rather than one queue. A single D1 database is single-threaded
 * and answers "overloaded" under exactly the burst a launch produces.
 *
 * The whole state is one small object under one key. Every method reads it,
 * changes it, writes it back - the input gate holds other requests to this
 * instance until the write is done, so nothing interleaves.
 */

interface Hold {
  feature: Feature;
  cost: number;
  /** What pays for this use: the trial, a premium subscription, or coins. */
  kind: "trial" | "premium" | "coins";
  at: number;
}

/** A Google Play subscription that has been verified server-side. */
interface Premium {
  /** End of the current paid period (epoch ms). Re-verified with Play once it passes. */
  until: number;
  autoRenewing: boolean;
  productId: string;
  /** The Play purchase token, kept to re-verify on renewal. Never sent to the app. */
  token: string;
}

interface State {
  accountId: string;
  /**
   * When the wallet was created. The trial end is **derived** from this and the
   * live `trialDays` rather than stored, so changing `trial_days` in Firestore
   * applies to every wallet - lengthening it extends current trials, shortening
   * it shortens them - instead of only to users who register afterwards.
   */
  createdAt: number;
  coins: number;
  /** Reserved but not yet committed or released, by request id. */
  holds: Record<string, Hold>;
  /** Recently committed request ids, so a retried commit/reserve never charges twice. */
  done: string[];
  trialUses: { day: string; count: number };
  ads: { day: string; count: number };
  /** AdMob transaction ids already credited. Bounded - see `rememberTx`. */
  txs: string[];
  welcomeGranted: boolean;
  premium?: Premium;
  premiumUses?: { day: string; count: number };
}

export interface WalletView {
  coins: number;
  /** Coins minus anything held for a request still in flight. */
  available: number;
  trialActive: boolean;
  trialEndsAt: number;
  trialUsesLeftToday: number;
  adsLeftToday: number;
  dailyAdCap: number;
  rewardPerAd: number;
  costs: Record<Feature, number>;
  /**
   * Present (possibly 0) only on the one call where the trial was seen to have
   * ended, so the app shows its one-time "trial ended" sheet exactly once - even
   * when the configured bonus is 0. Absent on every other call.
   */
  welcomeBonusGranted?: number;
  /** Fixoo Premium: no coins are charged while true. */
  premiumActive: boolean;
  premiumUntil: number;
  premiumAutoRenewing: boolean;
  premiumUsesLeftToday: number;
}

export type ReserveResult =
  | { ok: true; view: WalletView }
  | { ok: false; reason: "NO_COINS" | "TRIAL_LIMIT" | "PREMIUM_LIMIT" | "NO_WALLET"; view?: WalletView };

/** A hold older than this belongs to a request that died mid-flight; release it. */
const HOLD_TTL_MS = 2 * 60_000;
const DONE_KEEP = 50;
/** Ten videos a day, five days of AdMob retries - far more than enough. */
const TX_KEEP = 100;

export class WalletDO extends DurableObject<Env> {
  /**
   * The config for the call in progress, handed in by the front Worker (see
   * `loadCoinConfig`) so a wallet never reads Firestore itself. Safe as a field:
   * every method only awaits storage, and the input gate holds other calls to
   * this object until it is done, so two calls never share it.
   */
  private cfg!: CoinConfig;

  private trialEndsAt(s: State): number {
    return s.createdAt + this.cfg.trialDays * 86_400_000;
  }

  private async load(): Promise<State | null> {
    return (await this.ctx.storage.get<State>("s")) ?? null;
  }

  private async save(s: State): Promise<void> {
    await this.ctx.storage.put("s", s);
  }

  /**
   * Housekeeping that has to happen before any read: expire holds, roll days,
   * and notice the trial ending. Returns the bonus granted at that moment
   * (0 if the configured bonus is 0), or null when the trial did not just end.
   */
  private normalize(s: State, now: number): number | null {
    const cfg = this.cfg;
    for (const [id, h] of Object.entries(s.holds)) {
      if (now - h.at > HOLD_TTL_MS) delete s.holds[id];
    }
    const today = dayKey(now, cfg.dayOffsetMinutes);
    if (s.trialUses.day !== today) s.trialUses = { day: today, count: 0 };
    if (!s.premiumUses || s.premiumUses.day !== today) s.premiumUses = { day: today, count: 0 };
    if (s.ads.day !== today) s.ads = { day: today, count: 0 };

    if (now >= this.trialEndsAt(s) && !s.welcomeGranted) {
      s.welcomeGranted = true;
      const bonus = Math.max(0, cfg.welcomeBonus);
      s.coins += bonus;
      return bonus;
    }
    return null;
  }

  private view(s: State, now: number, granted: number | null = null): WalletView {
    const cfg = this.cfg;
    const holds = Object.values(s.holds);
    const held = holds.reduce((sum, h) => sum + (h.kind === "coins" ? h.cost : 0), 0);
    const trialHeld = holds.filter((h) => h.kind === "trial").length;
    const premiumHeld = holds.filter((h) => h.kind === "premium").length;
    const v: WalletView = {
      coins: s.coins,
      available: Math.max(0, s.coins - held),
      trialActive: now < this.trialEndsAt(s),
      trialEndsAt: this.trialEndsAt(s),
      trialUsesLeftToday: Math.max(0, cfg.trialDailyCap - s.trialUses.count - trialHeld),
      adsLeftToday: Math.max(0, cfg.dailyAdCap - s.ads.count),
      dailyAdCap: cfg.dailyAdCap,
      rewardPerAd: cfg.rewardPerAd,
      costs: cfg.costs,
      premiumActive: (s.premium?.until ?? 0) > now,
      premiumUntil: s.premium?.until ?? 0,
      premiumAutoRenewing: s.premium?.autoRenewing ?? false,
      premiumUsesLeftToday: Math.max(0, cfg.premiumDailyCap - (s.premiumUses?.count ?? 0) - premiumHeld),
    };
    if (granted !== null) v.welcomeBonusGranted = granted;
    return v;
  }

  /** Creates the wallet on first registration; a no-op for an existing one. */
  async init(accountId: string, cfg: CoinConfig): Promise<WalletView> {
    this.cfg = cfg;
    const now = Date.now();
    let s = await this.load();
    if (!s) {
      s = {
        accountId,
        createdAt: now,
        coins: 0,
        holds: {},
        done: [],
        trialUses: { day: "", count: 0 },
        ads: { day: "", count: 0 },
        txs: [],
        welcomeGranted: false,
      };
    }
    const granted = this.normalize(s, now);
    await this.save(s);
    return this.view(s, now, granted);
  }

  async get(cfg: CoinConfig): Promise<WalletView | null> {
    this.cfg = cfg;
    const now = Date.now();
    const s = await this.load();
    if (!s) return null;
    const granted = this.normalize(s, now);
    await this.save(s);
    return this.view(s, now, granted);
  }

  /**
   * Step one of a paid call: set the coin aside without taking it.
   *
   * The coin is only taken by `commit` once the model has actually answered;
   * `release` gives it back if it did not. So a failed or busy AI call never
   * costs the user anything, and there is no separate refund path to get wrong.
   * Holding it (rather than only checking the balance) is what stops two
   * concurrent calls from both spending the user's last coin.
   */
  async reserve(requestId: string, feature: Feature, cfg: CoinConfig): Promise<ReserveResult> {
    this.cfg = cfg;
    const now = Date.now();
    const s = await this.load();
    if (!s) return { ok: false, reason: "NO_WALLET" };
    this.normalize(s, now);
    const cost = cfg.costs[feature];

    // A retry of a request already reserved or already paid for: answer the
    // same way, charge nothing new.
    if (s.holds[requestId] || s.done.includes(requestId)) {
      await this.save(s);
      return { ok: true, view: this.view(s, now) };
    }

    if (cost === 0) {
      await this.save(s);
      return { ok: true, view: this.view(s, now) };
    }

    // Premium first, then the trial, then coins: a subscriber who is also
    // still in the trial window is not eating into either allowance twice.
    const v = this.view(s, now);
    if (v.premiumActive) {
      if (v.premiumUsesLeftToday <= 0) {
        await this.save(s);
        return { ok: false, reason: "PREMIUM_LIMIT", view: v };
      }
      s.holds[requestId] = { feature, cost, kind: "premium", at: now };
    } else if (v.trialActive) {
      if (v.trialUsesLeftToday <= 0) {
        await this.save(s);
        return { ok: false, reason: "TRIAL_LIMIT", view: v };
      }
      s.holds[requestId] = { feature, cost, kind: "trial", at: now };
    } else {
      if (v.available < cost) {
        await this.save(s);
        return { ok: false, reason: "NO_COINS", view: v };
      }
      s.holds[requestId] = { feature, cost, kind: "coins", at: now };
    }
    await this.save(s);
    return { ok: true, view: this.view(s, now) };
  }

  /** The model answered: make the reserved charge real. */
  async commit(requestId: string, cfg: CoinConfig): Promise<WalletView | null> {
    this.cfg = cfg;
    const now = Date.now();
    const s = await this.load();
    if (!s) return null;
    this.normalize(s, now);
    const h = s.holds[requestId];
    if (h) {
      delete s.holds[requestId];
      if (h.kind === "trial") s.trialUses.count += 1;
      else if (h.kind === "premium") s.premiumUses = { day: s.premiumUses?.day ?? "", count: (s.premiumUses?.count ?? 0) + 1 };
      else s.coins = Math.max(0, s.coins - h.cost);
      s.done.push(requestId);
      if (s.done.length > DONE_KEEP) s.done.splice(0, s.done.length - DONE_KEEP);
    }
    await this.save(s);
    return this.view(s, now);
  }

  /** The model did not answer: drop the hold, nothing is charged. */
  async release(requestId: string, cfg: CoinConfig): Promise<WalletView | null> {
    this.cfg = cfg;
    const now = Date.now();
    const s = await this.load();
    if (!s) return null;
    this.normalize(s, now);
    delete s.holds[requestId];
    await this.save(s);
    return this.view(s, now);
  }

  /**
   * Credits one rewarded video. Only ever called from the verified AdMob
   * server-side callback - never from anything the app sends.
   */
  async creditAd(
    transactionId: string,
    cfg: CoinConfig
  ): Promise<{ credited: number; reason?: "DUPLICATE" | "DAILY_CAP" | "NO_WALLET"; view?: WalletView }> {
    this.cfg = cfg;
    const now = Date.now();
    const s = await this.load();
    if (!s) return { credited: 0, reason: "NO_WALLET" };
    this.normalize(s, now);

    if (s.txs.includes(transactionId)) {
      await this.save(s);
      return { credited: 0, reason: "DUPLICATE", view: this.view(s, now) };
    }
    // Remembered even when capped, so an AdMob retry of a capped view stays capped.
    s.txs.push(transactionId);
    if (s.txs.length > TX_KEEP) s.txs.splice(0, s.txs.length - TX_KEEP);

    if (s.ads.count >= cfg.dailyAdCap) {
      await this.save(s);
      return { credited: 0, reason: "DAILY_CAP", view: this.view(s, now) };
    }
    s.ads.count += 1;
    s.coins += cfg.rewardPerAd;
    await this.save(s);
    return { credited: cfg.rewardPerAd, view: this.view(s, now) };
  }

  /**
   * Records a subscription the front Worker has just verified with Google Play.
   * Called on purchase, on restore, and on renewal re-checks - always with
   * Play's own expiry, never a date the app supplied.
   */
  async setPremium(
    premium: { until: number; autoRenewing: boolean; productId: string; token: string },
    cfg: CoinConfig
  ): Promise<WalletView | null> {
    this.cfg = cfg;
    const now = Date.now();
    const s = await this.load();
    if (!s) return null;
    this.normalize(s, now);
    s.premium = premium;
    await this.save(s);
    return this.view(s, now);
  }

  /**
   * The token to re-verify when a subscription's paid period has passed -
   * Play renews monthly, and this is how a renewal is noticed without
   * real-time notifications. Null when there is nothing to re-check.
   */
  async premiumToRecheck(): Promise<{ token: string; productId: string } | null> {
    const s = await this.load();
    if (!s?.premium) return null;
    if (s.premium.until > Date.now()) return null;
    return { token: s.premium.token, productId: s.premium.productId };
  }
}
