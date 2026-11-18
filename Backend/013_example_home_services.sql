-- OPTIONAL EXAMPLE DATA, not a schema migration.
-- Supabase SQL Editor: run after 011 for the existing "cpact" business.
-- All examples start hidden. Review scope, price and duration in the business
-- service editor before enabling "Visible to customers". No payments are made.
-- Safe to rerun: existing records, including your subsequent edits, are preserved.
begin;
do $$
declare target_business uuid := '61f17f54-d4f4-44d7-b3de-559819da8af2';
begin
 if not exists(select 1 from public.businesses where id=target_business and name='cpact') then
  raise exception 'Expected cpact business not found. Verify the target business before running example data.';
 end if;
 if exists(select 1 from adaa_private.catalog_services where id in (
  'adaa1300-0000-4000-8000-000000000001','adaa1300-0000-4000-8000-000000000002',
  'adaa1300-0000-4000-8000-000000000003','adaa1300-0000-4000-8000-000000000004',
  'adaa1300-0000-4000-8000-000000000005') and business_id<>target_business) then
  raise exception 'Example identifiers already belong to another business.';
 end if;
 if (select count(*) from adaa_private.catalog_services where business_id=target_business)
  + (select count(*) from (values
   ('adaa1300-0000-4000-8000-000000000001'::uuid),('adaa1300-0000-4000-8000-000000000002'::uuid),
   ('adaa1300-0000-4000-8000-000000000003'::uuid),('adaa1300-0000-4000-8000-000000000004'::uuid),
   ('adaa1300-0000-4000-8000-000000000005'::uuid)) ids(id)
   where not exists(select 1 from adaa_private.catalog_services c where c.id=ids.id))>200 then
  raise exception 'Examples would exceed the business catalogue limit.';
 end if;
 insert into adaa_private.catalog_services(id,business_id,name,description,price_minor,duration_minutes,active) values
 ('adaa1300-0000-4000-8000-000000000001',target_business,'Home cleaning (example)',
  'EXAMPLE: Sample scope and price only. Routine cleaning of agreed rooms and surfaces. Confirm property size, supplies and scope before publishing.',250000,120,false),
 ('adaa1300-0000-4000-8000-000000000002',target_business,'Deep cleaning (example)',
  'EXAMPLE: Sample scope and price only. Detailed cleaning of agreed areas. Review property size, exclusions and required supplies before publishing.',650000,240,false),
 ('adaa1300-0000-4000-8000-000000000003',target_business,'Plumbing assessment (example)',
  'EXAMPLE: Sample assessment visit only. Repair labour and replacement parts are not included. Confirm what your business offers before publishing.',150000,45,false),
 ('adaa1300-0000-4000-8000-000000000004',target_business,'Electrical assessment (example)',
  'EXAMPLE: Sample assessment visit only. Repair work and parts are not included. Confirm staff qualifications, scope and final pricing before publishing.',200000,60,false),
 ('adaa1300-0000-4000-8000-000000000005',target_business,'General handyman visit (example)',
  'EXAMPLE: Sample visit for agreed small household tasks. Materials and specialist work are excluded. Review scope and price before publishing.',250000,90,false)
 on conflict(id) do nothing;
end $$;
commit;
select name,price_minor/100.0 as example_price_kes,duration_minutes,active
from adaa_private.catalog_services where business_id='61f17f54-d4f4-44d7-b3de-559819da8af2'
 and id in ('adaa1300-0000-4000-8000-000000000001','adaa1300-0000-4000-8000-000000000002',
 'adaa1300-0000-4000-8000-000000000003','adaa1300-0000-4000-8000-000000000004','adaa1300-0000-4000-8000-000000000005') order by name;
