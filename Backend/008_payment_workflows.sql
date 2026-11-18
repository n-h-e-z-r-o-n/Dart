-- Apply once AFTER 005. Does not deploy Edge Functions or enable payments.
begin;
update adaa_private.wallet_settings set gateway_enabled=false where singleton;
-- Deferred triggers run at COMMIT after the RPC's security-definer context ends.
-- Give only this read-only invariant check its owning context, not ledger access to clients.
alter function adaa_private.wallet_balanced() security definer;
create index if not exists wallet_entries_journal_idx on adaa_private.wallet_entries(journal_id);
create index service_charges_customer_created_idx on adaa_private.service_charges(customer_id,created_at desc,id desc);
create index service_charges_business_created_idx on adaa_private.service_charges(business_id,created_at desc,id desc);
create index wallet_gateway_jobs_account_created_idx on adaa_private.wallet_gateway_jobs(account_id,created_at desc,id desc);
alter table adaa_private.wallet_settings add column accept_new_requests boolean not null default true;
create table adaa_private.wallet_policy (
 singleton boolean primary key default true check(singleton), version bigint not null default 1,
 configured boolean not null default false, min_external_minor bigint,
 max_external_minor bigint, daily_external_minor bigint, dispute_window_hours integer,
 platform_fee_minor bigint not null default 0 check(platform_fee_minor=0),
 configured_by uuid references auth.users(id), configured_at timestamptz,
 check(not configured or (min_external_minor>=100 and min_external_minor%100=0 and
  max_external_minor>=min_external_minor and max_external_minor<=100000000000 and max_external_minor%100=0 and
  daily_external_minor>=max_external_minor and daily_external_minor<=1000000000000 and
  dispute_window_hours between 1 and 2160))
);
insert into adaa_private.wallet_policy default values;
create table adaa_private.service_disputes (
 id uuid primary key default gen_random_uuid(), charge_id uuid not null unique references adaa_private.service_charges(id),
 customer_id uuid not null references auth.users(id), previous_state text not null check(previous_state in ('funded','completed')),
 reason text not null check(length(btrim(reason)) between 10 and 2000),
 state text not null default 'open' check(state in ('open','refunded','released')),
 created_at timestamptz not null default now(), resolved_at timestamptz,
 resolved_by uuid references auth.users(id), resolution text check(length(resolution) between 10 and 2000)
);
create table adaa_private.service_events (
 id bigint generated always as identity primary key,charge_id uuid not null references adaa_private.service_charges(id),
 action text not null,actor_id uuid references auth.users(id),message text not null default '',created_at timestamptz not null default now()
);
create table adaa_private.external_payment_reports (
 charge_id uuid primary key references adaa_private.service_charges(id),customer_id uuid not null references auth.users(id),
 reference text not null check(length(btrim(reference)) between 3 and 80),created_at timestamptz not null default now()
);
create index service_events_charge_idx on adaa_private.service_events(charge_id,created_at,id);
create table adaa_private.wallet_operator_alerts (
 kind text primary key,active boolean not null,last_seen_at timestamptz not null default now(),details jsonb not null,
 notification_lease_until timestamptz,last_notified_at timestamptz
);
create table adaa_private.wallet_worker_health (
 singleton boolean primary key default true check(singleton),last_run_at timestamptz,processed integer not null default 0,retry_later integer not null default 0
);
insert into adaa_private.wallet_worker_health default values;
create table adaa_private.wallet_recovery_audit (
 id bigint generated always as identity primary key,job_id uuid not null references adaa_private.wallet_gateway_jobs(id),
 reason text not null,created_at timestamptz not null default now()
);
create table adaa_private.wallet_custody_snapshots (
 id uuid primary key,observed_at timestamptz not null,recorded_at timestamptz not null default clock_timestamp(),
 observed_minor bigint not null,platform_funding_minor bigint not null,provider_fees_minor bigint not null,
 custody_liability_minor numeric not null,expected_minor numeric not null,difference_minor numeric not null,
 shortfall_minor numeric not null,source text not null,evidence jsonb not null
);
do $$ declare t text; begin
 foreach t in array array['wallet_policy','service_disputes','service_events','external_payment_reports','wallet_operator_alerts','wallet_worker_health','wallet_recovery_audit','wallet_custody_snapshots'] loop
  execute format('alter table adaa_private.%I enable row level security',t);
  execute format('revoke all on adaa_private.%I from public,anon,authenticated',t);
 end loop;
