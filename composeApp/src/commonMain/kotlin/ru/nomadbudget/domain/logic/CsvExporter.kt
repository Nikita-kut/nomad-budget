package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Account
import ru.nomadbudget.domain.model.Category
import ru.nomadbudget.domain.model.Money
import ru.nomadbudget.domain.model.Subcategory
import ru.nomadbudget.domain.model.Transaction

object CsvExporter {

    private const val HEADER = "date,type,account,amount,currency,category,subcategory,to_account,to_amount,to_currency,amount_rub,note"

    fun export(
        transactions: List<Transaction>,
        accounts: List<Account>,
        categories: List<Category>,
        subcategories: List<Subcategory>,
    ): String {
        val accountNames = accounts.associate { it.id to it.name }
        val categoryNames = categories.associate { it.id to it.name }
        val subcategoryNames = subcategories.associate { it.id to it.name }
        val rows = transactions.sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id }).map { tx ->
            val cells = when (tx) {
                is Transaction.Expense -> listOf(
                    "expense", accountNames[tx.accountId].orEmpty(), plain(tx.amount), tx.amount.currency.code,
                    categoryNames[tx.categoryId].orEmpty(), subcategoryNames[tx.subcategoryId].orEmpty(), "", "", "",
                )
                is Transaction.Income -> listOf(
                    "income", accountNames[tx.accountId].orEmpty(), plain(tx.amount), tx.amount.currency.code,
                    categoryNames[tx.categoryId].orEmpty(), "", "", "", "",
                )
                is Transaction.Transfer -> listOf(
                    "transfer", accountNames[tx.fromAccountId].orEmpty(), plain(tx.amount), tx.amount.currency.code,
                    "", "", accountNames[tx.toAccountId].orEmpty(), plain(tx.amount), tx.amount.currency.code,
                )
                is Transaction.Exchange -> listOf(
                    "exchange", accountNames[tx.fromAccountId].orEmpty(), plain(tx.given), tx.given.currency.code,
                    "", "", accountNames[tx.toAccountId].orEmpty(), plain(tx.received), tx.received.currency.code,
                )
            }
            (listOf(tx.date.toString()) + cells + listOf(plain(tx.amountBase), tx.note)).joinToString(",") { escape(it) }
        }
        return (listOf(HEADER) + rows).joinToString("\r\n") + "\r\n"
    }

    fun plain(money: Money): String {
        val factor = money.currency.minorFactor
        val sign = if (money.minor < 0) "-" else ""
        val abs = kotlin.math.abs(money.minor)
        val major = abs / factor
        val fraction = abs % factor
        return if (money.currency.minorUnits == 0) "$sign$major" else "$sign$major.${fraction.toString().padStart(money.currency.minorUnits, '0')}"
    }

    private fun escape(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private const val FORMULA_STARTS = "=+@"
}
