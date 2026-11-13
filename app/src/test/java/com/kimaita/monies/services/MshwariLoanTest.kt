package com.kimaita.monies.services

import com.kimaita.monies.data.database.models.getDefaultTransactionTypes
import com.kimaita.monies.services.MshwariLoanEvent.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MshwariLoanTest {
    private var n = 0
    private fun ev(kind: Kind, balance: Double? = null) =
        MshwariLoanEvent(kind, LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(n++.toLong()), balance)

    @Test
    fun noDataIsUnknown() = assertNull(computeMshwariLoan(emptyList()))

    @Test
    fun latestStatedFigure() {
        assertEquals(80.0, computeMshwariLoan(listOf(ev(Kind.STATED, 300.0), ev(Kind.STATED, 80.0)))!!, 0.001)
    }

    @Test
    fun laterLoanMakesTheFigureUnknown() {
        assertNull(computeMshwariLoan(listOf(ev(Kind.STATED, 300.0), ev(Kind.UNKNOWN))))
    }

    @Test
    fun repaymentAfterALoanStatesItAgain() {
        assertEquals(40.0, computeMshwariLoan(listOf(ev(Kind.UNKNOWN), ev(Kind.STATED, 40.0)))!!, 0.001)
    }

    @Test
    fun fullRepaymentBringsItToZero() {
        assertEquals(0.0, computeMshwariLoan(listOf(ev(Kind.STATED, 300.0), ev(Kind.CLEARED)))!!, 0.001)
    }

    @Test
    fun eventsAreReplayedInTimeOrder() {
        val first = ev(Kind.STATED, 300.0)
        val second = ev(Kind.CLEARED)
        assertEquals(0.0, computeMshwariLoan(listOf(second, first))!!, 0.001)
    }

    @Test
    fun shortRepaymentStatesTheLoanInItsOwnGroup() {
        val type = getDefaultTransactionTypes().first { it.name == "mshwari.loan_repayment" }
        val body = "AB12CD34EF Confirmed. Loan of Ksh100.00 repaid from M-PESA on 1/2/26 at 3:04 PM. Loan balance is Ksh1,234.50.  Transaction cost Kshs 0.00"
        val m = Regex(type.pattern!!).find(body)!!
        assertEquals("1,234.50", m.groups["LoanBal"]!!.value)
        assertEquals(body, m.groups["Body"]!!.value)
    }
}
