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

## AI coins (branch `feat/ai-coins-wallet`)

Every AI call already passes through this Worker, so it is also where AI coins
are enforced. The app only *displays* a balance; charging and crediting happen
here. Design: `docs/FixMoney_Analytics_Siri_Coins_System_Design.pdf` plus the
later decisions recorded in the root `CLAUDE.md`.

| Route | Who calls it | What it does |
|---|---|---|
| `POST /v1/register` | app, on launch | `{deviceKey}` (+ optional `Authorization: Bearer <token>`) -> `{token, accountId, wallet}` |
| `GET /v1/wallet` | app | balance, trial, ads left today, prices |
| `POST /v1/chat` | app | with a token: needs `X-Feature` (fixoo/voice/scan/insight) and `X-Request-Id`; reserve -> model -> commit, or release if the model did not answer |
| `POST /v1/spend` | app | `{feature, requestId}` for features with no model call (the Home insight, free by default) |
| `GET /v1/admob/ssv` | **AdMob's servers** | signed rewarded-ad callback - the only way coins are created |

Status codes the app must handle: `401 UNAUTHORIZED` (register again),
`402 NO_COINS`, `429 TRIAL_LIMIT`, `503 AI_BUSY` (`charged: false`), and on
`/v1/register` only, `429 IP_LIMIT` (too many new wallets from one network today -
the app just carries on without a token). Every
authenticated chat answer carries `X-Coins`, `X-Coins-Available`,
`X-Trial-Ends-At` and `X-Trial-Uses-Left`, so the app never polls for its balance.

- **No login.** The token is an HMAC the Worker signs (`token.ts`); the account
  is found by token first, device key second (`device.ts`), so reinstalls and
  backup-restored phones keep their wallet and trial.
- **One Durable Object per wallet** (`wallet.ts`), not one shared database: one
  request at a time per user, so the last coin cannot be spent twice, and users
  never queue behind each other.
- **Reserve -> commit.** A coin is set aside before the model call and taken only
  if the model answered. Busy/failed calls cost nothing; there is no refund path.
- **Token-less requests still work** (old uncharged path) while `REQUIRE_AUTH` is
  `"false"`, so builds already on test devices keep working. Flip it once every
  shipped build registers.
- Prices, trial length, reward per video and daily caps are `vars` in
  `wrangler.jsonc` as defaults, overridden live from Firestore (below).
- **New wallets are capped per network per day** (`ipgate.ts`, a Durable Object
  per `<ip>:<day>`, IPv6 counted per /64). Only a *new* wallet counts - a known
  device or a valid token never does. `NEW_ACCOUNTS_PER_IP` defaults to 20,
  loose on purpose because Indian carriers put many phones behind one address;
  `"0"` turns it off. It stops scripted trial farming, not sharing.

### Before the first deploy

```sh
npx wrangler secret put TOKEN_SECRET   # long random string; never commit it
npx wrangler deploy                     # migration v1: WalletDO, DeviceDO; v2: IpGateDO
```

AdMob console -> the rewarded ad unit -> Server-side verification -> callback URL
`https://<worker>.workers.dev/v1/admob/ssv`, then put the unit's numeric id in
`REWARDED_AD_UNITS`. The app must set the SSV `user_id` to the `accountId` from
`/v1/register`.

### Local test (verified 2026-10-07: 11 trial + 20 paid checks, plus the race; re-run after the IP limit)

No real Groq or AdMob needed: `test/mock_groq.py` stands in for Groq and
`test/ssvkeys.mjs` signs callbacks with a local key the Worker accepts as
`key_id=test` only in `DEV_MODE`.

```sh
T=$(mktemp -d); echo ok > $T/flag
python3 test/mock_groq.py $T/flag &                      # Groq stand-in on :8799
PUB=$(node test/ssvkeys.mjs $T gen)
cat > .dev.vars <<VARS
GROQ_API_KEY=dev-fake
TOKEN_SECRET=dev-secret
DEV_MODE=true
GROQ_URL=http://127.0.0.1:8799/
SSV_TEST_KEY=$PUB
DEV_NO_FALLBACK=true
FIREBASE_PROJECT_ID=
VARS
npx wrangler dev --port 8787 &
python3 test/e2e.py $T trial     # add TRIAL_DAYS=0 to .dev.vars and restart for: python3 test/e2e.py $T paid
```

