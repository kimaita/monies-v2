package com.kimaita.monies.services

import com.kimaita.monies.data.SmsSource
import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.data.database.models.UnparsedMessage
import com.kimaita.monies.data.database.models.UserAccount
import com.kimaita.monies.data.database.models.categoryKey
import com.kimaita.monies.data.database.models.getDefaultCategories
import com.kimaita.monies.data.database.models.getDefaultTransactionTypes
import com.kimaita.monies.models.Provider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSyncerTest {

    private class FakeInbox(var messages: List<SmsParser.SmsMessage>) : SmsSource {
        /** Mirrors the real query: case-insensitive sender match, keyset on _id. */
        override suspend fun readBatch(senders: Collection<String>, afterId: Long, limit: Int) =
            messages.filter { m -> (m.id ?: 0) > afterId && senders.any { it.equals(m.originatingAddress, true) } }
                .sortedBy { it.id }.take(limit)

        override suspend fun count(senders: Collection<String>, afterId: Long) =
            readBatch(senders, afterId, Int.MAX_VALUE).size

        var maxIdCalls = 0
        override suspend fun maxId(): Long {
            maxIdCalls++
            return messages.maxOfOrNull { it.id ?: 0 } ?: 0L
        }
    }

    private class FakeStore(types: List<TransactionType>) : SyncStore {
        var cursor = 0L
        val types = types.mapIndexed { i, t -> t.copy(id = i + 1) }
        val accounts = mutableListOf<UserAccount>()
        val transactions = mutableListOf<Transaction>()
        val unparsed = mutableListOf<UnparsedMessage>()
        val subjects = mutableMapOf<Pair<String, String>, Long>()
        val subjectCategories = mutableMapOf<Long, Int?>()
        val mergedInto = mutableMapOf<Long, Long>()
        val categories = getDefaultCategories().mapIndexed { i, c -> categoryKey(c.name) to i + 1 }.toMap()

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

        /** How many times merges were recomputed (each one scans every transaction in the real store). */
        var activityReads = 0

        override suspend fun accountActivity(): List<AccountActivity> {
            activityReads++
            return accounts.mapNotNull { a ->
                val times = transactions.filter { it.accountId == a.id }.map { it.transactionTime.toString() }
                if (times.isEmpty()) null else AccountActivity(a.id, a.provider, times.min(), times.max(), a.mergedInto)
            }
        }

        override suspend fun setMergedInto(accountId: Int, root: Int?) {
            val i = accounts.indexOfFirst { it.id == accountId }
            accounts[i] = accounts[i].copy(mergedInto = root)
        }

        /** like the real store before the flag is first written */
        var mergesStale = true
        override suspend fun mergesStale() = mergesStale
        override suspend fun setMergesStale(stale: Boolean) { mergesStale = stale }

        override suspend fun savedSubjects(rawNames: Collection<String>) =
            subjects.filterKeys { it.first in rawNames }.map { (key, id) ->
                val root = mergedInto[id] ?: id
                SavedSubject(key.first, key.second, root, subjectCategories[root])
            }

        override suspend fun insertSubject(subject: Subject): Long =
            (subjects.size + 1L).also {
                subjects[subject.rawName to subject.number] = it
                subjectCategories[it] = subject.categoryId
            }
        override suspend fun categoryIds() = categories
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
        SmsSyncer(store, inbox, SmsParser())

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
        for (batchSize in listOf(SmsSyncer.SMS_BATCH_SIZE, 1)) {
            val store = FakeStore(getDefaultTransactionTypes())
            runBlocking {
                val s = syncer(FakeInbox(listOf(msg(1, receipt, "airtelmoney"), msg(2, pay.body, "airtelmoney"))), store)
                if (batchSize == SmsSyncer.SMS_BATCH_SIZE) s.syncAll() else { s.syncBatch(1); s.syncBatch(1) }
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

    private val kplc = mpesaSamples.first { it.name == "ACME UTILITY" }.body.replace("ACME UTILITY", "KPLC PREPAID")

    @Test
    fun newSubjectsStartInTheirBuiltInCategory() {
        val store = FakeStore(getDefaultTransactionTypes())
        val java = mpesaSamples.first { it.type == "till" }.body.replace("PAUL KAMAU", "JAVA HOUSE VALLEY ARCADE")
        val bus = mpesaSamples.first { it.type == "pochi" }.body.replace("Mary  Doe", "SM705")
        val airtime = mpesaSamples.first { it.type == "airtime.own" }.body
        val person = mpesaSamples.first { it.type == "send_money" }.body
        val bundle = airtelSamples.first { it.type == "airtel.bundle" }.body
        val power = "AB12CD34EF5 Confirmed. Ksh 100 successfully paid to Kenya Power and Lighting Co Ltd on 28/05/26 at 03:05 PM. Fee: Ksh 0. Bal: Ksh 1888.0."
        run(
            FakeInbox(
                listOf(
                    msg(1, kplc), msg(2, java), msg(3, bus), msg(4, airtime), msg(5, person),
                    msg(6, bundle, "AIRTELMONEY"), msg(7, power, "AIRTELMONEY"),
                )
            ),
            store,
        )
        val c = store.categories
        assertEquals(
            listOf(c["electricity"], c["meals"], c["transport"], c["airtime & data"], null, c["airtime & data"], c["electricity"]),
            store.transactions.sortedBy { it.messageId }.map { it.transactionCategory },
        )
    }

    @Test
    fun aSavedSubjectKeepsTheCategoryTheUserPicked() {
        val store = FakeStore(getDefaultTransactionTypes())
        store.subjects["KPLC PREPAID" to "37100000000"] = 7L
        store.subjectCategories[7L] = store.categories.getValue("rent")
        run(FakeInbox(listOf(msg(1, kplc))), store)
        assertEquals(listOf(store.categories["rent"]), store.transactions.map { it.transactionCategory })
    }

    private val maskedJane = mpesaSamples.first { it.number == "0700***003" }.body
    private val fullJane = mpesaSamples.first { it.number == "0700000003" }.body

    @Test
    fun aMaskedNumberJoinsTheSavedSubjectWithTheFullNumber() {
        val store = FakeStore(getDefaultTransactionTypes())
        store.subjects["JANE DOE" to "0700000003"] = 7L
        store.subjects["JANE DOE" to "0700000004"] = 8L
        run(FakeInbox(listOf(msg(1, maskedJane))), store)
        assertEquals(listOf(7L), store.transactions.map { it.subject })
        assertEquals(2, store.subjects.size)
    }

    @Test
    fun aMaskedNumberFindsAFullOneSavedInTheSameBatch() {
        val store = FakeStore(getDefaultTransactionTypes())
        run(FakeInbox(listOf(msg(1, maskedJane), msg(2, fullJane))), store)
        assertEquals(listOf("JANE DOE" to "0700000003"), store.subjects.keys.toList())
        assertEquals(1, store.transactions.map { it.subject }.distinct().size)
    }

    @Test
    fun aMaskedNumberThatFitsNoneOrSeveralGetsItsOwnSubject() {
        for (saved in listOf(emptyList(), listOf("0700000003", "0700999003"))) {
            val store = FakeStore(getDefaultTransactionTypes())
            saved.forEachIndexed { i, n -> store.subjects["JANE DOE" to n] = 10L + i }
            run(FakeInbox(listOf(msg(1, maskedJane), msg(2, maskedJane.replace("TCQ000DDD4", "TCQ000DDD5")))), store)
            val own = store.subjects.getValue("JANE DOE" to "0700***003")
            assertEquals(listOf(own, own), store.transactions.map { it.subject })
        }
    }

    private fun act(id: Int, min: String, max: String, provider: String = "MPESA") =
        AccountActivity(id, provider, min, max)

    @Test
    fun sequentialSubscriptionsMerge() {
        val roots = mergeRoots(listOf(act(1, "2021-01-01T00:00:00", "2024-07-26T18:05:00"), act(2, "2024-07-26T20:08:00", "2026-11-01T00:00:00")))
        assertEquals(mapOf(1 to null, 2 to 1), roots)
    }

    @Test
    fun overlappingSubscriptionStaysSeparate() {
        val roots = mergeRoots(
            listOf(
                act(1, "2021-01-01T00:00:00", "2024-07-26T18:05:00"),
                act(2, "2024-07-26T20:08:00", "2026-11-01T00:00:00"),
                act(3, "2024-09-06T10:00:00", "2024-09-06T11:00:00"),
            )
        )
        assertEquals(mapOf(1 to null, 2 to 1, 3 to null), roots)
    }

    @Test
    fun differentProvidersNeverMerge() {
        val roots = mergeRoots(listOf(act(1, "2021-01-01T00:00:00", "2022-01-01T00:00:00"), act(2, "2023-01-01T00:00:00", "2024-01-01T00:00:00", "AIRTEL")))
        assertEquals(mapOf(1 to null, 2 to null), roots)
    }

    @Test
    fun aLaterOverlapUnmergesOnResync() {
        val store = FakeStore(getDefaultTransactionTypes())
        val inbox = FakeInbox(listOf(msg(1, sub = 1), msg(2, sub = 2)))
        run(inbox, store)
        assertEquals(listOf(null, null), store.accounts.map { it.mergedInto })

        fun retime(account: Int, time: String) {
            store.transactions.replaceAll { if (it.accountId == account) it.copy(transactionTime = java.time.LocalDateTime.parse(time)) else it }
            store.mergesStale = true
        }
        retime(1, "2021-01-01T10:00:00")
        retime(2, "2024-01-01T10:00:00")
        run(inbox, store)
        assertEquals(listOf(null, 1), store.accounts.map { it.mergedInto })

        retime(1, "2024-01-01T10:00:00")
        run(inbox, store)
        assertEquals(listOf(null, null), store.accounts.map { it.mergedInto })
    }

    @Test
    fun aMergedSubjectsNewMessagesGoToTheTarget() {
        val store = FakeStore(getDefaultTransactionTypes())
        run(FakeInbox(listOf(msg(1))), store)
        val source = store.transactions.single().subject!!
        val target = source + 100
        store.mergedInto[source] = target
        run(FakeInbox(listOf(msg(1), msg(2))), store)
        assertEquals(target, store.transactions.last().subject)
    }

    @Test
    fun aSyncThatSavesNothingNewLeavesMergesAlone() {
        val store = FakeStore(getDefaultTransactionTypes())
        val inbox = FakeInbox(listOf(msg(1)))
        run(inbox, store)
        assertEquals(1, store.activityReads)

        run(inbox, store)
        inbox.messages += msg(2, body = promo)
        run(inbox, store)
        assertEquals(1, store.activityReads)
    }

    @Test
    fun anInboxThatEndsOnAFullBatchStillRecomputesOnce() {
        val store = FakeStore(getDefaultTransactionTypes()).apply { mergesStale = false }
        val s = syncer(FakeInbox(listOf(msg(1), msg(2))), store)
        runBlocking {
            s.syncBatch(2)
            assertEquals(0, store.activityReads)
            s.syncBatch(2)
            s.syncBatch(2)
        }
        assertEquals(1, store.activityReads)
    }

    @Test
    fun theInboxIsAskedForItsHighestIdOncePerProcess() {
        val store = FakeStore(getDefaultTransactionTypes())
        val inbox = FakeInbox(listOf(msg(1), msg(2), msg(3)))
        runBlocking { syncer(inbox, store).let { s -> repeat(4) { s.syncBatch(1) } } }
        assertEquals(3, store.transactions.size)
        assertEquals(1, inbox.maxIdCalls)
    }

    @Test
    fun aBatchTimesEveryStep() {
        val store = FakeStore(getDefaultTransactionTypes())
        val result = runBlocking { syncer(FakeInbox(listOf(msg(1))), store).syncBatch() }
        val steps = result.timings.toString().split(" ").map { it.substringBefore('=') }
        assertEquals(listOf("cursor", "read", "parse", "subjects", "save", "merges", "other"), steps)
        assertTrue(result.timings.runSummary(1, 1, 1_000_000).startsWith("1 SMS in 1 batches, 1.0ms"))
    }

    @Test
    fun aCategoryChangedBetweenBatchesAppliesToTheNextOne() {
        val store = FakeStore(getDefaultTransactionTypes())
        val s = syncer(FakeInbox(listOf(msg(1), msg(2))), store)
        runBlocking {
            s.syncBatch(1)
            store.subjectCategories[store.transactions.single().subject!!] = store.categories.getValue("rent")
            s.syncBatch(1)
        }
        assertEquals(store.categories["rent"], store.transactions.last().transactionCategory)
    }
}
