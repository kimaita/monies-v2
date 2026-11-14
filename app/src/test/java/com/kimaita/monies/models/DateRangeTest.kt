package com.kimaita.monies.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DateRangeTest {

    private fun range(filter: TimeFilter, day: String) = getDatesForFilter(filter, LocalDate.parse(day))

    @Test
    fun dayIsTodayUpToTomorrow() {
        assertEquals("2026-11-14" to "2026-11-15", range(TimeFilter.DAY, "2026-11-14"))
    }

    @Test
    fun weekCoversTheLastSevenDaysIncludingToday() {
        assertEquals("2026-11-08" to "2026-11-15", range(TimeFilter.WEEK, "2026-11-14"))
    }

    @Test
    fun monthBoundaryRollsDayAndMonth() {
        assertEquals("2026-12-01" to "2026-12-02", range(TimeFilter.DAY, "2026-12-01"))
        assertEquals("2026-11-30" to "2026-12-01", range(TimeFilter.DAY, "2026-11-30"))
        assertEquals("2026-02-28" to "2026-03-01", range(TimeFilter.DAY, "2026-02-28"))
        assertEquals("2026-01-25" to "2026-02-01", range(TimeFilter.WEEK, "2026-01-31"))
    }

    @Test
    fun monthFilterClampsToTheShorterMonth() {
        assertEquals("2026-02-28" to "2026-04-01", range(TimeFilter.MONTH, "2026-03-31"))
        assertEquals("2026-12-14" to "2027-01-15", range(TimeFilter.MONTH, "2027-01-14"))
    }

    @Test
    fun yearBoundaryRollsBackIntoThePreviousYear() {
        assertEquals("2027-01-01" to "2027-01-02", range(TimeFilter.DAY, "2027-01-01"))
        assertEquals("2026-12-25" to "2027-01-01", range(TimeFilter.WEEK, "2026-12-31"))
        assertEquals("2026-07-01", range(TimeFilter.SIX_MONTHS, "2027-01-01").first)
    }

    @Test
    fun yearToDateStartsOnJanuaryFirstOfTheGivenYear() {
        assertEquals("2026-01-01" to "2027-01-01", range(TimeFilter.YTD, "2026-12-31"))
        assertEquals("2027-01-01" to "2027-01-02", range(TimeFilter.YTD, "2027-01-01"))
    }

    @Test
    fun allHasNoBoundsAndCustomRangeIgnoresToday() {
        assertEquals(null to null, range(TimeFilter.ALL, "2026-11-14"))
        val custom = TimeFilter.CustomRange(
            LocalDateTime.parse("2026-03-01T00:00:00"), LocalDateTime.parse("2026-03-31T23:59:59")
        )
        assertEquals(
            "2026-03-01T00:00:00" to "2026-03-31T23:59:59",
            getDatesForFilter(custom, LocalDate.parse("2030-01-01")),
        )
    }
}
