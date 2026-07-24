# Stripe → Square Migration — Android Side

**Status**: BLOCKED — do not start until the backend migration has shipped
**Blocked on**: `Breakroom/docs/stripe-to-square-migration.md` (in the Breakroom repo) —
specifically Phase 3 ("Backend: Storefront checkout + webhooks") needs to be live in
production before this app changes anything, since this app just consumes whatever
`platform` value and URLs the backend's `/api/billing/*` endpoints return.

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

1. **`CollectionsPaymentScreen.kt` line ~182**: the `"stripe" ->` platform check needs a
   `"square" ->` counterpart (or both, if the backend dual-runs during transition — see the
   Breakroom doc's "Open decisions" section for whether dual-run is happening). Confirm
   with the backend team/doc what the new `platform` field value will actually be before
   writing this.
2. **Line ~196**: replace (or add a conditional branch for) the hardcoded
   `https://dashboard.stripe.com/express` URL with whatever Square's seller dashboard URL
   is (Square Seller Dashboard, likely `https://squareup.com/dashboard/` — confirm exact
   URL/deep-link before implementing, don't guess).
3. **UI copy** (lines ~247, 291, 309, 340, 358, 387, 397, 409-414): update visible strings
   ("Connect with Stripe" → "Connect with Square", fee breakdown text, etc.). Check
   whether Square's actual processing fee differs from Stripe's ~2.9% + $0.30 — the fee
   breakdown text is currently hardcoded to Stripe's rate and needs the correct Square
   number, not just a find-replace of the word "Stripe".
4. **Model field name** (`BreakroomModels.kt` line ~1494): only rename if the backend
   actually renames the JSON field. If the backend keeps a generic field name instead
   (per its "Open decision: DB approach"), this Kotlin field may just need to become
   nullable/repurposed rather than renamed. Check the actual backend response shape before
   editing — don't rename blindly.
5. Grep this repo fresh for "stripe" again right before starting (do not trust this list
   if a lot of time has passed or other work has touched these files since 2026-07-24).

## How to verify the backend is actually ready

Before starting, confirm against a real (or staging) backend response:
```
GET /api/billing/plan
```
and check whether the `platform` field can return `"square"` yet, and whether
`GET /api/billing/connect/status` / `POST /api/billing/connect/start` return
Square-shaped URLs. If unsure, check in with whoever/whatever session is working the
Breakroom repo side — don't assume readiness from this doc alone, it may be stale.

## Progress log

- 2026-07-24: Doc created during planning session. No code changes made yet. Blocked on
  backend work in the Breakroom repo.
