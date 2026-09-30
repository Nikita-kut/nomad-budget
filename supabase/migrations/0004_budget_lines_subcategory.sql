-- План по подкатегориям: строка budget_lines может относиться к подкатегории.
-- Строка без subcategory_id — план всей категории; если у категории есть строки по подкатегориям,
-- приложение считает план категории их суммой.

alter table public.budget_lines
    add column subcategory_id uuid references public.subcategories (id) on delete cascade;

alter table public.budget_lines
    drop constraint budget_lines_period_id_category_id_key;

alter table public.budget_lines
    add constraint budget_lines_period_category_subcategory_key
    unique nulls not distinct (period_id, category_id, subcategory_id);
