/**
 * Device tokens: the app's only credential, and there is no login behind it.
 *
 * `/v1/register` hands one out per wallet; the app stores it (Android Auto
 * Backup and synchronizable iCloud Keychain carry it to a new phone) and sends
 * it as `Authorization: Bearer <token>` on every call.
 *
 * Format: `v1.<accountId>.<issuedAtMs>.<hmac>`, HMAC-SHA256 over everything
 * before the last dot. Checking one costs microseconds and no network call,
 * which is the point - a JWT from an identity provider would need its public
 * keys fetched and cached for nothing this app uses.
 *
 * Tokens do not expire. A token only proves "this wallet", it unlocks nothing
 * but spending that wallet's own free coins, and the user has no way to sign in
 * again if one were to lapse.
 */

const enc = new TextEncoder();

async function hmacKey(secret: string): Promise<CryptoKey> {
  return crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
    "verify",
  ]);
}

export function b64url(bytes: ArrayBuffer | Uint8Array): string {
  const u8 = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let s = "";
  for (const b of u8) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fromB64url(s: string): Uint8Array {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4);
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export async function signToken(secret: string, accountId: string, nowMs: number): Promise<string> {
  const body = `v1.${accountId}.${nowMs}`;
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(body));
  return `${body}.${b64url(sig)}`;
}

/** The account id the token was issued for, or null if it is malformed or forged. */
export async function verifyToken(secret: string, token: string): Promise<string | null> {
  const parts = token.split(".");
  if (parts.length !== 4 || parts[0] !== "v1") return null;
  const [, accountId, issuedAt, sig] = parts;
  if (!/^[0-9a-f-]{36}$/.test(accountId) || !/^\d+$/.test(issuedAt)) return null;
  let sigBytes: Uint8Array;
  try {
    sigBytes = fromB64url(sig);
  } catch {
    return null;
  }
  // crypto.subtle.verify is constant-time; comparing strings ourselves would not be.
  const ok = await crypto.subtle.verify(
    "HMAC",
    await hmacKey(secret),
    sigBytes,
    enc.encode(`v1.${accountId}.${issuedAt}`)
  );
  return ok ? accountId : null;
}

export function bearer(request: Request): string | null {
  const h = request.headers.get("Authorization");
  if (!h || !h.startsWith("Bearer ")) return null;
  return h.slice(7).trim() || null;
}

export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", enc.encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