end $$;
create trigger service_events_immutable before update or delete on adaa_private.service_events for each row execute function adaa_private.wallet_immutable();
create trigger wallet_recovery_audit_immutable before update or delete on adaa_private.wallet_recovery_audit for each row execute function adaa_private.wallet_immutable();
create trigger wallet_custody_snapshots_immutable before update or delete on adaa_private.wallet_custody_snapshots for each row execute function adaa_private.wallet_immutable();
alter table adaa_private.service_charges drop constraint service_charges_state_check;
alter table adaa_private.service_charges add check(state in ('requested','funded','completed','refunded','external_paid','disputed','cancelled'));
create function adaa_private.wallet_record_service_event() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if tg_op='INSERT' or old.state is distinct from new.state then
  insert into adaa_private.service_events(charge_id,action,actor_id) values(new.id,new.state,auth.uid());
 end if;
 return new;
end $$;
create trigger service_state_audit after insert or update of state on adaa_private.service_charges for each row execute function adaa_private.wallet_record_service_event();

create function public.configure_wallet_policy(p_min_minor bigint,p_max_minor bigint,p_daily_minor bigint,p_dispute_hours integer,p_platform_fee_minor bigint) returns void
language plpgsql security definer set search_path='' as $$
begin
 if not adaa_private.is_admin() then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if num_nonnulls(p_min_minor,p_max_minor,p_daily_minor,p_dispute_hours,p_platform_fee_minor)<>5 then raise exception 'INVALID_POLICY' using errcode='22023'; end if;
 -- Non-zero fees require separate fee ledger rules; never silently deduct an unimplemented fee.
 update adaa_private.wallet_policy set configured=true,version=version+1,min_external_minor=p_min_minor,max_external_minor=p_max_minor,
  daily_external_minor=p_daily_minor,dispute_window_hours=p_dispute_hours,platform_fee_minor=p_platform_fee_minor,
  configured_by=auth.uid(),configured_at=now() where singleton;
end $$;
create function public.get_wallet_policy() returns jsonb language plpgsql stable security definer set search_path='' as $$
declare v jsonb; begin
 if auth.uid() is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
 select jsonb_build_object('version',p.version,'configured',p.configured,'payments_active',p.configured and s.gateway_enabled and s.accept_new_requests,
  'currency','KES','min_external_minor',p.min_external_minor,'max_external_minor',p.max_external_minor,
  'daily_external_minor',p.daily_external_minor,'dispute_window_hours',p.dispute_window_hours,'platform_fee_minor',p.platform_fee_minor)
 into v from adaa_private.wallet_policy p cross join adaa_private.wallet_settings s;
 return v;
end $$;
alter function public.get_wallet_dashboard(uuid) set schema adaa_private;
alter function adaa_private.get_wallet_dashboard(uuid) rename to wallet_dashboard_v1;
revoke all on function adaa_private.wallet_dashboard_v1(uuid) from public,anon,authenticated;
create function public.get_wallet_dashboard(p_business_id uuid default null) returns jsonb language plpgsql security definer set search_path='' as $$
declare v jsonb; v_held numeric:=0; begin
 v:=adaa_private.wallet_dashboard_v1(p_business_id);
 if p_business_id is null then
  select coalesce(sum(c.amount_minor),0) into v_held from adaa_private.service_charges c
   left join adaa_private.service_disputes d on d.charge_id=c.id where c.customer_id=auth.uid()
   and (c.state='funded' or (c.state='disputed' and d.previous_state='funded'));
 end if;
 return v||jsonb_build_object('held_for_services_minor',v_held,'withdrawals_blocked',
  exists(select 1 from adaa_private.service_disputes d join adaa_private.service_charges c on c.id=d.charge_id where c.business_id=p_business_id and d.state='open'),'payments_active',
  (select gateway_enabled and accept_new_requests from adaa_private.wallet_settings where singleton) and (select configured from adaa_private.wallet_policy where singleton));
end $$;

