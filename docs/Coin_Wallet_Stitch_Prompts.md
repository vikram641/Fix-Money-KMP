# FixMoney - AI Coins UI prompts for Google Stitch

Written 2026-10-07 for branch `feat/ai-coins-wallet`. Design spec:
`docs/FixMoney_Analytics_Siri_Coins_System_Design.pdf` (section 6-7).

**How to use:** paste **Prompt 0** first, together with 2-3 screenshots of the
current app (Home, Fixoo chat, Settings) so Stitch copies the existing style. Then
paste one screen prompt at a time into the same project.

Colours below are the live `DesignDark` tokens from
`composeApp/.../ui/DesignTokens.kt`, which match the Stitch project's own
"Obsidian Violet Pulse" design system - keep them. The coin colour is that
system's existing `tertiary` amber, so Stitch does not invent a new gold.

**Rules every screen must follow (the app's honesty rules):**
coins have no cash value and cannot be bought; nothing syncs, there is no login,
no account, no email; no "encrypted", "secure vault", "AI confidence %" or other
invented claims. If Stitch adds any of these, delete them before porting.

---

## Prompt 0 - context + design system

```
I am adding an "AI Coins" feature to my existing finance app FixMoney. The
attached screenshots are the current app - match their style exactly: same dark
surfaces, same violet accent, same type, same card radius, same line icons.

What the feature is: tracking expenses is always free. Only the AI features cost
coins - Fixoo AI chat, voice quick-add, and receipt scan. A new user gets 5 days
free. After that, 1 coin = 1 AI use, and the user earns 5 coins by watching one
short video ad. Max 10 videos per day. There is no login and no purchase - coins
cannot be bought and have no money value.

Design tokens (dark):
- Screen background #0E141D, card #161C26, raised element #242A35,
  bottom bar #080E18
- Hairline stroke white at 8% opacity; accent stroke #917EFF at 22%
- Text primary #DDE2F1, muted #928EA1
- Accent #C9BFFF (soft), strong accent #917EFF
- Hero / primary gradient #7B61FF -> #4D35D4; button gradient #9B84FF -> #6B4FFF
- Positive #41E185, negative #FF6B6B
- Coin colour: the theme's existing tertiary amber #FFBA4B (container
  #C48400, on-tertiary #442B00) - use it ONLY for the coin glyph, coin
  amounts and coin pills, nowhere else, so coins are instantly recognisable
  on any screen. Do not introduce any other gold.

Typography: Plus Jakarta Sans. Numbers bold, tabular figures. Coin amounts are
the loudest element on wallet screens.

Coin glyph: a simple flat amber circle with a small sparkle/star cut in the
centre (AI hint). No dollar or rupee sign on the coin - it is not money.

Cards 24px radius, 20px screen gutters, pill buttons 56px tall, bottom sheets
with grab handle and 28px top radius. Line icons only, no emoji.

Never show: buy/purchase coins, prices, "premium", login/email/account,
"synced", "encrypted", "secure", confidence percentages, leaderboards.
```

---

## Prompt 1 - Wallet screen (main)

```
Design the "AI Coins" wallet screen. Opened from the coin chip in the Home
header and from Settings.

Top bar: back chevron, title "AI Coins", no other actions.

Hero card (violet gradient #7B61FF -> #4D35D4): large amber coin glyph on the
left, balance "12" in 44px bold with "coins" beside it in muted text. Under it:
"1 coin = 1 AI use".

Directly below inside the hero, a slim status row - design TWO variants of
this screen:
  Variant A (trial active): a pill "Free trial - 3 days left" with a small
  progress bar showing 2 of 5 days used, and the line "All AI features are
  free until 12 Oct".
  Variant B (trial over): no pill; instead "Earn 5 coins per video".

Primary action card: big full-width gradient button "Watch a video  +5"
(amber coin glyph before +5). Under the button: "7 of 10 videos left today"
with 10 small dots, 3 filled. When all 10 are used the button is disabled and
reads "Daily limit reached - resets tomorrow".

"What coins unlock" card, three rows each with a line icon, name, one-line
description and a "1 coin" amber pill on the right:
  - Fixoo AI chat - "Ask anything about your spending"
  - Voice quick-add - "Say '150 coffee' and it's saved"
  - Receipt scan - "Photo to expense in seconds"
Then one muted row: "Expense tracking, budgets, analytics and export are
always free" with a check icon.

"Recent activity" list (max 5 rows, "See all" link): each row has an icon, a
label and a signed amber amount, newest first:
  "Watched a video" +5 · Today 9:40 PM
  "Fixoo chat" -1 · Today 9:12 PM
  "Voice quick-add" -1 · Yesterday
  "AI busy - not charged" 0 (muted) · Yesterday
  "Free trial started" · 2 Oct

Footer note in small muted text: "Coins stay on this phone. They can't be
bought and have no cash value."
```

---

## Prompt 2 - Out of coins bottom sheet

```
Design a bottom sheet that appears when the user taps an AI feature (for
example sends a Fixoo message) with 0 coins.

Grab handle, then a medium amber coin glyph with a soft amber glow, title
"You're out of AI coins", body "Watch a short video to get 5 coins. Expense
tracking stays free."

Primary gradient button: "Watch a video  +5".
Secondary text button: "Not now".
Small muted line under the buttons: "7 of 10 videos left today".

Design a second state of the same sheet for when the daily video limit is
reached: title "You've used today's videos", body "New videos unlock
tomorrow. You can still add expenses by hand anytime.", single button "OK".
No upsell, no purchase option.
```

---

## Prompt 3 - Video reward states

```
Design the 4 small states around watching a rewarded video. These are
lightweight overlays on top of whatever screen the user was on, not full
screens.

1. Loading: a compact centred card "Loading video..." with a spinner and a
   "Cancel" text button.
2. Reward pending: a toast/snackbar at the bottom "+5 coins on the way..."
   with a small animated amber coin and a thin progress shimmer. (The server
   confirms the reward a few seconds after the video ends.)
3. Reward credited: the same toast turns into "+5 coins added" with the coin
   doing a small pop, and the balance chip in the header ticks up from 0 to 5.
   Show a frame of the header chip mid-animation.
4. Video failed / closed early: toast "Video didn't finish - no coins added.
   Try again?" with a "Retry" action. Neutral tone, not an error red.
```

---

## Prompt 4 - Coin chip + cost hints on existing screens

```
Show how coins appear on existing screens (use the attached Home and Fixoo
chat screenshots as the base and change only what is described):

1. Home app bar (the top row with the wallet logo, "Home", bell and avatar):
   put a compact pill chip with the amber coin glyph and "12" between the bell
   and the avatar. Tapping opens the wallet. Leave the greeting row and its
   sparkle button unchanged. During the free trial the chip
   reads "Trial · 3d" instead of a number, with the coin glyph.
2. Fixoo chat header: the same chip at top right.
3. Fixoo composer: a tiny muted line above the input "Each reply uses 1 coin"
   - only shown after the trial; during the trial it reads "Free during your
   trial".
4. Add Expense screen: the "Scan receipt" button gets a small "1 coin" amber
   pill on its right edge.
5. Settings: a new row at the top of the AI section, "AI Coins" with the amber
   glyph, subtitle "12 coins · 7 videos left today", chevron.

Keep all of these small - coins are a utility, not the centre of the app.
```

---

## Prompt 5 - Trial ended, one-time sheet

```
Design a one-time bottom sheet shown the first time the user opens the app
after their 5-day free trial ends.

Title "Your free trial has ended", body "Expense tracking is still free,
forever. AI features now use coins - 1 coin per use, and every short video
gives you 5." Then a 3-row mini explainer with icons: "Watch a video" ->
"Get 5 coins" -> "Use any AI feature".

Gift row (highlighted, amber accent): "Here are 10 coins to start" with the
coin glyph and "+10".

Primary button "Got it". Secondary text button "See how coins work" (opens
the wallet). Friendly, not salesy - no countdowns, no "upgrade".
```

---

## Prompt 6 - AI busy / not charged state (Fixoo chat)

```
On the Fixoo chat screen (use the attached screenshot), design the inline
message shown when the AI service is temporarily at capacity.

Instead of an assistant bubble, a muted system card in the conversation:
an hourglass line icon, "Fixoo is busy right now", "Try again in about 30
seconds. Your coin was not charged.", and a small "Retry" pill button. Card
uses the raised surface #242A35, no red, no warning colours.

Also show the voice quick-add variant as a toast: "Couldn't reach AI - not
saved, no coin used. Try again."
```
