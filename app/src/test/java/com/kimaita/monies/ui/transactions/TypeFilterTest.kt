package com.kimaita.monies.ui.transactions

import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.TransactionFilters
import com.kimaita.monies.services.typeGroups
import org.junit.Assert.assertEquals
import org.junit.Test

class TypeFilterTest {
    private val received = TransactionType(id = 1, name = "received", isInc = true)
    private val deposit = TransactionType(id = 2, name = "deposit", isInc = true)
    private val sent = TransactionType(id = 3, name = "sent", isInc = false)
    private val paybill = TransactionType(id = 4, name = "paybill", isInc = false)
    private val all = listOf(received, deposit, sent, paybill)

    @Test
    fun noDirectionOffersEveryType() {
        assertEquals(all, typesFor(null, all))
    }

    @Test
    fun directionOffersOnlyTypesOfThatDirection() {
        assertEquals(listOf(received, deposit), typesFor(true, all))
        assertEquals(listOf(sent, paybill), typesFor(false, all))
    }

    @Test
    fun pickingADirectionDropsSelectedTypesThatNoLongerFit() {
        val filters = TransactionFilters(typeFilters = listOf(1, 3, 4))
        assertEquals(listOf(1), filters.withNature(true, all).typeFilters)
        assertEquals(listOf(3, 4), filters.withNature(false, all).typeFilters)
        assertEquals(true, filters.withNature(true, all).natureFilter)
    }

    @Test
    fun clearingTheDirectionKeepsTheSelectedTypes() {
        val filters = TransactionFilters(natureFilter = false, typeFilters = listOf(3, 4))
        val cleared = filters.withNature(null, all)
        assertEquals(null, cleared.natureFilter)
        assertEquals(listOf(3, 4), cleared.typeFilters)
        // even before the types have loaded
        assertEquals(listOf(3, 4), filters.withNature(null, emptyList()).typeFilters)
    }

    @Test
    fun otherFiltersAreUntouched() {
        val filters = TransactionFilters(
            searchQuery = "jane", categoryFilters = listOf(7), typeFilters = listOf(1)
        )
        val result = filters.withNature(false, all)
        assertEquals("jane", result.searchQuery)
        assertEquals(listOf(7), result.categoryFilters)
        assertEquals(emptyList<Int>(), result.typeFilters)
    }

    @Test
    fun directionNarrowsBeforeTypesAreGroupedByName() {
        val two = TransactionType(id = 5, name = "received.bank", isInc = true, displayName = "Received")
        val one = TransactionType(id = 6, name = "received.person", isInc = true, displayName = "Received")
        val out = TransactionType(id = 7, name = "sent", isInc = false, displayName = "Sent")
        val types = listOf(two, one, out)
        assertEquals(listOf("Received", "Sent"), typeGroups(typesFor(null, types)).map { it.label })
        assertEquals(listOf(listOf(5, 6)), typeGroups(typesFor(true, types)).map { it.ids })
        assertEquals(listOf(listOf(7)), typeGroups(typesFor(false, types)).map { it.ids })
    }
}
