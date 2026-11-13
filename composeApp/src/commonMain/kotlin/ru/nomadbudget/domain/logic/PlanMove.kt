package ru.nomadbudget.domain.logic

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import ru.nomadbudget.domain.model.Period
import ru.nomadbudget.domain.model.SalaryCycle

object PlanMove {

    const val MIN_DAYS_LEFT = 7

    fun target(today: LocalDate, newStartDay: Int): Period {
        val containing = SalaryCycle.periodContaining(today, newStartDay)
        return if (today.daysUntil(containing.endExclusive) <= MIN_DAYS_LEFT) SalaryCycle.next(containing, newStartDay) else containing
    }
}
