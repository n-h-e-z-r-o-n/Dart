# Adaa backend — secure foundation

All backend implementation and SQL acceptance checks live here. This implements the specification's **first milestone**, not the entire universal platform MVP. The separate administration app is outside this change.

Storefronts and request-based appointments/inquiries: see [STOREFRONT_SETUP.md](STOREFRONT_SETUP.md). Existing projects run **011_storefront_bookings.sql** once after 008. Development checks are in 012; do not run them as a production migration.

## Supabase SQL Editor

1. Open the intended project's SQL Editor as the project administrator.
2. Paste the complete `001_foundation.sql` file into a new query and run it once. It is one transaction, contains no CLI commands, and needs no API keys. It assumes a new schema without these table/function names; it deliberately fails rather than overwriting existing data.
3. On a development project, run `002_acceptance_checks.sql` as another complete query. Expect the final `PASS` result. Fixtures roll back. If a check fails, execute `ROLLBACK` before retrying.
4. Run `003_user_journey.sql` once to add retry-safe onboarding, protected listing edits, business home and public profile contracts. Run `004_user_journey_checks.sql` on a development project to verify them. Existing projects that already ran `001` need only the new `003` migration; do not rerun `001`.
5. Enable email/password in Supabase Authentication. Set your email confirmation and password policies there. No account metadata grants administrative privileges.
6. In **Authentication → URL Configuration → Redirect URLs**, allow exactly `com.zadhron.adaa://auth/callback`. Email confirmation and password recovery use PKCE, with the verifier held in encrypted device storage. Use the default confirmation/reset templates containing `{{ .ConfirmationURL }}`. Open email links on the same phone that requested them. Configure your production email provider and verify delivery before release.

PostGIS is installed into `extensions`. If your existing project has PostGIS in another schema, move it to `extensions` before running this migration, or adapt the qualified geography type. Geographic search is a later milestone; this foundation stores an optional spatial coordinate and a required address for physical businesses.

## Android connection

