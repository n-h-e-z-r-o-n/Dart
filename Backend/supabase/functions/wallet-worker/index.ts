import { processGatewayJob, type Job } from "./gateway.ts";

const required = (name: string): string => {
  const value = Deno.env.get(name);
  if (!value) throw new Error("Worker configuration missing");
  return value;
};
async function authorized(value: string | null, expected: string): Promise<boolean> {
  if (!value) return false;
  const digest = async (text: string) => new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
  const [a, b] = await Promise.all([digest(value), digest(expected)]);
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}
Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return new Response(null, { status: 405 });
  try {
    if (!await authorized(request.headers.get("x-wallet-worker-secret"), required("WALLET_WORKER_SECRET"))) return new Response(null, { status: 401 });
    if (Deno.env.get("WALLET_GATEWAY_ENABLED") !== "true") return new Response("Wallet gateway disabled", { status: 503 });
    const url = required("SUPABASE_URL");
    const serviceKey = required("SUPABASE_SERVICE_ROLE_KEY");
    const token = required("ZADHRON_GATEWAY_TOKEN");
    const rpc = async (name: string, body: unknown = {}) => {
      const response = await fetch(`${url}/rest/v1/rpc/${name}`, { method: "POST", redirect: "error",
        headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json" },
        body: JSON.stringify(body), signal: AbortSignal.timeout(15000) });
      if (!response.ok) throw new Error("Wallet RPC failed");
      const text = await response.text();
      return text ? JSON.parse(text) : null;
    };
    const jobs = await rpc("claim_wallet_gateway_jobs") as Job[];
    // Independent provider calls are parallel; database row locks serialize settlement.
    const results = await Promise.allSettled(jobs.map(async (job) => {
      const result = await processGatewayJob(job, token);
      await rpc("record_wallet_gateway_progress", { p_job_id: job.id, p_gateway_request_id: result.requestId, p_review: result.state === "review" });
      if (result.state === "succeeded" || result.state === "failed") {
        await rpc("settle_wallet_gateway_job", { p_job_id: job.id, p_gateway_request_id: result.requestId,
          p_succeeded: result.state === "succeeded", p_receipt: result.receipt ?? null, p_evidence: result.evidence });
      }
    }));
    const processed = results.filter(r => r.status === "fulfilled").length;
    const retryLater = results.filter(r => r.status === "rejected").length;
    await rpc("record_wallet_worker_health", { p_processed: processed, p_retry_later: retryLater });
    const health = await rpc("get_wallet_operational_health");
    const alertUrl = Deno.env.get("WALLET_ALERT_WEBHOOK");
    if (alertUrl && health.requires_attention) {
      if (!alertUrl.startsWith("https://")) throw new Error("Alert endpoint must use HTTPS");
      const alert = await rpc("claim_wallet_alert_notification");
      if (alert) {
        let delivered = false;
        try {
          const response = await fetch(alertUrl, { method: "POST", redirect: "error", headers: {
            "Content-Type": "application/json", ...(Deno.env.get("WALLET_ALERT_TOKEN") ? { Authorization: `Bearer ${Deno.env.get("WALLET_ALERT_TOKEN")}` } : {}),
          }, body: JSON.stringify({ service: "Adaa wallet", ...alert }), signal: AbortSignal.timeout(10000) });
          delivered = response.ok;
        } finally { await rpc("finish_wallet_alert_notification", { p_delivered: delivered }); }
      }
    }
    // Only counts and operational booleans; no customer data or credentials in the response.
    return Response.json({ processed, retryLater, requiresAttention: health.requires_attention });
  } catch {
    return new Response("Wallet worker unavailable", { status: 503 });
  }
});
