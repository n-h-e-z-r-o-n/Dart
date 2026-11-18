-- Development acceptance checks. Run after 011; all fixtures roll back.
begin;
insert into auth.users(id) values
 ('abbbbbbb-1000-0000-0000-000000000001'),('abbbbbbb-1000-0000-0000-000000000002'),
 ('abbbbbbb-1000-0000-0000-000000000003'),('abbbbbbb-1000-0000-0000-000000000004');
insert into public.businesses(id,name,primary_category_id,operating_model,publication_status)
values('abbbbbbb-2000-0000-0000-000000000001','Booking studio',(select id from public.business_categories where name='Other'),'physical','published');
insert into public.business_members(business_id,user_id,role) values
 ('abbbbbbb-2000-0000-0000-000000000001','abbbbbbb-1000-0000-0000-000000000001','owner'),
 ('abbbbbbb-2000-0000-0000-000000000001','abbbbbbb-1000-0000-0000-000000000004','staff');
set local role authenticated;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000001';
select public.save_storefront('abbbbbbb-2000-0000-0000-000000000001','+254712345678','studio@example.com','https://example.com',
 'Monday to Saturday 09:00–18:00','Africa/Nairobi','{}',true);
select public.save_catalog_service('abbbbbbb-3000-0000-0000-000000000001','abbbbbbb-2000-0000-0000-000000000001','Signature service','A real service',350000,60,true);
set local role anon;
set local request.jwt.claim.sub='';
do $$begin
 if jsonb_array_length(public.get_storefront('abbbbbbb-2000-0000-0000-000000000001')->'services')<>1 then raise exception 'Public catalogue missing'; end if;
 if has_table_privilege('anon','adaa_private.booking_requests','select') then raise exception 'Anonymous booking leak'; end if;
 if has_function_privilege('anon','public.submit_booking_request(uuid,uuid,uuid,timestamptz,text,bigint)','execute') then raise exception 'Anonymous booking allowed'; end if;
end$$;
set local role authenticated;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000002';
select public.submit_booking_request('abbbbbbb-4000-0000-0000-000000000001','abbbbbbb-2000-0000-0000-000000000001',
 'abbbbbbb-3000-0000-0000-000000000001',now()+interval '2 days','First request',350000);
-- Exact retry: same transaction timestamp and parameters.
select public.submit_booking_request('abbbbbbb-4000-0000-0000-000000000001','abbbbbbb-2000-0000-0000-000000000001',
 'abbbbbbb-3000-0000-0000-000000000001',now()+interval '2 days','First request',350000);
do $$begin
 if jsonb_array_length(public.get_booking_requests())<>1 then raise exception 'Retry duplicated request'; end if;
 begin
  perform public.submit_booking_request('abbbbbbb-4000-0000-0000-000000000001','abbbbbbb-2000-0000-0000-000000000001',
   'abbbbbbb-3000-0000-0000-000000000001',now()+interval '2 days','Changed request',350000);
  raise exception 'Changed retry accepted';
 exception when sqlstate '22023' then if sqlerrm<>'REQUEST_ALREADY_USED' then raise; end if; end;
 begin
  perform public.submit_booking_request('abbbbbbb-4000-0000-0000-000000000002','abbbbbbb-2000-0000-0000-000000000001',
   'abbbbbbb-3000-0000-0000-000000000001',now()+interval '2 days','Wrong quote',300000);
  raise exception 'Changed quote accepted';
 exception when sqlstate '22023' then if sqlerrm<>'SERVICE_PRICE_CHANGED' then raise; end if; end;
 begin
  perform public.get_booking_requests('abbbbbbb-2000-0000-0000-000000000001');
  raise exception 'Customer read business inbox';
 exception when insufficient_privilege then null; end;
end$$;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000003';
select public.submit_booking_request('abbbbbbb-4000-0000-0000-000000000003','abbbbbbb-2000-0000-0000-000000000001',
 'abbbbbbb-3000-0000-0000-000000000001',now()+interval '2 days','Same preferred time',350000);
select public.submit_booking_request('abbbbbbb-4000-0000-0000-000000000004','abbbbbbb-2000-0000-0000-000000000001',null,null,'Do you offer home visits?',null);
do $$declare other_id uuid; begin
 if jsonb_array_length(public.get_booking_requests())<>2 then raise exception 'Customer scope incorrect'; end if;
 -- ID comes from an owner fixture variable without granting direct table access.
 other_id := (public.get_booking_requests()->0->>'id')::uuid;
 begin
  perform public.act_on_booking(other_id,'confirmed','I accept');
  raise exception 'Customer confirmed own request';
 exception when insufficient_privilege then null; end;
