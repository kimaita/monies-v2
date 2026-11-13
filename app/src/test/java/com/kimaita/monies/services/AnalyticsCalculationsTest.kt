package com.kimaita.monies.services

import com.kimaita.monies.data.dao.dtos.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsCalculationsTest {

    private fun day(date: String, total: Double) = DailyTotal(date, total, 1)

    @Test
    fun monthBoundaries_firstOfMonthAndYearRollover() {
        // Today is 1 Jan: last month is December, and 31 Dec spending must not leak into January.
        val pace = computePace(
            listOf(day("2025-12-15", 100.0), day("2025-12-31", 50.0), day("2026-01-01", 30.0)),
            LocalDate.of(2026, 1, 1),
        )
        assertEquals(listOf(30.0), pace.thisMonth)
        assertEquals(31, pace.lastMonth.size)
        assertEquals(150.0, pace.lastMonth.last(), 0.0)
        assertEquals(30.0, pace.spent, 0.0)
        // Compared at day 1: last month had 0 by then, so there is no percentage.
        assertNull(pace.changePct)
    }

    @Test
    fun monthBoundaries_leapFebruaryIsLastMonth() {
        val pace = computePace(listOf(day("2024-02-29", 10.0)), LocalDate.of(2024, 3, 2))
        assertEquals(29, pace.lastMonth.size)
        assertEquals(2, pace.thisMonth.size)
    }

    @Test
    fun thirtyOneDayMonthAgainstThirtyDayMonth() {
        // Today is 31 May; April has 30 days, so the comparison is made at day 30.
        val pace = computePace(
            listOf(
                day("2026-04-10", 100.0), day("2026-04-30", 100.0),   // April: 200 by day 30
                day("2026-05-05", 150.0), day("2026-05-30", 150.0), day("2026-05-31", 40.0),
            ),
            LocalDate.of(2026, 5, 31),
        )
        assertEquals(31, pace.thisMonth.size)
        assertEquals(30, pace.lastMonth.size)
        assertEquals(340.0, pace.spent, 0.0)
        // this month at day 30 = 300 vs 200 last month -> +50%
        assertEquals(50.0, pace.changePct!!, 1e-9)
    }

    @Test
    fun emptyLastMonth_hasNoComparison() {
        val pace = computePace(listOf(day("2026-05-02", 25.0)), LocalDate.of(2026, 5, 3))
        assertTrue(pace.lastMonth.isEmpty())
        assertNull(pace.changePct)
        assertEquals(listOf(0.0, 25.0, 25.0), pace.thisMonth)
        assertEquals(25.0, pace.spent, 0.0)
    }
}
