select string_agg(format('%s %s', t.relname, (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from public.%I', t.relname), false, true, '')))[1]::text), E'\n' order by t.relname)
from pg_class t
join pg_namespace n on n.oid = t.relnamespace and n.nspname = 'public'
where t.relkind = 'r';
select format('auth.users %s', count(*)) from backup.auth_users();
