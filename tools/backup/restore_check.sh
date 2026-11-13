#!/usr/bin/env bash
set -euo pipefail
dump="$1"
eval "$(supabase status -o env | grep -E '^DB_URL=')"
: "${DB_URL:?нет DB_URL}"

psql "$DB_URL" -q -v ON_ERROR_STOP=1 <<SQL
begin;
set session_replication_role = replica;
create temp table restore_users (j jsonb);
create temp table restore_identities (j jsonb);
\copy restore_users from '$dump/auth_users.jsonl'
\copy restore_identities from '$dump/auth_identities.jsonl'
do \$\$
declare
    r record;
    v_cols text;
begin
    for r in select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public' where c.relkind = 'r' loop
        execute format('truncate public.%I cascade', r.relname);
    end loop;
    for r in select * from (values ('users', 'restore_users'), ('identities', 'restore_identities')) as t(target, source) loop
        select string_agg(quote_ident(column_name), ', ' order by ordinal_position) into v_cols
        from information_schema.columns
        where table_schema = 'auth' and table_name = r.target and is_generated = 'NEVER' and generation_expression is null;
        execute format('insert into auth.%I (%s) select %s from %I, jsonb_populate_record(null::auth.%I, j) as x',
                       r.target, v_cols, v_cols, r.source, r.target);
    end loop;
end \$\$;
\i $dump/public.sql
commit;
SQL

psql "$DB_URL" -q -v ON_ERROR_STOP=1 -At -f tools/backup/counts.sql > "$dump/restored_counts.txt"

if ! diff -q "$dump/counts.txt" "$dump/restored_counts.txt" >/dev/null; then
    echo "::error::Восстановленный дамп не совпадает с продом по числу строк. Таблицы:"
    diff "$dump/counts.txt" "$dump/restored_counts.txt" | grep '^[<>]' | awk '{print $2}' | sort -u
    exit 1
fi
echo "Восстановление проверено: таблиц $(wc -l < "$dump/counts.txt"), числа строк совпали"
