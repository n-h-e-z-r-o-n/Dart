# Adaa: next-chat handoff and employee-workspace plan

Saved November 5, 2026. This document records the product discussion, current implementation and next proposed build. Employee invitations, employee workspaces and portable employment history are **planned, not implemented**. The latest user request was to write this handoff only, then stop. Do not interpret the roadmap below as a request to implement every phase immediately.

## 1. Project and working preferences

- Workspace: `C:\Users\user\Desktop\Kotlin Apps\Dart` on Windows, PowerShell.
- Android application: `Adaa/`, package `com.zadhron.adaa`, Kotlin and Jetpack Compose. Backend implementation belongs in `Backend/`.
- The user wants production implementation, clear journeys and polished UI, not placeholder controls or prototype behavior. Screens should be focused and separated by audience, with shared components where appropriate. Add Android Studio `@Preview` functions.
- The user prefers Supabase SQL Editor-ready migrations and RPCs for transactional rules, permissions and calculations. Use Edge Functions for external secret-bearing integrations; triggers/cron only where needed. Give the user exact files to run rather than claiming that writing SQL deploys it.
- No PR was requested. The user explicitly said not to create a PR; focus on code and conserve tokens. No commits or PRs were created in this work.
- Preserve unrelated existing changes/deletions in `.idea` and old Dart files. Most Adaa/Backend files were already untracked; do not mistake that for an instruction to delete, recreate or commit them.
- There are no running build, test or deployment commands left from this delivery. No employee feature build was started. Background tools such as Docker Desktop or an idle Gradle daemon may remain; they do not mean an application task is running.

## 2. Agreed direction: one identity, separate business relationships

The user initially described two account categories: **Personal** and **Business**. The application currently offers those choices at signup. The subsequent employee discussion refined the intended model:

1. One person has one Adaa identity/sign-in.
2. Personal mode provides customer functions: discovery, bookings, inquiries, wallet and service-payment requests.
3. A business is a separate workspace. A person who chooses Business at signup creates their identity and then sets up a business as its owner.
4. A business membership connects a person to a particular business, with a role and explicit permissions.
5. An existing personal/customer user can become an employee by accepting a business invitation using the same account. No second registration and no loss of customer features.
6. The person should be able to switch between Personal and their authorized workspaces. Owner/manager functions belong inside the relevant business workspace.
7. A person may belong to multiple businesses. Roles and permissions must stay separate for each membership.
8. Leaving a business ends that membership's operational access, not the person's Adaa account.

**Do not introduce Employee as a third global account type.** An employee profile is business-scoped operational information attached to a membership. A later portable professional/work-history profile belongs to the person. These are different concepts.

The existing auth `user_metadata.account_type` should remain an onboarding/landing preference, never an authorization source. Do not assume that a person choosing Personal cannot own a business, or that choosing Business grants access to any workspace.

## 3. Proposed employee experience

### Joining

- Owner invites a person to the intended business with a role/capabilities.
- The person signs in or creates an account and explicitly accepts the invitation.
- Accepting binds the invitation to the authenticated intended recipient. Possession of an arbitrary user UUID or another person's email must not grant access.
- No silent membership creation merely because an owner typed someone's email. Avoid account-discovery/enumeration leaks.
- Existing members should not be duplicated by retries or repeated invitations. Invitation expiration, cancellation and duplicate handling need explicit rules before shipping.

### Working

- Employee sees a workspace with assigned appointments/jobs, tasks, working hours and the customer information necessary for those assignments.
- Business-specific profile can contain job title, services performed, working hours, assignments and relevant permissions. Those fields are proposed and are not currently implemented.
- Owner/manager manages the team according to their authorized scope. Membership role changes must be enforced by the database, not just hidden buttons.
- Public staff profiles and letting customers choose a professional were suggested as optional later features, not an agreed requirement for phase one.
- Being an employee must not automatically grant business wallet visibility, withdrawals or escrow release.
- The existing owner-granted escrow-release permission remains separate. Withdrawal is currently owner-only. Adding employee functionality must not broaden either permission implicitly.

