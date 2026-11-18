-- OPTIONAL. Deploy wallet-worker and configure secrets BEFORE running this file.
-- Create Vault secrets `adaa_project_url` and `adaa_wallet_worker_secret` in the
-- Supabase Vault UI. The latter must match the Edge WALLET_WORKER_SECRET.
-- Never paste service-role or gateway tokens into this SQL or into Android.
begin;
create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;
do $$ begin
  if (select count(*) from vault.decrypted_secrets where name in ('adaa_project_url','adaa_wallet_worker_secret'))<>2 then
    raise exception 'Configure the two Adaa Vault secrets before installing the schedule'; end if;
  if (select decrypted_secret from vault.decrypted_secrets where name='adaa_project_url') !~ '^https://[a-z0-9]+\.supabase\.co$' then
    raise exception 'Invalid Adaa project origin'; end if;
end $$;
select cron.schedule('adaa-wallet-reconciliation','* * * * *',$job$
  select net.http_post(
    url:=(select decrypted_secret from vault.decrypted_secrets where name='adaa_project_url') || '/functions/v1/wallet-worker',
    headers:=jsonb_build_object('Content-Type','application/json','x-wallet-worker-secret',
      (select decrypted_secret from vault.decrypted_secrets where name='adaa_wallet_worker_secret')),
    body:='{}'::jsonb,timeout_milliseconds:=55000);
$job$);
commit;
