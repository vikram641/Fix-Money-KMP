# FixMoney — UI redesign prompts for Google Stitch

Written 2026-09-28 from the screenshots in `~/Downloads/ss_for_fixmoney`.

**How to use:** paste **Prompt 0** first and let Stitch settle the style. Then paste
one screen prompt at a time into the same project so the system stays consistent.
Every screen prompt assumes Prompt 0 is already applied.

---

## Prompt 0 — design system (paste this first)

```
Design a modern 2026 personal finance app for Android called FixMoney. It tracks
daily expenses, budgets per category, and has an AI assistant that answers spending
questions.

Visual direction: dark-first, premium fintech. Think Revolut / Monzo / Copilot
Money, not a generic template. Confident, calm, data-forward.

Colors:
- Base background #0A0E14 (near-black, slightly blue)
- Elevated surface #141A24, second level #1C2430
- Primary accent #7B61FF (violet), with a #9B84FF -> #6B4FFF gradient for the hero
  card and primary buttons
- Positive / under budget #2ED47A, warning #FFB020, over budget #FF5C5C
- Category tokens used consistently everywhere (chips, charts, list icons):
  Food #FF6B6B, Transport #4ECDC4, Shopping #45B7D1, Health #96CEB4,
  Bills #FFC53D, Entertainment #C56BFF, Other #8E9BAE
- Text: primary #F2F5FA, secondary #93A1B5, tertiary #5C6A7D

Typography:
- Headings and UI: Inter or Plus Jakarta Sans, semibold
- All money amounts: same family, BOLD, tabular figures, large and tight-tracked.
  Amounts are the loudest thing on every screen.
- No rounded/quirky display fonts anywhere.

Iconography: a single consistent outline icon set, 1.75px stroke, rounded caps
(Lucide / Phosphor style). NEVER use emoji as icons — not in the tab bar, not in
list rows, not for categories. Categories get a line glyph inside a soft tinted
circle using that category's color at 18% opacity.

Surfaces and depth: cards are filled (#141A24), 24px radius, no visible 1px
outline. Separate them with elevation and spacing, not borders. The primary card
on each screen may carry a subtle gradient and an inner top highlight. 20px screen
gutters, 16px between cards, 12px inside cards.

Components: pill segmented controls, full-width pill primary buttons (56px tall),
bottom sheets with a grab handle and 28px top radius, progress as rounded bars or
rings, chips as 36px tall pills.

Bottom navigation: 5 line icons — Home, Expenses, Analytics, Budget, Settings.
Icons with small labels, active item in violet with a soft filled pill behind it,
inactive in #5C6A7D. Floating action button is a violet gradient circle that sits
above the bar on the right.

Make it feel layered and breathable, with clear hierarchy. Avoid large empty
regions — if a section has little data, fill it with a compact, useful empty state.
```

---

## Prompt 1 — Home / dashboard

```
Design the Home screen.

Top row: greeting "Good evening, Vik" in 26px semibold with the month "September
2026" in secondary text underneath; on the right, a small AI assistant button
(sparkle icon in a soft violet circle) and a circular avatar with the initial "V".

Hero card (violet gradient, 24px radius, subtle inner top highlight):
label "SPENT THIS MONTH" in 11px uppercase letter-spaced, then "₹12,480" in 40px
bold tabular figures. Under it a slim rounded progress bar at 50% and the line
"50% of ₹25,000 budget". Along the bottom, three compact stats separated by thin
vertical dividers: "This week ₹2,150", "Daily avg ₹416", "Left ₹12,520".

Below, a bento grid of two cards side by side:
- Left: a donut chart, 120px, thin ring (18px stroke), category colors, with the
  total in the middle. Small legend underneath as two rows of colored dot + label +
  percentage.
- Right: "AI insight" card with a sparkle icon and one short sentence, e.g. "Food is
  up 32% versus last month." plus a small "Ask Fixoo" text link.

Then a section header row "Recent" with a "See all" link on the right, followed by
4 expense rows: a tinted category circle with a line icon, the note in medium
weight, the category and time in secondary text below it, and the amount on the
right in bold tabular figures.

Bottom navigation with Home active, and a violet gradient FAB with a plus icon
floating above it on the right.
```

---

## Prompt 2 — Expenses list

