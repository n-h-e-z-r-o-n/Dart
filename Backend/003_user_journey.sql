-- Apply after 001. Existing projects: run this as a separate complete SQL Editor query.
begin;
create table adaa_private.onboarding_requests (
  user_id uuid not null references auth.users(id) on delete cascade,
  request_id uuid not null,
  business_id uuid not null references public.businesses(id) on delete cascade,
  payload jsonb not null,
  primary key(user_id, request_id)
);
alter table adaa_private.onboarding_requests enable row level security;
revoke all on adaa_private.onboarding_requests from public, anon, authenticated;

create function public.create_business_once(p_request_id uuid, p_name text, p_category_id bigint,
  p_operating_model text, p_location_or_area text default null, p_description text default '', p_timezone text default 'UTC')
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid; v_existing jsonb; v_payload jsonb; v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'AUTH_REQUIRED' using errcode='28000'; end if;
  if p_request_id is null or p_description is null or length(p_description)>4000 then
    raise exception 'INVALID_INPUT' using errcode='22023'; end if;
  v_payload := jsonb_build_object('name',btrim(p_name),'category',p_category_id,'model',p_operating_model,
    'location',btrim(p_location_or_area),'description',btrim(p_description),'timezone',p_timezone);
  perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_user::text || p_request_id::text, 0));
  select business_id,payload into v_id,v_existing from adaa_private.onboarding_requests
    where user_id=v_user and request_id=p_request_id;
  if found then
    if v_existing <> v_payload then raise exception 'REQUEST_ALREADY_USED' using errcode='22023'; end if;
    return v_id;
  end if;
  v_id := public.create_business(p_name,p_category_id,p_operating_model,p_location_or_area,p_timezone);
  update public.businesses set description=btrim(p_description) where id=v_id;
  insert into adaa_private.onboarding_requests values(v_user,p_request_id,v_id,v_payload);
  return v_id;
end;
$$;

create function public.get_business_dashboard(p_business_id uuid) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare v_result jsonb;
begin
  if not adaa_private.has_role(p_business_id,array['owner','manager','staff']) then
    raise exception 'FORBIDDEN' using errcode='42501'; end if;
  select jsonb_build_object('id',b.id,'name',b.name,'category_id',b.primary_category_id,'category',c.name,
    'operating_model',b.operating_model,'publication_status',b.publication_status,
    'verification_status',b.verification_status,'description',b.description,'role',m.role,
    'can_publish',m.role in ('owner','manager') and b.publication_status in ('draft','unpublished'),
    'location_or_area',coalesce((select address from public.business_locations where business_id=b.id order by id limit 1),
      (select name from public.business_service_areas where business_id=b.id order by id limit 1),''),
    'members_count',(select count(*) from public.business_members where business_id=b.id),
    'locations_count',(select count(*) from public.business_locations where business_id=b.id))
  into v_result from public.businesses b join public.business_categories c on c.id=b.primary_category_id
  join public.business_members m on m.business_id=b.id and m.user_id=auth.uid() where b.id=p_business_id;
  return v_result;
end;
$$;

create function public.get_business_public_profile(p_business_id uuid) returns jsonb
language sql stable security invoker set search_path = '' as $$
  select jsonb_build_object('id',b.id,'name',b.name,'category_id',b.primary_category_id,'category',c.name,
    'operating_model',b.operating_model,'publication_status',b.publication_status,
    'verification_status',b.verification_status,'description',b.description,
    'location_or_area',coalesce((select address from public.business_locations where business_id=b.id order by id limit 1),
      (select name from public.business_service_areas where business_id=b.id order by id limit 1),''))
  from public.businesses b join public.business_categories c on c.id=b.primary_category_id
  where b.id=p_business_id and b.publication_status='published' and c.active;
$$;

create function public.update_business(p_business_id uuid,p_name text,p_category_id bigint,
  p_operating_model text,p_location_or_area text default null,p_description text default '') returns void
language plpgsql security definer set search_path = '' as $$
declare v_status text; v_previous_model text;
begin
  if not adaa_private.has_role(p_business_id,array['owner','manager']) then
    raise exception 'FORBIDDEN' using errcode='42501'; end if;
  select publication_status,operating_model into v_status,v_previous_model from public.businesses where id=p_business_id for update;
  if v_status='suspended' then raise exception 'BUSINESS_SUSPENDED' using errcode='22023'; end if;
  if p_name is null or length(btrim(p_name)) not between 1 and 160 or p_description is null or length(p_description)>4000 then
    raise exception 'INVALID_INPUT' using errcode='22023'; end if;
  if p_operating_model is null or p_operating_model not in ('physical','service_area','online') then
    raise exception 'INVALID_OPERATING_MODEL' using errcode='22023'; end if;
  if not exists(select 1 from public.business_categories where id=p_category_id and active) then
    raise exception 'INVALID_CATEGORY' using errcode='22023'; end if;
  if p_operating_model<>'online' and (p_location_or_area is null or length(btrim(p_location_or_area)) not between 1 and 500) then
    raise exception 'LOCATION_REQUIRED' using errcode='22023'; end if;
  -- This foundation has one onboarding location/area. Refuse bulk edits once
  -- a later locations module adds more records; never silently delete extra sites.
  if (select count(*) from public.business_locations where business_id=p_business_id)>1
    or (select count(*) from public.business_service_areas where business_id=p_business_id)>1 then
    raise exception 'MULTIPLE_LOCATIONS_REQUIRE_LOCATION_EDITOR' using errcode='22023'; end if;
  update public.businesses set name=btrim(p_name),primary_category_id=p_category_id,
    operating_model=p_operating_model,description=btrim(p_description) where id=p_business_id;
  if p_operating_model='physical' then
    delete from public.business_service_areas where business_id=p_business_id;
    if exists(select 1 from public.business_locations where business_id=p_business_id) then
      update public.business_locations set address=btrim(p_location_or_area),
        coordinates=case when address<>btrim(p_location_or_area) then null else coordinates end where business_id=p_business_id;
    else
      insert into public.business_locations(business_id,address) values(p_business_id,btrim(p_location_or_area));
    end if;
  elsif p_operating_model='service_area' then
    delete from public.business_locations where business_id=p_business_id;
    if exists(select 1 from public.business_service_areas where business_id=p_business_id) then
      update public.business_service_areas set name=btrim(p_location_or_area) where business_id=p_business_id;
    else
      insert into public.business_service_areas(business_id,name) values(p_business_id,btrim(p_location_or_area));
    end if;
  else
    delete from public.business_locations where business_id=p_business_id;
    delete from public.business_service_areas where business_id=p_business_id;
  end if;
end;
$$;
revoke all on function public.create_business_once(uuid,text,bigint,text,text,text,text),
  public.get_business_dashboard(uuid),public.get_business_public_profile(uuid),
  public.update_business(uuid,text,bigint,text,text,text) from public,anon,authenticated;
grant execute on function public.create_business_once(uuid,text,bigint,text,text,text,text),
  public.get_business_dashboard(uuid),public.update_business(uuid,text,bigint,text,text,text) to authenticated;
grant execute on function public.get_business_public_profile(uuid) to anon,authenticated;
notify pgrst,'reload schema';
commit;
