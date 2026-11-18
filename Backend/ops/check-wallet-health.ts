// Run from an external scheduler/CI, independent of the Supabase payment worker.
// Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY in the scheduler's secret store.
// Exit 1 means attention required, exit 2 means the monitor could not reach its data.
const url = Deno.env.get("SUPABASE_URL");
const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
if (!url || !key) { console.error("Wallet monitoring configuration is missing"); Deno.exit(2); }
try {
  const response = await fetch(`${url}/rest/v1/rpc/get_wallet_operational_health`, {
    method: "POST", redirect: "error", headers: { apikey: key, Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: "{}", signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error("Health endpoint unavailable");
  const health = await response.json();
  if (typeof health.requires_attention !== "boolean") throw new Error("Unexpected health response");
  console.log(JSON.stringify({ requiresAttention: health.requires_attention, queued: health.queued, review: health.review,
    stalePending: health.stale_pending, openDisputes: health.open_disputes, unbalancedJournals: health.unbalanced_journals,
    negativeAccounts: health.negative_accounts, workerStale: health.worker_stale }));
  Deno.exit(health.requires_attention ? 1 : 0);
} catch { console.error("Wallet health check failed; inspect the monitoring connection"); Deno.exit(2); }
