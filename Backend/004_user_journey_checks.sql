-- Development SQL Editor acceptance checks after 001 and 003. All fixtures roll back.
begin;
insert into auth.users(id) values ('adaaaaaa-1000-0000-0000-000000000001'),('adaaaaaa-1000-0000-0000-000000000002');
select set_config('request.jwt.claim.sub','adaaaaaa-1000-0000-0000-000000000001',true);
set local role authenticated;
select set_config('adaa.journey_id',public.create_business_once('adaaaaaa-2000-0000-0000-000000000001',
  'Journey studio',(select id from public.business_categories where name='Other'),'physical','12 Test Street','Our introduction')::text,true);
do $$ declare v_id uuid; v_details jsonb; begin
  v_id := public.create_business_once('adaaaaaa-2000-0000-0000-000000000001','Journey studio',
    (select id from public.business_categories where name='Other'),'physical','12 Test Street','Our introduction');
  if v_id<>current_setting('adaa.journey_id')::uuid or (select count(*) from public.get_my_businesses())<>1 then
    raise exception 'FAIL: retry created duplicate business'; end if;
  begin
    perform public.create_business_once('adaaaaaa-2000-0000-0000-000000000001','Changed payload',
      (select id from public.business_categories where name='Other'),'online');
    raise exception 'FAIL: reused request accepted changed payload';
  exception when invalid_parameter_value then null; end;
  v_details := public.get_business_dashboard(v_id);
  if v_details->>'name'<>'Journey studio' or v_details->>'members_count'<>'1'
     or v_details->>'can_publish'<>'true' or v_details->>'location_or_area'<>'12 Test Street' then
    raise exception 'FAIL: dashboard fields'; end if;
  if public.get_business_public_profile(v_id) is not null then raise exception 'FAIL: draft public profile leaked'; end if;
  perform public.set_business_member(v_id,'adaaaaaa-1000-0000-0000-000000000002','staff');
end $$;
reset role;
select set_config('request.jwt.claim.sub','adaaaaaa-1000-0000-0000-000000000002',true);
set local role authenticated;
do $$ declare v_details jsonb; begin
  v_details := public.get_business_dashboard(current_setting('adaa.journey_id')::uuid);
  if v_details->>'role'<>'staff' or v_details->>'can_publish'<>'false' then raise exception 'FAIL: staff permissions'; end if;
  begin
    perform public.update_business(current_setting('adaa.journey_id')::uuid,'Hijacked',1,'online');
    raise exception 'FAIL: staff edited listing';
  exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','adaaaaaa-1000-0000-0000-000000000001',true);
set local role authenticated;
select public.remove_business_member(current_setting('adaa.journey_id')::uuid,'adaaaaaa-1000-0000-0000-000000000002');
select public.update_business(current_setting('adaa.journey_id')::uuid,'Updated studio',
  (select id from public.business_categories where name='Other'),'service_area','Test district','Updated introduction');
do $$ declare v_details jsonb; begin
  v_details := public.get_business_dashboard(current_setting('adaa.journey_id')::uuid);
  if v_details->>'locations_count'<>'0' or v_details->>'location_or_area'<>'Test district'
     or v_details->>'publication_status'<>'draft' then raise exception 'FAIL: business edit or private status'; end if;
end $$;
select public.publish_business(current_setting('adaa.journey_id')::uuid);
reset role;
select set_config('request.jwt.claim.sub','adaaaaaa-1000-0000-0000-000000000002',true);
set local role authenticated;
do $$ begin
  begin
    perform public.get_business_dashboard(current_setting('adaa.journey_id')::uuid);
    raise exception 'FAIL: removed user sees private dashboard';
  exception when insufficient_privilege then null; end;
  -- Same request identifier is scoped to the authenticated user, not global.
  perform public.create_business_once('adaaaaaa-2000-0000-0000-000000000001','Another tenant',
    (select id from public.business_categories where name='Other'),'online');
end $$;
reset role;
select set_config('request.jwt.claim.sub','',true);
set local role anon;
do $$ declare v_details jsonb; begin
  v_details := public.get_business_public_profile(current_setting('adaa.journey_id')::uuid);
  if v_details->>'name'<>'Updated studio' or v_details->>'location_or_area'<>'Test district'
     or v_details ? 'role' or v_details ? 'members_count' then raise exception 'FAIL: public profile fields'; end if;
  begin
    perform public.get_business_dashboard(current_setting('adaa.journey_id')::uuid);
    raise exception 'FAIL: guest dashboard access';
  exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
select 'PASS: user journey contracts, idempotency, edits and dashboard isolation' as result;
