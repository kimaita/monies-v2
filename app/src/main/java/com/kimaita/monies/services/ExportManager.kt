package com.kimaita.monies.services

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kimaita.monies.di.ApplicationScope
import com.kimaita.monies.models.export.ExportFilters
import com.kimaita.monies.models.export.ExportFormat
import com.kimaita.monies.models.export.ExportState
import com.kimaita.monies.worker.ExportWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val workManager = WorkManager.getInstance(context)
    private val acknowledged = MutableStateFlow(false)
    @Volatile
    private var pendingFormat: ExportFormat? = null

    val state: StateFlow<ExportState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ExportWorker.WORK_NAME),
        acknowledged,
    ) { infos, ack -> infos.firstOrNull().toState(ack) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ExportState.Idle)

    fun start(format: ExportFormat, filters: ExportFilters) {
        pendingFormat = format
        acknowledged.value = false
        val request = OneTimeWorkRequestBuilder<ExportWorker>()
            .setInputData(ExportWorker.buildInput(format, filters))
            .build()
        // KEEP = ignore a tap if an export is already running; replaces a finished one
        workManager.enqueueUniqueWork(ExportWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(ExportWorker.WORK_NAME)
    }

    fun acknowledge() {
        acknowledged.value = true
        workManager.pruneWork()
    }

    private fun WorkInfo?.toState(ack: Boolean): ExportState {
        if (this == null) return ExportState.Idle
        return when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> {
                val f =
                    progress.getString(ExportWorker.KEY_FORMAT)?.let { ExportFormat.valueOf(it) }
                        ?: pendingFormat ?: ExportFormat.CSV
                val total = progress.getInt(ExportWorker.KEY_TOTAL, -1).takeIf { it >= 0 }
                ExportState.InProgress(progress.getInt(ExportWorker.KEY_PROCESSED, 0), total, f)
            }

            WorkInfo.State.SUCCEEDED ->
                if (ack) ExportState.Idle
                else outputData.getString(ExportWorker.KEY_URI)?.let { Uri.parse(it) }?.let { uri ->
                    ExportState.Completed(
                        uri = uri,
                        fileName = outputData.getString(ExportWorker.KEY_FILE).orEmpty(),
                        mime = outputData.getString(ExportWorker.KEY_MIME).orEmpty(),
                        count = outputData.getInt(ExportWorker.KEY_COUNT, -1),
                    )
                } ?: ExportState.Idle

            WorkInfo.State.FAILED ->
                if (ack) ExportState.Idle
                else ExportState.Failed(
                    outputData.getString(ExportWorker.KEY_ERROR) ?: "Export failed"
                )

            WorkInfo.State.CANCELLED -> ExportState.Idle
        }
    }
}