package com.kimaita.monies.data

import com.kimaita.monies.data.dao.dtos.CategoryBudget
import com.kimaita.monies.data.dao.dtos.LineSummary
import com.kimaita.monies.data.dao.dtos.accountLines
import com.kimaita.monies.models.CategoryApplyMode
import com.kimaita.monies.models.applyToAllOfSubject
import com.kimaita.monies.models.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {

    @Test
    fun themeModeMapsStoredValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("bogus"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
    }

    @Test
    fun applyModeDecidesWhetherToAsk() {
        assertEquals(CategoryApplyMode.ASK, CategoryApplyMode.fromKey(null))
        assertNull(applyToAllOfSubject(CategoryApplyMode.ASK, hasSubject = true))
        assertEquals(false, applyToAllOfSubject(CategoryApplyMode.ONE, hasSubject = true))
        assertEquals(true, applyToAllOfSubject(CategoryApplyMode.ALL, hasSubject = true))
        // No subject: never ask, only the one transaction can change.
        assertEquals(false, applyToAllOfSubject(CategoryApplyMode.ASK, hasSubject = false))
        assertEquals(false, applyToAllOfSubject(CategoryApplyMode.ALL, hasSubject = false))
    }

    @Test
    fun budgetProgressIsClamped() {
        assertEquals(0.5f, CategoryBudget(1, "Fuel", 1000.0, 500.0).progress, 0.001f)
        val over = CategoryBudget(1, "Fuel", 1000.0, 1500.0)
        assertEquals(1f, over.progress, 0.001f)
        assertTrue(over.isOver)
        assertEquals(0f, CategoryBudget(1, "Fuel", 0.0, 10.0).progress, 0.001f)
        assertFalse(CategoryBudget(1, "Fuel", 1000.0, 1000.0).isOver)
    }

    @Test
    fun activeLineKeepsItsNumberWhenAnEarlierLineIsDormant() {
        val all = listOf(
            com.kimaita.monies.data.dao.dtos.LineBalance(1, "MPESA", null, null),
            com.kimaita.monies.data.dao.dtos.LineBalance(2, "MPESA", null, null),
        )
        val names = com.kimaita.monies.data.dao.dtos.lineNames(all)
        // Line 2 is the only active one, but its name is still numbered across all lines.
        assertEquals("M-PESA", names[1])
        assertEquals("M-PESA 2", names[2])
        val summaries = listOf(
            LineSummary(1, "MPESA", null, 300, "2026-11-01T09:00:00"),
            LineSummary(2, "MPESA", null, 40, "2025-01-10T09:00:00"),
        )
        val lines = accountLines(summaries)
        assertEquals(listOf("M-PESA", "M-PESA 2"), lines.map { it.name })
        assertEquals(listOf(false, true), lines.map { it.dormant })
    }

    @Test
    fun accountLinesMarkDormantAndNumberProviders() {
        val lines = listOf(
            LineSummary(1, "MPESA", null, 40, "2025-01-10T09:00:00"),
            LineSummary(2, "MPESA", null, 300, "2026-11-01T09:00:00"),
            LineSummary(3, "AIRTEL", "Work", 5, "2026-10-01T09:00:00"),
            LineSummary(4, "MPESA", null, 0, null),
        )
        val result = accountLines(lines)
        assertEquals(listOf(true, false, false, false), result.map { it.dormant })
        assertEquals("Work", result[2].name)
        assertEquals(setOf("M-PESA", "M-PESA 2", "M-PESA 3"), setOf(result[0].name, result[1].name, result[3].name))
        assertEquals(40, result[0].count)
    }
}
