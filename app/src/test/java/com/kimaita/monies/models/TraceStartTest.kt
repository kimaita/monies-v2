package com.kimaita.monies.models

import com.kimaita.monies.data.database.DateTimeConverters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TraceStartTest {

    @Test
    fun startIsTheTextRoomStores() {
        // The anchor's own time must compare equal to the stored text, seconds and fractions included.
        val cases = listOf(
            "2026-11-03T16:21" to "2026-11-03T16:21:00",
            "2026-11-03T16:21:05" to "2026-11-03T16:21:05",
            "2026-11-03T16:21:05.123" to "2026-11-03T16:21:05.123",
        )
        for ((parsed, stored) in cases) {
            val time = LocalDateTime.parse(parsed)
            assertEquals(DateTimeConverters.fromLocalDateTime(time), TraceStart(time, 7).start)
            assertEquals(stored, TraceStart(time).start)
        }
    }

    @Test
    fun navigationArgumentsRoundTrip() {
        val start = TraceStart(LocalDateTime.parse("2026-11-03T16:21"), 42)
        assertEquals(start, traceStartOf(start.start, start.afterId))
    }

    @Test
    fun aDateTimeStartHasNoAnchor() {
        assertEquals(
            TraceStart(LocalDateTime.parse("2026-11-03T00:00")),
            traceStartOf("2026-11-03T00:00", -1L)
        )
        assertEquals(TraceStart(LocalDateTime.parse("2026-11-03T00:00")), traceStartOf("2026-11-03T00:00", null))
    }

    @Test
    fun missingOrBrokenArgumentsMeanNoTrace() {
        assertNull(traceStartOf(null, 42))
        assertNull(traceStartOf("", -1L))
        assertNull(traceStartOf("yesterday", 42))
    }

    @Test
    fun aTraceReplacesTheTimeFilterAndHasNoEnd() {
        val today = LocalDate.parse("2026-11-16")
        val start = TraceStart(LocalDateTime.parse("2026-11-03T16:21"), 42)
        val filters = TransactionFilters(timeFilter = TimeFilter.WEEK, since = start)
        assertEquals("2026-11-03T16:21:00" to null, filters.listDates(today))
        assertEquals(
            getDatesForFilter(TimeFilter.WEEK, today),
            filters.copy(since = null).listDates(today)
        )
    }
}
