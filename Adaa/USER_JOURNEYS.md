# Adaa — user journeys and screen ownership

## First launch

Native splash → Welcome. The three paths are explicit: **Explore businesses**, **Set up my business**, or **Sign in**. Exploring is available without an account or location permission. The welcome screen is not shown on every subsequent launch.

## Customer

Welcome → Explore → search/category → business profile. Account offers sign-in and account creation. A returning customer with a valid restored session lands directly in Explore. There are no nonfunctional booking, favorites or payment controls in this foundation.

## New business

Welcome/Explore → Set up my business → Sign in or Create account → email confirmation when enabled → My businesses → Set up my business.

1. **Business basics:** name, primary category, optional introduction.
2. **How you work:** physical location, service area, or online. Collect only the relevant location information and explain its public visibility.
3. **Review:** check the details, then create a private draft.

Successful creation opens Business home. The home explains publication status and the next step. Review listing → Publish business → explicit confirmation → live listing. Publishing is independent of creating the draft. Edits to an already published listing go live immediately; the review screen explains this before saving.

The draft remains in the ViewModel/SavedStateHandle across rotation and Android task restoration. It is not a server business until the final submit succeeds. Repeated submission after a lost response reuses the same idempotency identifier. If creation succeeds and the following dashboard fetch fails, the UI switches to the new business home with a retry action; it does not offer to create the same business again.

## Returning business member

Splash → validate/refresh saved session → fetch current memberships → reopen the remembered business if still permitted. A single available business opens directly. Multiple businesses without a permitted remembered selection show a chooser. No memberships show a clear setup/join-team entry screen. Removed membership never opens a private dashboard. A staff member can review the listing but does not receive edit or publication controls.

**Switch business** opens the chooser. **Explore** changes to customer mode. **Account** explains that one sign-in works for both modes and provides sign-out. Business selection is saved per user, not shared across accounts.

## Sign-in and recovery

Sign in and Create account are separate destinations. Forms explain invalid fields, hide passwords by default, offer password reveal, retain values on a failed attempt, and keep passwords out of saved state. Confirmation has resend and manual sign-in fallback. Forgot password sends a reset email without revealing whether an account exists. A valid PKCE email callback opens the new-password screen. Recovery sessions return to that screen after restart until the password is changed or the user signs out.

Session restoration errors offer **Try again** and **Browse without signing in**. Temporary connection failures do not erase encrypted saved credentials. Invalid/expired refresh sessions require sign-in. Sign-out always clears local identity and private screen data.

Required hosted setup: apply the backend migrations and allow the callback URL documented in `Backend/README.md`. Email sending and delivery must be verified with the project's configured provider. Local/UI tests do not certify delivery of real hosted confirmation or recovery emails.

## Code map

| Responsibility | File/directory |
| --- | --- |
| Native splash and email intent entry | `MainActivity.kt`, `res/values/themes.xml` |
| Screen host, bottom navigation, design previews | `ui/FoundationApp.kt` |
| Welcome and session reconnect | `ui/shared/WelcomeScreen.kt` |
| Sign-in, registration, email and password recovery | `ui/auth/AuthScreens.kt` |
| Customer Explore and shared Account | `ui/personal/DiscoveryScreens.kt`, `ui/shared/AccountScreen.kt` |
| Three business setup steps | `ui/business/BusinessSetupScreens.kt` |
| Business chooser, listing home and profile | `ui/business/BusinessHomeScreens.kt` |
| Reusable typography, buttons, fields, cards and vector artwork | `ui/shared/JourneyComponents.kt`, `ui/theme` |
| Presentation action contract | `ui/shared/JourneyActions.kt` |
| Navigation decisions, UI state, request serialization | `ui/viewmodel/FoundationViewModel.kt` |
| Models and gateway/preferences interfaces | `data/model/JourneyModels.kt` |
| Supabase HTTP/Auth/PKCE adapter | `data/remote/SupabaseRepository.kt` |
| Keystore session encryption and per-user preferences | `data/session` |
| Business rules, role checks and dashboard contracts | workspace `Backend` folder |

Screen composables receive state and callbacks; they never make network requests or create ViewModels. Backend permission checks remain authoritative even when UI controls are bypassed.


## Account categories and UI ownership

Create account now opens an explicit Personal / Business choice before the credentials form. Personal registration continues to Explore; Business registration continues to business setup/workspace. Supabase Auth stores `user_metadata.account_type` (`personal` / `business`) and the app reads it on sign-in, restoration and email callbacks. This is a navigation preference, never a permission grant: database membership remains authoritative. Existing accounts without metadata retain their previous routing behavior. No new SQL migration is needed for this signup metadata.

- `ui/personal`: discovery screens for personal users.
- `ui/business`: business onboarding, listing management and listing presentation.
- `ui/auth`: account category selection, sign-in, registration and recovery.
- `ui/shared`: shared account screen, welcome, components and presentation callbacks.
- `ui/FoundationApp.kt`: navigation host and Compose previews.

A public business listing is shared by both audiences and reuses the business presentation component. Team permissions remain owner/manager/staff, independent of the account category.


## Business dashboard and M-Pesa

