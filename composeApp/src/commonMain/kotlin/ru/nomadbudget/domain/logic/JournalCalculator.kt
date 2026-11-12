package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Transaction
import ru.nomadbudget.domain.model.sumIn

data class JournalDay(
    val date: LocalDate,
    val transactions: List<Transaction>,
    val spentBase: Money,
)

data class JournalSummary(
    val days: List<JournalDay>,
    val count: Int,
    val spent: Money,
    val earned: Money,
)

object JournalCalculator {

    fun days(transactions: List<Transaction>, base: Currency): List<JournalDay> = transactions
        .groupBy { it.date }
        .entries
        .sortedByDescending { it.key }
        .map { (date, list) -> JournalDay(date, list, spentBase(list, base)) }

    fun summary(transactions: List<Transaction>, base: Currency): JournalSummary = JournalSummary(
        days = days(transactions, base),
        count = transactions.size,
        spent = spentBase(transactions, base),
        earned = transactions.filterIsInstance<Transaction.Income>().map { it.amountBase }.sumIn(base),
    )

    fun matchesQuery(transaction: Transaction, query: String, labels: List<String>): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        if ((labels + transaction.note).any { it.lowercase().contains(needle) }) return true
        val digits = needle.filter(Char::isDigit)
        if (digits.isEmpty()) return false
        return transaction.amountBase.wholeUnits.toString().contains(digits) || transaction.ownAmount.wholeUnits.toString().contains(digits)
    }

    private fun spentBase(transactions: List<Transaction>, base: Currency): Money =
        transactions.filterIsInstance<Transaction.Expense>().map { it.amountBase }.sumIn(base)
}
