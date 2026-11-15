package ru.nomadbudget.features

enum class AppModule(val key: String, val title: String, val description: String, val requires: List<AppModule> = emptyList()) {
    DEBTS("debts", "Кредиты", "кредиты и рассрочки, обязательный платёж, досрочка"),
    CUSHION("cushion", "Подушка и «Себе»", "резерв на чёрный день и сколько откладывать каждый месяц"),
    FUNDS("funds", "Копилки", "деньги на крупные траты: аренда, отпуск, техника"),
    CAPITAL("capital", "Капитал", "счета минус долги, прогноз и цели", requires = listOf(CUSHION)),
    CHARTS("charts", "Графики", "доходы, расходы и подушка по месяцам"),
    CURRENCIES("currencies", "Мультивалютность", "счета в разных валютах, обмен, курсы"),
    BANK_WATCH("bank_watch", "Курс банка и бот", "слежение за курсом обмена и уведомления в Telegram", requires = listOf(CURRENCIES)),
    INBOX("inbox", "Входящие", "быстрые заметки о тратах, которые разбираешь позже"),
}

enum class Feature(val key: String, val module: AppModule?, val title: String) {
    DEBTS_SCREEN("debts.screen", AppModule.DEBTS, "Экран «Кредиты»"),
    DEBTS_MONTH_TILE("debts.month_tile", AppModule.DEBTS, "Плитка «Осталось по кредитам» в месяце"),
    DEBTS_PLAN("debts.plan", AppModule.DEBTS, "Кредиты в плане"),
    CUSHION_HEADER("cushion.header", AppModule.CUSHION, "Подушка в шапке"),
    CUSHION_MONTH_TILE("cushion.month_tile", AppModule.CUSHION, "Плитка «Подушка за месяц»"),
    CUSHION_PLAN("cushion.plan", AppModule.CUSHION, "«Себе» в плане"),
    CUSHION_ACCOUNTS("cushion.accounts", AppModule.CUSHION, "Подушка на экране счетов"),
    FUNDS_HEADER("funds.header", AppModule.FUNDS, "Копилки в шапке"),
    FUNDS_MONTH("funds.month", AppModule.FUNDS, "Копилки в месяце"),
    FUNDS_PLAN("funds.plan", AppModule.FUNDS, "Взносы в копилки в плане"),
    FUNDS_ACCOUNTS("funds.accounts", AppModule.FUNDS, "Копилки на экране счетов"),
    CAPITAL_SCREEN("capital.screen", AppModule.CAPITAL, "Экран «Капитал»"),
    CAPITAL_PLAN_FORECAST("capital.plan_forecast", AppModule.CAPITAL, "Прогноз капитала в плане"),
    CHARTS_SCREEN("charts.screen", AppModule.CHARTS, "Экран «Графики»"),
    CURRENCIES_EXCHANGE("currencies.exchange", AppModule.CURRENCIES, "Экран «Обмен»"),
    CURRENCIES_RATES("currencies.rates", AppModule.CURRENCIES, "Экран «Курсы»"),
    CURRENCIES_ACCOUNTS("currencies.accounts", AppModule.CURRENCIES, "Курсы и обмен на экране счетов"),
    CURRENCIES_SETTINGS("currencies.settings", AppModule.CURRENCIES, "Валюты в настройках"),
    BANK_WATCH_RATES("bank_watch.rates", AppModule.BANK_WATCH, "Курс банка на экране курсов"),
    INBOX_ENTRY("inbox.entry", AppModule.INBOX, "Входящие в новой записи"),
    TICKETS_BUTTON("tickets.button", null, "Кнопка «Сообщить о проблеме»"),
}

enum class Stage(val key: String, val title: String) {
    OWNER("owner", "только владелец"),
    ALL("all", "все"),
    OFF("off", "выключена");

    companion object {
        fun of(key: String?): Stage? = entries.firstOrNull { it.key == key }
    }
}

data class FeatureState(
    val stages: Map<String, Stage> = emptyMap(),
    val isAdmin: Boolean = false,
    val modulesOff: Set<String> = emptySet(),
    val blocksOff: Set<String> = emptySet(),
) {
    val enabledModules: Set<AppModule> = enabledModules(modulesOff)

    fun stageAllows(feature: Feature): Boolean = when (stages[feature.key]) {
        Stage.ALL -> true
        Stage.OWNER -> isAdmin
        Stage.OFF, null -> false
    }

    fun isOn(feature: Feature): Boolean =
        stageAllows(feature) && (feature.module == null || feature.module in enabledModules) && feature.key !in blocksOff

    fun isOn(module: AppModule): Boolean = module in enabledModules && Feature.entries.any { it.module == module && stageAllows(it) }

    companion object {
        val EVERYTHING = FeatureState(stages = Feature.entries.associate { it.key to Stage.ALL })
    }
}

fun enabledModules(modulesOff: Set<String>): Set<AppModule> {
    val result = mutableSetOf<AppModule>()
    fun enabled(module: AppModule): Boolean = module.key !in modulesOff && module.requires.all(::enabled)
    AppModule.entries.filterTo(result, ::enabled)
    return result
}

fun withModule(modulesOff: Set<String>, module: AppModule, on: Boolean): Set<String> {
    if (!on) {
        val dependents = AppModule.entries.filter { module in requiresClosure(it) }.map { it.key }
        return modulesOff + module.key + dependents
    }
    return modulesOff - (requiresClosure(module) + module).map { it.key }.toSet()
}

fun requiresClosure(module: AppModule): Set<AppModule> =
    module.requires.flatMap { requiresClosure(it) + it }.toSet()
