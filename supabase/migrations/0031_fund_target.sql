alter table public.accounts
    add column fund_category_id uuid,
    add column fund_subcategory_id uuid,
    add constraint accounts_fund_category_id_fkey foreign key (fund_category_id, user_id)
        references public.categories (id, user_id) on delete set null (fund_category_id),
    add constraint accounts_fund_subcategory_id_fkey foreign key (fund_subcategory_id, user_id)
        references public.subcategories (id, user_id) on delete set null (fund_subcategory_id);
