package com.kimaita.monies.services

import com.kimaita.monies.services.FulizaEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class FulizaTest {
    private var n = 0
    private fun ev(kind: Kind, amount: Double = 0.0, total: Double? = null, available: Double? = null) =
        FulizaEvent(kind, LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(n++.toLong()), amount, total, available)

    @Test
    fun ownersSequenceEndsWithPartialRepayment() {
        val state = computeFuliza(
            listOf(
                ev(Kind.LOAN, 500.0, total = 500.0),
                ev(Kind.LOAN, 200.0, total = 700.0),
                ev(Kind.FULL, 700.0, available = 3000.0),
                ev(Kind.LOAN, 1000.0, total = 1000.0),
                ev(Kind.LOAN, 1313.64, total = 2313.64),
                ev(Kind.PARTIAL, 529.0, available = 1215.36),
            )
        )
        assertEquals(1784.64, state.outstanding!!, 0.001)
        assertEquals(1215.36, state.available!!, 0.001)
    }

    @Test
    fun loanAfterFullRepaymentLowersAvailable() {
        val state = computeFuliza(
            listOf(ev(Kind.FULL, 100.0, available = 3000.0), ev(Kind.LOAN, 400.0, total = 400.0))
        )
        assertEquals(400.0, state.outstanding!!, 0.001)
        assertEquals(2600.0, state.available!!, 0.001)
    }

    @Test
    fun eventsAreReplayedInTimeOrderWhateverTheInputOrder() {
        val events = listOf(ev(Kind.FULL, 100.0, available = 3000.0), ev(Kind.LOAN, 400.0, total = 400.0))
        assertEquals(computeFuliza(events), computeFuliza(events.reversed()))
    }

    @Test
    fun noEventsMeansUnknown() {
        val state = computeFuliza(emptyList())
        assertNull(state.outstanding)
        assertNull(state.available)
    }
}
