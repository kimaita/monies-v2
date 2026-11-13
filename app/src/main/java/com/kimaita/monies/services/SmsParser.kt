package com.kimaita.monies.services


import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.models.Provider
import com.kimaita.monies.utils.getSafeValue
import com.kimaita.monies.utils.parseDateTime
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SmsParser @Inject constructor() {

    data class SmsMessage(
        val body: String, val id: Long?, val originatingAddress: String, val timestamp: Long
    )

    private val regexCache = ConcurrentHashMap<String, Regex>()

    private fun regexFor(pattern: String) = regexCache.getOrPut(pattern) {
        pattern.toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    }

    fun parse(
        messages: List<SmsMessage>, transactionTypes: List<TransactionType>
    ): List<Pair<Transaction, Subject?>> {

        val transactionList = mutableListOf<Pair<Transaction, Subject?>>()

        // Only a message's own provider's patterns are tried; a sender nobody knows is never matched.
        val patternsByProvider = transactionTypes
            .filter { it.pattern != null }
            .groupBy({ it.provider }, { Pair(it.id, regexFor(it.pattern!!)) })

        try {
            messages.forEach { message ->
                var matchedType: Int? = null
                var matchResult: MatchResult? = null

                val provider = Provider.forSender(message.originatingAddress)
                for ((type, regex) in patternsByProvider[provider?.name].orEmpty()) {
                    val result = regex.find(message.body)
                    if (result != null) {
                        matchedType = type
                        matchResult = result
                        break
                    }
                }

                if (matchedType != null && matchResult != null) {
                    try {
                        val (transaction, newSubject) = createTransactionFromMatch(
                            matchResult, matchedType, message
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
        matchResult: MatchResult, typeId: Int, message: SmsMessage
    ): Pair<Transaction, Subject?> {

        val groups = matchResult.groups as? MatchNamedGroupCollection


        val body = groups?.getSafeValue("Body")
        val code = groups?.getSafeValue("Code")
        val amount = groups?.getSafeValue("Amount")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val cost = groups?.getSafeValue("Cost")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val balance = (groups?.getSafeValue("Balance") ?: groups?.getSafeValue("MpesaBal"))
            ?.replace(",", "")?.toDoubleOrNull()
        val subjectName = groups?.getSafeValue("Name")?.replace(REPEATED_SPACES, " ")?.trim()
            ?.trimEnd('.')?.trim()?.takeIf { it.isNotEmpty() }
        val subjectNumber = groups?.getSafeValue("Number")?.trim()
        val date = groups?.getSafeValue("Date")
        val time = groups?.getSafeValue("Time")
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
            accountId = 0 // placeholder; SmsSyncer sets the real account
        )
        return Pair(transaction, newSubject)
    }

    private companion object {
        val REPEATED_SPACES = Regex("\\s{2,}")
    }
}
