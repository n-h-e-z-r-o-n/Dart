-- Run once after 008 in Supabase SQL Editor. No external payment activation.
begin;
create table adaa_private.storefronts (
 business_id uuid primary key references public.businesses(id),
 phone text not null default '' check(length(phone)<=40),
 email text not null default '' check(length(email)<=254),
 website text not null default '' check(website='' or website ~ '^https://[^[:space:]]+$'),
 hours text not null default '' check(length(hours)<=1500),
 timezone text not null default 'Africa/Nairobi',
 photos text[] not null default '{}' check(cardinality(photos)<=8),
 accepting_requests boolean not null default true,
 updated_at timestamptz not null default now()
);
create table adaa_private.catalog_services (
 id uuid primary key, business_id uuid not null references public.businesses(id),
 name text not null check(length(btrim(name)) between 1 and 120),
 description text not null default '' check(length(description)<=2000),
 price_minor bigint not null check(price_minor between 0 and 100000000),
 duration_minutes int not null check(duration_minutes between 5 and 1440),
 active boolean not null default true,
 updated_at timestamptz not null default now()
);
create index catalog_services_business on adaa_private.catalog_services(business_id,id);
create table adaa_private.booking_requests (
 id uuid primary key default gen_random_uuid(), request_id uuid not null,
 business_id uuid not null references public.businesses(id),
 customer_id uuid not null references auth.users(id),
 service_id uuid references adaa_private.catalog_services(id),
 service_name text not null, price_minor bigint, duration_minutes int,
 kind text not null check(kind in ('appointment','inquiry')),
 preferred_at timestamptz, timezone text not null,
 message text not null check(length(btrim(message)) between 1 and 2000),
 state text not null default 'pending' check(state in ('pending','confirmed','completed','cancelled','declined','answered','no_show')),
 response text not null default '' check(length(response)<=2000),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 unique(customer_id,request_id),
 check((kind='appointment' and service_id is not null and preferred_at is not null) or (kind='inquiry' and preferred_at is null))
);
create index bookings_business_page on adaa_private.booking_requests(business_id,created_at desc,id desc);
create index bookings_customer_page on adaa_private.booking_requests(customer_id,created_at desc,id desc);
create index bookings_business_state on adaa_private.booking_requests(business_id,state,created_at desc,id desc);
create index bookings_customer_state on adaa_private.booking_requests(customer_id,state,created_at desc,id desc);
create table adaa_private.booking_events (
 id bigint generated always as identity primary key,
 booking_id uuid not null references adaa_private.booking_requests(id),
 actor_id uuid not null references auth.users(id), state text not null,
 message text not null, created_at timestamptz not null default now()
);
create index booking_events_request on adaa_private.booking_events(booking_id,id);
alter table adaa_private.storefronts enable row level security;
alter table adaa_private.catalog_services enable row level security;
alter table adaa_private.booking_requests enable row level security;
alter table adaa_private.booking_events enable row level security;
revoke all on adaa_private.storefronts,adaa_private.catalog_services,adaa_private.booking_requests,adaa_private.booking_events from public,anon,authenticated;

