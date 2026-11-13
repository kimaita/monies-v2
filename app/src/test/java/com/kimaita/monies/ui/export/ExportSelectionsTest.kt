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
}
