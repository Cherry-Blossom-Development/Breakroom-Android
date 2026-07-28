# Stripe → Square Migration — Android Side

**Status**: DONE (2026-07-28)
**Was blocked on**: `Breakroom/docs/stripe-to-square-migration.md` (in the Breakroom repo) —
Phases 1-6 have since shipped, so this app's changes were implemented against the real
backend contract.

## Background

Stripe has turned off payment processing on the account. Dallas is migrating all
web-side payment processing to Square. This does **not** affect Google Play Billing
(the in-app subscription purchase flow via `BillingRepository`/`BillingClient`) — that's
a completely separate system and is untouched by this migration. This is only about the
"Billing & Plans" screen's marketplace/Pro-subscription-via-web-Stripe integration, which
is a thin wrapper around the Breakroom backend's REST API.

**The Android app has zero Stripe SDK integration.** It never embeds a card form or talks
to Stripe directly — it only displays a `platform` string returned by the backend and
opens URLs the backend hands back. This means the Android-side change is small: a string
comparison, a hardcoded URL, and some UI copy text. No new dependency, no new SDK, no App
review risk.

## Current state — every Stripe reference in this repo

Confirmed via full-repo grep (2026-07-24) — this is the complete list, not a sample:

- `app/src/main/java/com/cherryblossomdev/breakroom/ui/screens/CollectionsPaymentScreen.kt`
  - Line ~182: `"stripe" -> { { viewModel.startPortal(::openUrl) } }` — platform string
    check that decides which "manage" action to show
  - Line ~196: `"active" -> ActiveConnectCard { openUrl("https://dashboard.stripe.com/express") }`
    — **hardcoded Stripe dashboard URL**
  - Lines ~247, 291, 309, 340, 358, 387, 397, 409-414 — UI copy strings mentioning
    "Stripe" (button labels, explanatory text, fee breakdown copy)
- `app/src/main/java/com/cherryblossomdev/breakroom/data/CollectionsRepository.kt`
  - Line ~379: a section comment `// ── Billing / Stripe Connect ──` — just a comment,
    no logic tied to Stripe specifically (calls generic backend endpoints)
- `app/src/main/java/com/cherryblossomdev/breakroom/network/BreakroomApiService.kt`
  - Line ~888: section comment `// ==================== Billing / Stripe Connect ====================`
    — same, just a comment
- `app/src/main/java/com/cherryblossomdev/breakroom/data/models/BreakroomModels.kt`
  - Line ~158: comment `// Billing / Stripe Connect models`
  - Line ~1494: `val stripe_payment_intent_id: String? = null` — a model field name that
    mirrors the backend's JSON key
- `app/src/main/java/com/cherryblossomdev/breakroom/ui/screens/SessionsScreen.kt` —
  matched in a grep but only as an incidental substring/comment reference, not real logic
  worth tracking separately; re-check when doing the actual edit but expect nothing there

No embedded payment/checkout UI exists anywhere in this app (`PaymentIntent`,
`CardElement`, `checkout` were grepped with no real hits beyond the model field above) —
buyer-side storefront checkout is a web-only feature. This app only handles the
**seller onboarding** (Connect) and **subscriber** (Pro plan billing/portal) sides.

## What needs to change (once unblocked)

1. [x] **`CollectionsPaymentScreen.kt`**: the `"stripe" ->` platform check became
   `"square" ->`. No dual-run branch needed — the backend doc confirmed a hard cutover
   (existing Stripe processing was already fully dead), so there was never a window where
   both platform values needed handling simultaneously.
2. [x] **Dashboard URL**: replaced the hardcoded `https://dashboard.stripe.com/express`
   with `https://squareup.com/dashboard`, matching the link the web app's
   `CollectionsPaymentPage.vue` now uses.
3. [x] **UI copy**: all visible "Stripe" strings swapped to "Square" across both
   `CollectionsPaymentScreen.kt` and `BillingScreen.kt` (the second screen was **not** in
   this doc's original file inventory — found during implementation; it duplicates the
   same platform-check/portal/fee-copy pattern under "Billing & Plans"). Square's
   processing fee is the same 2.9% + $0.30 Stripe used (confirmed in the Breakroom doc),
   so the fee example dollar amounts needed no recalculation, just the label swap.
4. [x] **Model field rename**: the backend went with the generic processor-agnostic rename
   (migration 044), so `BreakroomModels.kt`'s `stripe_payment_intent_id` was renamed to
   `payment_intent_id` to match `orders.payment_intent_id`. It's a Gson-deserialized,
   currently-unused field (no UI reads it), so this was a safe rename with no call-site
   fallout.
5. [x] **Bigger-than-expected gap: the "Manage Subscription" portal is gone, not
   relabeled.** The original assumption in this doc — that the mobile change would just be
   a platform-string swap — undersold one piece: Square has no hosted Billing Portal
   equivalent, so the backend's `POST /api/billing/portal` endpoint was removed entirely
   (replaced by `POST /cancel` and `POST /update-payment-method`, both of which need a
   client-tokenized card via the Square Web Payments SDK — a web-only, JS-based flow with
   no Android equivalent implemented here). Rather than embed Square's native In-App
   Payments SDK (a real new dependency, out of scope for what was meant to be a trivial
   follow-up), both screens' "Manage Subscription" button for `platform === "square"` now
   opens `https://www.prosaurus.com/collections/payment-setup` in the browser, where the
   web app's custom cancel/update-card modals already live. `startPortal()` /
   `isOpeningPortal` and the dead `getBillingPortal` API call were removed from both
   `CollectionsRepository.kt`/`BreakroomApiService.kt` and both screens' ViewModels.

## Progress log

- 2026-07-24: Doc created during planning session. No code changes made yet. Blocked on
  backend work in the Breakroom repo.
- 2026-07-28: Backend Phases 1-6 confirmed shipped (see the Breakroom repo's migration
  doc). Implemented all five items above. Also found and updated a second screen
  (`BillingScreen.kt`, the "Billing & Plans" entry point) with the identical pattern that
  this doc's original inventory missed. Verified with a full `assembleDebug --rerun-tasks`
  build (no errors) and a repo-wide grep confirming zero remaining "stripe"/"Stripe"
  references. Not yet tested end-to-end against a live Square subscriber account (no such
  account exists yet to test against on this device).
