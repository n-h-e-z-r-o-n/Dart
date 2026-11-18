# Verification: storefront bookings and payment workflows

## Current storefront delivery (November 5)

- Debug build and lint pass; lint has zero errors and existing warnings, with no checks disabled. All 41 unit tests pass (8 booking, 21 foundation, 11 payment, 1 example).
- All six booking phone tests pass together on the installed final app, including native date/time selection, explicit review/send, inquiry submission, paused requests, staff restrictions, business acceptance confirmation and catalogue edits. The two existing business onboarding/accessibility phone tests also passed earlier in this delivery.
- One earlier full phone run reported missing Compose hierarchies during the appointment test. Its isolated rerun passed, followed by a successful complete six-test booking run. No production behavior was bypassed to obtain that result.
- PostgreSQL 17/PostGIS runs original migrations/checks through 011/012 successfully. Simultaneous same-time customer requests and confirmations are allowed; simultaneous identical retries return one booking. Existing wallet concurrency checks remain green. Filtered history pagination and closed-request filtering pass. All databases are disposable with emulated Auth; hosted Auth is not simulated as verified production behavior.
- Phone screenshots for the storefront, inquiry review, confirmation, business request and service editor were inspected. Samples remain test/preview data.
- User reports applying 011 to Supabase. This agent has not applied it or verified authenticated hosted booking writes. Optional 013 contains clearly marked, hidden example home services for cpact and has not been applied by this agent.

## Earlier payment delivery

- Android debug build and lint: pass. No lint checks were disabled.
- Unit tests: 31 pass (19 foundation journey tests, 11 payment tests, one existing example). Payment coverage includes exact money parsing, normalized phone input, stable references after uncertain responses, distinct new intents, no second mutation after a failed read, disabled/balance quote checks, staff scope restrictions, session rejection and stale-receipt clearing.
- Physical phone: full suite of 44 tests passes, including six personal/business transaction flows, dispute-based withdrawal restrictions, permission confirmation and 28 visual renders (19 foundation + nine payment screens). After the final retry-state correction, the seven focused financial/permission tests passed again on the final installed APK.
- The earlier No compose hierarchies failures were traced to the phone being asleep/keyguard showing. The dedicated non-exported debug sample activity now wakes/renders during instrumentation; MainActivity and release lock-screen behavior are unchanged.
- Phone-rendered wallet, quote/review, business completion, dispute and dark screenshots were reviewed. Preview data is design-time/test-only; runtime repositories never return sample balances.
- Fast PostgreSQL smoke: foundation, journey, wallet and payment-workflow acceptance suites pass and fixtures roll back. It emulates Auth and substitutes PostGIS in memory.
- Real PostgreSQL 17/PostGIS 3.6: unchanged migration/check scripts 001/002/003/004/005/006/008/009 pass in a disposable Docker database. Two-session checks pass for competing overspends, competing escrow releases, identical retry races and combined deposit/withdrawal daily-cap races. This uses emulated Auth identities but actual PostgreSQL roles, constraints, commits, row locks and PostGIS. The container and fixtures are removed afterwards.
- That real authenticated COMMIT test found and fixed the deferred balance trigger execution context. 005 contains the corrected definition for fresh installations; 008 upgrades existing installations without granting clients ledger access. Commit checks are now exercised under authenticated role.
- Gateway adapter: ten mocked-transport tests pass. No actual payment provider request was made by them.
- Deno type checking passes for wallet-worker/index.ts and ops/check-wallet-health.ts.
- The latest APK installed without clearing app data. MainActivity launch returned Status: ok. The phone's own lock remains in effect for the real app.

## Deployment boundary

The user reported applying 005 and 008. This agent has not deployed/configured the Edge Function or independently verified hosted payment settlement. No real customer deposits/payouts or live custody snapshots were created. Hosted Supabase Auth/PostgREST integration and explicitly authorized production gateway settlement remain required for live activation.

Limits, fee policy and dispute ownership still need approval. The implemented platform-fee mode is zero only; non-zero fees are deliberately rejected. An unconfigured policy or inactive gateway prevents new external requests. See PAYMENT_RELEASE.md for upgrade, monitoring, reconciliation, recovery and activation.
