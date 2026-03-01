package com.kimaita.monies.services


import com.kimaita.monies.data.database.models.Subject
import com.kimaita.monies.data.database.models.Transaction
import com.kimaita.monies.data.database.models.TransactionType
import com.kimaita.monies.utils.getSafeValue
import com.kimaita.monies.utils.parseDateTime
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class SmsParser @Inject constructor() {

    data class SmsMessage(
        val body: String, val id: Long?, val originatingAddress: String, val timestamp: Long
    )

    fun parse(
        messages: List<SmsMessage>, transactionTypes: List<TransactionType>
    ): List<Pair<Transaction, Subject?>> {

        val transactionList = mutableListOf<Pair<Transaction, Subject?>>()

        val compiledPatterns = transactionTypes.mapNotNull { type ->
            type.pattern?.let { pat ->
                Pair(
                    type.id,
                    pat.toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                )
            }
        }

        try {
            messages.forEach { message ->
                var matchedType: Int? = null
                var matchResult: MatchResult? = null

                for ((type, regex) in compiledPatterns) {
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
                        Timber.e(e, "Failed to parse message: %s", message.toString())
                    }
                } else {
                    Timber.e("Failed to parse message: %s", message.toString())

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
        val subjectName = groups?.getSafeValue("Name")?.trim()
        val subjectNumber = groups?.getSafeValue("Number")?.trim()
        val date = groups?.getSafeValue("Date")
        val time = groups?.getSafeValue("Time")
        val dateTime = parseDateTime(date, time, message.timestamp)

        val name = subjectName ?: subjectNumber
        val newSubject = name?.let { name ->
            Subject(name = name, number = subjectNumber ?: "")
        }
        val transaction = Transaction(
            amount = amount,
            transactionCode = code,
            message = body ?: message.body,
            messageId = message.id,
            cost = cost,
            transactionTime = dateTime,
            currency = "KES",
            subject = null,
            transactionType = typeId,
            transactionCategory = null,
            accountId = 0
        )
        return Pair(transaction, newSubject)
    }


}
