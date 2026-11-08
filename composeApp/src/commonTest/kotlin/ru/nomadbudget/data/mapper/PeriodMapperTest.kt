package ru.nomadbudget.data.mapper

import kotlinx.datetime.LocalDate
import ru.nomadbudget.domain.model.SalaryCycle
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodMapperTest {

    @Test
    fun title_isStartArrowEnd() {
        val period = SalaryCycle.periodContaining(LocalDate(2026, 9, 5), startDay = 5)
        assertEquals("05.09 → 05.10", PeriodMapper.title(period))
    }
}
