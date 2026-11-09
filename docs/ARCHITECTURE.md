# Архитектура

Kotlin Multiplatform + Compose Multiplatform (Web/Wasm и Android), DI — Koin, бэкенд — Supabase.

```
ru.nomadbudget
├── domain/            без зависимостей на другие слои; чистый Kotlin + kotlinx-datetime
│   ├── model/         Money, Currency, Transaction, Account (+AccountRole), Category, Debt (+DebtCalculator),
│   │                  Period (+SalaryCycle), RateTable, UserSettings, Draft (+DraftParser),
│   │                  BankRateWatch (+Draft, Snapshot, TelegramSubscriber, BankWatchData)
│   ├── logic/         калькуляторы без состояния: MonthModel, BudgetCalculator, PlanCalculator, HistoryCalculator,
│   │                  CapitalModel, BalanceCalculator, JournalCalculator, DebtCategories, EntryDefaults,
│   │                  RatePairs, ExchangeAnalyzer, BankRateAnalyzer, CsvExporter
│   └── repository/    порты: *Repository, AuthRepository, BankRateWatchRepository, KeyValueStore, OfflineStatus, FlushResult
├── data/              реализации портов; знает про Supabase и локальное хранилище
│   ├── dto/ mapper/   DTO ↔ domain
│   ├── local/         LocalStore (expect/actual), UserScopedStore, OfflineCache, PendingQueue, DraftsStore
│   ├── repository/    Supabase-реализации репозиториев
│   └── supabase/      фабрика клиента
├── presentation/      Compose; импортирует только domain
│   ├── home/          HomeState (данные + мемоизированные производные, без математики), HomeViewModel (делегирует),
│   │                  HomeStore + ActionRunner (единый launch / ошибки / «сохраняется»), LoadCoordinator,
│   │                  Transaction/Plan/Category/Account/Debt/Inbox/Settings/BankWatchActions, HomeScreen (навигация)
│   ├── components/    Kit (Ui-константы, StatTile, TileRow, ProgressTrack, ChartPanel, HeaderCell, AmountCell),
│   │                  Buttons, CenteredLazyColumn, Skeleton, Sidebar, AppIcons, Hints
│   ├── charts/        Charts.kt (Canvas-графики, niceScale), ChartsScreen
│   ├── format/        MoneyFormat, DateFormat, plural — единственное место, где числа превращаются в текст
│   ├── theme/         AppTheme, AppColors, ThemeSettings
│   └── <экраны>/      month, planning, journal, entry, accounts, exchange, rates (+BankRateWatchSection), capital, more,
│                      settings, onboarding, auth
├── di/                Koin-модули; demo/ — вымышленные данные и demoModule
└── core/              TodayProvider, SupabaseConfig

tools/rates/           fetch_rates.py (курсы раз в сутки), bank_watch.py (+test_bank_watch.py, ca/) — слежение за курсом банка
                       и Telegram-бот; Python stdlib, запуск из GitHub Actions. Описание — BANK_RATE_WATCH.md
```

## Правила слоёв

- `domain` не импортирует `presentation`, `data`, `di`, `demo`.
- `presentation` не импортирует `data`: только порты из `domain/repository`.
- `data` не импортирует `presentation`.
- Связывают слои только `di`, `demo` и `App.kt`.
- Любая формула — в `domain/logic` с тестом; экран только показывает.
- Текст с числами, датами, валютами собирается только в `presentation/format`.
- Ошибки действий — через `ActionRunner.run`: `CancellationException` не глотается, флаг «сохраняется» снимается в `finally`.

Проверяется `tools/check_rules.sh` в CI.

## Проверки

```bash
bash tools/check_rules.sh
python3 -m unittest tools/rates/test_bank_watch.py
./gradlew :composeApp:testAndroidHostTest :composeApp:wasmJsBrowserTest :androidApp:assembleDebug
```

Миграции БД — `supabase/migrations`, применяются до выкатки клиента, который их использует.
