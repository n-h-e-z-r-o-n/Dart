-- Run once AFTER 001 and 003. KES amounts are integer minor units (100 = KES 1).
-- No live gateway calls or automatic escrow release are installed by this migration.
begin;
create table adaa_private.wallet_settings (
  singleton boolean primary key default true check(singleton),
  gateway_enabled boolean not null default false
);
insert into adaa_private.wallet_settings default values;
alter table adaa_private.wallet_settings enable row level security;
revoke all on adaa_private.wallet_settings from public,anon,authenticated;
create table adaa_private.wallet_accounts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid unique references auth.users(id),
  business_id uuid unique references public.businesses(id),
  system_account boolean not null default false,
  currency text not null default 'KES' check (currency='KES'),
  check (num_nonnulls(user_id,business_id)+(case when system_account then 1 else 0 end)=1)
);
create unique index wallet_single_clearing on adaa_private.wallet_accounts(system_account) where system_account;
insert into adaa_private.wallet_accounts(system_account) values(true);
create table adaa_private.wallet_journals (
  id uuid primary key default gen_random_uuid(),
  scope text not null, request_id uuid not null, kind text not null,
  payload jsonb not null, actor_id uuid references auth.users(id),
  reference_id uuid not null, created_at timestamptz not null default now(),
  unique(scope,request_id)
);
create table adaa_private.wallet_entries (
  id bigint generated always as identity primary key,
  journal_id uuid not null references adaa_private.wallet_journals(id),
  account_id uuid not null references adaa_private.wallet_accounts(id),
  bucket text not null check(bucket in ('available','escrow','withdrawal_pending')),
  delta bigint not null check(delta<>0)
);
create index wallet_entries_balance on adaa_private.wallet_entries(account_id,bucket);
create index wallet_entries_journal_idx on adaa_private.wallet_entries(journal_id);
create table adaa_private.wallet_permissions (
  business_id uuid not null, user_id uuid not null,
  can_release_escrow boolean not null default false,
  granted_by uuid not null references auth.users(id), updated_at timestamptz not null default now(),
  primary key(business_id,user_id),
  foreign key(business_id,user_id) references public.business_members(business_id,user_id) on delete cascade
);
create table adaa_private.wallet_permission_audit (
  id bigint generated always as identity primary key, business_id uuid not null,
  user_id uuid not null, can_release_escrow boolean not null, actor_id uuid not null,
  created_at timestamptz not null default now()
);
create table adaa_private.service_charges (
  id uuid primary key default gen_random_uuid(), business_id uuid not null references public.businesses(id),
  customer_id uuid not null references auth.users(id), amount_minor bigint not null check(amount_minor between 1 and 100000000000),
  description text not null check(length(btrim(description)) between 1 and 500),
  state text not null default 'requested' check(state in ('requested','funded','completed','refunded','external_paid')),
  created_by uuid not null references auth.users(id), created_at timestamptz not null default now(),
  completed_by uuid references auth.users(id), completed_at timestamptz,
  request_id uuid not null, unique(created_by,request_id)
);
create table adaa_private.wallet_gateway_jobs (
  id uuid primary key default gen_random_uuid(), account_id uuid not null references adaa_private.wallet_accounts(id),
  actor_id uuid not null references auth.users(id), request_id uuid not null,
  direction text not null check(direction in ('deposit','withdrawal')),
  amount_minor bigint not null check(amount_minor between 100 and 100000000000 and amount_minor%100=0),
  phone text not null check(phone ~ '^254[17][0-9]{8}$'),
  account_reference text not null unique default substr(replace(gen_random_uuid()::text,'-',''),1,12),
  state text not null default 'queued' check(state in ('queued','pending','review','succeeded','failed')),
  gateway_request_id text unique, receipt text unique,
  attempts integer not null default 0, lease_until timestamptz, next_attempt_at timestamptz not null default now(),
  created_at timestamptz not null default now(), settled_at timestamptz,
  evidence jsonb, unique(actor_id,request_id)
);
create index wallet_jobs_due on adaa_private.wallet_gateway_jobs(next_attempt_at) where state in ('queued','pending');
do $$ declare t text; begin
  foreach t in array array['wallet_accounts','wallet_journals','wallet_entries','wallet_permissions','wallet_permission_audit','service_charges','wallet_gateway_jobs'] loop
    execute format('alter table adaa_private.%I enable row level security',t);
    execute format('revoke all on adaa_private.%I from public,anon,authenticated',t);
  end loop;