### Leaving and returning

- Offboarding revokes membership-based business access, assignments as appropriate and any delegated capabilities; the personal account stays usable.
- The proposed history model must preserve past employment/membership records rather than deleting all evidence of the relationship.
- The existing removal RPC currently deletes a membership. Design a forward migration and lifecycle before relying on it for employment history.
- Rejoining the same business should create/represent a new period without rewriting the previous period. Multiple concurrent employers must be supported.
- Removing the last owner/ownership transfer, invitations to existing owners and delegated management rights need explicit design; do not silently invent rules.

## 4. Portable work history: later phase

The user wants a person moving between businesses that use Adaa to retain a work record, and potentially share it with a prospective employer for reference checks.

Proposed record: business identity, job title/role, start/end dates, the recorded relationship, verification status and supporting audit events. Distinguish these facts carefully:

- **Adaa membership recorded:** the system knows when an invitation was accepted or business access existed.
- **Employment dates verified by the business:** a separately confirmed claim about actual employment dates.
- **Employee-provided claim:** must not be displayed as verified merely because it was entered.

An invitation alone does not prove actual employment, qualifications, attendance or employment duration. Never automatically backdate verified history from a self-declared date or from a migration's creation timestamp.

The discussion proposed employee-controlled sharing, correction requests and a way to dispute inaccurate records. Exact consent, retention, verification and dispute rules remain to be agreed. New employers must not automatically see an employee's entire history or private business records.

Private manager notes, disciplinary records, customer data and internal business documents do not belong in a portable shared profile. Do not build a hidden blacklist, automatic trust score or automated hiring decision from membership events.

## 5. Suggested build order (proposal, not work already performed)

### Phase 1: memberships, invitations and workspace switching

- Inspect existing membership and permission RPCs first.
- Design an additive membership lifecycle that preserves current businesses/owners and supports offboarding without destroying history.
- Implement invitation creation, recipient review/acceptance, cancellation/expiry and retry-safe acceptance with RLS/RPC enforcement.
- Add owner team screens, member profile details and Personal/Work switching. Restore only currently authorized workspaces on returning sign-in.
- Show invitations and access changes clearly, including expired/revoked invites, wrong-account acceptance and lost membership while a screen is open.
- Acceptance checks: cross-business isolation, recipient binding, duplicate/replayed acceptance, concurrent accept/revoke, privilege escalation prevention, immediate offboarding enforcement, and preservation of personal account access.

### Phase 2: employee operations and CRM

- Assign bookings/jobs to authorized employees; show their own assignments and permitted customer context.
- Add a business calendar, working hours/leave and manual capacity decisions. Do not assume one employee/seat per business.
- Add customer CRM linked to actual bookings: customer relationships, visit history, preferences, notes and follow-up tasks, with business-specific access controls.
- Add notification delivery for confirmations, reminders, changes and replies, with retries and delivery status. Current booking updates are in-app only.

### Phase 3: portable employment history

- Introduce recorded relationship periods and a separate employment verification workflow.
- Add employee review/correction/dispute and explicitly scoped history sharing.
- Plan retention/deletion and historical business identity before shipping. Removing access and retaining history are different operations.

### Open decisions before implementation

- Invitation channel (email, in-app or both), expiry period and recipient matching/account-linking rules.
- Which team actions managers may perform; whether selected staff can manage bookings through explicit capability.
- Required/optional employee profile fields, working-hours model and assignment visibility.
- Owner transfer/last-owner rules and treatment of inactive/rejoining memberships.
- History verification authority/evidence, consent/sharing, corrections/disputes and retention/deletion rules.
- Historical migration labels: do not present inferred membership periods as verified employment.

## 6. Broader business-feature backlog discussed

**First:** lightweight customer CRM; business booking calendar and staff assignment; team invitations/permissions/hours; customer notifications; quotes/invoices with approvals and outstanding amounts.

**Then:** job assignment/checklists/status tracking; real booking/customer/payment reports; expenses and receipt attachments; inventory, consumption, low-stock alerts and supplier purchases.

