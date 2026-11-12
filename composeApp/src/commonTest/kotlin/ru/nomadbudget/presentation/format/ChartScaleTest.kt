package ru.nomadbudget.presentation.format

import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.presentation.charts.NiceScale
import ru.nomadbudget.presentation.charts.niceScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartScaleTest {

    @Test
    fun capitalRangeGetsRoundTicks() {
        assertEquals(NiceScale(3_000_000.0, 5_000_000.0, 500_000.0), niceScale(listOf(3_210_000.0, 4_832_000.0), includeZero = false))
    }

    @Test
    fun columnsStartFromZero() {
        assertEquals(NiceScale(0.0, 250_000.0, 50_000.0), niceScale(listOf(80_000.0, 231_000.0), includeZero = true))
    }

    @Test
    fun negativeValuesKeepZeroInside() {
        val scale = niceScale(listOf(-62_758.0, 80_000.0), includeZero = true)
        assertEquals(50_000.0, scale?.step)
        assertEquals(-100_000.0, scale?.min)
        assertEquals(100_000.0, scale?.max)
    }

    @Test
    fun smallRatesGetFractionalStep() {
        val scale = niceScale(listOf(0.00305, 0.00342), includeZero = false)
        assertEquals(0.0001, scale?.step ?: 0.0, 1e-12)
    }

    @Test
    fun flatSeriesDoesNotCollapse() {
        val scale = niceScale(listOf(100.0, 100.0), includeZero = false)
        assertTrue(scale != null && scale.max > scale.min)
    }

    @Test
    fun emptySeriesHasNoScale() {
        assertNull(niceScale(emptyList(), includeZero = true))
    }

    @Test
    fun axisLabelsAreCompact() {
        assertEquals("4,5 млн", MoneyFormat.formatAxis(4_500_000.0))
        assertEquals("5 млн", MoneyFormat.formatAxis(5_000_000.0))
        assertEquals("250 тыс", MoneyFormat.formatAxis(250_000.0))
        assertEquals("−50 тыс", MoneyFormat.formatAxis(-50_000.0))
        assertEquals("0", MoneyFormat.formatAxis(0.0))
    }

    @Test
    fun tooltipShowsExactRubles() {
        assertEquals("4 832 105 ₽", MoneyFormat.formatWhole(4_832_105.4, currency = Currency.RUB))
    }

    @Test
    fun smallRatesAreQuotedPerThousand() {
        assertEquals(1_000L, MoneyFormat.quoteUnit(0.0032))
        assertEquals(1L, MoneyFormat.quoteUnit(314.0))
        assertEquals(10L, MoneyFormat.quoteUnit(0.5))
        assertEquals("1\u00A0000\u00A0RUB", MoneyFormat.unitLabel(1_000L, ru.nomadbudget.domain.model.Currency.RUB))
    }

    @Test
    fun changesArePercentWithPlainZero() {
        assertEquals("0", MoneyFormat.formatChange(0.00004))
        assertEquals("−0,14", MoneyFormat.formatChange(-0.00136))
        assertEquals("+1,50", MoneyFormat.formatChange(0.015))
    }

    @Test
    fun numbersKeepRequestedDecimals() {
        assertEquals("1,095", MoneyFormat.formatNumber(1.095, 3))
        assertEquals("−0,50", MoneyFormat.formatNumber(-0.5, 2))
        assertEquals("1\u00A0250", MoneyFormat.formatNumber(1250.0, 0))
    }
}
