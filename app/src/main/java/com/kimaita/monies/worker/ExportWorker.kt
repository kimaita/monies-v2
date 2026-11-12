package com.kimaita.monies.worker

import com.kimaita.monies.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.hasKeyWithValueOfType
import androidx.work.workDataOf
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.data.export.ExportStorage
import com.kimaita.monies.data.export.FormatWriter
import com.kimaita.monies.data.export.TransactionPages
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.ExportFormat
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TransactionsRepository,
    private val storage: ExportStorage,
    writers: Set<@JvmSuppressWildcards FormatWriter>,
) : CoroutineWorker(appContext, params) {

    private val writersByFormat = writers.associateBy { it.format }

    override suspend fun doWork(): Result {
        val format = inputData.getString(KEY_FORMAT)
            ?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() }
            ?: return Result.failure(workDataOf(KEY_ERROR to "Unknown export format"))
        val filters = inputData.toFilters()

        setForeground(foregroundInfo(0, null))

        return try {
            val total = runCatching { repository.countForExport(filters) }.getOrNull()
            setProgress(progressData(format, 0, total))

            val fileName = "transactions_${System.currentTimeMillis()}.${format.ext}"
            val writer = writersByFormat.getValue(format)
            var processed = 0

            val uri = storage.write(fileName, format.mime) { out ->
                val pages =
                    TransactionPages { onPage -> repository.forEachExportBatch(filters, onPage) }
                writer.write(out, pages) { count ->                 // called once per page
                    processed = count
                    setProgress(progressData(format, count, total))
                    setForeground(foregroundInfo(count, total))
                }
            }

            Result.success(
                workDataOf(
                    KEY_URI to uri.toString(),
                    KEY_FILE to fileName,
                    KEY_MIME to format.mime,
                    KEY_FORMAT to format.name,
                    KEY_COUNT to (total ?: processed),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Export failed")))
        }
    }

    // --- progress / notification ---

    private fun progressData(f: ExportFormat, processed: Int, total: Int?) = workDataOf(
        KEY_FORMAT to f.name, KEY_PROCESSED to processed, KEY_TOTAL to (total ?: -1),
    )

    private fun foregroundInfo(processed: Int, total: Int?): ForegroundInfo {
        ensureChannel()
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val b = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Exporting transactions")
            .setSmallIcon(R.drawable.download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", cancel)
        if (total != null && total > 0) {
            b.setContentText("$processed of $total").setProgress(total, processed, false)
        } else {
            b.setContentText(if (processed > 0) "$processed rows" else "Preparing…")
                .setProgress(0, 0, true)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ForegroundInfo(NOTIF_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, b.build())
    }

    private fun ensureChannel() {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Exports", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }


    private fun Data.toFilters() = ExportFilters(
        startDate = getString(K_START),
        endDate = getString(K_END),
        subjectId = getLongArray(K_SUBJECT)?.toList(),
        categoryId = getIntArray(K_CATEGORIES)?.toList(),
        transactionType = getIntArray(K_TYPES)?.toList(),
        account = if (hasKeyWithValueOfType<Int>(K_ACCOUNT)) getInt(K_ACCOUNT, 0) else null,
        natureFilter = if (hasKeyWithValueOfType<Boolean>(K_NATURE)) getBoolean(
            K_NATURE,
            false
        ) else null,
    )

    private fun Data.longOrNull(key: String): Long? =
        if (hasKeyWithValueOfType<Long>(key)) getLong(key, 0L) else null

    companion object {
        const val WORK_NAME = "transaction_export"
        private const val CHANNEL_ID = "exports"
        private const val NOTIF_ID = 4243

        const val KEY_FORMAT = "format"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_URI = "uri"
        const val KEY_FILE = "file"
        const val KEY_MIME = "mime"
        const val KEY_COUNT = "count"
        const val KEY_ERROR = "error"

        private const val K_START = "f_start"
        private const val K_END = "f_end"
        private const val K_SUBJECT = "f_subject"
        private const val K_CATEGORIES = "f_categories"
        private const val K_TYPES = "f_types"
        private const val K_ACCOUNT = "f_account"
        private const val K_NATURE = "f_nature"

        fun buildInput(format: ExportFormat, f: ExportFilters): Data {
            val b = Data.Builder().putString(KEY_FORMAT, format.name)
            f.startDate?.let { b.putString(K_START, it) }
            f.endDate?.let { b.putString(K_END, it) }
            f.subjectId?.takeIf { it.isNotEmpty() }
                ?.let { b.putLongArray(K_SUBJECT, it.toLongArray()) }
            f.categoryId?.takeIf { it.isNotEmpty() }
                ?.let { b.putIntArray(K_CATEGORIES, it.toIntArray()) }
            f.transactionType?.takeIf { it.isNotEmpty() }
                ?.let { b.putIntArray(K_TYPES, it.toIntArray()) }
            f.account?.let { b.putInt(K_ACCOUNT, it) }
            f.natureFilter?.let { b.putBoolean(K_NATURE, it) }
            return b.build()
        }
    }
}