# Payment workflows: deployment and release

This delivery implements personal and business payment screens, server-side wallet controls, dispute review and operations monitoring. Payment processing is disabled until configured and verified. Request-based appointments and inquiries are a separate addition in 011; see STOREFRONT_SETUP.md. The rest of the marketplace MVP is not complete.

## Existing project: SQL Editor order

You already applied 001, 003 and 005. Run **008_payment_workflows.sql** as one complete query, once. Do not rerun the earlier migrations. This upgrade also fixes the deferred ledger constraint's execution context at authenticated COMMIT. It resets gateway processing to disabled.

Run **009_payment_workflow_checks.sql** on a development project. All fixtures roll back and no provider request is made. For a fresh project: 001 → 003 → 005 → 008. Acceptance suites 002, 004, 006 and 009 are development checks, not schema migrations.

After deploying/configuring the Edge worker, install 007_wallet_schedule.sql for payment reconciliation and **010_wallet_monitor_schedule.sql** for SQL health monitoring. The monitor never moves money. See WALLET_SETUP.md for Vault and Edge secret names.

## Personal journey

Account → My personal wallet. The wallet displays available funds, funds held for services, pending withdrawals, gateway request statuses and a paginated wallet statement. The customer's payment code can be copied and shared with a business; the code does not authorize a debit.

Deposit/withdrawal → amount and phone → server quote → review → explicit confirmation → pending/confirmed status. The server enforces whole-KES external requests, min/max/daily caps, available balance and the quoted policy version. Repeated uncertain requests retain their reference. The UI polls visible pending requests only while the app is foregrounded. A new intent is distinct from a retry.

Account → My payment requests. The customer reviews a priced request and explicitly pays from the wallet into service escrow. A direct till/cash payment can instead be reported for business verification. Reporting an outside payment blocks accidental wallet payment until the report is withdrawn/rejected. It never credits a wallet. Unpaid requests can be cancelled. Funded/completed services have dispute and audit-timeline views.

## Business journey

Business home → Services & payments. Owners/managers create immutable service-price requests using the customer's payment code. The customer must authorize payment. An owner or a member with explicitly granted release permission confirms completion, releasing escrow to business available funds. Owners can refund held or completed payments; completed-payment refunds require sufficient business available funds. Money is never created to cover a refund.

Business home → Open business wallet. Only the owner can view balances or withdraw. Staff can access service details/authorized completion without wallet totals or withdrawal access. Owners grant/revoke release capability separately under Team permissions.

## Policy decisions and activation

The app does not invent your transaction limits, fee policy or dispute window. They remain unconfigured until approved. A platform administrator calls:

```sql
do $$
declare
  approved_min_minor bigint := null;
  approved_max_minor bigint := null;
  approved_daily_minor bigint := null;
  approved_dispute_hours integer := null;
begin
  if num_nonnulls(approved_min_minor,approved_max_minor,approved_daily_minor,approved_dispute_hours)<>4 then
    raise exception 'Replace the four NULLs with approved policy values first';
  end if;
  perform public.configure_wallet_policy(approved_min_minor,approved_max_minor,approved_daily_minor,approved_dispute_hours,0);
end $$;
```

The caller must be in public.platform_admins. Provision that membership through privileged administration, never customer metadata or an app signup. For SQL Editor maintenance, run as a project administrator and set the transaction-local request.jwt.claim.sub to the intended platform-admin user's ID before invoking administrator RPCs.

This version supports **zero Adaa platform fees**. Non-zero values are rejected because fee collection/refund ledger rules are not implemented; the UI never advertises or deducts an unsupported fee. Gateway/M-Pesa fees are separate from service prices. If Adaa should charge non-zero fees, keep activation disabled until those fee rules are specified and implemented. The daily cap combines deposits and withdrawals and resets at midnight UTC.

Completed-service disputes use the configured window. Funded-service disputes can be opened before release. The conservative implemented resolver is a platform administrator; neither party resolves its own dispute through business/customer permissions. Confirm this ownership before activation. There is no automatic timeout release of service escrow.

For the requested production deployment, verify the live gateway credentials/environment, approved policy, reconciliation baseline and monitoring before a privileged operator enables processing. Any actual live deposit or payout validation needs an explicitly approved amount and destination:

```sql
update adaa_private.wallet_settings set gateway_enabled = true,
  accept_new_requests = true where singleton;
```

Also set Edge WALLET_GATEWAY_ENABLED=true for the intended gateway environment. Do not enable merely because SQL applied successfully.

## Disputes and recovery

