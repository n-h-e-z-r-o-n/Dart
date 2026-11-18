-- Run AFTER 001_foundation.sql in SQL Editor on a development project.
-- Throws on any failed assertion. All fixtures are rolled back.
begin;
insert into auth.users(id) values
 ('adaaaaaa-0000-0000-0000-000000000001'),
 ('adaaaaaa-0000-0000-0000-000000000002'),
 ('adaaaaaa-0000-0000-0000-000000000003');

select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000001',true);
set local role authenticated;
select public.create_business('Adaa test tenant A', (select id from public.business_categories where name = 'Other'), 'online');
select public.create_business('Adaa test tenant A second', (select id from public.business_categories where name = 'Other'), 'physical', '123 Test Road');
do $$ begin
  if (select count(*) from public.get_my_businesses()) <> 2 then raise exception 'FAIL: multiple businesses'; end if;
  if exists(select 1 from public.search_businesses('Adaa test tenant')) then raise exception 'FAIL: draft discoverable'; end if;
  begin
    insert into public.platform_admins(user_id) values(auth.uid());
    raise exception 'FAIL: self promotion to admin';
  exception when insufficient_privilege then null; end;
  begin
    update public.businesses set verification_status = 'verified';
    raise exception 'FAIL: self verification';
  exception when insufficient_privilege then null; end;
  begin
    perform public.create_business('Missing address', (select id from public.business_categories where name='Other'), 'physical');
    raise exception 'FAIL: invalid location accepted';
  exception when invalid_parameter_value then null; end;
  begin
    perform public.set_business_member((select id from public.get_my_businesses() limit 1),
      'adaaaaaa-0000-0000-0000-000000000002', 'owner');
    raise exception 'FAIL: owner role assignable';
  exception when invalid_parameter_value then null; end;
  begin
    perform public.remove_business_member((select id from public.get_my_businesses() limit 1), auth.uid());
    raise exception 'FAIL: owner removable';
  exception when insufficient_privilege then null; end;
end $$;
-- Save fixture identifiers in transaction-local settings, never exposed by API.
select set_config('adaa.test_a', (select id::text from public.get_my_businesses() where name='Adaa test tenant A'), true);
select set_config('adaa.test_a_location', (select id::text from public.get_my_businesses() where name='Adaa test tenant A second'), true);
reset role;

select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000002',true);
set local role authenticated;
select public.create_business('Adaa test tenant B', (select id from public.business_categories where name = 'Other'), 'service_area', 'Test district');
do $$ begin
  if exists(select 1 from public.businesses where id = current_setting('adaa.test_a')::uuid) then
    raise exception 'FAIL: tenant B sees private tenant A'; end if;
  if exists(select 1 from public.business_locations where business_id = current_setting('adaa.test_a_location')::uuid) then
    raise exception 'FAIL: private locations leaked'; end if;
  if exists(select 1 from public.business_members where business_id = current_setting('adaa.test_a')::uuid) then
    raise exception 'FAIL: private membership leaked'; end if;
  if exists(select 1 from public.profiles where id <> auth.uid()) then raise exception 'FAIL: profiles leaked'; end if;
  begin
    perform public.publish_business(current_setting('adaa.test_a')::uuid);
    raise exception 'FAIL: cross tenant publication';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.business_members(business_id,user_id,role)
      values(current_setting('adaa.test_a')::uuid,auth.uid(),'owner');
    raise exception 'FAIL: ownership hijacked';
  exception when insufficient_privilege then null; end;
  begin
    perform public.set_business_member(current_setting('adaa.test_a')::uuid,auth.uid(),'manager');
    raise exception 'FAIL: cross tenant membership assignment';
  exception when insufficient_privilege then null; end;
end $$;
reset role;

select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000001',true);
set local role authenticated;
select public.set_business_member(current_setting('adaa.test_a')::uuid,'adaaaaaa-0000-0000-0000-000000000003','staff');
reset role;
select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000003',true);
set local role authenticated;
do $$ begin
  if (select count(*) from public.get_my_businesses()) <> 1 then raise exception 'FAIL: staff assigned scope'; end if;
  begin
    perform public.publish_business(current_setting('adaa.test_a')::uuid);
    raise exception 'FAIL: staff publication';
  exception when insufficient_privilege then null; end;
  begin
    perform public.set_business_member(current_setting('adaa.test_a')::uuid,'adaaaaaa-0000-0000-0000-000000000002','manager');
    raise exception 'FAIL: staff membership escalation';
  exception when insufficient_privilege then null; end;
end $$;
reset role;

select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000001',true);
set local role authenticated;
select public.set_business_member(current_setting('adaa.test_a')::uuid,'adaaaaaa-0000-0000-0000-000000000003','manager');
reset role;
select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000003',true);
set local role authenticated;
select public.publish_business(current_setting('adaa.test_a')::uuid);
reset role;
select set_config('request.jwt.claim.sub','',true);
set local role anon;
do $$ begin
  if (select count(*) from public.search_businesses('Adaa test tenant')) <> 1 then
    raise exception 'FAIL: guest publication visibility'; end if;
  begin
    perform public.create_business('Guest business',1,'online');
    raise exception 'FAIL: anonymous mutation';
  exception when insufficient_privilege then null; end;
end $$;
reset role;
-- Removing a member revokes draft access immediately.
select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000001',true);
set local role authenticated;
select public.set_business_member(current_setting('adaa.test_a_location')::uuid,'adaaaaaa-0000-0000-0000-000000000003','staff');
select public.remove_business_member(current_setting('adaa.test_a_location')::uuid,'adaaaaaa-0000-0000-0000-000000000003');
reset role;
select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000003',true);
set local role authenticated;
do $$ begin
  if exists(select 1 from public.businesses where id = current_setting('adaa.test_a_location')::uuid) then
    raise exception 'FAIL: removed member retains private access'; end if;
end $$;
reset role;
-- Suspended records cannot be republished even by their owner.
update public.businesses set publication_status='suspended' where id=current_setting('adaa.test_a')::uuid;
select set_config('request.jwt.claim.sub','adaaaaaa-0000-0000-0000-000000000001',true);
set local role authenticated;
do $$ begin
  begin
    perform public.publish_business(current_setting('adaa.test_a')::uuid);
    raise exception 'FAIL: suspended business republished';
  exception when invalid_parameter_value then null; end;
  if exists(select 1 from public.search_businesses('Adaa test tenant')) then raise exception 'FAIL: suspended business discoverable'; end if;
end $$;
reset role;
rollback;
select 'PASS: Adaa foundation access checks; fixtures rolled back' as result;
