package com.kimaita.monies.services

import com.kimaita.monies.data.database.models.getDefaultTransactionTypes
import com.kimaita.monies.models.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SmsParserSamplesTest {

    // Give each default type a distinct id, as the database would, so we can tell which one matched.
    private val types = getDefaultTransactionTypes().mapIndexed { i, t -> t.copy(id = i + 1) }
    private val typeIdByName = types.associate { it.name to it.id }
    private val smsTimestamp = 1_700_000_000_000L

    private fun parseOne(body: String, sender: String) = SmsParser()
        .parse(listOf(SmsParser.SmsMessage(body, 1L, sender, smsTimestamp)), types)

    private fun check(sample: Sample) {
        val result = parseOne(sample.body, sample.sender)
        assertEquals("no match for a ${sample.type} sample", 1, result.size)
        val (txn, subject) = result.single()
        assertEquals(sample.type, types.first { it.id == txn.transactionType }.name)
        assertEquals(sample.amount, txn.amount, 0.001)
        assertEquals(sample.cost, txn.cost!!, 0.001)
        if (sample.balance == null) assertNull(txn.balance) else assertEquals(sample.balance, txn.balance!!, 0.001)
        assertEquals(sample.name, subject?.name)
        assertEquals(sample.number, subject?.number?.takeIf { it.isNotEmpty() })
        val expectedTime = sample.dateTime
            ?: LocalDateTime.ofInstant(Instant.ofEpochMilli(smsTimestamp), ZoneId.systemDefault())
        assertEquals(expectedTime, txn.transactionTime)
    }

    @Test
    fun everyMpesaSampleParses() = checkAll(mpesaSamples)

    @Test
    fun everyAirtelSampleParses() = checkAll(airtelSamples)

    private fun checkAll(samples: List<Sample>) {
        samples.forEachIndexed { i, sample ->
            try {
                check(sample)
            } catch (e: AssertionError) {
                throw AssertionError("sample #$i (${sample.type}): ${e.message}", e)
            }
        }
    }

    @Test
    fun everyDefaultMpesaTypeHasASample() {
        val covered = mpesaSamples.map { it.type }.toSet() + "airtime.other" // no sample for this one
        assertEquals(emptySet<String>(), types.filter { it.provider == Provider.MPESA.name }.map { it.name }.toSet() - covered)
    }

    @Test
    fun everyDefaultAirtelTypeHasASample() {
        val covered = airtelSamples.map { it.type }.toSet()
        assertEquals(emptySet<String>(), types.filter { it.provider == Provider.AIRTEL.name }.map { it.name }.toSet() - covered)
    }

    @Test
    fun airtelMessagesAreNotMatchedForOtherSenders() {
        assertTrue(parseOne(airtelSamples.first { it.type == "airtel.bundle" }.body, "MPESA").isEmpty())
    }

    @Test
    fun transferTypes() {
        val transfers = types.filter { it.isTransfer }.map { it.name }.toSet()
        assertTrue(transfers.containsAll(listOf("mshwari.loan", "mshwari.loan_repayment", "fuliza.loan", "fuliza.final_pay")))
        assertTrue(transfers.none { !it.startsWith("mshwari.") && !it.startsWith("fuliza.") })
        assertTrue("reversal" !in transfers && "cash_withdrawal.atm" !in transfers)
    }

    @Test
    fun reversalsAreSplitByDirection() {
        val byName = types.associateBy { it.name }
        assertTrue(byName.getValue("reversal").isInc)
        assertTrue(!byName.getValue("reversal.debit").isInc)
        assertTrue(!byName.getValue("reversal.debit").isTransfer)
    }

    @Test
    fun unknownSenderIsNeverMatched() {
        val body = mpesaSamples.first { it.type == "till" }.body
        assertTrue(parseOne(body, "SOMEONE").isEmpty())
        assertTrue(parseOne(body, "").isEmpty())
    }

    @Test
    fun senderCaseDoesNotMatter() {
        val body = mpesaSamples.first { it.type == "till" }.body
        assertEquals(1, parseOne(body, "mpesa").size)
        assertEquals(Provider.MPESA, Provider.forSender("m-shwari"))
        assertEquals(Provider.AIRTEL, Provider.forSender("AirtelMoney"))
        assertNull(Provider.forSender("someone"))
    }

    @Test
    fun typesFromAnotherProviderAreNotTried() {
        val body = mpesaSamples.first { it.type == "till" }.body
        val other = types.map { it.copy(provider = "OTHER") }
        val result = SmsParser().parse(listOf(SmsParser.SmsMessage(body, 1L, "MPESA", smsTimestamp)), other)
        assertTrue(result.isEmpty())
        assertNotNull(typeIdByName["till"])
    }
}
