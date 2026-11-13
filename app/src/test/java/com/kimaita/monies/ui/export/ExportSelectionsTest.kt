package com.kimaita.monies.ui.export

import com.kimaita.monies.models.TimeFilter
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.services.TypeGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ExportSelectionsTest {
    private val base = ExportSelections(format = ExportFormat.CSV)

    @Test
    fun allTimeLeavesTheDatesOpenAndCopiesTheRest() {
        val sel = TransactionFilters(
            natureFilter = false,
            accountFilter = 3,
            searchQuery = "  jane  ",
            typeFilters = listOf(2, 5),
            categoryFilters = listOf(7),
        ).toExportSelections(base)

        assertNull(sel.startDate)
        assertNull(sel.endDate)
        assertEquals(false, sel.nature)
        assertEquals(3, sel.accountId)
        assertEquals(setOf(2, 5), sel.typeIds)
        assertEquals(setOf(7), sel.categoryIds)
        assertEquals("jane", sel.search)
        assertEquals(ExportFormat.CSV, sel.format)
    }

    @Test
    fun customRangeEndsOnItsOwnLastDay() {
        val sel = TransactionFilters(
            timeFilter = TimeFilter.CustomRange(
                LocalDateTime.of(2026, 11, 1, 0, 0),
                LocalDateTime.of(2026, 11, 13, 23, 59, 59),
            )
        ).toExportSelections(base)

        assertEquals(LocalDate.of(2026, 11, 1), sel.startDate)
        assertEquals(LocalDate.of(2026, 11, 13), sel.endDate)
    }

    @Test
    fun searchCountsAsAFilterAndGoesToTheExport() {
        val sel = base.copy(search = " 1,500 ")
        assertEquals(1, sel.activeFilterCount())
        assertEquals("1,500", sel.toExportFilters().search)
        assertNull(base.toExportFilters().search)
    }

    @Test
    fun typesSharingADisplayNameCountOnce() {
        val groups = listOf(TypeGroup("Airtime", listOf(2, 3)), TypeGroup("Sent", listOf(1)))
        val sel = base.copy(typeIds = setOf(2, 3, 1), nature = false)
        assertEquals(3, sel.activeFilterCount(groups))
        assertEquals(1, base.copy(typeIds = setOf(2, 3)).activeFilterCount(groups))
    }

    @Test
    fun theAccountIsNotACountedFilter() {
        val sel = base.copy(accountId = 3, nature = true)
        assertEquals(1, sel.activeFilterCount())
        assertEquals(3, sel.toExportFilters().account)
    }

    @Test
    fun clearFiltersKeepsAccountFormatAndDates() {
        val start = LocalDate.of(2026, 1, 1)
        val sel = base.copy(accountId = 3, startDate = start, nature = true, search = "x")
        // Mirrors ExportViewModel.clearFilters.
        val cleared = sel.copy(nature = null, search = "")
        assertEquals(0, cleared.activeFilterCount())
        assertEquals(3, cleared.accountId)
        assertEquals(start, cleared.startDate)
    }

    private fun DateBand.on(y: Int, m: Int, d: Int) = range(LocalDate.of(y, m, d))
    private fun date(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Test
    fun bandsInTheMiddleOfAYear() {
        val today = date(2026, 11, 14)
        assertEquals(null to null, DateBand.ALL_TIME.range(today))
        assertEquals(date(2026, 11, 1) to today, DateBand.THIS_MONTH.range(today))
        assertEquals(date(2026, 10, 1) to date(2026, 10, 31), DateBand.LAST_MONTH.range(today))
        assertEquals(date(2026, 8, 15) to today, DateBand.LAST_3_MONTHS.range(today))
        assertEquals(date(2026, 1, 1) to today, DateBand.THIS_YEAR.range(today))
        assertEquals(date(2025, 1, 1) to date(2025, 12, 31), DateBand.LAST_YEAR.range(today))
    }

    @Test
    fun lastMonthInJanuaryIsDecember() {
        assertEquals(date(2025, 12, 1) to date(2025, 12, 31), DateBand.LAST_MONTH.on(2026, 1, 15))
        assertEquals(date(2026, 1, 1) to date(2026, 1, 15), DateBand.THIS_MONTH.on(2026, 1, 15))
        assertEquals(date(2025, 1, 1) to date(2025, 12, 31), DateBand.LAST_YEAR.on(2026, 1, 15))
    }

    @Test
    fun lastThreeMonthsFromTheEndOfAMonth() {
        assertEquals(date(2026, 1, 1) to date(2026, 3, 31), DateBand.LAST_3_MONTHS.on(2026, 3, 31))
        assertEquals(date(2026, 3, 1) to date(2026, 5, 31), DateBand.LAST_3_MONTHS.on(2026, 5, 31))
    }

    @Test
    fun leapDays() {
        assertEquals(date(2028, 2, 1) to date(2028, 2, 29), DateBand.LAST_MONTH.on(2028, 3, 10))
        assertEquals(date(2027, 11, 30) to date(2028, 2, 29), DateBand.LAST_3_MONTHS.on(2028, 2, 29))
        assertEquals(date(2028, 1, 1) to date(2028, 12, 31), DateBand.LAST_YEAR.on(2029, 2, 28))
        assertEquals(date(2027, 1, 1) to date(2027, 12, 31), DateBand.LAST_YEAR.on(2028, 2, 29))
    }

    @Test
    fun aRangeEqualToABandSelectsIt() {
        val today = date(2026, 11, 14)
        val (start, end) = DateBand.THIS_MONTH.range(today)
        assertEquals(DateBand.THIS_MONTH, base.copy(startDate = start, endDate = end).band(today))
        assertEquals(DateBand.ALL_TIME, base.band(today))
        DateBand.entries.forEach { b ->
            val (s, e) = b.range(today)
            assertEquals(b, base.copy(startDate = s, endDate = e).band(today))
        }
    }

    @Test
    fun anyOtherRangeIsCustom() {
        val today = date(2026, 11, 14)
        assertNull(base.copy(startDate = date(2026, 1, 3), endDate = date(2026, 2, 14)).band(today))
        // A rolling month that a Transactions handoff can bring is not a calendar band.
        assertNull(base.copy(startDate = date(2026, 10, 15), endDate = today).band(today))
        assertNull(base.copy(startDate = date(2026, 11, 1)).band(today))
    }

    @Test
    fun customRangesReadShort() {
        val today = date(2026, 11, 14)
        assertEquals("3 Jan – 14 Feb 2025", rangeText(date(2025, 1, 3), date(2025, 2, 14), today))
        assertEquals("3 Jan – 14 Feb", rangeText(date(2026, 1, 3), date(2026, 2, 14), today))
        assertEquals("28 Dec 2025 – 3 Jan 2026", rangeText(date(2025, 12, 28), date(2026, 1, 3), today))
        assertEquals("5 Mar", rangeText(date(2026, 3, 5), date(2026, 3, 5), today))
        assertEquals("From 5 Mar", rangeText(date(2026, 3, 5), null, today))
        assertEquals("Last month", base.copy(startDate = date(2026, 10, 1), endDate = date(2026, 10, 31)).dateLabel(today))
        assertEquals("1 Oct – 30 Oct", base.copy(startDate = date(2026, 10, 1), endDate = date(2026, 10, 30)).dateLabel(today))
    }
}
