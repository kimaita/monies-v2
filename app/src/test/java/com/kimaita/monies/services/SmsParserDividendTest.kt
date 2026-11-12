package com.kimaita.monies.services

import com.kimaita.monies.data.BalanceExtractor
import com.kimaita.monies.data.database.models.getDefaultTransactionTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SmsParserDividendTest {

    // Give each default type a distinct id, as the database would, so we can tell which one matched.
    private val types = getDefaultTransactionTypes().mapIndexed { i, t -> t.copy(id = i + 1) }
    private val dividendId = types.first { it.name == "dividend" }.id

    private fun parseOne(body: String) = SmsParser()
        .parse(listOf(SmsParser.SmsMessage(body, 1L, "MPESA", 0L)), types)
        .single()

    @Test
    fun interimDividend_isParsedAsDividend() {
        val body = "TCQ000AAA1 Confirmed.  You have received a dividend payment from SAFARICOM PLC " +
            "INTERIM DIVIDEND 2025 of Ksh1,234.56 on 26/3/25  at 8:28 AM. Your new Account balance is Ksh2,000.00."

        val (txn, subject) = parseOne(body)

        assertEquals(dividendId, txn.transactionType)
        assertEquals("TCQ000AAA1", txn.transactionCode)
        assertEquals(1234.56, txn.amount, 0.001)
        assertEquals(0.0, txn.cost!!, 0.001)
        assertEquals(LocalDateTime.of(2025, 3, 26, 8, 28), txn.transactionTime)
        assertEquals("SAFARICOM PLC", subject?.name)
        assertEquals(2000.00, BalanceExtractor.extract(txn.message, types.first { it.id == dividendId }.pattern)!!, 0.001)
    }

    @Test
    fun finalDividend_groupsUnderSamePayer() {
        val body = "TCQ000BBB2 Confirmed.  You have received a dividend payment from SAFARICOM PLC " +
            "FINAL DIVIDEND 2024 of Ksh104.50 on 1/9/24  at 9:39 PM. Your new Account balance is Ksh100,359.84."

        val (txn, subject) = parseOne(body)

        assertEquals(dividendId, txn.transactionType)
        assertEquals(104.50, txn.amount, 0.001)
        assertEquals(LocalDateTime.of(2024, 9, 1, 21, 39), txn.transactionTime)
        assertEquals("SAFARICOM PLC", subject?.name)
    }

    @Test
    fun ordinaryReceive_isNotMistakenForDividend() {
        val body = "TCQ000CCC3 Confirmed.You have received Ksh500.00 from JANE DOE 0712345678 on 2/4/25 " +
            "at 10:15 AM  New M-PESA balance is Ksh3,500.00."

        val (txn, _) = parseOne(body)

        assertEquals(types.first { it.name == "received.person" }.id, txn.transactionType)
    }
}
