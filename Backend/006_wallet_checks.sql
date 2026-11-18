-- Development only, after 005. Test fixtures and all movements roll back.
begin;
update adaa_private.wallet_settings set gateway_enabled=true; -- fixture only
insert into auth.users(id) values
 ('adbbbbbb-1000-0000-0000-000000000001'),('adbbbbbb-1000-0000-0000-000000000002'),
 ('adbbbbbb-1000-0000-0000-000000000003'),('adbbbbbb-1000-0000-0000-000000000004');
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000001',true);
set local role authenticated;
select set_config('adaa.wallet_business',public.create_business('Wallet studio',
 (select id from public.business_categories where name='Other'),'online')::text,true);
select public.set_business_member(current_setting('adaa.wallet_business')::uuid,'adbbbbbb-1000-0000-0000-000000000003','staff');
select public.set_escrow_release_permission(current_setting('adaa.wallet_business')::uuid,'adbbbbbb-1000-0000-0000-000000000003',true);
select set_config('adaa.charge',public.create_service_charge('adbbbbbb-2000-0000-0000-000000000001',current_setting('adaa.wallet_business')::uuid,
 'adbbbbbb-1000-0000-0000-000000000002',10000,'Haircut')::text,true);
reset role;
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000002',true);
set local role authenticated;
do $$ begin
  begin perform public.pay_service_from_wallet(current_setting('adaa.charge')::uuid,'adbbbbbb-3000-0000-0000-000000000001');
    raise exception 'FAIL: spent unfunded wallet'; exception when invalid_parameter_value then null; end;
  begin perform public.settle_wallet_gateway_job(gen_random_uuid(),'forged',true,'forged','{}');
    raise exception 'FAIL: client settled deposit'; exception when insufficient_privilege then null; end;
  begin perform public.get_wallet_dashboard(current_setting('adaa.wallet_business')::uuid);
    raise exception 'FAIL: customer saw business wallet'; exception when insufficient_privilege then null; end;
  begin perform adaa_private.wallet_account('adbbbbbb-1000-0000-0000-000000000002');
    raise exception 'FAIL: private ledger helper exposed'; exception when insufficient_privilege then null; end;
end $$;
select set_config('adaa.deposit',public.request_wallet_gateway_operation('adbbbbbb-4000-0000-0000-000000000001','deposit',50000,'254712345678')::text,true);
do $$ begin
 if (public.get_wallet_dashboard()->>'available_minor')::bigint<>0 then raise exception 'FAIL: pending deposit credited'; end if;
 if public.request_wallet_gateway_operation('adbbbbbb-4000-0000-0000-000000000001','deposit',50000,'254712345678')::text<>current_setting('adaa.deposit') then
  raise exception 'FAIL: duplicate deposit'; end if;
 begin perform public.request_wallet_gateway_operation('adbbbbbb-4000-0000-0000-000000000001','deposit',60000,'254712345678');
  raise exception 'FAIL: changed request payload'; exception when invalid_parameter_value then null; end;
end $$;
reset role;
select set_config('adaa.account_reference',(select account_reference from adaa_private.wallet_gateway_jobs where id=current_setting('adaa.deposit')::uuid),true);
set local role service_role;
select public.claim_wallet_gateway_jobs();
select public.record_wallet_gateway_progress(current_setting('adaa.deposit')::uuid,'test-deposit');
select public.settle_wallet_gateway_job(current_setting('adaa.deposit')::uuid,'test-deposit',true,'TESTRECEIPT01',jsonb_build_object('requestId','test-deposit','amount',500,'status','succeeded','transactionId','TESTRECEIPT01','accountReference',current_setting('adaa.account_reference')));
select public.settle_wallet_gateway_job(current_setting('adaa.deposit')::uuid,'test-deposit',true,'TESTRECEIPT01',jsonb_build_object('requestId','test-deposit','amount',500,'status','succeeded','transactionId','TESTRECEIPT01','accountReference',current_setting('adaa.account_reference')));
reset role;
set local role authenticated;
select public.pay_service_from_wallet(current_setting('adaa.charge')::uuid,'adbbbbbb-3000-0000-0000-000000000001');
select public.pay_service_from_wallet(current_setting('adaa.charge')::uuid,'adbbbbbb-3000-0000-0000-000000000001');
do $$ begin
 if (public.get_wallet_dashboard()->>'available_minor')::bigint<>40000 then raise exception 'FAIL: duplicate hold or missing debit'; end if;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000004',true);
set local role authenticated;
do $$ begin
 begin perform public.complete_service_and_release(current_setting('adaa.charge')::uuid,gen_random_uuid());
  raise exception 'FAIL: outsider released escrow'; exception when insufficient_privilege then null; end;
 begin perform public.get_service_charges(current_setting('adaa.wallet_business')::uuid);
  raise exception 'FAIL: outsider saw service charges'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000003',true);
