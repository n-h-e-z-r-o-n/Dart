-- Adaa milestone 1. Paste this entire file into the Supabase SQL Editor.
-- Run once on a new project. Transactional: an error rolls back the whole migration.
begin;

create schema if not exists extensions;
create extension if not exists postgis with schema extensions;
create schema if not exists adaa_private;
revoke all on schema adaa_private from public;
grant usage on schema adaa_private to anon, authenticated;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '' check (length(display_name) <= 120),
  phone text check (length(phone) <= 40),
  created_at timestamptz not null default now()
);
create table public.platform_admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
create table public.business_categories (
  id bigint generated always as identity primary key,
  name text not null unique check (length(btrim(name)) between 1 and 80),
  active boolean not null default true
);
create table public.businesses (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(btrim(name)) between 1 and 160),
  primary_category_id bigint not null references public.business_categories(id),
  operating_model text not null check (operating_model in ('physical', 'service_area', 'online')),
  description text not null default '' check (length(description) <= 4000),
  publication_status text not null default 'draft'
    check (publication_status in ('draft', 'published', 'unpublished', 'suspended')),
  verification_status text not null default 'unverified'
    check (verification_status in ('unverified', 'pending', 'verified', 'rejected')),
  created_at timestamptz not null default now()
);
create table public.business_members (
  business_id uuid not null references public.businesses(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('owner', 'manager', 'staff')),
  created_at timestamptz not null default now(),
  primary key (business_id, user_id)
);
create index business_members_user_idx on public.business_members(user_id);
create table public.business_locations (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  address text not null check (length(btrim(address)) between 1 and 500),
  coordinates extensions.geography(point, 4326),
  timezone text not null default 'UTC'
);
create index business_locations_business_idx on public.business_locations(business_id);
create index business_locations_geo_idx on public.business_locations using gist(coordinates);
create table public.business_service_areas (
  id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(id) on delete cascade,
  name text not null check (length(btrim(name)) between 1 and 500)
);
create index business_service_areas_business_idx on public.business_service_areas(business_id);
create index businesses_public_idx on public.businesses(primary_category_id, created_at desc, id)
  where publication_status = 'published';

create function adaa_private.is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.platform_admins where user_id = (select auth.uid()));
$$;
create function adaa_private.has_role(p_business_id uuid, p_roles text[]) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.business_members
    where business_id = p_business_id and user_id = (select auth.uid()) and role = any(p_roles));
$$;
create function adaa_private.is_public(p_business_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.businesses where id = p_business_id and publication_status = 'published');
$$;
create function adaa_private.create_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles(id, display_name)
  values (new.id, left(coalesce(new.raw_user_meta_data->>'display_name', ''), 120));
  return new;
end;
$$;
create trigger adaa_auth_user_created after insert on auth.users
for each row execute function adaa_private.create_profile();
insert into public.profiles(id) select id from auth.users on conflict do nothing;

alter table public.profiles enable row level security;
alter table public.platform_admins enable row level security;
alter table public.business_categories enable row level security;
alter table public.businesses enable row level security;
alter table public.business_members enable row level security;
alter table public.business_locations enable row level security;
alter table public.business_service_areas enable row level security;

-- Remove Supabase default grants. All mutations go through narrow RPCs,
-- except profile editing, whose column grants exclude identity/created_at.
revoke all on public.profiles, public.platform_admins, public.business_categories,
  public.businesses, public.business_members, public.business_locations,
  public.business_service_areas from public, anon, authenticated;
grant select on public.business_categories, public.businesses, public.business_locations,
  public.business_service_areas to anon, authenticated;
grant select on public.profiles, public.business_members to authenticated;
grant update(display_name, phone) on public.profiles to authenticated;
create policy profiles_read on public.profiles for select to authenticated using (id = (select auth.uid()));
create policy profiles_edit on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
create policy categories_read on public.business_categories for select to anon, authenticated using (active);
create policy businesses_read on public.businesses for select to anon, authenticated using (
  publication_status = 'published' or adaa_private.has_role(id, array['owner','manager','staff']) or adaa_private.is_admin()
);
create policy members_read on public.business_members for select to authenticated using (
  user_id = (select auth.uid()) or adaa_private.has_role(business_id, array['owner','manager']) or adaa_private.is_admin()
);
create policy locations_read on public.business_locations for select to anon, authenticated using (
  adaa_private.is_public(business_id) or adaa_private.has_role(business_id, array['owner','manager','staff']) or adaa_private.is_admin()
);
create policy areas_read on public.business_service_areas for select to anon, authenticated using (
  adaa_private.is_public(business_id) or adaa_private.has_role(business_id, array['owner','manager','staff']) or adaa_private.is_admin()
);

create function public.create_business(
  p_name text, p_category_id bigint, p_operating_model text,
  p_location_or_area text default null, p_timezone text default 'UTC'
) returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid; v_user uuid := auth.uid();
begin
  if v_user is null then raise exception 'AUTH_REQUIRED' using errcode = '28000'; end if;
  if p_name is null or length(btrim(p_name)) not between 1 and 160 then
    raise exception 'INVALID_BUSINESS_NAME' using errcode = '22023'; end if;
  if p_operating_model is null or p_operating_model not in ('physical','service_area','online') then
    raise exception 'INVALID_OPERATING_MODEL' using errcode = '22023'; end if;
  if not exists(select 1 from public.business_categories where id = p_category_id and active) then
    raise exception 'INVALID_CATEGORY' using errcode = '22023'; end if;
  if p_operating_model <> 'online' and (p_location_or_area is null or length(btrim(p_location_or_area)) not between 1 and 500) then
    raise exception 'LOCATION_REQUIRED' using errcode = '22023'; end if;
  if p_timezone is null or not exists(select 1 from pg_catalog.pg_timezone_names where name = p_timezone) then
    raise exception 'INVALID_TIMEZONE' using errcode = '22023'; end if;
  insert into public.businesses(name, primary_category_id, operating_model)
    values(btrim(p_name), p_category_id, p_operating_model) returning id into v_id;
  insert into public.business_members(business_id, user_id, role) values(v_id, v_user, 'owner');
  if p_operating_model = 'physical' then
    insert into public.business_locations(business_id, address, timezone) values(v_id, btrim(p_location_or_area), p_timezone);
  elsif p_operating_model = 'service_area' then
    insert into public.business_service_areas(business_id, name) values(v_id, btrim(p_location_or_area));
  end if;
  return v_id;