end$$;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000004';
do $$declare v uuid; begin
 v:=(public.get_booking_requests('abbbbbbb-2000-0000-0000-000000000001')->0->>'id')::uuid;
 begin perform public.act_on_booking(v,'declined','No capacity'); raise exception 'Staff mutated request'; exception when insufficient_privilege then null; end;
 begin perform public.save_catalog_service('abbbbbbb-3000-0000-0000-000000000001','abbbbbbb-2000-0000-0000-000000000001','Changed','',100,60,true);
  raise exception 'Staff edited catalogue'; exception when insufficient_privilege then null; end;
end$$;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000001';
do $$declare r jsonb; v uuid; begin
 for r in select value from jsonb_array_elements(public.get_booking_requests('abbbbbbb-2000-0000-0000-000000000001')) loop
  v:=(r->>'id')::uuid;
  if r->>'kind'='appointment' then
   perform public.act_on_booking(v,'confirmed','Our team can accommodate you.');
   perform public.act_on_booking(v,'confirmed','Retry');
   if jsonb_array_length(public.get_booking_detail(v)->'events')<>2 then raise exception 'Repeated acceptance duplicated event'; end if;
  else perform public.act_on_booking(v,'answered','Yes, please contact us with your area.'); end if;
 end loop;
 if jsonb_array_length(public.get_booking_requests('abbbbbbb-2000-0000-0000-000000000001',null,null,'confirmed'))<>2
  or jsonb_array_length(public.get_booking_requests('abbbbbbb-2000-0000-0000-000000000001',null,null,'closed'))<>1
  or jsonb_array_length(public.get_booking_requests('abbbbbbb-2000-0000-0000-000000000001',null,null,'pending'))<>0 then
  raise exception 'Server-side status filters incorrect'; end if;
end$$;
select public.save_catalog_service('abbbbbbb-3000-0000-0000-000000000001','abbbbbbb-2000-0000-0000-000000000001','Renamed service','',400000,90,false);
reset role;
do $$begin
 if (select count(*) from adaa_private.booking_requests where state='confirmed')<>2 then raise exception 'Same-time appointments limited'; end if;
 if exists(select 1 from adaa_private.booking_requests where kind='appointment' and (price_minor<>350000 or service_name<>'Signature service')) then raise exception 'Booking snapshot changed'; end if;
 if exists(select 1 from adaa_private.wallet_journals) then raise exception 'Booking moved wallet funds'; end if;
end$$;
set local role authenticated;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000002';
do $$declare v uuid; begin
 v:=(public.get_booking_requests()->0->>'id')::uuid;
 begin perform public.act_on_booking(v,'cancelled',''); raise exception 'Missing cancellation reason accepted';
 exception when sqlstate '22023' then if sqlerrm<>'BOOKING_REASON_REQUIRED' then raise; end if; end;
 perform public.act_on_booking(v,'cancelled','My plans changed.');
 if (public.get_booking_detail(v)->>'state')<>'cancelled' then raise exception 'Cancellation failed'; end if;
end$$;
reset role;
-- Enough older records to verify cursor filtering beyond the first page.
insert into adaa_private.booking_requests(request_id,business_id,customer_id,service_name,kind,timezone,message,state,created_at)
select gen_random_uuid(),'abbbbbbb-2000-0000-0000-000000000001','abbbbbbb-1000-0000-0000-000000000003','Historical inquiry','inquiry','Africa/Nairobi','History fixture','answered',now()-make_interval(days=>i)
from generate_series(1,31) i;
set local role authenticated;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000003';
do $$declare page jsonb; next_page jsonb; last_row jsonb; begin
 page:=public.get_booking_requests(null,null,null,'closed');
 if jsonb_array_length(page)<>30 then raise exception 'Page limit incorrect'; end if;
 last_row:=page->29;
 next_page:=public.get_booking_requests(null,(last_row->>'created_at')::timestamptz,(last_row->>'id')::uuid,'closed');
 if jsonb_array_length(next_page)<>2 then raise exception 'Filtered pagination lost older records'; end if;
 if exists(select 1 from jsonb_array_elements(page) a join jsonb_array_elements(next_page) b on a->>'id'=b->>'id') then raise exception 'Pagination repeated records'; end if;
end$$;
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000001';
select public.save_storefront('abbbbbbb-2000-0000-0000-000000000001','','','','','Africa/Nairobi','{}',false);
set local request.jwt.claim.sub='abbbbbbb-1000-0000-0000-000000000002';
do $$begin
 begin
  perform public.submit_booking_request('abbbbbbb-4000-0000-0000-000000000005','abbbbbbb-2000-0000-0000-000000000001',null,null,'New inquiry while paused',null);
  raise exception 'Paused business received request';
 exception when sqlstate '22023' then if sqlerrm<>'REQUESTS_PAUSED' then raise; end if; end;
 if jsonb_array_length(public.get_booking_requests())<>1 then raise exception 'Pause hid existing customer request'; end if;
end$$;
reset role;
rollback;
select 'PASS: storefront authorization, snapshots, retries, same-time capacity, replies, cancellation and payment isolation' result;