set local role authenticated;
select public.complete_service_and_release(current_setting('adaa.charge')::uuid,'adbbbbbb-5000-0000-0000-000000000001');
select public.complete_service_and_release(current_setting('adaa.charge')::uuid,'adbbbbbb-5000-0000-0000-000000000001');
do $$ begin
 begin perform public.request_wallet_gateway_operation(gen_random_uuid(),'withdrawal',10000,'254712345678',current_setting('adaa.wallet_business')::uuid);
  raise exception 'FAIL: delegated staff could withdraw'; exception when insufficient_privilege then null; end;
 begin perform public.get_wallet_dashboard(current_setting('adaa.wallet_business')::uuid);
  raise exception 'FAIL: delegated staff saw wallet totals'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000001',true);
set local role authenticated;
do $$ declare v jsonb; begin
 v:=public.get_wallet_dashboard(current_setting('adaa.wallet_business')::uuid);
 if v->>'available_minor'<>'10000' or v->>'escrow_minor'<>'0' or v->>'completed_services'<>'1' then raise exception 'FAIL: release totals'; end if;
end $$;
select set_config('adaa.withdrawal',public.request_wallet_gateway_operation('adbbbbbb-6000-0000-0000-000000000001','withdrawal',10000,'254712345678',current_setting('adaa.wallet_business')::uuid)::text,true);
do $$ declare v jsonb; begin
 v:=public.get_wallet_dashboard(current_setting('adaa.wallet_business')::uuid);
 if v->>'available_minor'<>'0' or v->>'withdrawal_pending_minor'<>'10000' then raise exception 'FAIL: withdrawal not reserved'; end if;
 begin perform public.request_wallet_gateway_operation(gen_random_uuid(),'withdrawal',100,'254712345678',current_setting('adaa.wallet_business')::uuid);
  raise exception 'FAIL: overspent reserved funds'; exception when invalid_parameter_value then null; end;
end $$;
select public.set_escrow_release_permission(current_setting('adaa.wallet_business')::uuid,'adbbbbbb-1000-0000-0000-000000000003',false);
select set_config('adaa.charge2',public.create_service_charge(gen_random_uuid(),current_setting('adaa.wallet_business')::uuid,
 'adbbbbbb-1000-0000-0000-000000000002',10000,'Second service')::text,true);
reset role;
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000002',true);
set local role authenticated;
select public.pay_service_from_wallet(current_setting('adaa.charge2')::uuid,gen_random_uuid());
reset role;
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000003',true);
set local role authenticated;
do $$ begin
 begin perform public.complete_service_and_release(current_setting('adaa.charge2')::uuid,gen_random_uuid());
  raise exception 'FAIL: revoked permission remained active'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adbbbbbb-1000-0000-0000-000000000001',true);
set local role authenticated;
select public.refund_service_escrow(current_setting('adaa.charge2')::uuid,'adbbbbbb-7000-0000-0000-000000000001');
select public.refund_service_escrow(current_setting('adaa.charge2')::uuid,'adbbbbbb-7000-0000-0000-000000000001');
select set_config('adaa.external',public.create_service_charge(gen_random_uuid(),current_setting('adaa.wallet_business')::uuid,
 'adbbbbbb-1000-0000-0000-000000000002',30000,'Direct till service')::text,true);
select public.record_external_service_payment(current_setting('adaa.external')::uuid);
reset role;
set local role service_role;
select public.claim_wallet_gateway_jobs();
select public.record_wallet_gateway_progress(current_setting('adaa.withdrawal')::uuid,'test-withdrawal');
select public.settle_wallet_gateway_job(current_setting('adaa.withdrawal')::uuid,'test-withdrawal',true,'TESTRECEIPT02','{"requestId":"test-withdrawal","amount":100,"status":"succeeded","transactionId":"TESTRECEIPT02"}');
reset role;
do $$ begin
 if exists(select 1 from adaa_private.wallet_entries group by journal_id having sum(delta)<>0) then raise exception 'FAIL: unbalanced ledger'; end if;
 if exists(select 1 from adaa_private.wallet_entries e join adaa_private.wallet_accounts a on a.id=e.account_id where not a.system_account
  group by e.account_id,e.bucket having sum(e.delta)<0) then raise exception 'FAIL: negative wallet'; end if;
 if (select completed_by from adaa_private.service_charges where id=current_setting('adaa.charge')::uuid)<>'adbbbbbb-1000-0000-0000-000000000003'::uuid then
  raise exception 'FAIL: release actor not recorded'; end if;
 begin update adaa_private.wallet_entries set delta=delta+1;
  raise exception 'FAIL: ledger editable'; exception when insufficient_privilege then null; end;
end $$;
-- A journal without equal/opposite entries must not commit.
do $$ begin
 begin
  insert into adaa_private.wallet_journals(scope,request_id,kind,payload,reference_id)
    values('broken-fixture',gen_random_uuid(),'test','{}',gen_random_uuid());
  set constraints all immediate;
  raise exception 'FAIL: unbalanced journal accepted';
 exception when others then
  if sqlerrm='FAIL: unbalanced journal accepted' then raise; end if;
  if sqlerrm<>'UNBALANCED_JOURNAL' then raise; end if;
 end;
end $$;
set constraints all immediate;
rollback;
select 'PASS: wallet isolation, ledger balance, idempotency, escrow delegation, external payment separation and settlement' as result;
