package com.kimaita.monies.data

import com.kimaita.monies.data.database.models.UserAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectiveSelectionTest {
    private val accounts = listOf(
        UserAccount(id = 1, provider = "MPESA", subscriptionId = 1),
        UserAccount(id = 2, provider = "MPESA", subscriptionId = 2, mergedInto = 1),
        UserAccount(id = 3, provider = "AIRTEL", subscriptionId = 1),
    )

    @Test
    fun rootStaysSelected() = assertEquals(1, effectiveSelection(1, accounts, setOf(1, 3)))

    @Test
    fun mergedRowSelectsItsRoot() = assertEquals(1, effectiveSelection(2, accounts, setOf(1, 3)))

    @Test
    fun unknownDormantOrNoneSelectsAll() {
        assertNull(effectiveSelection(9, accounts, setOf(1, 3)))
        assertNull(effectiveSelection(3, accounts, setOf(1)))
        assertNull(effectiveSelection(null, accounts, setOf(1, 3)))
    }
}
