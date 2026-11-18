-- Optional, after 008. SQL dead-man monitoring runs even if the HTTP worker is down.
-- No payments, automatic refunds or escrow releases are performed by this job.
begin;
create extension if not exists pg_cron;
select cron.schedule('adaa-wallet-health','* * * * *',$job$
 select public.get_wallet_operational_health();
$job$);
commit;
