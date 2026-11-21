package ru.nomadbudget.domain.model

enum class AccountKind { CARD, ACCOUNT, CASH, SAVINGS, INVESTMENT }

enum class AccountRole { LIFE, FUND, CUSHION }

data class Account(
    val id: String,
    val name: String,
    val currency: Currency,
    val kind: AccountKind,
    val isSavings: Boolean,
    val openingBalance: Money,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val isFund: Boolean = false,
    val fundMonthly: Money? = null,
    val fundGoal: Money? = null,
    val fundDay: Int? = null,
    val archivedOn: kotlinx.datetime.LocalDate? = null,
    val fundCategoryId: String? = null,
    val fundSubcategoryId: String? = null,
) {
    init {
        require(openingBalance.currency == currency) { "Валюта начального остатка не совпадает со счётом" }
        require(!(isFund && isSavings)) { "Счёт не может быть и копилкой, и резервом" }
    }

    fun activeOn(date: kotlinx.datetime.LocalDate): Boolean = !isArchived || (archivedOn != null && archivedOn >= date)

    val fundTarget: FundTarget?
        get() = fundCategoryId?.takeIf { isFund }?.let { FundTarget(it, fundSubcategoryId) }

    val fundPlan: FundPlan
        get() = FundPlan(fundMonthly, fundGoal, fundDay, fundTarget)

    fun withFundPlan(plan: FundPlan): Account = copy(
        fundMonthly = plan.monthly,
        fundGoal = plan.goal,
        fundDay = plan.day,
        fundCategoryId = plan.target?.categoryId,
        fundSubcategoryId = plan.target?.subcategoryId,
    )

    val role: AccountRole
        get() = when {
            isSavings -> AccountRole.CUSHION
            isFund -> AccountRole.FUND
            else -> AccountRole.LIFE
        }
}

data class FundTarget(
    val categoryId: String,
    val subcategoryId: String? = null,
)

data class FundPlan(
    val monthly: Money? = null,
    val goal: Money? = null,
    val day: Int? = null,
    val target: FundTarget? = null,
) {
    fun normalized(): FundPlan = copy(
        monthly = monthly?.takeIf { it.minor > 0L && target == null },
        goal = goal?.takeIf { it.minor > 0L },
    )
}