The supplied project ID is configured as `https://tzigjjmdgdatauhhhbqq.supabase.co`. To override it, add to your **Gradle user properties** (`%USERPROFILE%\.gradle\gradle.properties`):

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
```

Alternatively pass `-PSUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co` when building. Rebuild after changing it. The publishable key supplied for Adaa is included in BuildConfig. No secret/service-role key is stored in the app or repository. Rotate the secret shared in chat in the Supabase dashboard; none of these SQL scripts require it.

Android uses Supabase Auth's HTTP endpoints and PostgREST RPCs. Sessions persist with AES-GCM authenticated encryption using a key held in Android Keystore. The encrypted file, including pending PKCE verifiers, lives in `noBackupFilesDir`. On startup, the app validates the saved identity and refreshes expired tokens; transient connection failures preserve the saved session and offer retry or guest browsing. Rejected refresh tokens clear the session and require sign-in. Sign-out clears local credentials even when the network is unavailable. Password recovery and email callbacks require the redirect configuration above. The previous business selection is scoped to the user and checked against fresh backend memberships before reopening it. FCM remains outside this milestone.

## Contracts

All identifiers are UUIDs except category IDs (bigint). Errors use PostgreSQL SQLSTATE `28000` (authentication), `42501` (authorization), and `22023` (invalid input), with stable uppercase messages. Ordinary FK/check violations retain native SQLSTATEs. PostgREST returns its standard `{code,message,details,hint}` error JSON. Void RPCs return no usable payload.

| RPC | Parameters | Return / permissions |
| --- | --- | --- |
| `create_business` | `p_name text`, `p_category_id bigint`, `p_operating_model text`, `p_location_or_area text = null`, `p_timezone text = 'UTC'` | UUID; authenticated; atomically creates draft, owner, and appropriate location/area |
| `get_my_businesses` | none | List of `{id,name,operating_model,publication_status,verification_status,role}`; all caller memberships, ordered by creation descending and ID |
| `publish_business` | `p_business_id uuid` | void; owner/manager; validates category/location and blocks suspended listings |
| `set_business_member` | `p_business_id uuid`, `p_user_id uuid`, `p_role text` | void; owner only; assigns existing user as manager/staff; cannot replace owner |
| `remove_business_member` | `p_business_id uuid`, `p_user_id uuid` | void; owner only; cannot remove owner |
| `search_businesses` | `p_query text = ''`, `p_limit int = 20`, `p_offset int = 0` | List of `{id,name,operating_model,category_name}`; public, published only; limit clamped 1–100, offset >= 0; ordered by creation descending and ID |
| `create_business_once` | `p_request_id uuid`, `p_name text`, `p_category_id bigint`, `p_operating_model text`, `p_location_or_area text = null`, `p_description text = ''`, `p_timezone text = 'UTC'` | UUID; authenticated; serializes matching user/request identifiers, returns the same business on matching retries, rejects a changed payload |
| `get_business_dashboard` | `p_business_id uuid` | JSON object for a current member; listing information, access role, publication capability and backend counts |
| `get_business_public_profile` | `p_business_id uuid` | Published listing JSON or null; no private role or team count |
| `update_business` | `p_business_id uuid`, `p_name text`, `p_category_id bigint`, `p_operating_model text`, `p_location_or_area text = null`, `p_description text = ''` | void; owner/manager; validates data, preserves publication/verification, blocks suspended listings; editing the single onboarding location clears stale coordinates when its address changes |

Search currently matches business/category substrings, not offerings or geographic filters. The membership list is intentionally unpaginated for workspace selection. Search exposes no membership, profile, or operational records.

Profile edits use the table API with RLS and column-level grants for `display_name` and `phone` only. Categories are publicly readable when active. Location/area records are readable by the public only for published businesses. All other table writes are denied to API roles. Admins may inspect business/membership data but cannot assign themselves admin rights; provisioning requires trusted SQL by a project administrator. Publication and verification are independent.

## UI and remaining phases

`MainActivity` shows the native branded splash and opens a focused route host. First-time visitors see Welcome with separate Explore, Set up my business and Sign in actions. Returning users restore the customer or business home after validation. Authentication, confirmation, recovery, business selection and each of the three business setup steps have separate screen composables. Business home shows actual backend listing/team information and working review/edit/publish actions. It does not invent appointment or revenue metrics. See `Adaa/USER_JOURNEYS.md` for routing and maintenance details. No Android-side validation substitutes for backend rules. The previous SQLite business screens are retained for design/reference and are not presented as cloud functionality or migrated automatically. Their data remains on device.

Compose `@Preview` functions render sample data without network calls or databases. Open `FoundationApp.kt` in Android Studio and select Split or Design for numbered Welcome, Explore, Sign in, Create account, Confirm email, three setup steps, Business home and Dark previews. `SplashScreen.kt` contains native splash artwork previews. The retained Schedule, Clients, Services and Finance screen files also have stateless previews.

Later phases still require catalog and capabilities, business hours/coordinates and multiple-location management, spatial/full-text discovery, favorites, appointment concurrency/lifecycle/history, eligible reviews, operational dashboard metrics, outbox/FCM worker, moderation, admin UI and release QA. The listing-management home is implemented; a full appointments/earnings dashboard is not. Do not use old SQLite workflows as acceptance evidence for those features.

## Verification

`002_acceptance_checks.sql` checks two-tenant isolation, multiple businesses per account, drafts, anonymous reads, staff/manager permissions, self-promotion denial, immutable ownership, invalid onboarding and suspended publication. `004_user_journey_checks.sql` adds retry idempotency, private dashboard access, protected editing, public field boundaries and removed-member checks. Run them against Supabase after applying their migrations. Android verification: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` from `Adaa`. On a connected Android device, `gradlew.bat :app:connectedDebugAndroidTest` runs UI/accessibility-path assertions, screenshot generation and encrypted-session recreation/tamper checks.

For a local smoke test without a hosted database, run from the workspace root in PowerShell:

```powershell
$checkPath = Join-Path $env:TEMP 'adaa-foundation-sql-check'
npm.cmd install --prefix $checkPath @electric-sql/pglite@0.5.15 --no-audit --no-fund
node Backend/tests/check-foundation.mjs "$checkPath/node_modules/@electric-sql/pglite"
```

This executes the migration and acceptance checks in an in-memory PostgreSQL engine, with Supabase Auth emulated and the geography column replaced by text. It verifies actual RLS/grants/RPC behavior and fixture rollback, but does not validate the hosted Auth trigger integration or PostGIS installation. Run the unchanged SQL in Supabase to validate those.


## Wallet and escrow upgrade

The business dashboard, wallet ledger and delegated escrow release are added by `005_wallet_escrow.sql`; `006_wallet_checks.sql` verifies them on a development project. External payment processing remains disabled by default. Read [WALLET_SETUP.md](WALLET_SETUP.md) for RPC contracts, Edge worker secrets, optional `007_wallet_schedule.sql`, sandbox verification and activation. Do not run the schedule before deploying/configuring the worker.


## Complete personal and business payment workflows

Existing projects with 005 should apply `008_payment_workflows.sql` once. It also repairs the deferred ledger COMMIT check, adds policy-version checks and caps, history, dispute/refund controls, custody reconciliation and operations RPCs. `009_payment_workflow_checks.sql` is a rollback-only development suite. `010_wallet_monitor_schedule.sql` optionally schedules durable SQL health monitoring after 008. See [PAYMENT_RELEASE.md](PAYMENT_RELEASE.md) for the precise upgrade, configuration and release order. External payments remain disabled until policy and gateway verification.
