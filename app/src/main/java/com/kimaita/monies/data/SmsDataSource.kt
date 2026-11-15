package com.kimaita.monies.data

import android.content.Context
import android.provider.Telephony
import com.kimaita.monies.services.SmsParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The SMS inbox as the syncer sees it; faked in JVM tests. */
interface SmsSource {
    suspend fun readBatch(senders: Collection<String>, afterId: Long, limit: Int): List<SmsParser.SmsMessage>
    suspend fun count(senders: Collection<String>, afterId: Long): Int

    /** Highest `_id` in the whole inbox, 0 when empty. */
    suspend fun maxId(): Long
}

class SmsDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : SmsSource {
    /** One keyset page of SMS from the given senders (case-insensitive) with _ID > afterId, ordered ascending. */
    override suspend fun readBatch(
        senders: Collection<String>,
        afterId: Long,
        limit: Int,
    ): List<SmsParser.SmsMessage> = withContext(Dispatchers.IO) {
        if (senders.isEmpty()) return@withContext emptyList()

        val placeholders = senders.joinToString(",") { "?" }
        val selection =
            "${Telephony.Sms.Inbox.ADDRESS} COLLATE NOCASE IN ($placeholders) AND ${Telephony.Sms.Inbox._ID} > ?"
        val args = (senders + afterId.toString()).toTypedArray()
        val projection = arrayOf(
            Telephony.Sms.Inbox._ID, Telephony.Sms.Inbox.BODY,
            Telephony.Sms.Inbox.ADDRESS, Telephony.Sms.Inbox.DATE, Telephony.Sms.Inbox.SUBSCRIPTION_ID,
        )

        val out = ArrayList<SmsParser.SmsMessage>(limit)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, selection, args,
            "${Telephony.Sms.Inbox._ID} ASC LIMIT $limit",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox._ID)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.BODY)
            val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.ADDRESS)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.Inbox.DATE)
            val subIdx = c.getColumnIndex(Telephony.Sms.Inbox.SUBSCRIPTION_ID)
            while (out.size < limit && c.moveToNext()) {
                out.add(
                    SmsParser.SmsMessage(
                        c.getString(bodyIdx),
                        c.getLong(idIdx),
                        c.getString(addrIdx),
                        c.getLong(dateIdx),
                        if (subIdx >= 0) c.getInt(subIdx).coerceAtLeast(-1) else -1,
                    )
                )
            }
        }
        out
    }


    override suspend fun count(senders: Collection<String>, afterId: Long): Int =
        withContext(Dispatchers.IO) {
            if (senders.isEmpty()) return@withContext 0
            val placeholders = senders.joinToString(",") { "?" }
            val selection =
                "${Telephony.Sms.Inbox.ADDRESS} COLLATE NOCASE IN ($placeholders) AND ${Telephony.Sms.Inbox._ID} > ?"
            val args = (senders + afterId.toString()).toTypedArray()
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.Inbox._ID),
                selection, args, null,
            )?.use { it.count } ?: 0
        }

    override suspend fun maxId(): Long = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.Inbox._ID),
            null, null, "${Telephony.Sms.Inbox._ID} DESC LIMIT 1",
        )?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
    }
}
