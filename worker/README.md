# FixMoney AI proxy

A Cloudflare Worker that holds the Groq key so the app does not have to.

## Why this exists

`Secrets.GROQ_API_KEY` is a compile-time constant, so it lands in the APK's dex
and comes out with `strings` in seconds - verified on a real build. The account
has no card attached, so there is **no billing exposure**: the worst case is
someone draining the org's 1,000 requests/day and every real user getting a
`429`. That makes this an availability fix, not an emergency.

## Why TypeScript and not FastAPI

Python Workers are GA and FastAPI runs on them via the ASGI adapter, so that
route is open. But Python Workers run on Pyodide (WebAssembly), which softens
the ~1ms isolate start that was the entire reason for choosing Workers over a
free container host that sleeps after 15 minutes. This Worker reads a body,
attaches a header and forwards - a framework buys nothing for that.

## Why not Render / a free container host

Render's free tier spins down after 15 minutes idle and takes 30-60s to wake.
For voice capture and receipt scanning that is unusable. Workers have no cold
start (~1ms p50), 100k requests/day free, and need no credit card.

The 10ms CPU limit on the free plan is **CPU** time, not wall-clock - waiting on
Groq is I/O and does not count, so a 3s inference call fits comfortably.

## Two tiers, not two accounts

```
Groq (1,000 req/day)  --429/5xx-->  Workers AI (10,000 Neurons/day)
```

Workers AI is a binding, so tier two needs no key and no second account. Both
allowances are legitimate and separately granted.

Chaining two *free accounts at the same provider* - a second signup on another
email - is a terms violation at every provider that offers a free tier, and the
usual outcome is both accounts banned. That is strictly worse than running out
of quota, which on a card-less account costs nothing and clears at 00:00 UTC.

The chain lives here rather than in the app, so it can be reordered - or a third
tier added - without shipping a release. The fallback reshapes its reply into
the OpenAI `choices[0].message.content` form the app already parses, so no
caller needs a second code path.

## Deploy

```sh
cd worker
npm install
npx wrangler login

# the key never goes in a file - this stores it encrypted on Cloudflare
npx wrangler secret put GROQ_API_KEY

# optional: per-IP daily cap
npx wrangler kv namespace create RATE   # then uncomment kv_namespaces in wrangler.jsonc

npx wrangler deploy
```

Local run: put `GROQ_API_KEY=...` in `.dev.vars` (gitignored) and `npm run dev`.

## Then, in the app

Point the three AI call sites at `https://<worker>.workers.dev/v1/chat` and drop
the `Authorization` header - the Worker adds it. The call sites are:

- `shared/.../ai/GroqChatAssistant.kt`
- `shared/.../ai/VoiceExpenseParser.kt`
- `shared/.../ai/ReceiptTextParser.kt`

The request shape is unchanged (same `messages[]`), so only the URL and the
header go. `model` is **pinned in the Worker** and ignored from the caller.

Once shipped, rotate the Groq key. Doing it before then kills the live Play
Store app's AI features, since that build carries the old key.

## What this does and does not protect

Fixes: the key is no longer extractable, it rotates without an app release, the
model cannot be swapped for an expensive one by a caller, and one IP cannot
drain the daily allowance.

Does not fix: anyone who pulls the endpoint URL out of the APK can still call
it. Closing that needs device attestation - Play Integrity on Android, App
Attest on iOS, or Firebase App Check over both - verified in the Worker before
forwarding. Worth doing only if the endpoint actually starts getting abused.
