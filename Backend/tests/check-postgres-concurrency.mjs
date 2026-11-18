// Isolated disposable PostgreSQL/PostGIS container. No hosted URL or live credentials.
// Requires a running Docker engine. Creates/removes only its own labelled container.
import { spawn } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
const name = `adaa-wallet-check-${randomUUID().slice(0, 8)}`;
const image = 'postgis/postgis:17-3.6';
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
function command(args, input = '') {
  return new Promise(resolve => {
    const child = spawn('docker', args, { windowsHide: true });
    let out = '', error = '';
    child.stdout.on('data', data => out += data); child.stderr.on('data', data => error += data);
    child.on('error', e => resolve({ code: 1, out, error: e.message }));
    child.on('close', code => resolve({ code, out, error }));
    child.stdin.end(input);
  });
}
const requireSuccess = result => { if (result.code !== 0) throw new Error(result.error || result.out); return result.out.trim(); };
const sql = text => command(['exec', '-i', name, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', 'adaa_test_wallet'], text);
const owner = 'addddddd-1000-0000-0000-000000000001';
const customer = 'addddddd-1000-0000-0000-000000000002';
const business = 'addddddd-2000-0000-0000-000000000001';
const charge = n => `addddddd-3000-0000-0000-${String(n).padStart(12, '0')}`;
const nonce = n => `addddddd-4000-0000-0000-${String(n).padStart(12, '0')}`;
const session = (user, operation, delay = false) => sql(`begin; set local request.jwt.claim.sub='${user}'; set local role authenticated; ${operation}; ${delay ? 'select pg_sleep(1);' : ''} commit;`);
let started = false;
try {
  console.log('Starting isolated PostgreSQL/PostGIS verification (first run may pull the image).');
  requireSuccess(await command(['run', '-d', '--name', name, '--label', 'adaa.task=wallet-verification', '-e', `POSTGRES_PASSWORD=${randomUUID()}`, image]));
  started = true;
  let ready = false;
  for (let attempt = 0; attempt < 60; attempt++) {
    if ((await command(['exec', name, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres'])).code === 0) { ready = true; break; }
    await sleep(500);
  }
  if (!ready) throw new Error('Disposable database did not become ready');
  // template0 avoids the image's public-schema PostGIS defaults; apply the real migration unchanged.
  requireSuccess(await command(['exec', name, 'createdb', '-U', 'postgres', '-T', 'template0', 'adaa_test_wallet']));
  requireSuccess(await sql(`create role anon; create role authenticated; create role service_role;
    create schema auth; create table auth.users(id uuid primary key,raw_user_meta_data jsonb);
    create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
    grant usage on schema auth to anon,authenticated; grant execute on function auth.uid() to anon,authenticated;`));
  for (const file of ['001_foundation.sql','002_acceptance_checks.sql','003_user_journey.sql','004_user_journey_checks.sql','005_wallet_escrow.sql','006_wallet_checks.sql','008_payment_workflows.sql','009_payment_workflow_checks.sql','011_storefront_bookings.sql','012_storefront_booking_checks.sql']) {
    requireSuccess(await sql(await readFile(new URL(`../${file}`, import.meta.url), 'utf8')));
    console.log(`PASS: ${file}`);
  }
  requireSuccess(await sql(`insert into auth.users(id) values('${owner}'),('${customer}');
    insert into public.businesses(id,name,primary_category_id,operating_model) values('${business}','Concurrency studio',(select id from public.business_categories where name='Other'),'online');
    insert into public.business_members(business_id,user_id,role) values('${business}','${owner}','owner');
    select adaa_private.wallet_move('test_seed','${nonce(1)}','test_seed','${nonce(1)}',
      (select id from adaa_private.wallet_accounts where system_account),'available',adaa_private.wallet_account('${customer}'),'available',100000,null);
    insert into adaa_private.service_charges(id,business_id,customer_id,amount_minor,description,created_by,request_id) values
      ('${charge(1)}','${business}','${customer}',70000,'First competing charge','${owner}','${nonce(2)}'),
      ('${charge(2)}','${business}','${customer}',70000,'Second competing charge','${owner}','${nonce(3)}');`));
  const spend = await Promise.all([session(customer, `select public.pay_service_from_wallet('${charge(1)}','${nonce(4)}')`, true), session(customer, `select public.pay_service_from_wallet('${charge(2)}','${nonce(5)}')`)]);
  if (spend.filter(r => r.code === 0).length !== 1 || !spend.some(r => r.error.includes('INSUFFICIENT_FUNDS'))) throw new Error('Concurrent overspend guard failed: ' + JSON.stringify(spend.map(r => ({code:r.code,out:r.out.trim(),error:r.error.trim()}))));
  console.log('PASS: competing payments cannot overspend one wallet');
  const funded = requireSuccess(await sql(`select id from adaa_private.service_charges where state='funded';`));
  const release = await Promise.all([session(owner, `select public.complete_service_and_release('${funded}','${nonce(6)}')`, true), session(owner, `select public.complete_service_and_release('${funded}','${nonce(7)}')`)]);
  if (release.filter(r => r.code === 0).length !== 1 || !release.some(r => r.error.includes('ALREADY_COMPLETED'))) throw new Error('Concurrent release guard failed');
  console.log('PASS: competing completion requests release escrow once');
  requireSuccess(await sql(`insert into adaa_private.service_charges(id,business_id,customer_id,amount_minor,description,created_by,request_id)
    values('${charge(3)}','${business}','${customer}',10000,'Same-request race','${owner}','${nonce(8)}');`));
  const retry = await Promise.all([session(customer, `select public.pay_service_from_wallet('${charge(3)}','${nonce(9)}')`, true), session(customer, `select public.pay_service_from_wallet('${charge(3)}','${nonce(9)}')`)]);
  if (retry.some(r => r.code !== 0) || retry[0].out.trim() !== retry[1].out.trim()) throw new Error('Concurrent idempotent retry failed');
  console.log('PASS: concurrent identical retries return the same journal');
  requireSuccess(await sql(`select adaa_private.wallet_move('test_seed','${nonce(10)}','test_seed','${nonce(10)}',
    (select id from adaa_private.wallet_accounts where system_account),'available',adaa_private.wallet_account('${customer}'),'available',100000,null);
    update adaa_private.wallet_policy set configured=true,min_external_minor=100,max_external_minor=50000,daily_external_minor=50000,dispute_window_hours=72;
    update adaa_private.wallet_settings set gateway_enabled=true;`));
  const limits = await Promise.all([session(customer, `select public.request_wallet_gateway_operation('${nonce(11)}','deposit',40000,'254712345678',null,1)`, true), session(customer, `select public.request_wallet_gateway_operation('${nonce(12)}','withdrawal',40000,'254712345678',null,1)`)]);
  if (limits.filter(r => r.code === 0).length !== 1 || !limits.some(r => r.error.includes('WALLET_DAILY_LIMIT'))) throw new Error('Concurrent daily cap guard failed');
  console.log('PASS: deposit/withdrawal races cannot bypass the daily cap');
  const otherCustomer = 'addddddd-1000-0000-0000-000000000003';
  const catalogService = 'addddddd-5000-0000-0000-000000000001';
  requireSuccess(await sql(`insert into auth.users(id) values('${otherCustomer}');
    update public.businesses set publication_status='published' where id='${business}';
    insert into adaa_private.catalog_services(id,business_id,name,description,price_minor,duration_minutes)
      values('${catalogService}','${business}','Concurrency service','',10000,60);`));
  const bookingCall = reference => `select public.submit_booking_request('${reference}','${business}','${catalogService}',date_trunc('hour',now())+interval '2 days','Same preferred time',10000)`;
  const bookings = await Promise.all([session(customer, bookingCall(nonce(13)), true), session(otherCustomer, bookingCall(nonce(14)))]);
  if (bookings.some(r => r.code !== 0) || bookings[0].out.trim() === bookings[1].out.trim()) throw new Error('Concurrent booking requests incorrectly limited business capacity');
  const accepts = await Promise.all(bookings.map(r => session(owner, `select public.act_on_booking('${r.out.trim()}','confirmed','Team has capacity')`)));
  if (accepts.some(r => r.code !== 0)) throw new Error('Business cannot confirm both same-time customers');
  const bookingRetries = await Promise.all([session(customer, bookingCall(nonce(15)), true), session(customer, bookingCall(nonce(15)))]);
  if (bookingRetries.some(r => r.code !== 0) || bookingRetries[0].out.trim() !== bookingRetries[1].out.trim()) throw new Error('Concurrent booking retry duplicated intent');
  console.log('PASS: simultaneous same-time requests and confirmations have no seat limit; identical booking retries create one request');
  requireSuccess(await sql(`do $$begin
    if exists(select 1 from adaa_private.wallet_entries group by journal_id having sum(delta)<>0) then raise exception 'Unbalanced ledger'; end if;
    if exists(select 1 from adaa_private.wallet_entries e join adaa_private.wallet_accounts a on a.id=e.account_id where not a.system_account group by e.account_id,e.bucket having sum(delta)<0) then raise exception 'Negative account'; end if;
  end$$;`));
  console.log('PASS: real PostgreSQL/PostGIS acceptance and concurrency checks');
} catch (error) { console.error('Database verification failed:', error.message); process.exitCode = 1; }
finally { if (started) requireSuccess(await command(['rm', '-f', '-v', name])); }
