package com.kimaita.monies.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test
    fun ftsQuerySplitsStripsAndPrefixes() {
        assertEquals("Jane* Doe*", ftsQueryOf("  Jane   Doe "))
        assertEquals("ACME* token*", ftsQueryOf("ACME, token!"))
        assertEquals("0700000001*", ftsQueryOf("0700-000001"))
    }

    @Test
    fun emptyOrSymbolOnlyQueryHasNoFilter() {
        assertNull(ftsQueryOf(""))
        assertNull(ftsQueryOf("   "))
        assertNull(ftsQueryOf("!? -"))
    }

    @Test
    fun numericQueryIsAnAmountPrefix() {
        assertEquals("1500", amountQueryOf("1500"))
        assertEquals("2500.5", amountQueryOf(" 2,500.5 "))
        assertNull(amountQueryOf("jane"))
        assertNull(amountQueryOf(""))
    }
}
