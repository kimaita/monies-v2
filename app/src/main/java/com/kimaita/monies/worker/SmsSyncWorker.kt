package com.kimaita.monies.worker

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kimaita.monies.R
import com.kimaita.monies.models.SyncTimings
import com.kimaita.monies.services.SmsSyncer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val smsSyncer: SmsSyncer,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "SmsSyncWorker"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_YEAR = "year"
        const val KEY_FOREGROUND = "foreground"

        /** Marks the import the first-run screen follows; incoming-SMS and re-import work does not carry it. */
        const val TAG_FIRST_RUN = "first_run"
        private const val CHANNEL_ID = "sms_sync"
        private const val NOTIFICATION_ID = 4242

        /**
         * Starts the foreground import of the whole inbox. [firstRun] tags it for the first-run screen and
         * replaces whatever holds [WORK_NAME] (an incoming-SMS sync would otherwise keep it from starting);
         * the import resumes from the sync cursor. A re-import keeps any work already running.
         */
        fun enqueueImport(context: Context, firstRun: Boolean) {
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
                .setInputData(workDataOf(KEY_FOREGROUND to true))
                .apply { if (firstRun) addTag(TAG_FIRST_RUN) }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (firstRun) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        val foreground = inputData.getBoolean(KEY_FOREGROUND, false)
        val batchSize = SmsSyncer.SMS_BATCH_SIZE
        val countStart = System.nanoTime()
        val total = smsSyncer.pendingCount()
        val countNanos = System.nanoTime() - countStart
        val zone = ZoneId.systemDefault()
        var processed = 0
        var maxYear = 0
        val label = if (foreground) "Import" else "Sync"

        if (foreground) setForeground(buildForegroundInfo(0, total))
        setProgress(workDataOf(KEY_PROCESSED to 0, KEY_TOTAL to total))
        logStart(label, total, countNanos, batchSize)

        val run = SyncTimings()
        var batches = 0
        val start = System.nanoTime()
        return try {
            while (true) {
                val result = smsSyncer.syncBatch(batchSize)
                processed += result.count
                run += result.timings
                batches++

                result.newestEpochMillis?.let { millis ->
                    val year = Instant.ofEpochMilli(millis).atZone(zone).year
                    if (year > maxYear) maxYear = year
                }

                setProgress(
                    workDataOf(
                        KEY_PROCESSED to processed,
                        KEY_TOTAL to total,
                        KEY_YEAR to maxYear,
                    )
                )
                if (foreground) updateNotification(processed, total)
                if (result.count < batchSize) break
            }
            Timber.tag(SmsSyncer.LOG_TAG)
                .i("%s done: %s", label, run.runSummary(processed, batches, System.nanoTime() - start))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SmsSyncWorker failed.")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * The phone and build a run is measured on. Debug builds are not shrunk by R8 and run in ART's
     * debuggable mode, so CPU steps such as parse read slower than they would in a release build.
     */
    private fun logStart(label: String, pending: Int, countNanos: Long, batchSize: Int) {
        val am = applicationContext.getSystemService(ActivityManager::class.java)
        val debuggable = applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Timber.tag(SmsSyncer.LOG_TAG).i(
            "%s start: %d SMS pending (counted in %s), batch %d; %s %s, Android %d, heap %d MB, low-RAM %b, debuggable %b",
            label, pending, SyncTimings.ms(countNanos), batchSize, Build.MANUFACTURER, Build.MODEL,
            Build.VERSION.SDK_INT, am.memoryClass, am.isLowRamDevice, debuggable,
        )
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(0, 0)

    private fun buildForegroundInfo(processed: Int, total: Int): ForegroundInfo {
        val notification = buildNotification(processed, total)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(processed: Int, total: Int): Notification {
        ensureChannel()
        val indeterminate = total <= 0
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Importing M-PESA messages")
            .setContentText(if (indeterminate) "Starting…" else "$processed of $total")
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setProgress(if (indeterminate) 0 else total, processed, indeterminate)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(processed: Int, total: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIFICATION_ID, buildNotification(processed, total))
    }

    private fun ensureChannel() {
        val mgr = applicationContext.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "M-PESA import",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }
}