**Later:** loyalty/packages/memberships; consent-based follow-ups; verified-service feedback/reviews; multiple branches; commissions with refund adjustments; customer agreements, job photos and approvals with access control.

This is an Odoo-like modular direction, not a request to recreate Odoo or implement every module now. The suggested next focus was CRM linked to bookings, staff assignment and reminders; the employee discussion makes membership/invitation foundations a prerequisite for employee operations.

## 7. Existing implementation: accounts and business foundation

- Premium native plum/champagne splash; guided welcome/auth/signup/email confirmation/recovery and business setup screens.
- Separate Personal/Business signup choice saved to Supabase account metadata. Owner/manager/staff are business roles, not signup categories.
- Encrypted persisted sessions, shared Supabase Auth repository with serialized token refresh, expected-user binding for authenticated operations and remembered business selection scoped to the user.
- Three named business setup steps, retry-safe business creation, draft/published listing state, public discovery and guarded business editing.
- `public.business_members` currently has business/user composite key and roles `owner`, `manager`, `staff`.
- Existing `set_business_member`/`remove_business_member` RPCs operate on existing accounts. They are not an invitation/consent/work-history system.
- Existing owner team screen controls escrow-release permission for current members; a complete employee invitation/profile/scheduling interface does not exist.

Key files:

- `Adaa/app/src/main/java/com/zadhron/adaa/ui/FoundationApp.kt`: route host.
- `ui/viewmodel/FoundationViewModel.kt`: session/onboarding/workspace navigation state.
- `data/model/JourneyModels.kt`: account modes, routes, business/member models and gateway contracts.
- `data/remote/SupabaseRepository.kt`: shared authenticated transport/session handling.
- `ui/auth/`, `ui/business/`, `ui/personal/`, `ui/shared/`: audience-specific screens and shared presentation.
- `Backend/001_foundation.sql`, `003_user_journey.sql`: original schema, membership and guided journey RPCs.

## 8. Existing storefront and booking delivery

The business profile previously had little information and no action. The current code adds:

- Public storefront: business description, real service catalogue/prices/durations, location/directions, opening hours, contact links and up to eight HTTPS photos (first is cover). Photo upload from the phone is not implemented; public image links are supported with bounded downloads/decoding and fallbacks.
- Owner/manager service and public-detail editors, hidden/inactive services, request pause setting and configured IANA business time zone (default Africa/Nairobi).
- Customer appointment request: choose service, preferred date/time, message, review and explicit send. Guest drafts survive sign-in without automatically submitting.
- General inquiries without a service/time, with business reply/closure.
- Personal and business inboxes with database status filters before stable cursor pagination (30/page), detail/history, foreground refresh and manual refresh.
- Server state transitions for pending, confirmed, completed, declined, cancelled, answered and no-show; cancellation/decline/no-show reasons required. Completion/no-show cannot be recorded before the requested start.
- Service name, price, duration and time zone are snapshotted. Changed price before submission requires fresh review; edits do not rewrite existing requests.
- Stable references prevent duplicate requests after uncertain network results; 20 newly created requests per customer per hour is an abuse limit, not a seat limit.
- Owners/managers manage bookings; staff currently read business requests but cannot accept/decline/edit. This is separate from escrow-release delegation.

**Critical user correction:** do not allocate a unique slot or impose one customer per time. A business may have multiple staff/seats. Multiple customers can request the same time, and the business manually confirms whichever it can serve. The UI must say Awaiting business confirmation until accepted. There is no automatic expiry or implicit capacity cap.

Changing time currently means cancelling with a reason and sending a new request, not silent rescheduling. Unpublished/suspended businesses cannot receive new requests/confirmations; existing records remain accessible to the parties. Pausing does not cancel existing bookings. No push reminders are implemented.

Booking actions do not debit wallets, create service charges or release escrow. Service payment requests are a separate existing workflow requiring customer approval. Cancelling an appointment does not automatically refund payment.

Files:

