-- При регистрации пользователя создаём ему стартовые счета, категории и подкатегории.
-- Функция security definer: выполняется с правами владельца, минуя RLS, потому что
-- в момент триггера auth.uid() ещё не равен новому пользователю.

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
        (p_user, 'RU карта',          'RUB', 'card',       false, 10),
        (p_user, 'RU счёт',           'RUB', 'account',    false, 20),
        (p_user, 'RU наличка',        'RUB', 'cash',       false, 30),
        (p_user, 'USD карта Freedom', 'USD', 'card',       false, 40),
        (p_user, 'USD наличка',       'USD', 'cash',       false, 50),
        (p_user, 'VND наличка',       'VND', 'cash',       false, 60),
        (p_user, 'Инвесткопилка',     'RUB', 'investment', true,  70);

    insert into public.categories (user_id, name, kind, sort_order) values
        (p_user, 'Зарплата',           'income', 10),
        (p_user, 'С прошлого месяца',  'income', 20),
        (p_user, 'Возврат',            'income', 30),
        (p_user, 'Кешбек',             'income', 40);

    insert into public.categories (user_id, name, kind, sort_order) values
        (p_user, 'Еда',                     'expense', 10),
        (p_user, 'Транспорт',               'expense', 20),
        (p_user, 'Жильё',                   'expense', 30),
        (p_user, 'Здоровье',                'expense', 40),
        (p_user, 'Документы',               'expense', 50),
        (p_user, 'Связь',                   'expense', 60),
        (p_user, 'Комиссии',                'expense', 70),
        (p_user, 'Путешествия',             'expense', 80),
        (p_user, 'Подарки и родным',        'expense', 90),
        (p_user, 'Разное',                  'expense', 100),
        (p_user, 'Развлечения',             'expense', 110),
        (p_user, 'Долг',                    'expense', 120);

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Еда';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'продукты'), (p_user, v_cat, 'кафе и рестораны'), (p_user, v_cat, 'доставка');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Транспорт';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'Grab и такси'), (p_user, v_cat, 'аренда байка'), (p_user, v_cat, 'бензин'), (p_user, v_cat, 'авиабилеты');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Жильё';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'кв плата РФ'), (p_user, v_cat, 'кв плата Вьетнам'), (p_user, v_cat, 'аренда');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Здоровье';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'аптека'), (p_user, v_cat, 'врач'), (p_user, v_cat, 'страховка'), (p_user, v_cat, 'спорт');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Документы';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'виза'), (p_user, v_cat, 'визаран'), (p_user, v_cat, 'нотариус и переводы'), (p_user, v_cat, 'ВНЖ');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Связь';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'SIM VN'), (p_user, v_cat, 'SIM RU'), (p_user, v_cat, 'VPN'), (p_user, v_cat, 'подписки');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Комиссии';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'банк'), (p_user, v_cat, 'обменник'), (p_user, v_cat, 'конвертация');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Путешествия';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'отели'), (p_user, v_cat, 'туры'), (p_user, v_cat, 'билеты');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Подарки и родным';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'подарок'), (p_user, v_cat, 'перевод родным');

    select id into v_cat from public.categories where user_id = p_user and kind = 'expense' and name = 'Долг';
    insert into public.subcategories (user_id, category_id, name) values
        (p_user, v_cat, 'дом рф'), (p_user, v_cat, 'альфа'), (p_user, v_cat, 'втб'), (p_user, v_cat, 'озон'), (p_user, v_cat, 'кредитка');
end $$;

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    perform public.seed_defaults_for_user(new.id);
    return new;
end $$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();
