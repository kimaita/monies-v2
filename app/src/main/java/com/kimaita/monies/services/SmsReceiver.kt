package com.kimaita.monies.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kimaita.monies.models.Provider
import com.kimaita.monies.worker.SmsSyncWorker
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val fromProvider = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            .any { Provider.forSender(it.originatingAddress) != null }
        if (!fromProvider) return

        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInitialDelay( // let the default SMS app persist the row first
                3,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SmsSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}