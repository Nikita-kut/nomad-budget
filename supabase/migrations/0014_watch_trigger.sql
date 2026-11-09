create extension if not exists pg_cron;
create extension if not exists pg_net;

create or replace function public.dispatch_bank_watch()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_token text;
begin
    select decrypted_secret into v_token from vault.decrypted_secrets where name = 'github_dispatch_token' limit 1;
    if v_token is null or v_token = '' then
        return;
    end if;
    perform net.http_post(
        url := 'https://api.github.com/repos/Nikita-kut/nomad-budget/actions/workflows/bank-watch.yml/dispatches',
        headers := jsonb_build_object(
            'Authorization', 'Bearer ' || v_token,
            'Accept', 'application/vnd.github+json',
            'X-GitHub-Api-Version', '2022-11-28',
            'User-Agent', 'nomad-budget-cron'
        ),
        body := jsonb_build_object('ref', 'main')
    );
end $$;

revoke execute on function public.dispatch_bank_watch() from public, anon, authenticated;

select cron.unschedule(jobid) from cron.job where jobname = 'bank-watch-dispatch';
select cron.schedule('bank-watch-dispatch', '*/15 * * * *', 'select public.dispatch_bank_watch()');