end $$;

create function adaa_private.wallet_immutable() returns trigger language plpgsql set search_path='' as $$
begin raise exception 'LEDGER_IMMUTABLE' using errcode='42501'; end $$;
create trigger wallet_entries_immutable before update or delete on adaa_private.wallet_entries for each row execute function adaa_private.wallet_immutable();
create trigger wallet_journals_immutable before update or delete on adaa_private.wallet_journals for each row execute function adaa_private.wallet_immutable();
create trigger wallet_permission_audit_immutable before update or delete on adaa_private.wallet_permission_audit for each row execute function adaa_private.wallet_immutable();
create function adaa_private.wallet_balanced() returns trigger language plpgsql security definer set search_path='' as $$
begin
  if (select count(*)<2 or sum(delta)<>0 from adaa_private.wallet_entries where journal_id=new.id) then
    raise exception 'UNBALANCED_JOURNAL'; end if;
  return null;
end $$;
create constraint trigger wallet_balanced after insert on adaa_private.wallet_journals
  deferrable initially deferred for each row execute function adaa_private.wallet_balanced();

create function adaa_private.wallet_account(p_user uuid default null,p_business uuid default null) returns uuid
language plpgsql security definer set search_path='' as $$
declare v_id uuid;
begin
  if num_nonnulls(p_user,p_business)<>1 then raise exception 'INVALID_ACCOUNT'; end if;
  if p_user is not null then
    insert into adaa_private.wallet_accounts(user_id) values(p_user) on conflict(user_id) do nothing;
    select id into v_id from adaa_private.wallet_accounts where user_id=p_user;
  else
    insert into adaa_private.wallet_accounts(business_id) values(p_business) on conflict(business_id) do nothing;
    select id into v_id from adaa_private.wallet_accounts where business_id=p_business;
  end if;
  return v_id;
end $$;
create function adaa_private.wallet_balance(p_account uuid,p_bucket text) returns bigint
language sql stable security definer set search_path='' as $$
  select coalesce(sum(delta),0)::bigint from adaa_private.wallet_entries where account_id=p_account and bucket=p_bucket;
