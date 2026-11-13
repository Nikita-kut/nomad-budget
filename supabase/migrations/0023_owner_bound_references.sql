-- Ссылки между пользовательскими таблицами привязаны к владельцу: внешний ключ (колонка, user_id) → (id, user_id).
-- Обычный внешний ключ проверяется в обход RLS, и без этого можно сослаться на чужой счёт, категорию или кредит по id.

do $$
declare
    r record;
    v_action text;
begin
    for r in
        select c.oid, c.conname, child.relname as child, parent.relname as parent,
               a.attname as col, pa.attname as parent_col, c.confdeltype
        from pg_constraint c
        join pg_class child on child.oid = c.conrelid
        join pg_class parent on parent.oid = c.confrelid
        join pg_namespace n on n.oid = child.relnamespace and n.nspname = 'public'
        join pg_namespace pn on pn.oid = parent.relnamespace and pn.nspname = 'public'
        join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
        join pg_attribute pa on pa.attrelid = c.confrelid and pa.attnum = c.confkey[1]
        where c.contype = 'f'
          and cardinality(c.conkey) = 1
          and exists (select 1 from pg_attribute x where x.attrelid = c.conrelid and x.attname = 'user_id' and not x.attisdropped)
          and exists (select 1 from pg_attribute x where x.attrelid = c.confrelid and x.attname = 'user_id' and not x.attisdropped)
    loop
        if not exists (
            select 1 from pg_constraint u
            where u.conrelid = format('public.%I', r.parent)::regclass and u.contype in ('u', 'p')
              and u.conkey::int[] @> array[
                  (select attnum from pg_attribute where attrelid = format('public.%I', r.parent)::regclass and attname = r.parent_col),
                  (select attnum from pg_attribute where attrelid = format('public.%I', r.parent)::regclass and attname = 'user_id')
              ]::int[]
              and cardinality(u.conkey) = 2
        ) then
            execute format('alter table public.%I add constraint %I unique (%I, user_id)',
                           r.parent, r.parent || '_' || r.parent_col || '_user_key', r.parent_col);
        end if;

        v_action := case r.confdeltype
            when 'c' then 'on delete cascade'
            when 'n' then format('on delete set null (%I)', r.col)
            when 'r' then 'on delete restrict'
            else ''
        end;

        execute format('alter table public.%I drop constraint %I', r.child, r.conname);
        execute format('alter table public.%I add constraint %I foreign key (%I, user_id) references public.%I (%I, user_id) %s',
                       r.child, r.conname, r.col, r.parent, r.parent_col, v_action);
    end loop;
end $$;

-- Аноним (запрос без входа) не читает, не пишет и не вызывает ничего в public: всё приложение работает после входа.
revoke all on all tables in schema public from anon;
revoke all on all sequences in schema public from anon;
revoke execute on all functions in schema public from public, anon;

alter default privileges in schema public revoke all on tables from anon;
alter default privileges in schema public revoke all on sequences from anon;
alter default privileges in schema public revoke execute on functions from public, anon;
