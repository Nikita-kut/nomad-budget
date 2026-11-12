# UI rules

One rule, one component. A fix to a visual problem goes into the shared component or this file, never into a single screen.

## Layout

- Scrollable screens use `CenteredLazyColumn`: the scroll area is full width, content is centered and capped at `Ui.ContentMaxWidth` on every screen.
- No two side-by-side scroll columns; summary and sections go in one column.
- No side-by-side columns of unequal height. Either stack groups full width, or use `EqualRow` / `TileRow` (equal height via `IntrinsicSize.Max`).
- Spacing between cards: `Ui.Gap`. Card padding: `Ui.CardPadding`.

## Summary tiles

- Always `StatTile` inside `TileRow`.
- A tile has a fixed structure: label, value, progress track (space reserved even when empty), two-line caption. Tiles in one row are the same height.
- The main tile of a screen is `accent = true`; only one per row.

## Charts

- Always inside `ChartPanel`: title, hint, legend, own period chips, own zoom (− / +), hover hint.
- Each chart keeps its own period and zoom; no shared period selector for a whole screen.
- Values are passed in rubles (or the rate itself), never pre-scaled to thousands.
- The Y axis uses `niceScale`: round steps 1 / 2 / 2.5 / 5 × 10ⁿ, about 4–5 gridlines. Columns and cumulative lines include zero; trend lines (capital, rates) fit the data.
- Axis labels: `MoneyFormat.formatAxis` (`4,5 млн`, `250 тыс`) or `formatQuote` for rates. Hover tooltip: exact value via `formatValue` (`MoneyFormat.formatWhole`).
- Charts are our own Canvas components (`presentation/charts/Charts.kt`): geometry and labels are cached with `drawWithCache`, hover redraws only the marker. No third-party chart library: it re-laid out every frame and made scrolling janky.
- Zoom (− / +) shows the last N points; there is no horizontal scrolling inside a chart.

## Tables and lists

- Header cells: `HeaderCell`, amounts: `AmountCell`, fixed width `Ui.AmountColumn`, right-aligned.
- Every row is separated by `HorizontalDivider()` in the default `outlineVariant` color. Do not lower its alpha.
- Long lists show 5 rows collapsed with «Показать все N».

## Buttons

Four kinds, all in `presentation/components/Buttons.kt`. Raw Material buttons are not used on screens (checked by `tools/check_rules.sh`).

- `PrimaryButton` — the one main action of a screen, card or dialog: «Внести платёж», «Записать», «Сохранить», dialog confirm. Filled accent, radius 10, height 44.
- `SecondaryButton` — other actions next to content: «Скопировать план», «Сверить остаток», «Назад» in a wizard. Accent at 12 % opacity with accent text, no outline.
- `AddTile` — creating a new item in a list or grid: «Добавить кредит», «Добавить счёт», «Добавить категорию». Dashed tile with «+», sits where the new card will appear.
- `TextButton` — minor actions inside cards and dialogs: «Отмена», «Показать все», «Изменить».

No outlined buttons. No more than one `PrimaryButton` per card.

## Окна

- Любое действие поверх экрана — модальное окно, полноэкранных окон нет. Короткое (подтверждение, одно-два поля) — `AlertDialog`, `ConfirmDialog`, `TextDialog`; длинная форма со списком — `AppModal` (по центру, не шире формы, высота по содержимому и не больше экрана, прокрутка внутри, крестик и «Отмена» / основная кнопка внизу). Проверяет `tools/check_rules.sh`.
- Переход в другой раздел («Кредиты», «Капитал») — это навигация, а не окно: подпись со стрелкой «→».

## Удаление и архив

- Всё, что можно добавить, можно и убрать: счёт, категорию, подкатегорию, кредит, цель, валюту, подписчика, заметку.
- Убрать — только через подтверждение: `DeleteIconButton` (корзина и диалог «Удалить …? …» в одном компоненте) или `TrashIconButton`, открывающий свой `ConfirmDialog`. Корзина в другом месте не используется (проверяет `tools/check_rules.sh`). Исключения: операции в журнале — свайп с кнопкой «Отменить» на 10 секунд; черновые строки в онбординге до сохранения.
- Если у сущности есть история (счета, категории, подкатегории, кредиты), она уходит в архив, а не удаляется. Архив виден на том же экране в карточке «В архиве: N» с кнопкой «Вернуть» («Открыть снова» для кредитов).
- Валюту можно убрать из своего списка, только если на ней нет активных счетов; пока есть — корзина неактивна и рядом число счетов.
- Карточка цели открывает редактирование по нажатию; цель «Подушка на N мес.» открывает настройки подушки.

## Forms

- Creating an entity asks for everything it needs in one dialog: fields depend on the chosen type (a fund asks for monthly contribution, next payment and day; every account asks for the current balance). No «create, then find and edit».

## Transaction types

