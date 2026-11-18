# Adaa wallet and escrow setup

**Latest payment screens and release controls:** apply `008_payment_workflows.sql` after 005 and follow [PAYMENT_RELEASE.md](PAYMENT_RELEASE.md). It adds the quoted policy-version parameter to gateway requests, dispute controls and operations monitoring.

## Apply the database upgrade

Your existing project already has 001 and 003. Run the entire `005_wallet_escrow.sql` once in Supabase SQL Editor. Do not rerun 001 or 003. Wallet deposits/withdrawals default to disabled. `006_wallet_checks.sql` is a separate development-only acceptance suite: all fixture users, balances and payment jobs roll back. It never calls M-Pesa.

Amounts are integer KES minor units: 100 = KES 1. External M-Pesa operations currently require whole KES; internal escrow supports minor units. No platform fee is charged by these RPCs. Provider fees and reconciliation of the platform clearing account must be agreed before activating payments.

## Contracts

Authenticated RPCs:

| RPC | Purpose |
| --- | --- |
| `get_wallet_dashboard(p_business_id uuid = null)` | Own personal wallet, or owner-only business wallet totals and the latest ten ledger entries. Includes payments_active. |
| `get_business_wallet_team(p_business_id uuid)` | Owner-only existing members and escrow-release permissions. |
| `set_escrow_release_permission(p_business_id uuid,p_user_id uuid,p_allowed boolean)` | Owner grants/revokes release capability for an existing team member. Does not grant withdrawal. |
| `create_service_charge(p_request_id uuid,p_business_id uuid,p_customer_id uuid,p_amount_minor bigint,p_description text)` | Owner/manager creates an immutable priced charge for the customer. Does not debit money. |
| `get_service_charges(p_business_id uuid = null)` | Customer's own charges, or a member's business charges; latest 100. |
| `pay_service_from_wallet(p_charge_id uuid,p_request_id uuid)` | Customer accepts that charge: available customer funds move to held business escrow atomically. |
| `complete_service_and_release(p_charge_id uuid,p_request_id uuid)` | Owner or explicitly authorized member confirms completion and moves held escrow to business available funds. Records actor and time. |
| `refund_service_escrow(p_charge_id uuid,p_request_id uuid)` | Owner returns an uncompleted funded service's escrow to the customer's available funds. |
| `record_external_service_payment(p_charge_id uuid)` | Owner/authorized releaser marks a requested charge paid outside Adaa. Never changes wallet balances. |
| `request_wallet_gateway_operation(p_request_id uuid,p_direction text,p_amount_minor bigint,p_phone text,p_business_id uuid = null)` | Customer deposit/withdrawal, or owner-only business withdrawal. Reserves withdrawal funds before queueing. Phone is normalized Kenyan format, e.g. 2547xxxxxxxx. Disabled until activation. |

Use a fresh UUID for each intent and preserve it across retries. Do not use a new request ID after an uncertain response. Changes to an existing intent are rejected. Service state transitions prevent double funding/release even under different retry IDs. Wallet account rows serialize monetary mutations in deterministic order. The append-only journal uses equal/opposite entries and a deferred constraint trigger rejects unbalanced commits. Permissions and release actors are audited. Direct table writes and worker RPCs are denied to ordinary authenticated users.

## Deploy server-side gateway worker

From `Backend`, deploy `supabase/functions/wallet-worker` with the Supabase CLI:

```powershell
supabase functions deploy wallet-worker --project-ref tzigjjmdgdatauhhhbqq
```

Use Supabase Edge Function secrets (never Android or a committed file):

- `ZADHRON_GATEWAY_TOKEN`: configured Zadhron gateway application token with collection, payout and transaction-lookup scopes. Confirm provisioning with the gateway operator; its public docs describe both dashboard token generation and configured application auth.
- `WALLET_WORKER_SECRET`: a new cryptographically random secret used only by the cron worker endpoint.
- `WALLET_GATEWAY_ENABLED`: leave unset or false until production configuration and release checks pass; set true for the intended configured gateway environment only after verification.
- `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`: supplied by the Supabase Edge runtime. Keep the service-role key server-side.

`config.toml` disables Supabase JWT verification for this one cron endpoint. The function independently requires the dedicated worker secret. It accepts no wallet instructions or raw M-Pesa callbacks. It claims queued jobs via service-role-only RPCs, sends stable idempotency keys, then polls authenticated stored gateway transactions. A successful HTTP submission is never a credited payment. Successful settlement requires the expected amount/reference and receipt. The worker and SQL both check evidence.

The documented gateway base is https://payments.zadhron.com. Its upstream environment is selected by the gateway operator, not by an Adaa request. For the requested live deployment, confirm a production deployment/token/environment and correct collection/payout scopes. Any real validation transaction needs an approved amount and phone/destination. No live payment was initiated during implementation.

## Schedule reconciliation (optional until worker is deployed)

In Supabase Vault create `adaa_project_url` with the project HTTPS origin and `adaa_wallet_worker_secret` matching the Edge worker secret. Run `007_wallet_schedule.sql` once after deployment. It installs a named one-minute cron job through pg_cron/pg_net. Rerunning uses the same named job. It never automatically releases service escrow.

Enable new deposit/withdrawal requests only after production release checks and worker configuration:

```sql
update adaa_private.wallet_settings set gateway_enabled = true where singleton;
```

Disable new requests/claims with the same statement using false, and set Edge WALLET_GATEWAY_ENABLED=false. Existing funds/reservations are retained.

## Reconciliation and failure behavior

A crashed or uncertain dispatch is looked up using its original gateway request ID/idempotency key, never blindly resubmitted. Missing records stay pending. Mismatched evidence, timeout results and failed withdrawals enter review; held withdrawal funds remain reserved. This intentionally prefers review to a duplicate payment or unsafe refund.

An operator must inspect review/stale jobs through SQL Editor, query the gateway/provider independently and resolve the actual outcome. To resume polling after correction, set the specific job state to pending, lease_until=null and next_attempt_at=now(). Never reset attempts to zero or create another withdrawal to recover an uncertain payout.

For a conclusively failed payout, an operator/service backend may call `settle_wallet_gateway_job` with the same stored request ID, p_succeeded=false, no receipt, and authenticated transaction evidence containing requestId, amount in whole KES, terminal failed/cancelled status, and operator_verified_no_payout=true. Do not infer failure from an HTTP timeout or missing record. Successful reconciliation requires matching terminal evidence and a unique receipt. No general callback endpoint is installed: the gateway docs say signed outbound merchant webhooks are not implemented, so this adapter polls the authenticated gateway lookup instead.

SQL acceptance verifies ledger/grants/state transitions, not actual Safaricom funds. Gateway adapter tests use mocked transport. Hosted integration, production token scopes, payout lookup coverage, transaction retention, fees and explicitly authorized end-to-end deposit/withdrawal settlement remain to verify before activation. The personal/business wallet, service payment/completion, refund and dispute screens are implemented; see PAYMENT_RELEASE.md for the current workflow and deployment gate.

## Local verification

```powershell
node Backend/tests/check-foundation.mjs "$env:TEMP/adaa-foundation-sql-check/node_modules/@electric-sql/pglite"
node --test Backend/tests/wallet-gateway.test.mjs
npx --yes deno check Backend/supabase/functions/wallet-worker/index.ts
```

The smoke engine emulates Supabase Auth and substitutes the PostGIS column in memory only. Apply unchanged SQL on a development Supabase project for hosted verification.