-- Hide the old implementation so clients cannot bypass limits, dispute holds or quoted policy versions.
alter function public.request_wallet_gateway_operation(uuid,text,bigint,text,uuid) set schema adaa_private;
alter function adaa_private.request_wallet_gateway_operation(uuid,text,bigint,text,uuid) rename to wallet_request_gateway_v1;
revoke all on function adaa_private.wallet_request_gateway_v1(uuid,text,bigint,text,uuid) from public,anon,authenticated;
create function public.quote_wallet_operation(p_direction text,p_amount_minor bigint,p_business_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v adaa_private.wallet_policy; v_account uuid; v_used numeric; begin
 if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 select * into v from adaa_private.wallet_policy where singleton;
 if not v.configured then raise exception 'WALLET_POLICY_NOT_CONFIGURED' using errcode='22023'; end if;
 if p_business_id is not null and p_direction='withdrawal' and exists(select 1 from adaa_private.service_disputes d join adaa_private.service_charges c on c.id=d.charge_id
  where c.business_id=p_business_id and d.state='open') then raise exception 'WITHDRAWAL_BLOCKED_BY_DISPUTE' using errcode='22023'; end if;
 if p_direction is null or p_direction not in ('deposit','withdrawal') or (p_business_id is not null and p_direction<>'withdrawal') or
  p_amount_minor is null or p_amount_minor<v.min_external_minor or p_amount_minor>v.max_external_minor or p_amount_minor%100<>0 then
  raise exception 'WALLET_AMOUNT_OUTSIDE_LIMITS' using errcode='22023'; end if;
 v_account:=adaa_private.wallet_account(case when p_business_id is null then auth.uid() end,p_business_id);
 select coalesce(sum(amount_minor),0) into v_used from adaa_private.wallet_gateway_jobs where account_id=v_account
  and created_at>=date_trunc('day',now() at time zone 'UTC') at time zone 'UTC' and state<>'failed';
 return jsonb_build_object('policy_version',v.version,'amount_minor',p_amount_minor,'fee_minor',v.platform_fee_minor,
  'total_debit_minor',case when p_direction='withdrawal' then p_amount_minor+v.platform_fee_minor else 0 end,
  'remaining_daily_minor',greatest(v.daily_external_minor-v_used,0),'available_minor',adaa_private.wallet_balance(v_account,'available'),
  'payments_active',(select gateway_enabled and accept_new_requests from adaa_private.wallet_settings where singleton));
end $$;
create function public.request_wallet_gateway_operation(p_request_id uuid,p_direction text,p_amount_minor bigint,p_phone text,p_business_id uuid default null,p_policy_version bigint default null) returns uuid
language plpgsql security definer set search_path='' as $$
declare v_account uuid; v_existing adaa_private.wallet_gateway_jobs; v_quote jsonb; begin
 if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 v_account:=adaa_private.wallet_account(case when p_business_id is null then auth.uid() end,p_business_id);
 perform id from adaa_private.wallet_accounts where id=v_account for update;
 select * into v_existing from adaa_private.wallet_gateway_jobs where actor_id=auth.uid() and request_id=p_request_id;
 if found then
  if v_existing.account_id is distinct from v_account or v_existing.direction is distinct from p_direction or
   v_existing.amount_minor is distinct from p_amount_minor or v_existing.phone is distinct from p_phone then raise exception 'REQUEST_ALREADY_USED' using errcode='22023'; end if;
  return v_existing.id;
 end if;
 if p_business_id is not null and exists(select 1 from adaa_private.service_disputes d join adaa_private.service_charges c on c.id=d.charge_id where c.business_id=p_business_id and d.state='open') then
  raise exception 'WITHDRAWAL_BLOCKED_BY_DISPUTE' using errcode='22023'; end if;
 v_quote:=public.quote_wallet_operation(p_direction,p_amount_minor,p_business_id);
 if p_policy_version is distinct from (v_quote->>'policy_version')::bigint then raise exception 'WALLET_POLICY_CHANGED' using errcode='22023'; end if;
 if not (v_quote->>'payments_active')::boolean then raise exception 'WALLET_PAYMENTS_NOT_ACTIVE' using errcode='22023'; end if;
 select * into v_existing from adaa_private.wallet_gateway_jobs where account_id=v_account and direction=p_direction and state in ('queued','pending','review') order by created_at limit 1;
 if found then
  if v_existing.amount_minor=p_amount_minor and v_existing.phone=p_phone then return v_existing.id; end if;
  raise exception 'ACTIVE_WALLET_OPERATION' using errcode='22023';
 end if;
 if p_amount_minor>(v_quote->>'remaining_daily_minor')::numeric then raise exception 'WALLET_DAILY_LIMIT' using errcode='22023'; end if;
 return adaa_private.wallet_request_gateway_v1(p_request_id,p_direction,p_amount_minor,p_phone,p_business_id);
end $$;

create function public.get_wallet_operations(p_business_id uuid default null,p_before timestamptz default null,p_before_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v_account uuid; begin
 if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 v_account:=adaa_private.wallet_account(case when p_business_id is null then auth.uid() end,p_business_id);
 return coalesce((select jsonb_agg(x) from (select id,direction,amount_minor,right(phone,4) as phone_suffix,state,receipt,created_at,settled_at
  from adaa_private.wallet_gateway_jobs where account_id=v_account and (p_before is null or (created_at,id)<(p_before,p_before_id))
  order by created_at desc,id desc limit 30)x),'[]'::jsonb);
end $$;
create function public.get_wallet_activity(p_business_id uuid default null,p_before timestamptz default null,p_before_id uuid default null) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v_account uuid; begin
 if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 v_account:=adaa_private.wallet_account(case when p_business_id is null then auth.uid() end,p_business_id);
 return coalesce((select jsonb_agg(x) from (select j.id,j.kind,j.created_at,j.reference_id,
  jsonb_agg(jsonb_build_object('bucket',e.bucket,'amount_minor',e.delta) order by e.id) as entries
  from adaa_private.wallet_entries e join adaa_private.wallet_journals j on j.id=e.journal_id
  where e.account_id=v_account and (p_before is null or (j.created_at,j.id)<(p_before,p_before_id))
  group by j.id order by j.created_at desc,j.id desc limit 30)x),'[]'::jsonb);
end $$;
create function public.get_wallet_operation(p_operation_id uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
declare j adaa_private.wallet_gateway_jobs; a adaa_private.wallet_accounts; begin
 select * into j from adaa_private.wallet_gateway_jobs where id=p_operation_id;
 if not found then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 select * into a from adaa_private.wallet_accounts where id=j.account_id;
 if auth.uid() is null or (a.user_id is distinct from auth.uid() and not coalesce(adaa_private.has_role(a.business_id,array['owner']),false)) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 return jsonb_build_object('id',j.id,'direction',j.direction,'amount_minor',j.amount_minor,'phone_suffix',right(j.phone,4),
  'state',j.state,'receipt',j.receipt,'created_at',j.created_at,'settled_at',j.settled_at);
end $$;
create function public.get_payment_identity() returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
 return jsonb_build_object('payment_code',auth.uid(),'display_name',coalesce((select display_name from public.profiles where id=auth.uid()),''));
end $$;
create function public.get_payment_requests(p_business_id uuid default null,p_before timestamptz default null,p_before_id uuid default null) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner','manager','staff'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(x) from (select c.id,c.business_id,b.name as business_name,c.customer_id,
  coalesce(p.display_name,'Customer') as customer_name,c.description,c.amount_minor,c.state,c.created_at,c.completed_at,
  (p_business_id is not null and adaa_private.wallet_can_release(c.business_id)) as can_release,
  (p_business_id is not null and adaa_private.has_role(c.business_id,array['owner'])) as can_refund,
  exists(select 1 from adaa_private.external_payment_reports r where r.charge_id=c.id) as external_reported
  from adaa_private.service_charges c join public.businesses b on b.id=c.business_id left join public.profiles p on p.id=c.customer_id
  where ((p_business_id is not null and c.business_id=p_business_id) or (p_business_id is null and c.customer_id=auth.uid()))
   and (p_before is null or (c.created_at,c.id)<(p_before,p_before_id)) order by c.created_at desc,c.id desc limit 30)x),'[]'::jsonb);
