import { test } from 'node:test';
import assert from 'node:assert/strict';
import { processGatewayJob } from '../supabase/functions/wallet-worker/gateway.ts';
const job = { id: 'fake-job', direction: 'deposit', amount_minor: 150000, phone: '254712345678',
  account_reference: 'abc123', attempts: 1, gateway_request_id: null };
const response = body => new Response(JSON.stringify(body), { status: 200 });
const transaction = { requestId: 'request', status: 'succeeded', amount: 1500, transactionId: 'receipt', accountReference: 'abc123' };
test('accepted prompt does not credit a wallet', async () => {
  const result = await processGatewayJob(job, 'fake-token', async (url, options) => {
    assert.ok(url.endsWith('/stk-push'));
    assert.equal(options.headers['Idempotency-Key'], 'adaa-fake-job');
    assert.equal(JSON.parse(options.body).amount, 1500);
    return response({ success: true, requestId: 'request', status: 'succeeded' });
  });
  assert.deepEqual(result, { requestId: 'request', state: 'pending' });
});
test('expired lease queries the old key without charging again', async () => {
  const result = await processGatewayJob({ ...job, attempts: 2 }, 'fake-token', async (url, options) => {
    assert.equal(options.method, undefined);
    assert.ok(url.includes('idempotencyKey=adaa-fake-job'));
    return new Response(null, { status: 404 });
  });
  assert.equal(result.state, 'pending');
});
test('matching stored terminal receipt allows settlement', async () => {
  const result = await processGatewayJob({ ...job, gateway_request_id: 'request' }, 'fake-token', async () => response({ success: true, transaction }));
  assert.equal(result.state, 'succeeded');
  assert.equal(result.receipt, 'receipt');
});
for (const [label, fields] of [['wrong amount', { amount: 1400 }], ['wrong reference', { accountReference: 'other' }],
  ['wrong request', { requestId: 'other' }], ['missing receipt', { transactionId: null }]]) {
  test(`does not credit ${label}`, async () => {
    const result = await processGatewayJob({ ...job, gateway_request_id: 'request' }, 'fake-token', async () => response({ success: true, transaction: { ...transaction, ...fields } }));
    assert.equal(result.state, 'review');
  });
}
test('failed payout remains reserved for operator reconciliation', async () => {
  const result = await processGatewayJob({ ...job, direction: 'withdrawal', attempts: 2 }, 'fake-token', async () => response({ success: true, transaction: { ...transaction, status: 'failed' } }));
  assert.equal(result.state, 'review');
});
test('gateway timeout does not authorize an automatic refund', async () => {
  const result = await processGatewayJob({ ...job, attempts: 2 }, 'fake-token', async () => response({ success: true, transaction: { ...transaction, status: 'timeout' } }));
  assert.equal(result.state, 'review');
});
test('provider errors do not cause a second dispatch', async () => {
  const result = await processGatewayJob(job, 'fake-token', async () => new Response(null, { status: 502 }));
  assert.equal(result.state, 'pending');
});
