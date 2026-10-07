import { DurableObject } from "cloudflare:workers";
import type { Env } from "./env";

/**
 * Maps one physical device to its wallet.
 *
 * One instance per device, named by a salted hash of the device key the app
 * sends (hashed ANDROID_ID on Android, a Keychain UUID on iOS) - the raw key is
 * never stored. Both survive an uninstall, so a reinstall finds the same wallet
 * and, more importantly, the same trial: without this, deleting and reinstalling
 * the app would be an unlimited free trial.
 *
 * A device with a restored token (new phone via backup) is linked to the
 * token's wallet instead of getting a new one - see `/v1/register`.
 */
export class DeviceDO extends DurableObject<Env> {
  /** The wallet for this device, creating the link to `candidate` if there is none yet. */
  async claim(candidate: string): Promise<{ accountId: string; created: boolean }> {
    const existing = await this.ctx.storage.get<string>("accountId");
    if (existing) return { accountId: existing, created: false };
    await this.ctx.storage.put({ accountId: candidate, firstSeen: Date.now() });
    return { accountId: candidate, created: true };
  }

  async accountId(): Promise<string | null> {
    return (await this.ctx.storage.get<string>("accountId")) ?? null;
  }
}
