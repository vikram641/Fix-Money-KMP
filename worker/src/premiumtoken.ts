import { DurableObject } from "cloudflare:workers";
import type { Env } from "./env";

/**
 * Which account a Play purchase token currently unlocks. One instance per
 * token, named by its SHA-256.
 *
 * Without this, any number of accounts could send the same token and all get
 * Premium. A token still moves - a new phone without a backup gets a fresh
 * account and must be able to restore - but only one account holds it at a
 * time: the previous holder loses Premium when it moves.
 */
export class PremiumTokenDO extends DurableObject<Env> {
  /** Binds the token to [accountId]. Returns the account it moved away from, if any. */
  async claim(accountId: string): Promise<string | null> {
    const holder = (await this.ctx.storage.get<string>("account")) ?? null;
    if (holder === accountId) return null;
    await this.ctx.storage.put("account", accountId);
    return holder;
  }
}

export async function tokenKey(purchaseToken: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(purchaseToken));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
