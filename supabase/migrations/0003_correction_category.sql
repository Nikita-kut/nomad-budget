-- Категории «Корректировка» для сверки остатков: расходная для недостачи, доходная для излишка.
-- Новым пользователям создаются при регистрации, существующим — приложение создаёт при первой сверке.

create or replace function public.seed_correction_categories(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.categories (user_id, name, kind, sort_order) values
        (p_user, 'Корректировка', 'expense', 900),
        (p_user, 'Корректировка', 'income',  900)
    on conflict (user_id, kind, name) do nothing;
end $$;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.seed_defaults_for_user(new.id);
    perform public.seed_correction_categories(new.id);
    return new;
end $$;
