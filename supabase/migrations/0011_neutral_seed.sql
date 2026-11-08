create or replace function public.seed_defaults_for_user(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_cat uuid;
begin
    insert into public.accounts (user_id, name, currency, kind, is_savings, sort_order) values
        (p_user, 'Карта',    'RUB', 'card', false, 10),
        (p_user, 'Наличные', 'RUB', 'cash', false, 20),
        (p_user, 'Подушка',  'RUB', 'savings', true, 30);

    insert into public.categories (user_id, name, kind, sort_order) values
        (p_user, 'Зарплата',     'income', 10),
        (p_user, 'Прочий доход', 'income', 20);

    insert into public.categories (user_id, name, kind, sort_order) values
        (p_user, 'Еда',         'expense', 10),
        (p_user, 'Транспорт',   'expense', 20),
        (p_user, 'Жильё',       'expense', 30),
        (p_user, 'Здоровье',    'expense', 40),
        (p_user, 'Связь',       'expense', 50),
        (p_user, 'Развлечения', 'expense', 60),
        (p_user, 'Путешествия', 'expense', 70),
        (p_user, 'Подарки',     'expense', 80),
        (p_user, 'Разное',      'expense', 90),
        (p_user, 'Кредиты',     'expense', 100);

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Еда';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'продукты'), (p_user, v_cat, 'кафе'), (p_user, v_cat, 'доставка');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Транспорт';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'такси'), (p_user, v_cat, 'общественный транспорт'), (p_user, v_cat, 'топливо');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Жильё';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'аренда'), (p_user, v_cat, 'коммунальные услуги');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Здоровье';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'аптека'), (p_user, v_cat, 'врач'), (p_user, v_cat, 'спорт');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Связь';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'мобильная связь'), (p_user, v_cat, 'интернет'), (p_user, v_cat, 'подписки');
end $$;

revoke execute on function public.seed_defaults_for_user(uuid) from public, anon, authenticated;
revoke execute on function public.seed_correction_categories(uuid) from public, anon, authenticated;
