package ru.nomadbudget.demo

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.logic.BudgetLine
import ru.nomadbudget.domain.logic.CapitalGoal
import ru.nomadbudget.domain.model.BaseChange
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.ExchangeRate
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.UserSettings
import kotlin.time.Clock

internal class DemoBaseSnapshot(
    val transactions: List<Transaction>,
    val budgetLines: Map<String, List<BudgetLine>>,
    val savingsTargets: Map<String, Money>,
    val capitalGoals: List<CapitalGoal>,
    val settings: UserSettings,
    val afterChange: List<Transaction>,
)

internal class DemoBaseChanger(private val db: DemoDatabase) {

    private val series: Map<Currency, List<ExchangeRate>> = db.rateHistory.groupBy { it.quote }.mapValues { (_, list) -> list.sortedBy { it.date } }

    fun change(code: String) {
        val from = db.currencies.first { it.code == db.settings.baseCurrencyCode }
        val to = requireNotNull(db.currencies.firstOrNull { it.code == code }) { "Нет курса для $code" }
        if (from == to) return
        val before = DemoBaseSnapshot(
            transactions = db.transactions.toList(),
            budgetLines = db.budgetLines.mapValues { it.value.toList() },
            savingsTargets = db.savingsTargets.toMap(),
            capitalGoals = db.capitalGoals.toList(),
            settings = db.settings,
            afterChange = emptyList(),
        )
        db.transactions.replaceEach { rebase(it, to) }
        db.budgetLines.forEach { (periodId, lines) ->
            val start = startOf(periodId)
            lines.replaceEach { it.copy(planned = convert(it.planned, to, start)) }
        }
        db.savingsTargets.keys.toList().forEach { periodId ->
            db.savingsTargets[periodId] = convert(db.savingsTargets.getValue(periodId), to, startOf(periodId))
        }
        db.capitalGoals.replaceEach { it.copy(amount = convert(it.amount, to, db.today)) }
        db.settings = db.settings.copy(
            baseCurrencyCode = to.code,
            currencyCodes = (db.settings.currencyCodes + from.code - to.code).distinct().sorted(),
        )
        db.baseSnapshot = DemoBaseSnapshot(
            before.transactions, before.budgetLines, before.savingsTargets, before.capitalGoals, before.settings, db.transactions.toList(),
        )
        db.baseChange = BaseChange(from.code, to.code, Clock.System.now(), reverted = false)
    }

    fun revert() {
        val snapshot = db.baseSnapshot
        val change = db.baseChange
        check(snapshot != null && change != null && !change.reverted) { "Нечего откатывать" }
        check(db.transactions == snapshot.afterChange) { "После смены базы уже есть новые или изменённые операции, откат невозможен" }
        db.transactions.clear()
        db.transactions += snapshot.transactions
        db.budgetLines.clear()
        snapshot.budgetLines.forEach { (id, lines) -> db.budgetLines[id] = lines.toMutableList() }
        db.savingsTargets.clear()
        db.savingsTargets += snapshot.savingsTargets
        db.capitalGoals.clear()
        db.capitalGoals += snapshot.capitalGoals
        db.settings = db.settings.copy(baseCurrencyCode = snapshot.settings.baseCurrencyCode, currencyCodes = snapshot.settings.currencyCodes)
        db.baseSnapshot = null
        db.baseChange = change.copy(reverted = true)
    }

    private fun rebase(tx: Transaction, to: Currency): Transaction = when (tx) {
        is Transaction.Expense -> tx.copy(amountBase = exactOr(tx.amount, tx.amountBase, to, tx.date))
        is Transaction.Income -> tx.copy(amountBase = exactOr(tx.amount, tx.amountBase, to, tx.date))
        is Transaction.Transfer -> tx.copy(amountBase = exactOr(tx.amount, tx.amountBase, to, tx.date))
        is Transaction.Exchange -> tx.copy(
            amountBase = if (tx.received.currency == to) tx.received else exactOr(tx.given, tx.amountBase, to, tx.date),
        )
    }

    private fun exactOr(amount: Money, amountBase: Money, to: Currency, date: LocalDate): Money =
        if (amount.currency == to) amount else convert(amountBase, to, date)

    private fun convert(money: Money, to: Currency, date: LocalDate): Money =
        if (money.currency == to) money else Money.ofMajor(money.toMajor() * pivotPerUnit(money.currency, date) / pivotPerUnit(to, date), to)

    private fun pivotPerUnit(currency: Currency, date: LocalDate): Double {
        if (currency == Currency.RATE_PIVOT) return 1.0
        val list = requireNotNull(series[currency]?.takeIf { it.isNotEmpty() }) { "Нет курса для ${currency.code}" }
        return (list.lastOrNull { it.date <= date } ?: list.first()).basePerUnit
    }

    private fun startOf(periodId: String): LocalDate = LocalDate.parse(periodId.removePrefix("demo:"))
}
