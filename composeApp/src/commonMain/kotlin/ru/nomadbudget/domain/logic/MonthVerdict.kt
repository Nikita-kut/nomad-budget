package ru.nomadbudget.domain.logic

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.model.Money

enum class SavingsState { NONE, DONE, PENDING, MISSED }

enum class VerdictLevel { GOOD, WARNING, BAD }

data class MonthVerdict(
    val spentFromIncome: Money,
    val plannedFromIncome: Money,
    val incomeSpendOk: Boolean,
    val savingsState: SavingsState,
    val saved: Money,
    val savingsTarget: Money,
    val unplannedFromSavings: Money,
) {
    val level: VerdictLevel
        get() = when {
            !incomeSpendOk || savingsState == SavingsState.MISSED -> VerdictLevel.BAD
            savingsState == SavingsState.PENDING || !unplannedFromSavings.isZero -> VerdictLevel.WARNING
            else -> VerdictLevel.GOOD
        }

    companion object {
        fun evaluate(summary: MonthSummary, savingsTarget: Money?, periodEnded: Boolean): MonthVerdict? {
            if (summary.expensePlanned.isZero) return null
            val plannedFromIncome = summary.expensePlanned - summary.plannedFromSavings
            val spent = summary.expenseFromIncome
            val target = savingsTarget ?: Money.zero(Currency.BASE)
            val state = when {
                target.isZero -> SavingsState.NONE
                summary.netSaved.wholeUnits >= target.wholeUnits -> SavingsState.DONE
                periodEnded -> SavingsState.MISSED
                else -> SavingsState.PENDING
            }
            return MonthVerdict(
                spentFromIncome = spent,
                plannedFromIncome = plannedFromIncome,
                incomeSpendOk = spent.wholeUnits <= plannedFromIncome.wholeUnits,
                savingsState = state,
                saved = summary.netSaved,
                savingsTarget = target,
                unplannedFromSavings = summary.unplannedFromSavings,
            )
        }
    }
}