create function public.get_storefront(p_business_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare v jsonb; v_manage boolean;
begin
 v_manage:=adaa_private.has_role(p_business_id,array['owner','manager']);
 if not adaa_private.is_public(p_business_id) and not adaa_private.has_role(p_business_id,array['owner','manager','staff']) then
   raise exception 'LISTING_UNAVAILABLE' using errcode='22023'; end if;
 select jsonb_build_object('phone',coalesce(s.phone,''),'email',coalesce(s.email,''),
  'website',coalesce(s.website,''),'hours',coalesce(s.hours,''),'timezone',coalesce(s.timezone,'Africa/Nairobi'),
  'photos',coalesce(to_jsonb(s.photos),'[]'::jsonb),'accepting_requests',coalesce(s.accepting_requests,true),
  'can_manage',v_manage,'services',coalesce((select jsonb_agg(to_jsonb(c) order by c.name,c.id)
    from adaa_private.catalog_services c where c.business_id=p_business_id and (c.active or v_manage)),'[]'::jsonb))
 into v from (select 1) x left join adaa_private.storefronts s on s.business_id=p_business_id;
 return v;
end $$;

create function public.save_storefront(p_business_id uuid,p_phone text,p_email text,p_website text,p_hours text,
 p_timezone text,p_photos text[],p_accepting_requests boolean) returns void
language plpgsql security definer set search_path='' as $$
begin
 if not adaa_private.has_role(p_business_id,array['owner','manager']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if not exists(select 1 from pg_catalog.pg_timezone_names where name=p_timezone) or p_photos is null
  or exists(select 1 from unnest(p_photos) u where u is null or length(u)>2000 or u !~ '^https://[^[:space:]]+$')
  or p_email is null or (p_email<>'' and p_email !~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$') then
   raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 insert into adaa_private.storefronts(business_id,phone,email,website,hours,timezone,photos,accepting_requests)
 values(p_business_id,btrim(p_phone),btrim(p_email),btrim(p_website),btrim(p_hours),p_timezone,p_photos,p_accepting_requests)
 on conflict(business_id) do update set phone=excluded.phone,email=excluded.email,website=excluded.website,
 hours=excluded.hours,timezone=excluded.timezone,photos=excluded.photos,accepting_requests=excluded.accepting_requests,updated_at=now();
end $$;

create function public.save_catalog_service(p_id uuid,p_business_id uuid,p_name text,p_description text,p_price_minor bigint,
 p_duration_minutes int,p_active boolean) returns void
language plpgsql security definer set search_path='' as $$
begin
 if not adaa_private.has_role(p_business_id,array['owner','manager']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 -- Lock the business for catalogue count/ownership decisions, not booking capacity.
 perform 1 from public.businesses where id=p_business_id for update;
 if exists(select 1 from adaa_private.catalog_services where id=p_id and business_id<>p_business_id) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if not exists(select 1 from adaa_private.catalog_services where id=p_id) and
  (select count(*) from adaa_private.catalog_services where business_id=p_business_id)>=200 then raise exception 'CATALOG_LIMIT' using errcode='22023'; end if;
 insert into adaa_private.catalog_services(id,business_id,name,description,price_minor,duration_minutes,active)
 values(p_id,p_business_id,btrim(p_name),btrim(p_description),p_price_minor,p_duration_minutes,p_active)
 on conflict(id) do update set name=excluded.name,description=excluded.description,price_minor=excluded.price_minor,
 duration_minutes=excluded.duration_minutes,active=excluded.active,updated_at=now();
end $$;

create function public.submit_booking_request(p_request_id uuid,p_business_id uuid,p_service_id uuid,p_preferred_at timestamptz,
 p_message text,p_expected_price_minor bigint default null) returns uuid
language plpgsql security definer set search_path='' as $$
declare v adaa_private.booking_requests; c adaa_private.catalog_services; z text; enabled boolean;
begin
 if auth.uid() is null then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 -- Serializes retries and rate-limit checks for this customer, never allocates a seat.
 perform 1 from public.profiles where id=auth.uid() for update;
 select * into v from adaa_private.booking_requests where customer_id=auth.uid() and request_id=p_request_id;
 if found then
  if v.business_id<>p_business_id or v.service_id is distinct from p_service_id or v.preferred_at is distinct from p_preferred_at
    or v.message<>btrim(p_message) or v.price_minor is distinct from p_expected_price_minor then
     raise exception 'REQUEST_ALREADY_USED' using errcode='22023'; end if;
  return v.id;
 end if;
 if not adaa_private.is_public(p_business_id) then raise exception 'LISTING_UNAVAILABLE' using errcode='22023'; end if;
 select timezone,accepting_requests into z,enabled from adaa_private.storefronts where business_id=p_business_id;
 if enabled=false then raise exception 'REQUESTS_PAUSED' using errcode='22023'; end if;
 if p_request_id is null or p_message is null or length(btrim(p_message)) not between 1 and 2000 then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 if (select count(*) from adaa_private.booking_requests where customer_id=auth.uid() and created_at>now()-interval '1 hour')>=20 then
  raise exception 'BOOKING_RATE_LIMIT' using errcode='22023'; end if;
 if p_service_id is not null then
  select * into c from adaa_private.catalog_services where id=p_service_id and business_id=p_business_id and active for share;
  if not found then raise exception 'SERVICE_UNAVAILABLE' using errcode='22023'; end if;
  if p_expected_price_minor is distinct from c.price_minor then raise exception 'SERVICE_PRICE_CHANGED' using errcode='22023'; end if;
  if p_preferred_at is null or p_preferred_at<=now() or p_preferred_at>now()+interval '1 year' then raise exception 'INVALID_BOOKING_TIME' using errcode='22023'; end if;
 elsif p_preferred_at is not null or p_expected_price_minor is not null then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 insert into adaa_private.booking_requests(request_id,business_id,customer_id,service_id,service_name,price_minor,duration_minutes,
  kind,preferred_at,timezone,message) values(p_request_id,p_business_id,auth.uid(),p_service_id,coalesce(c.name,'General inquiry'),c.price_minor,c.duration_minutes,
  case when p_service_id is null then 'inquiry' else 'appointment' end,p_preferred_at,coalesce(z,'Africa/Nairobi'),btrim(p_message)) returning * into v;
 insert into adaa_private.booking_events(booking_id,actor_id,state,message) values(v.id,auth.uid(),'pending','Request submitted. Awaiting business confirmation.');
 return v.id;
end $$;

create function public.get_booking_requests(p_business_id uuid default null,p_before timestamptz default null,p_before_id uuid default null,p_state text default null) returns jsonb
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null or (p_business_id is not null and not adaa_private.has_role(p_business_id,array['owner','manager','staff'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if p_state is not null and p_state not in ('pending','confirmed','completed','closed') then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 return coalesce((select jsonb_agg(to_jsonb(q) order by q.created_at desc,q.id desc) from (
  select r.*,b.name business_name,coalesce(p.display_name,'') customer_name,
  (p_business_id is not null and adaa_private.has_role(r.business_id,array['owner','manager'])) can_manage,
  (r.customer_id=auth.uid()) is_customer
  from adaa_private.booking_requests r join public.businesses b on b.id=r.business_id left join public.profiles p on p.id=r.customer_id
  where ((p_business_id is null and r.customer_id=auth.uid()) or r.business_id=p_business_id)
   and (p_state is null or r.state=p_state or (p_state='closed' and r.state in ('cancelled','declined','answered','no_show')))
   and (p_before is null or (r.created_at,r.id)<(p_before,p_before_id))
  order by r.created_at desc,r.id desc limit 30) q),'[]'::jsonb);
end $$;

create function public.get_booking_detail(p_id uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare v adaa_private.booking_requests;
begin
 select * into v from adaa_private.booking_requests where id=p_id;
 if auth.uid() is null or v.id is null or (v.customer_id<>auth.uid() and not adaa_private.has_role(v.business_id,array['owner','manager','staff'])) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 return to_jsonb(v)||jsonb_build_object('business_name',(select name from public.businesses where id=v.business_id),
  'customer_name',coalesce((select display_name from public.profiles where id=v.customer_id),''),
  'can_manage',adaa_private.has_role(v.business_id,array['owner','manager']), 'is_customer',v.customer_id=auth.uid(),
  'events',coalesce((select jsonb_agg(to_jsonb(e) order by e.id) from adaa_private.booking_events e where e.booking_id=v.id),'[]'::jsonb));
end $$;

create function public.act_on_booking(p_id uuid,p_action text,p_response text default '') returns void
language plpgsql security definer set search_path='' as $$
declare v adaa_private.booking_requests; target text;
begin
 select * into v from adaa_private.booking_requests where id=p_id for update;
 if auth.uid() is null or v.id is null then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 if p_response is null or length(btrim(p_response))>2000 then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 if p_action='cancelled' then
  if v.customer_id<>auth.uid() and not adaa_private.has_role(v.business_id,array['owner','manager']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 else
  if not adaa_private.has_role(v.business_id,array['owner','manager']) then raise exception 'FORBIDDEN' using errcode='42501'; end if;
 end if;
 if p_action not in ('confirmed','completed','cancelled','declined','answered','no_show') or p_action is null then raise exception 'INVALID_INPUT' using errcode='22023'; end if;
 if v.state=p_action then return; end if; -- transport retry, never repeats an event
 if not ((p_action in ('declined','cancelled') and v.state='pending') or
  (p_action='cancelled' and v.state='confirmed') or
  (p_action='confirmed' and v.kind='appointment' and v.state='pending' and v.preferred_at>now()) or
  (p_action='completed' and v.kind='appointment' and v.state='confirmed' and v.preferred_at<=now()) or
  (p_action='no_show' and v.kind='appointment' and v.state='confirmed' and v.preferred_at<=now()) or
  (p_action='answered' and v.kind='inquiry' and v.state='pending' and length(btrim(p_response))>0)) then
  raise exception 'BOOKING_STATE_CHANGED' using errcode='22023'; end if;
 if p_action='confirmed' and not adaa_private.is_public(v.business_id) then raise exception 'LISTING_UNAVAILABLE' using errcode='22023'; end if;
 if p_action in ('declined','cancelled','no_show') and length(btrim(p_response))=0 then raise exception 'BOOKING_REASON_REQUIRED' using errcode='22023'; end if;
 update adaa_private.booking_requests set state=p_action,response=btrim(p_response),updated_at=now() where id=p_id;
 insert into adaa_private.booking_events(booking_id,actor_id,state,message) values(p_id,auth.uid(),p_action,btrim(p_response));
 -- A booking never debits a wallet or releases money. Payment requires explicit customer consent.
end $$;

revoke all on function public.get_storefront(uuid),public.save_storefront(uuid,text,text,text,text,text,text[],boolean),
 public.save_catalog_service(uuid,uuid,text,text,bigint,int,boolean),public.submit_booking_request(uuid,uuid,uuid,timestamptz,text,bigint),
 public.get_booking_requests(uuid,timestamptz,uuid,text),public.get_booking_detail(uuid),public.act_on_booking(uuid,text,text) from public,anon,authenticated;
grant execute on function public.get_storefront(uuid) to anon,authenticated;
grant execute on function public.save_storefront(uuid,text,text,text,text,text,text[],boolean),
 public.save_catalog_service(uuid,uuid,text,text,bigint,int,boolean),public.submit_booking_request(uuid,uuid,uuid,timestamptz,text,bigint),
 public.get_booking_requests(uuid,timestamptz,uuid,text),public.get_booking_detail(uuid),public.act_on_booking(uuid,text,text) to authenticated;
notify pgrst,'reload schema';
commit;