end;
$$;

create function public.get_my_businesses() returns table(
  id uuid, name text, operating_model text, publication_status text, verification_status text, role text
) language sql stable security invoker set search_path = '' as $$
  select b.id, b.name, b.operating_model, b.publication_status, b.verification_status, m.role
  from public.businesses b join public.business_members m on m.business_id = b.id
  where m.user_id = (select auth.uid()) order by b.created_at desc, b.id;
$$;

create function public.publish_business(p_business_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare v_business public.businesses;
begin
  if not adaa_private.has_role(p_business_id, array['owner','manager']) then
    raise exception 'FORBIDDEN' using errcode = '42501'; end if;
  select * into v_business from public.businesses where id = p_business_id for update;
  if v_business.publication_status = 'suspended' then raise exception 'BUSINESS_SUSPENDED' using errcode = '22023'; end if;
  if not exists(select 1 from public.business_categories where id = v_business.primary_category_id and active) then
    raise exception 'INVALID_CATEGORY' using errcode = '22023'; end if;
  if v_business.operating_model = 'physical' and not exists(select 1 from public.business_locations where business_id = p_business_id) then
    raise exception 'LOCATION_REQUIRED' using errcode = '22023'; end if;
  if v_business.operating_model = 'service_area' and not exists(select 1 from public.business_service_areas where business_id = p_business_id) then
    raise exception 'SERVICE_AREA_REQUIRED' using errcode = '22023'; end if;
  update public.businesses set publication_status = 'published' where id = p_business_id;
end;
$$;

-- Owners can assign existing registered users. No caller-supplied owner role.
-- Serializing on the business prevents concurrent membership changes racing.
create function public.set_business_member(p_business_id uuid, p_user_id uuid, p_role text) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform 1 from public.businesses where id = p_business_id for update;
  if not adaa_private.has_role(p_business_id, array['owner']) then
    raise exception 'FORBIDDEN' using errcode = '42501'; end if;
  if p_role is null or p_role not in ('manager','staff') then raise exception 'INVALID_ROLE' using errcode = '22023'; end if;
  if exists(select 1 from public.business_members where business_id = p_business_id and user_id = p_user_id and role = 'owner') then
    raise exception 'OWNER_IMMUTABLE' using errcode = '42501'; end if;
  insert into public.business_members(business_id,user_id,role) values(p_business_id,p_user_id,p_role)
    on conflict(business_id,user_id) do update set role = excluded.role;
end;
$$;
create function public.remove_business_member(p_business_id uuid, p_user_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
begin
  perform 1 from public.businesses where id = p_business_id for update;
  if not adaa_private.has_role(p_business_id, array['owner']) then raise exception 'FORBIDDEN' using errcode = '42501'; end if;
  if exists(select 1 from public.business_members where business_id = p_business_id and user_id = p_user_id and role = 'owner') then
    raise exception 'OWNER_IMMUTABLE' using errcode = '42501'; end if;
  delete from public.business_members where business_id = p_business_id and user_id = p_user_id;
end;
$$;

-- No private membership/customer fields in the discovery response.
create function public.search_businesses(p_query text default '', p_limit integer default 20, p_offset integer default 0)
returns table(id uuid, name text, operating_model text, category_name text)
language sql stable security invoker set search_path = '' as $$
  select b.id, b.name, b.operating_model, c.name from public.businesses b
  join public.business_categories c on c.id = b.primary_category_id
  where b.publication_status = 'published' and c.active
    and (b.name ilike '%' || left(coalesce(p_query,''),160) || '%'
      or c.name ilike '%' || left(coalesce(p_query,''),160) || '%')
  order by b.created_at desc, b.id limit least(greatest(coalesce(p_limit,20),1),100)
  offset greatest(coalesce(p_offset,0),0);
$$;

revoke all on function adaa_private.is_admin(), adaa_private.has_role(uuid,text[]),
  adaa_private.is_public(uuid), adaa_private.create_profile() from public, anon, authenticated;
grant execute on function adaa_private.is_admin(), adaa_private.has_role(uuid,text[]),
  adaa_private.is_public(uuid) to anon, authenticated;
revoke all on function public.create_business(text,bigint,text,text,text), public.get_my_businesses(),
  public.publish_business(uuid), public.set_business_member(uuid,uuid,text),
  public.remove_business_member(uuid,uuid), public.search_businesses(text,integer,integer) from public, anon, authenticated;
grant execute on function public.create_business(text,bigint,text,text,text), public.get_my_businesses(),
  public.publish_business(uuid), public.set_business_member(uuid,uuid,text),
  public.remove_business_member(uuid,uuid) to authenticated;
grant execute on function public.search_businesses(text,integer,integer) to anon, authenticated;

insert into public.business_categories(name) values
  ('Beauty & wellness'), ('Home services'), ('Professional services'), ('Retail'),
  ('Food & drink'), ('Health & fitness'), ('Education'), ('Other');
notify pgrst, 'reload schema';
commit;
