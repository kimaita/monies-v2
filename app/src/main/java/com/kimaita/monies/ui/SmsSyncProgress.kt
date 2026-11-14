package com.kimaita.monies.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kimaita.monies.worker.SmsSyncWorker
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/** State of the first-run import. Work without [SmsSyncWorker.TAG_FIRST_RUN] is [Idle]. */
sealed interface SmsSyncProgress {
    data object Idle : SmsSyncProgress
    data class Running(val processed: Int, val total: Int?, val year: Int?) : SmsSyncProgress
    data object Done : SmsSyncProgress

    /** The import failed or was cancelled; the screen offers Retry for both. */
    data object Failed : SmsSyncProgress
}

/** What the app shows at start-up; [Import] carries what the import screen draws. */
sealed interface StartupScreen {
    data object Blank : StartupScreen
    data class Import(
        val processed: Int = 0,
        val total: Int? = null,
        val year: Int? = null,
        val failed: Boolean = false,
    ) : StartupScreen

    data object App : StartupScreen
}

/**
 * Which screen to show. [progress] is null until WorkManager has answered; [isFirstRun] is only
 * consulted when no first-run work exists, so it is never remembered across an import.
 */
fun startupScreen(progress: SmsSyncProgress?, isFirstRun: Boolean?): StartupScreen = when (progress) {
    null -> StartupScreen.Blank
    is SmsSyncProgress.Running -> StartupScreen.Import(progress.processed, progress.total, progress.year)
    SmsSyncProgress.Failed -> StartupScreen.Import(failed = true)
    SmsSyncProgress.Done -> StartupScreen.App
    SmsSyncProgress.Idle -> when (isFirstRun) {
        null -> StartupScreen.Blank
        true -> StartupScreen.Import()
        false -> StartupScreen.App
    }
}

/** True when the inbox has never been imported and no first-run work exists yet, so one must be started. */
fun shouldStartFirstRunImport(progress: SmsSyncProgress?, isFirstRun: Boolean?): Boolean =
    progress == SmsSyncProgress.Idle && isFirstRun == true

/**
 * Collapses the first-run work to one state: running beats succeeded beats failed or cancelled, because
 * finished attempts stay in WorkManager for a day beside a newer one.
 */
fun firstRunProgress(infos: List<WorkInfo>): SmsSyncProgress {
    val active = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
        ?: infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
    return when {
        active != null -> SmsSyncProgress.Running(
            processed = active.progress.getInt(SmsSyncWorker.KEY_PROCESSED, 0),
            total = active.progress.getInt(SmsSyncWorker.KEY_TOTAL, -1).takeIf { it > 0 },
            year = active.progress.getInt(SmsSyncWorker.KEY_YEAR, 0).takeIf { it > 0 },
        )

        infos.any { it.state == WorkInfo.State.SUCCEEDED } -> SmsSyncProgress.Done
        infos.any { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED } ->
            SmsSyncProgress.Failed

        else -> SmsSyncProgress.Idle
    }
}

/**
 * WorkManager drops a finished first-run import when an incoming SMS takes over [SmsSyncWorker.WORK_NAME],
 * which would read as "no import"; a finished import stays finished for as long as it is watched.
 */
fun keepDone(previous: SmsSyncProgress?, next: SmsSyncProgress): SmsSyncProgress =
    if (previous == SmsSyncProgress.Done && next == SmsSyncProgress.Idle) previous else next

/** Progress of the tagged first-run import; null until WorkManager has answered. */
@Composable
fun rememberSmsImportProgress(context: Context): State<SmsSyncProgress?> {
    val progress = remember {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(SmsSyncWorker.TAG_FIRST_RUN)
            .map(::firstRunProgress)
            .scan<SmsSyncProgress, SmsSyncProgress?>(null) { previous, next -> keepDone(previous, next) }
    }
    return progress.collectAsStateWithLifecycle(initialValue = null)
}