- Expense: `colors.bad`, income: `colors.good`, transfer: `colors.save`, exchange: `colors.warning`.
- Journal rows show a 4 dp stripe and a type label in the type color; non-expense rows get a light tint of it.

## Colors

- Only theme tokens (`MaterialTheme.colorScheme.*`, `AppTheme.colors.*`). No literal colors in screens.

## Typography and shape

- Font: Inter (Regular, Medium, SemiBold). No Bold; numbers use SemiBold.
- Card radius 16, tiles 20, large blocks 24; buttons are pills.
- The one main tile of a screen uses the hero gradient (`AppTheme.colors.hero`).

## Icons

- Only `AppIcons` (Lucide line icons, stroke 1.75). No Material icons.

## Navigation

- Wide screens: grouped sidebar (Бюджет / Деньги / Анализ), rows 36 dp, collapsible to icons with tooltips.
- Narrow screens: bottom bar with five items.

## Тексты

- Канонические термины: «подушка» (не «накопления»; «Себе» — только имя строки плана и цели), «копилка», «конверты» (гибкие категории расходов, одно имя на всех экранах), «кредит» / «Кредиты», «операция» (не «запись», не «опер.»), «Как сейчас» (сценарий по медиане, не «по темпу»), «Объединить» подкатегории (не «слить»), «изменение» курса (не «дельта», не «изм.»).
- Обращение на «ты», но без рода: безличные формы вместо мужского прошедшего времени («Отдано / Получено», «Внесено / Снято», «если сумма изменилась», а не «Отдал», «Положил», «если изменил»).
- Один голос для статусов: безличный («Сохранение…», «Запишется расход», «Записано»), без «мы» («Сохраняем…») и «я» («запишу»).
- Числительные только через `plural(n, one, few, many)` из `presentation/format/Plural.kt` (`operations(n)` для операций): «1 операция», «2 операции», «5 операций», «11 операций».
- Месяц с годом в тексте: `DateFormat.monthYear` («ноябрь 2027») или `DateFormat.byMonthYear` («к ноябрю 2027»), не «11.2027».
- Подсказки не завязаны на мышь: «нажми или наведи», а не «наведи».
- Инструкции только на существующие действия: не отправлять пользователя «заполнить таблицу курсов» или на вкладки, которых нет.
- Примеры сумм и плейсхолдеры — в масштабе рублей («такси 450», «1,5к = 1 500»); никаких стран, городов, местных валют и банков.

## Валюты: символ или код

Три способа назвать валюту — символ (₽, $), код (RUB, USD), слово («рубли»). У каждого своё место; внутри одного объекта (суммы, курса, подписи) способ один.

- **Сумма** (число + валюта) — символ, только через `MoneyFormat.format` / `formatSigned` / `formatWhole`: «9 246 ₽», «$100», «−38 500 ₽». Положение символа задаёт `Currency.symbolBeforeAmount`; руками символ к числу не приписывать. Валюте без общепринятого символа в `Currency.symbol` лежит код — это единственный случай, когда код стоит у числа.
- **Курс** — коды с обеих сторон, через `MoneyFormat.rateText` / `quoteText`: «1 USD = 92,46 RUB», «1 000 RUB = 10,82 USD». Курс — отношение двух валют, а не деньги на счёте: так делают Wise, Revolut, XE, ЦБ; «$» — символ десятка валют, а разное положение символов («$1 = 92 ₽») ломает выравнивание прямого и обратного курса в таблице. Единица масштабируется через `quoteUnit` (1 / 10 / 100 / 1 000 …) и подписывается `unitLabel(unit, currency)` → «1 000 RUB».
- **Заголовок колонки с курсом** — `MoneyFormat.perUnitLabel(unit, from, to)`: «RUB за 1 USD», «USD за 1 000 RUB». Подсказка графика — «сколько RUB за 1 USD», в том же порядке.
- **Валюта как сущность без числа** — код: `CurrencyChip`, пикеры («Валюта: USD»), названия пар и графиков («USD и RUB», «USD → RUB: твой курс в обменах»), подписи полей («Отдано, USD», «Сколько, RUB», «План на месяц, RUB»), строки журнала («Обмен USD на RUB»), ошибки («Нет курса для USD»). Подпись поля с базовой валютой — тоже код (`Currency.BASE.code`), не «₽»: иначе рядом стоят «Сумма, ₽» и «Взнос в месяц, USD».
- **Базовая валюта в прозе** — слово: «в рублях», «к рублю», «Пересчёт в рубли», колонка «В рублях». Не «в ₽» и не «в RUB». Код базовой валюты — только в подписях полей и в курсах (см. выше).
- **Не смешивать** символ и код в одной подписи: не «1 ₽ в USD», не «сколько USD за 100 ₽», не «USD · $». Сумма и курс рядом допустимы, каждый по своему правилу: «≈ 9 246 ₽ по курсу 1 USD = 92,46 RUB».
- Текст с валютой не собирается в domain: `ExchangeAnalyzer` отдаёт `RateQuote`, строку делает `MoneyFormat`.
