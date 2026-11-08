package ru.nomadbudget.data.mapper

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import ru.nomadbudget.domain.model.Period

object PeriodMapper {

    fun title(period: Period): String = "${shortDayMonth(period.start)} → ${shortDayMonth(period.endExclusive)}"

    private fun shortDayMonth(date: LocalDate): String =
        "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}"
}