$$;
create function adaa_private.wallet_can_release(p_business uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select adaa_private.has_role(p_business,array['owner']) or exists(
    select 1 from adaa_private.wallet_permissions p join public.business_members m using(business_id,user_id)
    where p.business_id=p_business and p.user_id=auth.uid() and p.can_release_escrow);
$$;
-- All wallet mutations serialize on account rows in deterministic order.
create function adaa_private.wallet_move(p_scope text,p_request uuid,p_kind text,p_reference uuid,
  p_from uuid,p_from_bucket text,p_to uuid,p_to_bucket text,p_amount bigint,p_actor uuid) returns uuid
language plpgsql security definer set search_path='' as $$
declare v_id uuid; v_payload jsonb; v_previous jsonb; v_system boolean;
begin
  if p_request is null or p_amount is null or p_amount not between 1 and 100000000000 or
    (p_from=p_to and p_from_bucket=p_to_bucket) then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
  perform pg_advisory_xact_lock(hashtextextended(p_scope||p_request::text,0));
  v_payload:=jsonb_build_object('kind',p_kind,'reference',p_reference,'from',p_from,'from_bucket',p_from_bucket,
    'to',p_to,'to_bucket',p_to_bucket,'amount',p_amount);
  select id,payload into v_id,v_previous from adaa_private.wallet_journals where scope=p_scope and request_id=p_request;
  if found then
    if v_previous<>v_payload then raise exception 'REQUEST_ALREADY_USED' using errcode='22023'; end if;
    return v_id;
  end if;
  perform id from adaa_private.wallet_accounts where id in(p_from,p_to) order by id for update;
  select system_account into strict v_system from adaa_private.wallet_accounts where id=p_from;
  if not v_system and adaa_private.wallet_balance(p_from,p_from_bucket)<p_amount then
    raise exception 'INSUFFICIENT_FUNDS' using errcode='22023'; end if;
  insert into adaa_private.wallet_journals(scope,request_id,kind,payload,reference_id,actor_id)
    values(p_scope,p_request,p_kind,v_payload,p_reference,p_actor) returning id into v_id;
  insert into adaa_private.wallet_entries(journal_id,account_id,bucket,delta)
    values(v_id,p_from,p_from_bucket,-p_amount),(v_id,p_to,p_to_bucket,p_amount);
  return v_id;
end $$;

create function public.set_escrow_release_permission(p_business_id uuid,p_user_id uuid,p_allowed boolean) returns void
language plpgsql security definer set search_path='' as $$
begin
  if not adaa_private.has_role(p_business_id,array['owner']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
  insert into adaa_private.wallet_permissions(business_id,user_id,can_release_escrow,granted_by)
    values(p_business_id,p_user_id,p_allowed,auth.uid()) on conflict(business_id,user_id)
    do update set can_release_escrow=excluded.can_release_escrow,granted_by=auth.uid(),updated_at=now();
  insert into adaa_private.wallet_permission_audit(business_id,user_id,can_release_escrow,actor_id)
    values(p_business_id,p_user_id,p_allowed,auth.uid());
end $$;
create function public.get_business_wallet_team(p_business_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
  if not adaa_private.has_role(p_business_id,array['owner']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
  return coalesce((select jsonb_agg(x) from (
    select m.user_id,coalesce(p.display_name,'') as display_name,m.role,
      (m.role='owner' or coalesce(w.can_release_escrow,false)) as can_release_escrow
    from public.business_members m left join public.profiles p on p.id=m.user_id
    left join adaa_private.wallet_permissions w on w.business_id=m.business_id and w.user_id=m.user_id
    where m.business_id=p_business_id order by m.created_at,m.user_id)x),'[]'::jsonb);
end $$;
create function public.create_service_charge(p_request_id uuid,p_business_id uuid,p_customer_id uuid,p_amount_minor bigint,p_description text) returns uuid
language plpgsql security definer set search_path='' as $$
declare v adaa_private.service_charges; begin
  if not adaa_private.has_role(p_business_id,array['owner','manager']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
  insert into adaa_private.service_charges(business_id,customer_id,amount_minor,description,created_by,request_id)
    values(p_business_id,p_customer_id,p_amount_minor,btrim(p_description),auth.uid(),p_request_id)
    on conflict(created_by,request_id) do nothing;
  select * into v from adaa_private.service_charges where created_by=auth.uid() and request_id=p_request_id;
  if v.business_id<>p_business_id or v.customer_id<>p_customer_id or v.amount_minor<>p_amount_minor or v.description<>btrim(p_description) then
    raise exception 'REQUEST_ALREADY_USED' using errcode='22023'; end if;
  return v.id;
end $$;
create function public.pay_service_from_wallet(p_charge_id uuid,p_request_id uuid) returns uuid
language plpgsql security definer set search_path='' as $$
declare v adaa_private.service_charges; v_journal uuid; begin
  select * into v from adaa_private.service_charges where id=p_charge_id for update;
  if not found or v.customer_id<>auth.uid() or auth.uid() is null then raise exception 'FORBIDDEN' using errcode='42501'; end if;
  if v.state not in ('requested','funded') then raise exception 'INVALID_SERVICE_STATE' using errcode='22023'; end if;
  if v.state='funded' then
    select id into v_journal from adaa_private.wallet_journals where scope=auth.uid()::text and request_id=p_request_id and reference_id=v.id and kind='service_hold';
    if v_journal is null then raise exception 'ALREADY_FUNDED' using errcode='22023'; end if;
    return v_journal;
  end if;
  if exists(select 1 from public.businesses where id=v.business_id and publication_status='suspended') then
    raise exception 'BUSINESS_SUSPENDED' using errcode='22023'; end if;
  v_journal:=adaa_private.wallet_move(auth.uid()::text,p_request_id,'service_hold',v.id,
    adaa_private.wallet_account(v.customer_id), 'available',adaa_private.wallet_account(null,v.business_id),'escrow',v.amount_minor,auth.uid());
  update adaa_private.service_charges set state='funded' where id=v.id;
  return v_journal;
end $$;
create function public.complete_service_and_release(p_charge_id uuid,p_request_id uuid) returns uuid
language plpgsql security definer set search_path='' as $$
declare v adaa_private.service_charges; v_account uuid; v_journal uuid; begin
  select * into v from adaa_private.service_charges where id=p_charge_id for update;
  if not found or not adaa_private.wallet_can_release(v.business_id) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
  if v.state not in ('funded','completed') then raise exception 'INVALID_SERVICE_STATE' using errcode='22023'; end if;
  if v.state='completed' then
    select id into v_journal from adaa_private.wallet_journals where scope=auth.uid()::text and request_id=p_request_id and reference_id=v.id and kind='service_release';
    if v_journal is null then raise exception 'ALREADY_COMPLETED' using errcode='22023'; end if;
    return v_journal;
  end if;
  v_account:=adaa_private.wallet_account(null,v.business_id);
  v_journal:=adaa_private.wallet_move(auth.uid()::text,p_request_id,'service_release',v.id,v_account,'escrow',v_account,'available',v.amount_minor,auth.uid());
  update adaa_private.service_charges set state='completed',completed_by=auth.uid(),completed_at=now() where id=v.id;
  return v_journal;
end $$;
create function public.refund_service_escrow(p_charge_id uuid,p_request_id uuid) returns uuid
language plpgsql security definer set search_path='' as $$
declare v adaa_private.service_charges; v_journal uuid; begin
  select * into v from adaa_private.service_charges where id=p_charge_id for update;
  if not found or not adaa_private.has_role(v.business_id,array['owner']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
  if v.state not in ('funded','refunded') then raise exception 'INVALID_SERVICE_STATE' using errcode='22023'; end if;
  if v.state='refunded' then
    select id into v_journal from adaa_private.wallet_journals where scope=auth.uid()::text and request_id=p_request_id and reference_id=v.id and kind='service_refund';
    if v_journal is null then raise exception 'ALREADY_REFUNDED' using errcode='22023'; end if;
    return v_journal;
  end if;
  v_journal:=adaa_private.wallet_move(auth.uid()::text,p_request_id,'service_refund',v.id,
    adaa_private.wallet_account(null,v.business_id),'escrow',adaa_private.wallet_account(v.customer_id),'available',v.amount_minor,auth.uid());
  update adaa_private.service_charges set state='refunded' where id=v.id;
  return v_journal;
end $$;
create function public.record_external_service_payment(p_charge_id uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
  update adaa_private.service_charges set state='external_paid',completed_by=auth.uid(),completed_at=now()
    where id=p_charge_id and state='requested' and adaa_private.wallet_can_release(business_id);
  if not found then raise exception 'FORBIDDEN_OR_INVALID_STATE' using errcode='42501'; end if;
end $$;

create function public.request_wallet_gateway_operation(p_request_id uuid,p_direction text,p_amount_minor bigint,p_phone text,p_business_id uuid default null) returns uuid
language plpgsql security definer set search_path='' as $$
declare v_account uuid; v adaa_private.wallet_gateway_jobs; begin
  if auth.uid() is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  if not (select gateway_enabled from adaa_private.wallet_settings where singleton) then
    raise exception 'WALLET_PAYMENTS_NOT_ACTIVE' using errcode='22023'; end if;
  if p_direction is null or p_direction not in ('deposit','withdrawal') then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
  -- Business top-ups are not part of this flow. Only the owner can withdraw business funds.
  if p_business_id is not null and (p_direction<>'withdrawal' or not adaa_private.has_role(p_business_id,array['owner'])) then
    raise exception 'FORBIDDEN' using errcode='42501'; end if;
  v_account:=adaa_private.wallet_account(case when p_business_id is null then auth.uid() end,p_business_id);
  perform pg_advisory_xact_lock(hashtextextended('gateway'||auth.uid()::text||p_request_id::text,0));
  insert into adaa_private.wallet_gateway_jobs(account_id,actor_id,request_id,direction,amount_minor,phone)
    values(v_account,auth.uid(),p_request_id,p_direction,p_amount_minor,p_phone) on conflict(actor_id,request_id) do nothing;
  select * into v from adaa_private.wallet_gateway_jobs where actor_id=auth.uid() and request_id=p_request_id;
  if v.account_id<>v_account or v.direction<>p_direction or v.amount_minor<>p_amount_minor or v.phone<>p_phone then
    raise exception 'REQUEST_ALREADY_USED' using errcode='22023'; end if;
  if p_direction='withdrawal' then
    perform adaa_private.wallet_move('reserve',v.id,'withdrawal_reserve',v.id,v_account,'available',v_account,'withdrawal_pending',p_amount_minor,auth.uid());
  end if;
  return v.id;
end $$;

create function public.get_wallet_dashboard(p_business_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v_id uuid; v_result jsonb; begin
  if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner'])) then
    raise exception 'FORBIDDEN' using errcode='42501'; end if;
  v_id:=adaa_private.wallet_account(case when p_business_id is null then auth.uid() end,p_business_id);
  select jsonb_build_object('currency','KES','payments_active',(select gateway_enabled from adaa_private.wallet_settings where singleton),'available_minor',adaa_private.wallet_balance(v_id,'available'),
    'escrow_minor',adaa_private.wallet_balance(v_id,'escrow'),'withdrawal_pending_minor',adaa_private.wallet_balance(v_id,'withdrawal_pending'),
    'completed_services',(select count(*) from adaa_private.service_charges where business_id=p_business_id and state='completed'),
    'held_services',(select count(*) from adaa_private.service_charges where business_id=p_business_id and state='funded'),
    'completed_service_value_minor',coalesce((select sum(amount_minor) from adaa_private.service_charges where business_id=p_business_id and state='completed'),0),
    'updated_at',now(), 'recent_transactions',coalesce((select jsonb_agg(x) from (
      select j.id,j.kind,j.reference_id,j.created_at,e.bucket,e.delta as amount_minor from adaa_private.wallet_entries e
      join adaa_private.wallet_journals j on j.id=e.journal_id where e.account_id=v_id order by j.created_at desc,e.id desc limit 10
    )x),'[]'::jsonb)) into v_result;
  return v_result;
end $$;
create function public.get_service_charges(p_business_id uuid default null) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
  if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner','manager','staff'])) then
    raise exception 'FORBIDDEN' using errcode='42501'; end if;
  return coalesce((select jsonb_agg(x) from (
    select id,business_id,customer_id,amount_minor,description,state,created_at,completed_by,completed_at
    from adaa_private.service_charges where (p_business_id is not null and business_id=p_business_id)
      or (p_business_id is null and customer_id=auth.uid()) order by created_at desc limit 100)x),'[]'::jsonb);
end $$;

-- Worker-only RPCs. Never expose service-role credentials to Android.
create function public.claim_wallet_gateway_jobs() returns jsonb
language sql security definer set search_path='' as $$
  with claimed as (update adaa_private.wallet_gateway_jobs set attempts=attempts+1,lease_until=now()+interval '5 minutes',
    state='pending',next_attempt_at=now()+interval '1 minute'
  where id in(select id from adaa_private.wallet_gateway_jobs where state in ('queued','pending')
    and (select gateway_enabled from adaa_private.wallet_settings where singleton)
    and next_attempt_at<=now() and (lease_until is null or lease_until<now()) order by created_at for update skip locked limit 10)
  returning *) select coalesce(jsonb_agg(claimed),'[]'::jsonb) from claimed;
$$;
create function public.record_wallet_gateway_progress(p_job_id uuid,p_gateway_request_id text,p_review boolean default false) returns void
language plpgsql security definer set search_path='' as $$
begin
  update adaa_private.wallet_gateway_jobs set gateway_request_id=coalesce(gateway_request_id,p_gateway_request_id),
    state=case when p_review then 'review' else 'pending' end,lease_until=null
    where id=p_job_id and state='pending' and (gateway_request_id is null or p_gateway_request_id is null or gateway_request_id=p_gateway_request_id);
  if not found then raise exception 'INVALID_GATEWAY_STATE'; end if;
end $$;
create function public.settle_wallet_gateway_job(p_job_id uuid,p_gateway_request_id text,p_succeeded boolean,p_receipt text,p_evidence jsonb) returns void
language plpgsql security definer set search_path='' as $$
declare v adaa_private.wallet_gateway_jobs; v_clearing uuid; begin
  select * into v from adaa_private.wallet_gateway_jobs where id=p_job_id for update;
  if not found or p_succeeded is null or p_gateway_request_id is null or v.gateway_request_id is distinct from p_gateway_request_id
    or p_evidence is null or jsonb_typeof(p_evidence)<>'object' then raise exception 'INVALID_GATEWAY_EVIDENCE'; end if;
  if v.state in ('succeeded','failed') then
    if (v.state='succeeded')<>p_succeeded or v.receipt is distinct from nullif(p_receipt,'') then raise exception 'SETTLEMENT_CONFLICT'; end if;
    return;
  end if;
  if p_succeeded and nullif(p_receipt,'') is null then raise exception 'RECEIPT_REQUIRED'; end if;
  if p_evidence->>'requestId' is distinct from p_gateway_request_id or
    (p_evidence->>'amount')::numeric*100 is distinct from v.amount_minor::numeric then raise exception 'EVIDENCE_MISMATCH'; end if;
  if p_succeeded and (p_evidence->>'status' is distinct from 'succeeded' or
    p_evidence->>'transactionId' is distinct from p_receipt or
    (v.direction='deposit' and p_evidence->>'accountReference' is distinct from v.account_reference)) then raise exception 'EVIDENCE_MISMATCH'; end if;
  if not p_succeeded and (p_evidence->>'status' not in ('failed','cancelled') or p_evidence->>'status' is null) then raise exception 'NONFINAL_FAILURE'; end if;
  if not p_succeeded and v.direction='withdrawal' and (p_evidence->>'operator_verified_no_payout') is distinct from 'true' then
    raise exception 'OPERATOR_RECONCILIATION_REQUIRED'; end if;
  select id into v_clearing from adaa_private.wallet_accounts where system_account;
  if v.direction='deposit' and p_succeeded then
    perform adaa_private.wallet_move('settle',v.id,'wallet_deposit',v.id,v_clearing,'available',v.account_id,'available',v.amount_minor,null);
  elsif v.direction='withdrawal' then
    perform adaa_private.wallet_move('settle',v.id,case when p_succeeded then 'wallet_withdrawal' else 'withdrawal_return' end,v.id,
      v.account_id,'withdrawal_pending',case when p_succeeded then v_clearing else v.account_id end,'available',v.amount_minor,null);
  end if;
  update adaa_private.wallet_gateway_jobs set state=case when p_succeeded then 'succeeded' else 'failed' end,
    receipt=nullif(p_receipt,''),evidence=p_evidence,settled_at=now(),lease_until=null where id=v.id;
end $$;

-- Explicitly remove PostgreSQL's default PUBLIC execution privilege.
do $$ declare f record; begin
  for f in select p.oid::regprocedure as signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace
    where (n.nspname='adaa_private' and p.proname like 'wallet_%') or (n.nspname='public' and p.proname in (
      'set_escrow_release_permission','get_business_wallet_team','create_service_charge','pay_service_from_wallet','complete_service_and_release',
      'refund_service_escrow','record_external_service_payment','request_wallet_gateway_operation','get_wallet_dashboard','get_service_charges',
      'claim_wallet_gateway_jobs','record_wallet_gateway_progress','settle_wallet_gateway_job')) loop
    execute format('revoke all on function %s from public,anon,authenticated',f.signature);
  end loop;
end $$;
grant execute on function public.set_escrow_release_permission(uuid,uuid,boolean),public.create_service_charge(uuid,uuid,uuid,bigint,text),
  public.pay_service_from_wallet(uuid,uuid),public.complete_service_and_release(uuid,uuid),public.refund_service_escrow(uuid,uuid),
  public.record_external_service_payment(uuid),public.request_wallet_gateway_operation(uuid,text,bigint,text,uuid),
  public.get_wallet_dashboard(uuid),public.get_service_charges(uuid),public.get_business_wallet_team(uuid) to authenticated;
grant execute on function public.claim_wallet_gateway_jobs(),public.record_wallet_gateway_progress(uuid,text,boolean),
  public.settle_wallet_gateway_job(uuid,text,boolean,text,jsonb) to service_role;
notify pgrst,'reload schema';
commit;
