package com.kimaita.monies.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kimaita.monies.data.DefaultTransactionsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SmsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: DefaultTransactionsRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "SmsSyncWorker"
        const val PROGRESS = "PROGRESS"
    }

    override suspend fun doWork(): Result {
        val batchSize = 512
        var currentOffset = 0
        var messagesProcessedInLastRun: Int

        setProgress(workDataOf(PROGRESS to 0))
        Timber.i("SmsSyncWorker started.")

        try {
            do {
                Timber.d("Processing batch with offset: %d", currentOffset)

                messagesProcessedInLastRun =
                    repository.syncSmsBatch(applicationContext, batchSize, currentOffset)

                currentOffset += messagesProcessedInLastRun
                Timber.d(
                    "Processed %d messages in this batch. Total processed: %d",
                    messagesProcessedInLastRun,
                    currentOffset
                )
                setProgress(workDataOf(PROGRESS to currentOffset))
            } while (messagesProcessedInLastRun in 1..batchSize)
            Timber.i("SmsSyncWorker finished successfully.")
            setProgress(workDataOf(PROGRESS to -1))
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "SmsSyncWorker failed with an exception.")
            return Result.failure()
        }
    }
}
