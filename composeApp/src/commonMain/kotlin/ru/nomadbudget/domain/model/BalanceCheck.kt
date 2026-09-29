package ru.nomadbudget.domain.model

import kotlinx.datetime.LocalDate

data class BalanceCheck(
    val id: String,
    val accountId: String,
    val date: LocalDate,
    val actual: Money,
    val computed: Money,
    val note: String = "",
) {
    val difference: Money get() = actual - computed
}

object CorrectionCategory {
    const val NAME: String = "Корректировка"
}