- get_open_payment_disputes(): platform-admin-only review queue.
- get_payment_request(charge ID): parties/members see the service, evidence timeline and resolution. Reviewers can inspect case records through privileged SQL maintenance; the two public-profile apps do not include an administrator console.
- resolve_payment_dispute(charge ID, refund boolean, reason, request UUID): platform-admin-only full refund or release. Records the reviewer and reason. An open dispute blocks new business withdrawals and queued first-time payouts. Already-dispatched provider requests are reconciled, never assumed cancelled.
- respond_payment_dispute(charge ID, message): customer or business owner/manager adds an audit message.
- retry_wallet_job_poll(job ID, reason): service-role-only recovery. It retains the original provider/idempotency references and never resets attempts to a new charge.
- settle_wallet_gateway_job(...): service-role-only verified settlement. A conclusively failed payout requires terminal evidence and operator_verified_no_payout=true. An HTTP timeout/404 is not evidence of failure. A refund cannot exceed the available source funds; insufficient funds leave the case unresolved for operator recovery.

Use a stable request UUID per financial intent. Review stale/review jobs before initiating another payout. Do not edit wallet entries or journals: they are append-only and protected by balancing constraints. Direct outside payments must never be imported as wallet deposits.

## Custody reconciliation

Per-transaction lookup is automatic. Comparing actual M-Pesa funds to Adaa liabilities additionally needs a verified provider balance/report. The gateway documentation does not expose a fully documented synchronous balance read suitable for blindly crediting/reconciling tenants. The delivered service-role-only **record_wallet_custody_snapshot** RPC records a verified operator/provider report; it never changes balances.

1. Pause new external requests with `update adaa_private.wallet_settings set accept_new_requests=false where singleton;`. Leave gateway processing enabled to drain already-accepted work.
2. Resolve queued/pending/review jobs using verified provider outcomes. Stop and investigate any ambiguous payout.
3. Obtain a fresh verified provider balance and cumulative platform-funding/provider-fee figures for the dedicated custody account. Do not substitute one business's wallet balance for the provider shortcode balance.
4. Call record_wallet_custody_snapshot with a stable snapshot UUID, observed balance in minor KES, verified cumulative platform funding, verified cumulative provider fees, observed timestamp, source and evidence JSON. It requires no unresolved gateway jobs and a snapshot no older than five minutes.
5. The RPC compares observed funds with platform funding + outstanding ledger custody − provider fees. It records differences and any shortfall in coverage. Resolve discrepancies before resuming new requests.

The snapshot is immutable and idempotent. A mismatch never mints money, releases escrow or silently adjusts customer funds. Health monitoring flags a missing/stale (24-hour) or mismatched/underfunded snapshot while the gateway is active. Provider-balance import automation remains dependent on a verified gateway contract; operator reconciliation is available now.

## Monitoring

get_wallet_operational_health is service-role-only. It checks stale jobs, review outcomes, unbalanced/negative accounts, worker heartbeat/retries and custody reconciliation. SQL cron writes durable wallet_operator_alerts even if the payment worker is unavailable.

Optional Edge secrets WALLET_ALERT_WEBHOOK (HTTPS) and WALLET_ALERT_TOKEN enable operator notifications with counts/operational details, no customer identities. Notifications have a lease, a 30-minute success throttle and retry after failed delivery. No notification endpoint is configured or contacted by this implementation task.

For an independent scheduler/CI, run:

```powershell
deno run --allow-env=SUPABASE_URL,SUPABASE_SERVICE_ROLE_KEY --allow-net Backend/ops/check-wallet-health.ts
```

Use the scheduler's secret store. Exit 0 means no current alert, 1 means attention required, and 2 means monitoring could not reach/validate its data. Configure the scheduler to alert on 1 or 2. This separate check is necessary to detect a dead payment worker rather than expecting it to report its own outage.

## Verification and remaining deployment work

- Build, lint, unit and physical-phone UI tests are run locally; see VERIFICATION.md for current results.
- check-postgres-concurrency.mjs runs unchanged migrations/acceptance suites in a disposable PostgreSQL 17/PostGIS container and tests real simultaneous overspend, release, retry and daily-cap calls. It uses no hosted credentials and removes its own container/fixtures.
- check-foundation.mjs provides faster in-memory smoke checks with emulated Auth.
- wallet-gateway.test.mjs uses mocked provider transport; no live payment is made.

Hosted Supabase Auth/RPC integration, actual production gateway scopes/environment/retention, explicitly authorized live deposit/payout settlement and the verified custody report must still be checked in your deployment. No gateway credentials or specific live-fund transaction authorization were available here. Code/tests do not replace those release checks. Keep deposits/withdrawals disabled until they pass.