- `data/model/BookingModels.kt`, `data/remote/BookingRepository.kt`.
- `ui/viewmodel/BookingViewModel.kt`.
- `ui/personal/StorefrontScreen.kt` (including customer storefront preview).
- `ui/business/StorefrontManagementScreens.kt` (including service-editor preview).
- `ui/shared/BookingFeature.kt`, `BookingScreens.kt`.
- `Backend/011_storefront_bookings.sql`, `012_storefront_booking_checks.sql`, `STOREFRONT_SETUP.md`.

The public storefront draft survives guest-to-user sign-in. Switching away from an already bound identity clears that person's private draft/request state. Private inbox/workspace view models are keyed by identity/business. Authenticated repository requests verify the intended account; do not weaken this when adding employee switching.

## 9. Backend application/deployment boundary

- Supabase project ref: `tzigjjmdgdatauhhhbqq`; URL: `https://tzigjjmdgdatauhhhbqq.supabase.co`.
- Public app configuration is already in Android BuildConfig. Do not copy secret/service-role/gateway credentials into this handoff, app or source. A secret was pasted earlier in the chat and was not used or stored in the implementation.
- User previously reported foundation/journey migrations applied and working business onboarding.
- User reported applying wallet 005 and payment-workflow 008.
- **User explicitly reported applying 011 storefront/bookings during this session.** Do not tell them to rerun it without a specific reason. It was designed to run once, not as an idempotent schema replay.
- This agent did not apply migrations to the hosted project. Local PostgreSQL checks are not proof of authenticated live hosted booking writes.
- 011 alone is the required new schema migration for storefronts/bookings. No Edge Function or cron is required for that feature.
- 012 is a rollback acceptance suite for a development database, not a production migration. Other even-numbered acceptance suites likewise must not be presented as required production upgrades.
- **Optional `013_example_home_services.sql` is sample data, not a schema migration.** The user authorized fabricated services after being asked for real ones. The file targets the existing published Home services business named `cpact`, is retry-safe without overwriting edits, and inserts five clearly labelled inactive examples. This agent has not applied it; the user has not confirmed applying it.
- Sample services: home cleaning KES 2,500/120min; deep cleaning KES 6,500/240min; plumbing assessment KES 1,500/45min; electrical assessment KES 2,000/60min; handyman visit KES 2,500/90min. All are hidden until reviewed/enabled in the service editor. These are invented examples, not verified business offerings or approved prices.

## 10. Wallet/payment architecture and outstanding deployment

- Gateway: Zadhron Payments at `https://payments.zadhron.com`, M-Pesa integration.
- Use gateway only for money entering/leaving Adaa: customer wallet deposits and customer/business withdrawals. Service payments are internal, atomic, balanced ledger transfers into business escrow, then released to business available funds after authorized completion.
- Direct cash or business till payments are external/offline records and never credit Adaa wallets.
- Owner may release escrow and explicitly delegate that permission to selected employees. Delegation does not grant withdrawal access. Business withdrawals remain owner-only.
- Available funds, escrow and pending withdrawals are separate. Uncertain payouts remain reserved for reconciliation, not silently refunded or made spendable.
- Private append-only double-entry ledger, row locks, idempotency, settlement evidence checks, dispute holds, admin resolution, refunds, gateway outbox/reconciliation and operational/custody monitoring are implemented. SQL installation leaves external gateway processing disabled.
- Personal and business wallet/payment/refund/dispute screens exist. They never substitute preview balances for runtime data.
- `Backend/supabase/functions/wallet-worker/index.ts` is a **Supabase Edge Function** named `wallet-worker`; `gateway.ts` is supporting code. These cannot be pasted into SQL Editor.
- Gateway worker deployment, live credentials/scopes/environment, worker secret, scheduling/monitoring setup and verified live settlement have not been independently completed/confirmed here. Supabase CLI had no authenticated project access; no deployment was attempted.
- User explicitly wants a production gateway, not a mandatory sandbox deployment. Documentation was updated to production configuration and explicitly authorized live verification. General integration permission does not specify an amount/destination for spending real money; no live deposit/payout was initiated.
- Approved min/max/daily external limits, fee policy and dispute ownership remain unresolved. Current fee implementation supports zero Adaa platform fees only. Do not invent approved monetary rules or remove the gate just to dismiss a banner.
- Gateway upstream environment is selected by its operator, not by an Android request. Current worker reconciles through authenticated transaction polling, not an unverified merchant callback.
- See `Backend/PAYMENT_RELEASE.md`, `WALLET_SETUP.md`, SQL 005/008 and optional schedules 007/010. Those deployment steps are separate from the now-applied storefront migration.