```
Design the Expenses screen.

Header "Expenses" in 28px semibold, with a search icon and a filter icon on the
right. Below it a search field with a 16px radius, a leading search icon and the
placeholder "Search expenses".

A horizontally scrolling row of filter chips: "All" (active, violet fill), then
"Food", "Transport", "Shopping", "Bills" — each inactive chip carries a small dot
in its own category color.

Then the list, grouped by day. Each group has a sticky-feeling header row with the
date on the left ("Today", "Yesterday", "26 September") and that day's total on the
right in secondary text. Rows inside a group sit on one continuous elevated card
with hairline separators between them.

Each row: 44px tinted circle with the category line icon, note in 15px medium, the
category name in 13px secondary underneath, and the amount right-aligned in 16px
bold tabular figures. A swipe-to-delete state is visible on one row, revealing a
red delete action behind it.

Bottom navigation with Expenses active.
```

---

## Prompt 3 — Add expense

```
Design the Add Expense screen as a full-height bottom sheet with a grab handle.

Title "Add expense" on the left, a close X on the right.

The amount is the hero: a huge centered "₹1,250" in 48px bold tabular figures with
a blinking caret, the currency symbol smaller and in secondary color. Under it a
row of three quick-amount chips: "₹100", "₹500", "₹1,000".

Category picker: a 4-column grid of category tiles, each a tinted rounded square
(20px radius) with the category line icon and its label beneath. The selected tile
is filled with its category color at full saturation with a white icon and a small
check badge.

Below that: a "Note" text field with a 16px radius, and a date row showing a
calendar icon with "Today, 28 Sep" and a chevron.

Two secondary actions side by side as outlined tiles: "Scan receipt" with a camera
icon and "Attach photo" with an image icon.

A full-width violet gradient "Save expense" pill button pinned at the bottom, 56px
tall.
```

---

## Prompt 4 — Analytics

```
Design the Analytics screen.

Header "Analytics" with a month pill on the right reading "Sep 2026" with a chevron.

A segmented pill control with three options: "Month" (active), "6 months", "Year".

Primary card: "Monthly spending" label, a big total "₹12,480" with a green "-8% vs
Aug" delta pill next to it, then a 6-column bar chart. Bars are rounded, violet
gradient, the current month brighter and labelled with its value above it; earlier
months in a muted violet. Month labels underneath in tertiary text and no visible
axis lines.

Second card "Top categories": rows of a tinted category circle with a line icon,
the category name, the amount right-aligned in bold, and under each a thin rounded
progress bar in that category's own color showing its share.

Third card "Spending pattern": a small area chart with a soft violet gradient fill
showing daily spend across the month, with the peak point marked by a dot and a
tooltip reading "₹1,850 · 14 Sep".

Bottom navigation with Analytics active.
```

---

## Prompt 5 — Budget

```
Design the Budget screen.

Header "Budget" with a month stepper below it: a left chevron, "September 2026",
a right chevron, all inside one pill.

Overview card: a large circular progress ring (160px, 14px stroke) with "₹12,480"
in the middle in bold tabular figures and "of ₹25,000" in secondary text under it.
The ring fills in violet, and turns amber past 80% and red past 100%. To the right
of the ring, or under it, two stat lines: "Left ₹12,520" and "14 days to go".

"Categories" section: one card per category, each showing the tinted circle with
its icon, the category name, "₹3,200 of ₹5,000" in secondary text, a percentage on
the right, and a thick rounded progress bar in that category's color. One category
is shown over budget: red bar, red amount, and a small "Over by ₹400" chip.

A dashed-outline "Add category budget" row with a plus icon at the end of the list.

Bottom navigation with Budget active.
```

---

## Prompt 6 — Set budget sheet

```
Design a "Set budget" bottom sheet with a grab handle and 28px top corners.

Title "Set budget" on the left, close X on the right.

A horizontal row of category tiles to choose from — tinted rounded squares with
line icons and labels, the selected one filled in its own category color.

A month pill reading "September 2026" centred below.

The amount input is the hero: a large centred "₹5,000" in 40px bold tabular
figures with an underline that glows violet when focused. Under it, three preset
chips: "₹2,500", "₹5,000", "₹10,000".

A small helper line in secondary text: "You've spent ₹3,200 in Food this month."

Two buttons at the bottom on one row: "Cancel" as a ghost button with a subtle
outline, and "Save budget" as a violet gradient pill, the primary one wider.
```

