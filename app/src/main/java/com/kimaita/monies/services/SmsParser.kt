package com.kimaita.monies.services


import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.Provider
import com.kimaita.monies.utils.groupValue
import com.kimaita.monies.utils.parseDateTime
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SmsParser @Inject constructor() {

    data class SmsMessage(
        val body: String, val id: Long?, val originatingAddress: String, val timestamp: Long,
        /** SIM the message arrived on; -1 = unknown. */
        val subscriptionId: Int = -1,
    )

    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Anchored at the start of the message, as every pattern describes it from its first character.
     * Unanchored, a pattern that does not fit is retried at every offset of the body, which made
     * matching about ten times slower.
     */
    private fun regexFor(pattern: String) = regexCache.getOrPut(pattern) { typeRegex(pattern) }

    fun parse(
        messages: List<SmsMessage>, transactionTypes: List<TransactionType>
    ): List<Pair<Transaction, Subject?>> {

        val transactionList = mutableListOf<Pair<Transaction, Subject?>>()

        val patternsByProvider = transactionTypes
            .filter { it.pattern != null }
            .groupBy({ it.provider }, { Pair(it.id, regexFor(it.pattern!!)) })

        try {
            messages.forEach { message ->
                var matchedType: Int? = null
                var matchedRegex: Regex? = null
                var matchResult: MatchResult? = null

                val provider = Provider.forSender(message.originatingAddress)
                for ((type, regex) in patternsByProvider[provider?.name].orEmpty()) {
                    val result = regex.find(message.body)
                    if (result != null) {
                        matchedType = type
                        matchedRegex = regex
                        matchResult = result
                        break
                    }
                }

                if (matchedType != null && matchedRegex != null && matchResult != null) {
                    try {
                        val (transaction, newSubject) = createTransactionFromMatch(
                            matchResult, matchedRegex, matchedType, message
                        )
                        transactionList.add(Pair(transaction, newSubject))
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse message id %d", message.id)
                    }
                } else {
                    Timber.e("Failed to parse message id %d", message.id)

                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to process batch")
        }
        return transactionList

    }

    private fun createTransactionFromMatch(
        matchResult: MatchResult, regex: Regex, typeId: Int, message: SmsMessage
    ): Pair<Transaction, Subject?> {

        fun group(name: String) = matchResult.groupValue(regex, name)

        val body = group("Body")
        val code = group("Code")
        val amount = group("Amount")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val cost = group("Cost")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val balance = (group("Balance") ?: group("MpesaBal"))
            ?.replace(",", "")?.toDoubleOrNull()
        val subjectName = group("Name")?.replace(REPEATED_SPACES, " ")?.trim()
            ?.trimEnd('.')?.trim()?.takeIf { it.isNotEmpty() }
        val subjectNumber = group("Number")?.trim()
        val date = group("Date")
        val time = group("Time")
        val dateTime = parseDateTime(date, time, message.timestamp)

        val name = subjectName ?: subjectNumber
        val newSubject = name?.let { name ->
            Subject(rawName = name, name = name, number = subjectNumber ?: "")
        }
        val transaction = Transaction(
            amount = amount,
            transactionCode = code,
            message = body ?: message.body,
            messageId = message.id,
            cost = cost,
            balance = balance,
            transactionTime = dateTime,
            currency = "KES",
            subject = null,
            transactionType = typeId,
            transactionCategory = null,
            accountId = 0
        )
        return Pair(transaction, newSubject)
    }

    private companion object {
        val REPEATED_SPACES = Regex("\\s{2,}")
    }
}

/** A transaction type's pattern as the parser runs it: anchored at the start of the message. */
internal fun typeRegex(pattern: String) =
    "^\\s*(?:$pattern)".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