end $$;
create function public.get_payment_request(p_charge_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare c adaa_private.service_charges; begin
 select * into c from adaa_private.service_charges where id=p_charge_id;
 if not found or auth.uid() is null or (c.customer_id<>auth.uid() and not adaa_private.has_role(c.business_id,array['owner','manager','staff'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 return jsonb_build_object('id',c.id,'business_id',c.business_id,'business_name',(select name from public.businesses where id=c.business_id),
  'customer_id',c.customer_id,'customer_name',coalesce((select display_name from public.profiles where id=c.customer_id),''),
  'description',c.description,'amount_minor',c.amount_minor,'state',c.state,'created_at',c.created_at,'completed_at',c.completed_at,
  'can_release',adaa_private.wallet_can_release(c.business_id),'can_refund',adaa_private.has_role(c.business_id,array['owner']),
  'external_reported',exists(select 1 from adaa_private.external_payment_reports where charge_id=c.id),
  'dispute',(select jsonb_build_object('id',id,'reason',reason,'state',state,'resolution',resolution,'created_at',created_at) from adaa_private.service_disputes where charge_id=c.id),
  'events',coalesce((select jsonb_agg(x) from (select e.action,e.message,e.created_at,coalesce(p.display_name,'') as actor_name
    from adaa_private.service_events e left join public.profiles p on p.id=e.actor_id where charge_id=c.id order by e.created_at,e.id)x),'[]'::jsonb));
end $$;
create function public.cancel_payment_request(p_charge_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare c adaa_private.service_charges; begin
 select * into c from adaa_private.service_charges where id=p_charge_id for update;
 if not found or auth.uid() is null or (c.customer_id<>auth.uid() and not adaa_private.has_role(c.business_id,array['owner','manager'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if c.state='cancelled' then return; end if;
 if c.state<>'requested' then raise exception 'INVALID_SERVICE_STATE' using errcode='22023'; end if;
 update adaa_private.service_charges set state='cancelled' where id=c.id;
end $$;
create function public.report_external_payment(p_charge_id uuid,p_reference text) returns void language plpgsql security definer set search_path='' as $$
declare c adaa_private.service_charges; begin
 select * into c from adaa_private.service_charges where id=p_charge_id for update;
 if not found or c.customer_id is distinct from auth.uid() then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if c.state<>'requested' then raise exception 'INVALID_SERVICE_STATE' using errcode='22023'; end if;
 insert into adaa_private.external_payment_reports(charge_id,customer_id,reference) values(c.id,auth.uid(),btrim(p_reference)) on conflict(charge_id) do nothing;
 if found then insert into adaa_private.service_events(charge_id,action,actor_id,message) values(c.id,'external_payment_reported',auth.uid(),btrim(p_reference)); end if;
end $$;
-- A reported outside payment cannot also be paid in-app until the business checks it.
alter function public.pay_service_from_wallet(uuid,uuid) set schema adaa_private;
alter function adaa_private.pay_service_from_wallet(uuid,uuid) rename to wallet_pay_service_v1;
revoke all on function adaa_private.wallet_pay_service_v1(uuid,uuid) from public,anon,authenticated;
create function public.pay_service_from_wallet(p_charge_id uuid,p_request_id uuid) returns uuid language plpgsql security definer set search_path='' as $$
begin
 perform id from adaa_private.service_charges where id=p_charge_id and customer_id=auth.uid() for update;
 if not found then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if exists(select 1 from adaa_private.external_payment_reports where charge_id=p_charge_id) then raise exception 'EXTERNAL_PAYMENT_AWAITING_CONFIRMATION' using errcode='22023'; end if;
 return adaa_private.wallet_pay_service_v1(p_charge_id,p_request_id);
end $$;
create function public.withdraw_external_payment_report(p_charge_id uuid,p_reason text) returns void language plpgsql security definer set search_path='' as $$
declare c adaa_private.service_charges; begin
 select * into c from adaa_private.service_charges where id=p_charge_id for update;
 if not found or c.state<>'requested' or auth.uid() is null or (c.customer_id<>auth.uid() and not adaa_private.has_role(c.business_id,array['owner','manager'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if p_reason is null or length(btrim(p_reason)) not between 10 and 2000 then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 delete from adaa_private.external_payment_reports where charge_id=c.id;
 if found then insert into adaa_private.service_events(charge_id,action,actor_id,message) values(c.id,'external_report_withdrawn',auth.uid(),btrim(p_reason)); end if;
end $$;
create function public.refund_payment_request(p_charge_id uuid,p_request_id uuid) returns uuid language plpgsql security definer set search_path='' as $$
declare c adaa_private.service_charges; v uuid; begin
 select * into c from adaa_private.service_charges where id=p_charge_id for update;
 if not found or not adaa_private.has_role(c.business_id,array['owner']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if c.state='refunded' then
  select id into v from adaa_private.wallet_journals where scope=auth.uid()::text and request_id=p_request_id and reference_id=c.id and kind='service_refund';
  if v is null then raise exception 'ALREADY_REFUNDED' using errcode='22023'; end if; return v;
 end if;
 if c.state not in ('funded','completed') then raise exception 'INVALID_SERVICE_STATE' using errcode='22023'; end if;
 v:=adaa_private.wallet_move(auth.uid()::text,p_request_id,'service_refund',c.id,adaa_private.wallet_account(null,c.business_id),
  case when c.state='funded' then 'escrow' else 'available' end,adaa_private.wallet_account(c.customer_id),'available',c.amount_minor,auth.uid());
 update adaa_private.service_charges set state='refunded' where id=c.id; return v;
end $$;
create function public.open_payment_dispute(p_charge_id uuid,p_reason text) returns uuid language plpgsql security definer set search_path='' as $$
declare c adaa_private.service_charges; v uuid; v_window integer; begin
 select * into c from adaa_private.service_charges where id=p_charge_id for update;
 if not found or c.customer_id is distinct from auth.uid() then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 select id into v from adaa_private.service_disputes where charge_id=c.id;
 if found then return v; end if;
 if c.state not in ('funded','completed') then raise exception 'INVALID_SERVICE_STATE' using errcode='22023'; end if;
 select dispute_window_hours into v_window from adaa_private.wallet_policy where singleton and configured;
 if c.state='completed' and (v_window is null or c.completed_at<now()-make_interval(hours=>v_window)) then raise exception 'DISPUTE_WINDOW_CLOSED' using errcode='22023'; end if;
 -- Serialize against withdrawal requests before publishing the withdrawal hold.
 perform id from adaa_private.wallet_accounts where business_id=c.business_id for update;
 insert into adaa_private.service_disputes(charge_id,customer_id,previous_state,reason) values(c.id,auth.uid(),c.state,btrim(p_reason)) returning id into v;
 update adaa_private.service_charges set state='disputed' where id=c.id;
 insert into adaa_private.service_events(charge_id,action,actor_id,message) values(c.id,'dispute_opened',auth.uid(),btrim(p_reason));
 return v;
end $$;
create function public.respond_payment_dispute(p_charge_id uuid,p_message text) returns void language plpgsql security definer set search_path='' as $$
declare c adaa_private.service_charges; begin
 select * into c from adaa_private.service_charges where id=p_charge_id for update;
 if not found or c.state<>'disputed' or auth.uid() is null or (c.customer_id<>auth.uid() and not adaa_private.has_role(c.business_id,array['owner','manager'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if p_message is null or length(btrim(p_message)) not between 10 and 2000 then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 insert into adaa_private.service_events(charge_id,action,actor_id,message) values(c.id,'dispute_response',auth.uid(),btrim(p_message));
end $$;
create function public.resolve_payment_dispute(p_charge_id uuid,p_refund boolean,p_reason text,p_request_id uuid) returns void language plpgsql security definer set search_path='' as $$
declare c adaa_private.service_charges; d adaa_private.service_disputes; v_account uuid; begin
 if not adaa_private.is_admin() then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 select * into c from adaa_private.service_charges where id=p_charge_id for update;
 select * into d from adaa_private.service_disputes where charge_id=c.id for update;
 if not found or p_refund is null or p_reason is null or length(btrim(p_reason)) not between 10 and 2000 then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 if d.state<>'open' then
  if (d.state='refunded') is distinct from p_refund then raise exception 'DISPUTE_ALREADY_RESOLVED' using errcode='22023'; end if;
  return;
 end if;
 v_account:=adaa_private.wallet_account(null,c.business_id);
 if p_refund then
  perform adaa_private.wallet_move(auth.uid()::text,p_request_id,'dispute_refund',c.id,v_account,
   case when d.previous_state='funded' then 'escrow' else 'available' end,adaa_private.wallet_account(c.customer_id),'available',c.amount_minor,auth.uid());
 elsif d.previous_state='funded' then
  perform adaa_private.wallet_move(auth.uid()::text,p_request_id,'dispute_release',c.id,v_account,'escrow',v_account,'available',c.amount_minor,auth.uid());
 end if;
 update adaa_private.service_disputes set state=case when p_refund then 'refunded' else 'released' end,resolved_by=auth.uid(),resolved_at=now(),resolution=btrim(p_reason) where id=d.id;
 update adaa_private.service_charges set state=case when p_refund then 'refunded' else 'completed' end,
  completed_by=case when p_refund then completed_by else auth.uid() end,completed_at=case when p_refund then completed_at else coalesce(completed_at,now()) end where id=c.id;
 insert into adaa_private.service_events(charge_id,action,actor_id,message) values(c.id,'dispute_resolved',auth.uid(),btrim(p_reason));
end $$;
create function public.get_open_payment_disputes() returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 if not adaa_private.is_admin() then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(x) from (select d.*,c.business_id,c.amount_minor from adaa_private.service_disputes d join adaa_private.service_charges c on c.id=d.charge_id where d.state='open' order by d.created_at limit 100)x),'[]'::jsonb);
end $$;

-- Operational monitoring stays server-side. It never auto-releases escrow or refunds ambiguous payouts.
create function public.record_wallet_worker_health(p_processed integer,p_retry_later integer) returns void language plpgsql security definer set search_path='' as $$
begin
 if p_processed<0 or p_retry_later<0 then raise exception 'INVALID_INPUT'; end if;
 update adaa_private.wallet_worker_health set last_run_at=now(),processed=p_processed,retry_later=p_retry_later where singleton;
end $$;
create function public.get_wallet_operational_health() returns jsonb language plpgsql security definer set search_path='' as $$
declare v jsonb; v_alert boolean; begin
 select jsonb_build_object('queued',(select count(*) from adaa_private.wallet_gateway_jobs where state='queued'),
  'review',(select count(*) from adaa_private.wallet_gateway_jobs where state='review'),
  'stale_pending',(select count(*) from adaa_private.wallet_gateway_jobs where state in ('queued','pending') and created_at<now()-interval '15 minutes'),
  'open_disputes',(select count(*) from adaa_private.service_disputes where state='open'),
  'unbalanced_journals',(select count(*) from (select journal_id from adaa_private.wallet_entries group by journal_id having sum(delta)<>0)x),
  'negative_accounts',(select count(*) from (select e.account_id,e.bucket from adaa_private.wallet_entries e join adaa_private.wallet_accounts a on a.id=e.account_id
   where not a.system_account group by e.account_id,e.bucket having sum(delta)<0)x),
  'worker_stale',(select gateway_enabled from adaa_private.wallet_settings where singleton) and
   coalesce((select last_run_at<now()-interval '5 minutes' from adaa_private.wallet_worker_health where singleton),true),
  'worker_last_run_at',(select last_run_at from adaa_private.wallet_worker_health where singleton),
  'worker_retry_later',(select retry_later from adaa_private.wallet_worker_health where singleton),
  'custody_reconciliation_needed',(select gateway_enabled from adaa_private.wallet_settings where singleton) and
    not coalesce((select recorded_at>now()-interval '24 hours' and difference_minor=0 and shortfall_minor=0
     from adaa_private.wallet_custody_snapshots order by recorded_at desc,id desc limit 1),false)) into v;
 v_alert:=(v->>'review')::bigint>0 or (v->>'stale_pending')::bigint>0 or (v->>'unbalanced_journals')::bigint>0 or
  (v->>'negative_accounts')::bigint>0 or (v->>'worker_stale')::boolean or (v->>'worker_retry_later')::integer>0 or (v->>'custody_reconciliation_needed')::boolean;
 insert into adaa_private.wallet_operator_alerts(kind,active,details) values('wallet_health',v_alert,v)
  on conflict(kind) do update set active=excluded.active,details=excluded.details,last_seen_at=now();
 return v||jsonb_build_object('requires_attention',v_alert);
end $$;
create function public.record_wallet_custody_snapshot(p_snapshot_id uuid,p_observed_minor bigint,p_platform_funding_minor bigint,
 p_provider_fees_minor bigint,p_observed_at timestamptz,p_source text,p_evidence jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v_liability numeric; v_expected numeric; v_id uuid; v_previous adaa_private.wallet_custody_snapshots; begin
 if num_nonnulls(p_snapshot_id,p_observed_minor,p_platform_funding_minor,p_provider_fees_minor,p_observed_at,p_source,p_evidence)<>7
  or p_observed_minor<0 or p_platform_funding_minor<0 or p_provider_fees_minor<0 or length(btrim(p_source)) not between 3 and 200
  or jsonb_typeof(p_evidence)<>'object' or p_observed_at>clock_timestamp() or p_observed_at<clock_timestamp()-interval '5 minutes' then raise exception 'INVALID_RECONCILIATION'; end if;
 perform pg_advisory_xact_lock(hashtextextended('custody'||p_snapshot_id::text,0));
 select * into v_previous from adaa_private.wallet_custody_snapshots where id=p_snapshot_id;
 if found then
  if v_previous.observed_minor<>p_observed_minor or v_previous.platform_funding_minor<>p_platform_funding_minor or v_previous.provider_fees_minor<>p_provider_fees_minor
   or v_previous.observed_at<>p_observed_at or v_previous.source<>btrim(p_source) or v_previous.evidence<>p_evidence then raise exception 'REQUEST_ALREADY_USED'; end if;
  return jsonb_build_object('difference_minor',v_previous.difference_minor,'shortfall_minor',v_previous.shortfall_minor);
 end if;
 -- Pause creation, drain processing, then compare a fresh verified provider snapshot.
 if (select accept_new_requests from adaa_private.wallet_settings where singleton) or exists(select 1 from adaa_private.wallet_gateway_jobs where state in ('queued','pending','review')) then raise exception 'RECONCILIATION_REQUIRES_QUIET_WINDOW'; end if;
 select id into v_id from adaa_private.wallet_accounts where system_account for update;
 select -coalesce(sum(delta),0) into v_liability from adaa_private.wallet_entries where account_id=v_id;
 v_expected:=p_platform_funding_minor+v_liability-p_provider_fees_minor;
 insert into adaa_private.wallet_custody_snapshots(id,observed_at,observed_minor,platform_funding_minor,provider_fees_minor,
  custody_liability_minor,expected_minor,difference_minor,shortfall_minor,source,evidence)
  values(p_snapshot_id,p_observed_at,p_observed_minor,p_platform_funding_minor,p_provider_fees_minor,v_liability,v_expected,
   p_observed_minor-v_expected,greatest(v_liability-p_observed_minor,0),btrim(p_source),p_evidence);
 return jsonb_build_object('difference_minor',p_observed_minor-v_expected,'shortfall_minor',greatest(v_liability-p_observed_minor,0));
end $$;
create function public.retry_wallet_job_poll(p_job_id uuid,p_reason text) returns void language plpgsql security definer set search_path='' as $$
begin
 if p_reason is null or length(btrim(p_reason)) not between 10 and 2000 then raise exception 'INVALID_INPUT'; end if;
 update adaa_private.wallet_gateway_jobs set state='pending',attempts=greatest(attempts,1),lease_until=null,next_attempt_at=now()
  where id=p_job_id and state in ('pending','review');
 if not found then raise exception 'INVALID_GATEWAY_STATE'; end if;
 insert into adaa_private.wallet_recovery_audit(job_id,reason) values(p_job_id,btrim(p_reason));
end $$;
create or replace function public.claim_wallet_gateway_jobs() returns jsonb language sql security definer set search_path='' as $$
 with claimed as(update adaa_private.wallet_gateway_jobs j set attempts=j.attempts+1,lease_until=now()+interval '5 minutes',
  state='pending',next_attempt_at=now()+interval '1 minute'
  where j.id in(select q.id from adaa_private.wallet_gateway_jobs q join adaa_private.wallet_accounts a on a.id=q.account_id
   where q.state in ('queued','pending') and (select gateway_enabled from adaa_private.wallet_settings where singleton)
   and (select configured from adaa_private.wallet_policy where singleton)
   and (q.attempts>0 or not exists(select 1 from adaa_private.service_disputes d join adaa_private.service_charges c on c.id=d.charge_id
     where c.business_id=a.business_id and d.state='open'))
   and q.next_attempt_at<=now() and (q.lease_until is null or q.lease_until<now()) order by q.created_at for update of q skip locked limit 10)
  returning j.*) select coalesce(jsonb_agg(claimed),'[]'::jsonb) from claimed;
$$;
create function public.claim_wallet_alert_notification() returns jsonb language sql security definer set search_path='' as $$
 with alert as(update adaa_private.wallet_operator_alerts set notification_lease_until=now()+interval '5 minutes'
  where kind='wallet_health' and active and (last_notified_at is null or last_notified_at<now()-interval '30 minutes')
   and (notification_lease_until is null or notification_lease_until<now()) returning kind,details)
 select to_jsonb(alert) from alert;
$$;
create function public.finish_wallet_alert_notification(p_delivered boolean) returns void language sql security definer set search_path='' as $$
 update adaa_private.wallet_operator_alerts set notification_lease_until=null,
  last_notified_at=case when p_delivered then now() else last_notified_at end where kind='wallet_health';
$$;

do $$ declare f record; begin
 for f in select p.oid::regprocedure as signature from pg_proc p join pg_namespace n on n.oid=p.pronamespace where
  (n.nspname='adaa_private' and p.proname='wallet_record_service_event') or
  (n.nspname='public' and p.proname in ('configure_wallet_policy','get_wallet_policy','get_wallet_dashboard','quote_wallet_operation','request_wallet_gateway_operation',
   'get_wallet_operations','get_wallet_operation','get_wallet_activity','get_payment_identity','get_payment_requests','get_payment_request','cancel_payment_request',
   'report_external_payment','withdraw_external_payment_report','pay_service_from_wallet','refund_payment_request','open_payment_dispute','respond_payment_dispute',
   'resolve_payment_dispute','get_open_payment_disputes','record_wallet_worker_health','get_wallet_operational_health','retry_wallet_job_poll',
   'claim_wallet_alert_notification','finish_wallet_alert_notification','record_wallet_custody_snapshot')) loop
  execute format('revoke all on function %s from public,anon,authenticated',f.signature);
 end loop;
end $$;
grant execute on function public.configure_wallet_policy(bigint,bigint,bigint,integer,bigint),public.get_wallet_policy(),public.get_wallet_dashboard(uuid),
 public.quote_wallet_operation(text,bigint,uuid),public.request_wallet_gateway_operation(uuid,text,bigint,text,uuid,bigint),
 public.get_wallet_operations(uuid,timestamptz,uuid),public.get_wallet_operation(uuid),public.get_wallet_activity(uuid,timestamptz,uuid),public.get_payment_identity(),
 public.get_payment_requests(uuid,timestamptz,uuid),public.get_payment_request(uuid),public.cancel_payment_request(uuid),
 public.report_external_payment(uuid,text),public.withdraw_external_payment_report(uuid,text),public.pay_service_from_wallet(uuid,uuid),public.refund_payment_request(uuid,uuid),
 public.open_payment_dispute(uuid,text),public.respond_payment_dispute(uuid,text),public.resolve_payment_dispute(uuid,boolean,text,uuid),
 public.get_open_payment_disputes() to authenticated;
grant execute on function public.record_wallet_worker_health(integer,integer),public.get_wallet_operational_health(),public.retry_wallet_job_poll(uuid,text),
 public.claim_wallet_alert_notification(),public.finish_wallet_alert_notification(boolean),
 public.record_wallet_custody_snapshot(uuid,bigint,bigint,bigint,timestamptz,text,jsonb) to service_role;
notify pgrst,'reload schema';
commit;