---

## Prompt 7 — Fixoo AI chat

```
Design the "Fixoo" AI assistant chat screen.

Header: a back chevron, a 36px violet gradient circle with a sparkle icon, then
"Fixoo" in semibold with "AI expense assistant" in 12px secondary underneath, and a
small online dot.

Empty state, centred in the upper third: a large soft violet glow behind a sparkle
icon, the line "Ask me anything about your spending" in 18px semibold, and a
secondary subtitle.

Below it, suggestion cards in a 2-column grid rather than a cramped chip row — each
card has a small icon, a short question ("Am I over budget?", "Biggest expense this
week", "Where did my money go?", "Compare to last month") and a soft tinted
background.

Show one exchange above the input: a user bubble aligned right in violet, and an
assistant reply aligned left on an elevated surface with a sparkle avatar. The
assistant reply includes a small inline breakdown — three rows of colored dot,
category name and amount.

Input bar pinned at the bottom: a rounded 24px field with the placeholder "Ask
about your spending", a mic icon inside on the right, and a violet gradient circular
send button beside it.
```

---

## Prompt 8 — Settings

```
Design the Settings screen.

Header "Settings" in 28px semibold.

Profile card: a 56px violet gradient circle with the initial "V", the name "Vik" in
17px semibold, "Free plan" in 13px secondary underneath, and a chevron on the right.

Then grouped setting cards with section labels above each group in 12px uppercase
tertiary text.

PREFERENCES: "Dark theme" with a moon line icon and a toggle (on), "Budget alerts"
with a bell line icon and a toggle (on), "Currency" with a globe line icon and the
value "INR" plus a chevron.

QUICK ADD: "Floating voice bubble" with a mic line icon, a toggle, and a two-line
secondary description "Tap a floating bubble anywhere to add an expense by voice".

TOOLS: "Export data" with a download line icon and a chevron, "SIP calculator" with
a trending-up line icon and a chevron.

Every icon sits in a 40px rounded square tinted at 12% of the accent — NOT emoji on
white circles. Rows inside a group share one card with hairline separators.

Bottom navigation with Settings active.
```

---

## Prompt 9 — Onboarding (3 screens)

```
Design a 3-screen onboarding flow.

Screen 1 — welcome: a large violet gradient blob glow behind a wallet line icon
centred in the upper half, the headline "Money, finally under control" in 32px
semibold with a tight line height, a two-line secondary subtitle, and a full-width
violet gradient "Get started" pill button. A "Sign in" text link below it.

Screen 2 — your name: at the top a slim progress bar showing step 1 of 3 with a back
chevron on the left. An icon tile in violet gradient, the headline "What should we
call you?", a helper line, and a single large text field with a 16px radius and a
violet focus ring. A "Continue" pill button at the bottom.

Screen 3 — monthly budget: same progress header at step 2 of 3. The headline "Set
your monthly budget", then a huge centred "₹25,000" in 44px bold tabular figures,
three preset chips beneath it, and a slider with a violet filled track showing the
range "₹0 – ₹1,00,000". Below, "Track these categories" with a horizontal row of
category tiles that can be toggled on, each in its own color when selected.
A "Continue" pill button at the bottom.
```

---

## What the redesign should fix

These are the specific weaknesses in the current build, so check the output against them:

| Current | Should become |
|---|---|
| Emoji as icons in tab bar, settings rows, category tiles | One consistent outline icon set |
| Every card is a flat 1px outlined rounded rect | Filled surfaces separated by elevation and spacing |
| Rounded display font for numbers too | Tabular-figure bold numerals, amounts as the loudest element |
| Huge flat single-color donut with side legend | Thin ring with the total inside, legend underneath, in a bento grid |
| Large dead space on Home, Analytics and Chat | Compact useful sections; empty states that still say something |
| Placeholder icons on SIP inputs (card / sparkle / bell) | Icons that match their field |
| Ad banner wedged between content and nav | Its own slot with clear separation, or inside the list |
| Set Budget sheet mixes underline and outlined inputs | One input style throughout |