`DEV_NO_FALLBACK` matters: without it a mocked Groq 429 is answered by the
**real** Workers AI binding (it is always remote), so the "busy" path never runs.
Delete `.dev.vars` afterwards - with `GROQ_URL` set, `npm run dev` talks to the mock.

**Add `--var REWARDED_AD_UNITS:1234567890` for the paid suite**: `test/ssvkeys.mjs` signs callbacks for that dummy unit, and `wrangler.jsonc` now only accepts the real one (`8131398118`), so without it the first SSV check fails with `ad unit not accepted`.

**`FIREBASE_PROJECT_ID=` (empty) is required since `coin_config/app` went live.**
Without it the local Worker reads the production document and its `trial_days: 5`
silently beats `TRIAL_DAYS=0` from `.dev.vars`, so the paid suite runs in trial.

To leave a device's `wrangler dev` alone, run the suite on a second one:
`npx wrangler dev --port 8788 --env-file <file> --persist-to <dir> [--var TRIAL_DAYS:0]`
and `FIXMONEY_WORKER=http://127.0.0.1:8788 python3 test/e2e.py ...`.

### Live config from Firestore (no deploy)

`vars` in `wrangler.jsonc` are only defaults. Any field in the Firestore document
`coin_config/app` overrides them within 5 minutes: `trial_days`, `trial_daily_cap`,
`reward_per_ad`, `daily_ad_cap`, `welcome_bonus`, `cost_fixoo`, `cost_voice`,
`cost_scan`, `cost_insight` (numbers; int, double or string all accepted; clamped to
sane maxima), plus `premium_daily_cap`.

**Live since 2026-10-07**: the rule below is published in `expenseapp-7b500` and
`coin_config/app` exists with every field set to the `wrangler.jsonc` defaults
(int64). Edit a value in the Firebase console and it applies within 5 minutes.

```
match /coin_config/{doc} { allow read: if true; allow write: if false; }
```

The trial end is `createdAt + trial_days`, computed on every call, so changing
`trial_days` moves the end for **existing** wallets too.

### Testing on a phone against `wrangler dev`

See the root `CLAUDE.md` ("Testing coins on the device against a local Worker"):
`adb reverse tcp:8787 tcp:8787`, then launch the debug build with
`--es worker_url http://127.0.0.1:8787`.

## Fixoo Premium (Rs 99/month Google Play subscription)

Play Billing, not a direct Google Pay link: Play policy requires it for digital
subscriptions, and Play's checkout offers UPI / Google Pay / cards in India.

`POST /v1/premium/verify {purchaseToken}` (device token required) verifies the
token with Google (`purchases.subscriptionsv2.get`), stores Play's expiry in the
wallet and acknowledges the purchase. While active, AI calls cost no coins, up to
`premium_daily_cap` per day. Expired subscriptions are re-verified on the next
`GET /v1/wallet`, which is how renewals and cancellations are picked up.

### One-time setup (Play Console + Google Cloud)

1. Play Console -> set up a **payments profile** (merchant account) if there is none.
2. Upload a build that contains the Billing library (the KMP app) to the **internal
   testing** track - Play only lets you create subscription products after that.
3. Monetize -> Products -> **Subscriptions** -> create `fixoo_premium_monthly`,
   add an auto-renewing **monthly base plan at Rs 99**, activate it.
4. Google Cloud (same project as the app's Firebase is fine) -> enable
   **Google Play Android Developer API** -> create a **service account** ->
   create a JSON key.
5. Play Console -> Users and permissions -> invite the service account's email
   with "View financial data" and "Manage orders and subscriptions".
6. `npx wrangler secret put PLAY_SERVICE_ACCOUNT` and paste the whole JSON key.
7. Play Console -> Settings -> **License testing** -> add your Gmail. Install the
   app from the internal-testing link; test purchases are not charged.

`DEV_MODE` accepts `dev-premium-*` tokens so the flow can be tested locally
without any of the above; production must never set `DEV_MODE`.
