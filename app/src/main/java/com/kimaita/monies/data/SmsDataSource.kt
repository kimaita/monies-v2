package com.kimaita.monies.data

import android.content.Context
import android.provider.Telephony
import com.kimaita.monies.services.SmsParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SmsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** One keyset page of provider SMS with _ID > afterId, ordered ascending. */
    suspend fun readBatch(
        providers: Collection<String>,
        afterId: Long,
        limit: Int,
    ): List<SmsParser.SmsMessage> = withContext(Dispatchers.IO) {
        if (providers.isEmpty()) return@withContext emptyList()

        val placeholders = providers.joinToString(",") { "?" }
        val selection =
            "${Telephony.Sms.Inbox.ADDRESS} IN ($placeholders) AND ${Telephony.Sms.Inbox._ID} > ?"
        val args = (providers.map { it } + afterId.toString()).toTypedArray()
        val projection = arrayOf(
            Telephony.Sms.Inbox._ID, Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.DATE,
        )

        val out = ArrayList<SmsParser.SmsMessage>(limit)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args,
            "${Telephony.Sms.Inbox._ID} ASC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
            val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)
            while (out.size < limit && c.moveToNext()) {
                out.add(
                    SmsParser.SmsMessage(
                        c.getString(bodyIdx),
                        c.getLong(idIdx),
                        c.getString(addrIdx),
                        c.getLong(dateIdx)
                    )
                )
            }
        }
        out
    }


    suspend fun count(providers: Collection<String>, afterId: Long): Int =
        withContext(Dispatchers.IO) {
            if (providers.isEmpty()) return@withContext 0
            val placeholders = providers.joinToString(",") { "?" }
            val selection =
                "${Telephony.Sms.Inbox.ADDRESS} IN ($placeholders) AND ${Telephony.Sms.Inbox._ID} > ?"
            val args = (providers.map { it } + afterId.toString()).toTypedArray()
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.Inbox._ID),
                selection, args, null,
            )?.use { it.count } ?: 0
        }
}