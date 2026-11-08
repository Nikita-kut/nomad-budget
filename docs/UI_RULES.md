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
- Wheel scroll never zooms a chart (`zoomEnabled = false`).

## Tables and lists

- Header cells: `HeaderCell`, amounts: `AmountCell`, fixed width `Ui.AmountColumn`, right-aligned.
- Every row is separated by `HorizontalDivider()` in the default `outlineVariant` color. Do not lower its alpha.
- Long lists show 5 rows collapsed with «Показать все N».

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
