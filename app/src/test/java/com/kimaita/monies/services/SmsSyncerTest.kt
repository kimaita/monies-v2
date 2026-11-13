package com.kimaita.monies.services

import com.kimaita.monies.data.SmsSource
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.models.UnparsedMessage
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.data.database.models.getDefaultTransactionTypes
import com.kimaita.monies.models.Provider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSyncerTest {

    private class FakeInbox(var messages: List<SmsParser.SmsMessage>) : SmsSource {
        // Mirrors the real query: case-insensitive sender match, keyset on _id.
        override suspend fun readBatch(senders: Collection<String>, afterId: Long, limit: Int) =
            messages.filter { m -> (m.id ?: 0) > afterId && senders.any { it.equals(m.originatingAddress, true) } }
                .sortedBy { it.id }.take(limit)

        override suspend fun count(senders: Collection<String>, afterId: Long) =
            readBatch(senders, afterId, Int.MAX_VALUE).size

        override suspend fun maxId() = messages.maxOfOrNull { it.id ?: 0 } ?: 0L
    }

    private class FakeStore(types: List<TransactionType>) : SyncStore {
        var cursor = 0L
        val types = types.mapIndexed { i, t -> t.copy(id = i + 1) }
        val accounts = mutableListOf<UserAccount>()
        val transactions = mutableListOf<Transaction>()
        val unparsed = mutableListOf<UnparsedMessage>()
        val subjects = mutableMapOf<Pair<String, String>, Long>()
        val subjectCategories = mutableMapOf<Long, Int?>()

        override suspend fun cursor() = cursor
        override suspend fun setCursor(id: Long) { cursor = id }
        override suspend fun transactionTypes() = types
        override suspend fun accountId(provider: String, subscriptionId: Int) =
            accounts.firstOrNull { it.provider == provider && it.subscriptionId == subscriptionId }?.id

        override suspend fun createAccount(account: UserAccount): Int {
            val saved = account.copy(id = accounts.size + 1)
            accounts += saved
            return saved.id
        }

        override suspend fun subjectId(subject: Subject) =
            subjects.getOrPut(subject.rawName to subject.number) { subjects.size + 1L }

        override suspend fun subjectCategory(subjectId: Long) = subjectCategories[subjectId]
        override suspend fun insertTransactions(transactions: List<Transaction>) {
            transactions.forEach { t -> if (this.transactions.none { it.message == t.message }) this.transactions += t }
        }

        override suspend fun deletePairedAirtelReceipts() {
            val id = { name: String -> types.first { it.name == name }.id }
            val paid = transactions.filter { it.transactionType == id("airtel.pay") }.map { it.transactionCode }.toSet()
            transactions.removeAll { it.transactionType == id("airtel.pay.receipt") && it.transactionCode in paid }
        }

        override suspend fun insertUnparsed(messages: List<UnparsedMessage>) {
            messages.forEach { m -> if (unparsed.none { it.body == m.body }) unparsed += m }
        }

        override suspend fun inTransaction(block: suspend () -> Unit) = block()
    }

    private val sample = mpesaSamples.first { it.type == "paybill" }
    private val promo = "Dear customer, get 20% off data bundles this weekend. Dial *544# now."
    private val unrecognised = "ABCDE12345 Confirmed. something we have no pattern for"

    private fun msg(id: Long, body: String = "${"ABCDEFGH"}${id.toString().padStart(2, '0')} ${sample.body.substringAfter(' ')}", sender: String = "MPESA", sub: Int = -1) =
        SmsParser.SmsMessage(body, id, sender, 1_700_000_000_000L + id, sub)

    private fun syncer(inbox: FakeInbox, store: FakeStore) =
        SmsSyncer(store, inbox, SmsParser()) { null }

    private fun run(inbox: FakeInbox, store: FakeStore) = runBlocking { syncer(inbox, store).syncAll() }

    @Test
    fun cursorPastTheInboxIsResetAndTheInboxIsRead() {
        val store = FakeStore(getDefaultTransactionTypes()).apply { cursor = 99_999 }
        run(FakeInbox(listOf(msg(1), msg(2))), store)
        assertEquals(2, store.transactions.size)
        assertEquals(2L, store.cursor)
    }

    @Test
    fun cursorInsideTheInboxIsKept() {
        val store = FakeStore(getDefaultTransactionTypes()).apply { cursor = 1 }
        run(FakeInbox(listOf(msg(1), msg(2))), store)
        assertEquals(1, store.transactions.size)
    }

    @Test
    fun twoSubscriptionsMakeTwoAccounts() {
        val store = FakeStore(getDefaultTransactionTypes())
        run(FakeInbox(listOf(msg(1, sub = 1), msg(2, sub = 2), msg(3, sub = 2))), store)
        assertEquals(listOf(1 to 1, 2 to 2), store.accounts.map { it.id to it.subscriptionId })
        assertEquals(listOf(1, 2, 2), store.transactions.map { it.accountId })
    }

    @Test
    fun aSenderInAnotherCaseIsStillRead() {
        val store = FakeStore(getDefaultTransactionTypes())
        run(FakeInbox(listOf(msg(1, sender = "mpesa"), msg(2, sender = "Mpesa"), msg(3, sender = "someone"))), store)
        assertEquals(2, store.transactions.size)
        assertEquals(setOf("MPESA"), store.accounts.map { it.provider }.toSet())
        assertTrue(Provider.forSender("mpesa") == Provider.MPESA)
    }

    @Test
    fun airtelSenderInAnyCaseIsRead() {
        val store = FakeStore(getDefaultTransactionTypes())
        val bodies = airtelSamples.filter { it.type == "airtel.send_money" }.map { it.body }
        run(
            FakeInbox(listOf(msg(1, bodies[0], "AIRTELMONEY"), msg(2, bodies[1], "AirtelMoney"), msg(3, bodies[2], "airtelmoney"))),
            store,
        )
        assertEquals(3, store.transactions.size)
        assertEquals(setOf("AIRTEL"), store.accounts.map { it.provider }.toSet())
    }

    @Test
    fun pairedTillPaymentYieldsOneRowWithTheMerchant() {
        val receipt = airtelSamples.first { it.type == "airtel.pay.receipt" }.body
        val pay = airtelSamples.first { it.type == "airtel.pay" }
        val payTypeId = 1 + getDefaultTransactionTypes().indexOfFirst { it.name == "airtel.pay" }
        for (batchSize in listOf(512, 1)) {
            val store = FakeStore(getDefaultTransactionTypes())
            runBlocking {
                val s = syncer(FakeInbox(listOf(msg(1, receipt, "airtelmoney"), msg(2, pay.body, "airtelmoney"))), store)
                if (batchSize == 512) s.syncAll() else { s.syncBatch(1); s.syncBatch(1) }
            }
            val row = store.transactions.single()
            assertEquals(payTypeId, row.transactionType)
            assertEquals(pay.name, store.subjects.entries.single { it.value == row.subject }.key.first)
        }
    }

    @Test
    fun transactionLikeMessageThatDoesNotMatchIsKeptAndAPromoIsNot() {
        val store = FakeStore(getDefaultTransactionTypes())
        run(FakeInbox(listOf(msg(1), msg(2, body = unrecognised), msg(3, body = promo))), store)
        assertEquals(1, store.transactions.size)
        assertEquals(listOf(unrecognised), store.unparsed.map { it.body })
    }

    @Test
    fun newTransactionInheritsTheSubjectCategory() {
        val store = FakeStore(getDefaultTransactionTypes())
        store.subjects[sample.name!! to sample.number!!] = 7L
        store.subjectCategories[7L] = 3
        run(FakeInbox(listOf(msg(1))), store)
        assertEquals(listOf(7L), store.transactions.map { it.subject })
        assertEquals(listOf(3), store.transactions.map { it.transactionCategory })
    }
}
