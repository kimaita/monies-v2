package com.kimaita.monies.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kimaita.monies.worker.SmsSyncWorker

sealed interface SmsSyncProgress {
    data object Idle : SmsSyncProgress
    data class Running(val processed: Int, val total: Int?, val year: Int?) : SmsSyncProgress
    data object Done : SmsSyncProgress
    data object Failed : SmsSyncProgress
}

@Composable
fun rememberSmsImportProgress(context: Context): State<SmsSyncProgress> {
    val wm = remember { WorkManager.getInstance(context) }
    val infos by wm.getWorkInfosForUniqueWorkFlow(SmsSyncWorker.WORK_NAME)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    return remember {
        derivedStateOf {
            when (val info = infos.firstOrNull()) {
                null -> SmsSyncProgress.Idle
                else -> when (info.state) {
                    WorkInfo.State.RUNNING -> SmsSyncProgress.Running(
                        processed = info.progress.getInt(SmsSyncWorker.KEY_PROCESSED, 0),
                        total = info.progress.getInt(SmsSyncWorker.KEY_TOTAL, -1).takeIf { it > 0 },
                        year = info.progress.getInt(SmsSyncWorker.KEY_YEAR, 0).takeIf { it > 0 },
                    )

                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        SmsSyncProgress.Running(0, null, null)

                    WorkInfo.State.SUCCEEDED -> SmsSyncProgress.Done
                    WorkInfo.State.FAILED -> SmsSyncProgress.Failed
                    WorkInfo.State.CANCELLED -> SmsSyncProgress.Idle
                }
            }
        }
    }
}