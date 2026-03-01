package com.kimaita.monies.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kimaita.monies.data.DefaultTransactionsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: DefaultTransactionsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val transactionProviders = listOf("MPESA")
        // TODO: Dynamically fetch transaction providers from database
//        val transactionProviders = repository.getTransactionProviders()

        val validMessages = messages.filter {
            transactionProviders.contains(it.originatingAddress)
        }.map {
            SmsParser.SmsMessage(
                it.messageBody,
                id = null,
                it.originatingAddress ?: "None",
                it.timestampMillis
            )
        }

        if (validMessages.isNotEmpty()) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.parseAndSaveSms(validMessages)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}