## 11. Verification completed and limitations

- Final Android debug build and lint passed. Lint has zero errors and existing warnings; no lint checks were disabled. One intermediate lint run crashed internally; a later complete final run passed.
- All **41 unit tests** passed: 8 booking, 21 foundation journey, 11 payment and 1 example.
- All **six booking phone tests** passed together on the installed final app: native date/time appointment selection/review/send, inquiry review/send, request pause, staff restrictions, explicit business acceptance and service editing.
- The two existing business onboarding/accessibility phone tests also passed earlier in this delivery. Historical full payment/device runs are documented separately; do not claim the entire old suite was rerun on this final build.
- An earlier appointment phone test failed with missing Compose hierarchies. Its isolated rerun and then the complete six-booking-test rerun passed. Root cause of that transient occurrence was not established; do not claim an app bug was fixed simply by rerunning.
- Screenshots of storefront, inquiry review, sent state, business request and service editor were visually checked. Screenshot/test money and services are fixtures only.
- Real disposable PostgreSQL 17/PostGIS 3.6 passed unchanged migrations/checks through 011/012. Actual concurrent requests verify that same-time requests/confirmations have no seat limit, identical retries create one request and wallet races cannot overspend or double-release. Auth identities are emulated locally; PostgreSQL roles, constraints, locks and commits are real.
- In-memory PGlite acceptance checks also pass, including filtered pagination beyond the first page, authorization, price snapshots, replies, cancellation and payment isolation.
- MainActivity launched successfully after reinstalling without clearing app data. No actual customer funds were moved.
- Employee invitations, staff assignment, workspaces and employment history have no new implementation/tests yet.

Verification files: `Backend/VERIFICATION.md`, `Backend/tests/check-foundation.mjs`, `Backend/tests/check-postgres-concurrency.mjs`, Android `BookingJourneyTest.kt`, `BookingFlowsUiTest.kt`, `FoundationJourneyTest.kt` and existing payment/journey tests.

Local commands if changes justify rerunning them (do not repeat everything merely to resume a chat):

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2"
.\Adaa\gradlew.bat -p Adaa :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --max-workers=2 --console=plain
node Backend/tests/check-postgres-concurrency.mjs
node Backend/tests/check-foundation.mjs "$env:TEMP\adaa-foundation-sql-check\node_modules\@electric-sql\pglite"
```

Run one Gradle build at a time; concurrent builds previously wasted resources. PostgreSQL concurrency checks need Docker running and create/remove their own disposable database container. They do not use hosted credentials.

ADB is under `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`; the physical phone was connected wirelessly at last check. Recheck connectivity, do not assume it persists. Debug-only `JourneyTestActivity` is non-exported and wakes during UI tests; real MainActivity/release lock-screen protection is unchanged.

Final APK: `Adaa/app/build/outputs/apk/debug/app-debug.apk`.

Phone screenshots: `C:\Users\user\.codex\visualizations\2026\11\04\01a1a607-6d53-7673-a27c-1058bbf16ae8\adaa-booking-review` (01 through 06).

## 12. Suggested next-chat starting point

Read this file, inspect the existing membership schema/RPCs and current audience/navigation files, then confirm the next requested scope with the user. A sensible first build is invitations, a non-destructive membership lifecycle and workspace switching, followed by employee assignments/CRM. Preserve the existing customer journeys and wallet authorization boundaries. The current session ended at planning/documentation; no automatic build, financial activation, deployment or scheduled task was authorized by creating this handoff.
