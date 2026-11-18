// Provider boundary. No Android token, customer-supplied amount or callback is trusted here.
export type Job = {
  id: string; direction: "deposit" | "withdrawal"; amount_minor: number;
  phone: string; account_reference: string; attempts: number; gateway_request_id: string | null;
};
export type Resolution = { requestId: string | null; state: "pending" | "review" | "succeeded" | "failed"; receipt?: string; evidence?: unknown };
type ObjectValue = Record<string, unknown>;
const object = (value: unknown): ObjectValue => value !== null && typeof value === "object" && !Array.isArray(value) ? value as ObjectValue : {};
const string = (value: unknown): string | null => typeof value === "string" && value.length > 0 ? value : null;

export async function processGatewayJob(job: Job, token: string, transport: typeof fetch = fetch): Promise<Resolution> {
  if (!Number.isSafeInteger(job.amount_minor) || job.amount_minor <= 0 || job.amount_minor % 100 !== 0) throw new Error("Invalid stored amount");
  const headers = { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
  const key = `adaa-${job.id}`;
  const base = "https://payments.zadhron.com";
  const lookup = async (): Promise<Resolution> => {
    const query = new URLSearchParams(job.gateway_request_id ? { requestId: job.gateway_request_id } : { idempotencyKey: key });
    const response = await transport(`${base}/api/mpesa/transactions/status?${query}`, { headers, redirect: "error", signal: AbortSignal.timeout(15000) });
    // Missing/late records are not proof of failure. Do not refund or resubmit.
    if (response.status === 404) return { requestId: job.gateway_request_id, state: "pending" };
    if (!response.ok) throw new Error("Gateway lookup unavailable");
    const body = object(await response.json());
    const transaction = object(body.transaction);
    const requestId = string(transaction.requestId);
    if (body.success !== true || !requestId || (job.gateway_request_id && requestId !== job.gateway_request_id)) {
      return { requestId: job.gateway_request_id, state: "review" };
    }
    if (!["succeeded", "failed", "cancelled", "timeout"].includes(String(transaction.status))) return { requestId, state: "pending" };
    // Match integer KES value from the authenticated stored gateway record, never the handset response.
    const amount = transaction.amount;
    if (typeof amount !== "number" || !Number.isFinite(amount) || amount * 100 !== job.amount_minor ||
      (job.direction === "deposit" && transaction.accountReference !== job.account_reference)) return { requestId, state: "review" };
    if (transaction.status === "succeeded") {
      const receipt = string(transaction.transactionId);
      return receipt ? { requestId, state: "succeeded", receipt, evidence: transaction } : { requestId, state: "review" };
    }
    // An uncertain/failed payout remains reserved for operator reconciliation.
    // Release it only after an operator independently confirms no payout occurred.
    if (job.direction === "withdrawal" || transaction.status === "timeout") return { requestId, state: "review" };
    return { requestId, state: "failed", evidence: transaction };
  };
  // A retried job might have crashed after an upstream charge. Look up the SAME key;
  // never send a new STK/B2C request merely because a lease expired.
  if (job.attempts > 1 || job.gateway_request_id) return await lookup();
  const isDeposit = job.direction === "deposit";
  const payload = isDeposit
    ? { phoneNumber: job.phone, amount: job.amount_minor / 100, accountReference: job.account_reference, transactionDesc: "AdaaWallet" }
    : { phoneNumber: job.phone, amount: job.amount_minor / 100, remarks: "Adaa withdrawal" };
  const response = await transport(`${base}/api/mpesa/${isDeposit ? "stk-push" : "b2c"}`, {
    method: "POST", headers: { ...headers, "Idempotency-Key": key }, body: JSON.stringify(payload),
    redirect: "error", signal: AbortSignal.timeout(15000),
  });
  // Even a 5xx can follow provider acceptance. Retain reservation and recover by lookup.
  if (!response.ok) return { requestId: null, state: "pending" };
  const body = object(await response.json());
  return { requestId: body.success === true ? string(body.requestId) : null, state: "pending" };
}
