import { DurableObject } from "cloudflare:workers";
import type { Env } from "./env";

/**
 * Counts new wallets per network per day, so a script inventing device keys
 * cannot mint free trials (and welcome bonuses) without limit.
 *
 * One instance per IP per day, named `<network>:<yyyy-mm-dd>`. A Durable Object
 * rather than KV because the count has to be exact - KV is eventually
 * consistent, a burst would sail past it, and its 1,000 free writes a day would
 * be the real limit. Old instances are never read again; each holds one
 * integer, so leaving them costs nothing.
 *
 * Only *creating* a wallet counts. A device that already has one, or a request
 * carrying a valid token, never touches this - a reinstall or a whole office on
 * one Wi-Fi re-registering is fine.
 *
 * The limit is deliberately loose: Indian mobile carriers put many phones
 * behind one public address (CGNAT), so a busy tower can create dozens of
 * honest accounts a day. This stops farming, not sharing.
 */
export class IpGateDO extends DurableObject<Env> {
  /** Takes one slot if any are left. False means this network is over today's limit. */
  async take(limit: number): Promise<boolean> {
    const used = (await this.ctx.storage.get<number>("n")) ?? 0;
    if (used >= limit) return false;
    await this.ctx.storage.put("n", used + 1);
    return true;
  }
}

/**
 * The address to count against. IPv6 phones get a whole /64 from their carrier
 * and can change the low half at will, so v6 is counted per /64.
 */
export function networkOf(request: Request): string {
  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  if (!ip.includes(":")) return ip;
  // Expand "::" first: the same /64 can be written with the zero run in
  // different places, and only the expanded form compares reliably.
  const [head, tail] = ip.toLowerCase().split("::");
  const left = head ? head.split(":") : [];
  const right = tail ? tail.split(":") : [];
  const full = tail === undefined ? left : [...left, ...Array(8 - left.length - right.length).fill("0"), ...right];
  return `${full.slice(0, 4).map((g) => g.replace(/^0+(?=.)/, "")).join(":")}::/64`;
}