Business home is the post-authentication dashboard for authorized business accounts. It shows backend team/location counts, publication state and next actions. The owner-only M-Pesa balance panel explicitly says not connected; no placeholder monetary amounts or transaction totals are presented as real.

Payment provider selected: Zadhron Payments (https://payments.zadhron.com/docs), using M-Pesa. Public documentation describes asynchronous STK collection and payout operations. The confirmed design uses gateway deposits/withdrawals and an internal Adaa ledger for service payments. A provider shortcode balance is not automatically the balance of an Adaa tenant. No gateway tokens belong in Android. No live charges or payouts were initiated during this work.


### Confirmed wallet model

Zadhron/M-Pesa is used only for money entering or leaving Adaa: customer wallet deposits and customer/business withdrawals. Service payments between Adaa wallets are internal ledger movements, with escrow holding funds according to service state. A direct payment to a business till is external/offline and must not credit an Adaa wallet or be counted as wallet collections. Internal transfers require transactional balanced ledger entries and idempotency rather than independent client-side balance edits. The owner or an explicitly authorized member confirms completion and releases escrow; business withdrawals remain owner-only. Backend/005_wallet_escrow.sql provides the ledger/RPCs. Backend/supabase/functions/wallet-worker provides server-side gateway dispatch and reconciliation; deployment, secrets and hosted sandbox verification remain pending.


## Current wallet dashboard delivery

Business home now reads owner-only `get_wallet_dashboard` after loading listing data. Available balance, escrow, pending withdrawals, completed-service value/count and recent ledger movements come from the server; missing/offline wallet data is labelled unavailable and is never replaced by fabricated amounts. Staff do not see wallet totals. Team permissions live in `ui/business/BusinessTeamScreen.kt`; an explicit confirmation grants/revokes existing employees' escrow-release ability. New members/invitations remain separate. Personal wallet checkout, business service completion, refund and dispute screens are now implemented in the payment feature described below.

See Backend/WALLET_SETUP.md for migration/deployment order and activation. Payment processing defaults to disabled. Compose previews include account type, business wallet dashboard and team permissions. Device tests cover owner/staff visibility, grant confirmation and screenshot fixtures; real gateway settlement has not been exercised.


## Personal and business payment screens

Account -> My personal wallet / My payment requests. Business home -> Open business wallet (owner only) / Services & payments (members). `ui/personal/PersonalWalletScreen.kt` and `ui/business/BusinessWalletScreen.kt` provide audience-specific entry points; common transaction UI lives under `ui/shared/WalletScreens.kt` and `ServicePaymentScreens.kt`. `PaymentFeature.kt` connects a separate `PaymentViewModel` to `PaymentRepository`/RPCs. Each user/business scope has isolated financial state, cleared on leaving. The shared Auth repository serializes token refresh and rejects financial requests bound to another signed-in user.

Deposits/withdrawals have separate form, quote/review and request-status screens. Payment requests require customer confirmation before a wallet debit. Business completion/refunds require explicit confirmation. Direct till/cash reports require business verification and never credit wallets. Customers open/respond to disputes; owners/managers can respond; platform-admin RPCs resolve them. Status/history are server-derived and paginated. The owner wallet shows dispute-based withdrawal holds. Completed-service refund attempts cannot overdraw the business.

Compose previews are in `ui/personal/PersonalPaymentPreviews.kt` and `ui/business/BusinessPaymentPreviews.kt`; preview fixtures are isolated under `ui/preview/PaymentPreviewData.kt` and never used by runtime repositories. See Backend/PAYMENT_RELEASE.md for migration 008, development checks 009 and monitoring schedule 010. Live activation still needs approved limits/fee policy/dispute ownership, deployed worker secrets, verified production provider environment and explicitly authorized live settlement checks. Request-based appointments and inquiries are described below.

## Storefronts and request-based appointments

Explore → storefront now includes services and prices, opening hours, location/directions, contact links and a photo gallery. Customers select a service, preferred date/time and message, then review and explicitly submit a request. Guests continue through sign-in without automatically sending it. All times use the business's configured IANA time zone. Multiple customers can choose the same time; no seat/employee capacity is assumed.

Account → My bookings & inquiries contains confirmations, replies, cancellation and event history. Business home → Storefront, bookings & inquiries contains the business inbox, service editor and public-detail/settings editor. Owners/managers confirm/decline/cancel appointments, reply to inquiries and record completion/no-shows after the requested start. Staff read the inbox without gaining booking management rights. Status filters run on the database before cursor pagination. Inboxes and open details refresh while the app is foregrounded.

`ui/personal/StorefrontScreen.kt`, `ui/business/StorefrontManagementScreens.kt` and shared request/review/inbox/detail screens remain separate from `BookingViewModel` and `BookingRepository`. Storefront and service-editor previews use `@Preview`; runtime content comes from RPCs. `Backend/011_storefront_bookings.sql` is the new migration, with rollback checks in 012 and setup in STOREFRONT_SETUP.md. No booking operation moves wallet funds. Payment/refund approval remains in the existing payment feature. New push reminders and device photo upload are not implemented; public HTTPS image links and in-app updates are supported.
