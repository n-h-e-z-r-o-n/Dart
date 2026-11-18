-- Development only after 008. All fixtures/ledger movements roll back.
begin;
insert into auth.users(id) values
 ('adcccccc-1000-0000-0000-000000000001'),('adcccccc-1000-0000-0000-000000000002'),
 ('adcccccc-1000-0000-0000-000000000003'),('adcccccc-1000-0000-0000-000000000004');
insert into public.platform_admins(user_id) values('adcccccc-1000-0000-0000-000000000004');
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000001',true);
set local role authenticated;
select set_config('adaa.flow_business',public.create_business('Flow studio',(select id from public.business_categories where name='Other'),'online')::text,true);
do $$ begin
 begin perform public.configure_wallet_policy(100,50000,100000,72,0); raise exception 'FAIL: owner set platform policy'; exception when insufficient_privilege then null; end;
 begin perform public.quote_wallet_operation('deposit',10000); raise exception 'FAIL: unconfigured policy allowed'; exception when invalid_parameter_value then null; end;
 begin perform adaa_private.wallet_request_gateway_v1(gen_random_uuid(),'deposit',10000,'254712345678',null); raise exception 'FAIL: limits bypass exposed'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000004',true);
set local role authenticated;
select public.configure_wallet_policy(100,50000,100000,72,0);
reset role;
update adaa_private.wallet_settings set gateway_enabled=true;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000002',true);
set local role authenticated;
do $$ begin
 begin perform public.request_wallet_gateway_operation(gen_random_uuid(),'deposit',10000,'254712345678',null,1); raise exception 'FAIL: stale quote accepted'; exception when invalid_parameter_value then null; end;
 begin perform public.quote_wallet_operation('deposit',60000); raise exception 'FAIL: oversized deposit accepted'; exception when invalid_parameter_value then null; end;
end $$;
select set_config('adaa.flow_deposit',public.request_wallet_gateway_operation('adcccccc-2000-0000-0000-000000000001','deposit',50000,'254712345678',null,2)::text,true);
do $$ begin
 if public.request_wallet_gateway_operation(gen_random_uuid(),'deposit',50000,'254712345678',null,2)::text<>current_setting('adaa.flow_deposit') then raise exception 'FAIL: duplicate active prompt'; end if;
 if jsonb_array_length(public.get_wallet_operations())<>1 then raise exception 'FAIL: operation history'; end if;
 if public.get_payment_identity()->>'payment_code'<>'adcccccc-1000-0000-0000-000000000002' then raise exception 'FAIL: identity'; end if;
end $$;
reset role;
select set_config('adaa.flow_reference',(select account_reference from adaa_private.wallet_gateway_jobs where id=current_setting('adaa.flow_deposit')::uuid),true);
set local role service_role;
select public.claim_wallet_gateway_jobs();
select public.record_wallet_gateway_progress(current_setting('adaa.flow_deposit')::uuid,'flow-deposit');
select public.settle_wallet_gateway_job(current_setting('adaa.flow_deposit')::uuid,'flow-deposit',true,'FLOWRECEIPT01',
 jsonb_build_object('requestId','flow-deposit','amount',500,'status','succeeded','transactionId','FLOWRECEIPT01','accountReference',current_setting('adaa.flow_reference')));
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000001',true);
set local role authenticated;
select set_config('adaa.flow_charge',public.create_service_charge(gen_random_uuid(),current_setting('adaa.flow_business')::uuid,'adcccccc-1000-0000-0000-000000000002',20000,'Disputed service')::text,true);
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000002',true);
set local role authenticated;
select public.pay_service_from_wallet(current_setting('adaa.flow_charge')::uuid,gen_random_uuid());
select public.open_payment_dispute(current_setting('adaa.flow_charge')::uuid,'Service was not provided as agreed.');
select public.respond_payment_dispute(current_setting('adaa.flow_charge')::uuid,'Adding details for the support reviewer.');
do $$ begin
 begin perform public.resolve_payment_dispute(current_setting('adaa.flow_charge')::uuid,true,'I approve my own refund',gen_random_uuid()); raise exception 'FAIL: customer resolved dispute'; exception when insufficient_privilege then null; end;
 if public.get_payment_request(current_setting('adaa.flow_charge')::uuid)->>'state'<>'disputed' then raise exception 'FAIL: missing dispute state'; end if;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000001',true);
set local role authenticated;
do $$ begin
 begin perform public.complete_service_and_release(current_setting('adaa.flow_charge')::uuid,gen_random_uuid()); raise exception 'FAIL: disputed escrow released'; exception when invalid_parameter_value then null; end;
 begin perform public.request_wallet_gateway_operation(gen_random_uuid(),'withdrawal',100,'254712345678',current_setting('adaa.flow_business')::uuid,2); raise exception 'FAIL: business withdrawal during dispute'; exception when invalid_parameter_value then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000004',true);
set local role authenticated;
select public.resolve_payment_dispute(current_setting('adaa.flow_charge')::uuid,true,'Verified missing service; return customer funds.',gen_random_uuid());
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000002',true);
set local role authenticated;
do $$ begin
 if public.get_wallet_dashboard()->>'available_minor'<>'50000' then raise exception 'FAIL: dispute refund missing'; end if;
 if jsonb_array_length(public.get_wallet_activity())<>3 then raise exception 'FAIL: journal activity not grouped'; end if;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000003',true);
set local role authenticated;
do $$ begin
 begin perform public.get_payment_request(current_setting('adaa.flow_charge')::uuid); raise exception 'FAIL: foreign charge details leaked'; exception when insufficient_privilege then null; end;
 begin perform public.get_wallet_operational_health(); raise exception 'FAIL: operational data exposed'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000001',true);
set local role authenticated;
select set_config('adaa.flow_external',public.create_service_charge(gen_random_uuid(),current_setting('adaa.flow_business')::uuid,'adcccccc-1000-0000-0000-000000000002',10000,'Outside payment')::text,true);
reset role;
select set_config('request.jwt.claim.sub','adcccccc-1000-0000-0000-000000000002',true);
set local role authenticated;
select public.report_external_payment(current_setting('adaa.flow_external')::uuid,'EXTERNAL123');
do $$ begin
 begin perform public.pay_service_from_wallet(current_setting('adaa.flow_external')::uuid,gen_random_uuid()); raise exception 'FAIL: external report also paid in-app'; exception when invalid_parameter_value then null; end;
end $$;
select public.withdraw_external_payment_report(current_setting('adaa.flow_external')::uuid,'Reference entered in error; no payment was made.');
select public.cancel_payment_request(current_setting('adaa.flow_external')::uuid);
reset role;
set local role service_role;
select public.record_wallet_worker_health(1,0);
select public.get_wallet_operational_health();
reset role;
update adaa_private.wallet_settings set accept_new_requests=false;
set local role service_role;
select public.record_wallet_custody_snapshot('adcccccc-9000-0000-0000-000000000001',50000,0,0,now(),'fixture gateway report','{"fixture":true}');
select public.record_wallet_custody_snapshot('adcccccc-9000-0000-0000-000000000001',50000,0,0,now(),'fixture gateway report','{"fixture":true}');
do $$ begin
 if (public.get_wallet_operational_health()->>'requires_attention')::boolean then raise exception 'FAIL: matching custody snapshot incorrectly flagged'; end if;
end $$;
reset role;
set local role authenticated;
set constraints all immediate;
reset role;
rollback;
select 'PASS: policy/limits, quote versions, history isolation, disputes, outside payments and operations health' as result;
