package com.kimaita.monies

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kimaita.monies.data.DefaultTransactionsRepository
import com.kimaita.monies.services.SmsContentObserver
import com.kimaita.monies.services.SmsSyncer
import com.kimaita.monies.ui.MainScaffold
import com.kimaita.monies.ui.SmsSyncProgress
import com.kimaita.monies.ui.onboarding.FirstRunImportScreen
import com.kimaita.monies.ui.onboarding.OnboardingScreen
import com.kimaita.monies.ui.permission.PermissionScreen
import com.kimaita.monies.ui.rememberSmsImportProgress
import com.kimaita.monies.ui.theme.AppTheme
import com.kimaita.monies.worker.SmsSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: DefaultTransactionsRepository

    @Inject
    lateinit var smsSyncer: SmsSyncer

    @Inject
    lateinit var smsContentObserver: SmsContentObserver
    val permissionsToRequest: Array<String> = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                val scope = rememberCoroutineScope()
                var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }
                var hasRequiredPermissions by remember { mutableStateOf(checkPermissions()) }

                LaunchedEffect(Unit) { onboardingComplete = repository.isOnboardingComplete() }

                val permissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { hasRequiredPermissions = checkPermissions() }

                when {
                    onboardingComplete == null ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        )

                    onboardingComplete == false ->
                        OnboardingScreen(
                            onSkip = {
                                scope.launch { repository.setOnboardingComplete() }
                                onboardingComplete = true
                            },
                            onFinish = {
                                scope.launch { repository.setOnboardingComplete() }
                                onboardingComplete = true
                                if (!hasRequiredPermissions) permissionsLauncher.launch(
                                    permissionsToRequest
                                )
                            },
                        )

                    !hasRequiredPermissions ->
                        PermissionScreen(onGrantClick = {
                            permissionsLauncher.launch(
                                permissionsToRequest
                            )
                        })

                    else -> {
                        val importProgress by rememberSmsImportProgress(applicationContext)
                        var firstRun by remember { mutableStateOf<Boolean?>(null) }

                        LaunchedEffect(Unit) {
                            val first = repository.isFirstRun()
                            firstRun = first
                            if (first) enqueueSmsImport() else smsSyncer.syncAll()
                        }
                        ObserveIncomingSms(smsContentObserver)

                        val showImport = firstRun == true &&
                                importProgress !is SmsSyncProgress.Done &&
                                importProgress !is SmsSyncProgress.Failed

                        Crossfade(targetState = showImport, label = "import-handoff") { importing ->
                            when {
                                firstRun == null ->
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surface)
                                    )

                                importing -> {
                                    val p = importProgress as? SmsSyncProgress.Running
                                    FirstRunImportScreen(
                                        processed = p?.processed ?: 0,
                                        total = p?.total,
                                        year = p?.year
                                    )
                                }

                                else -> MainScaffold()
                            }
                        }
                    }
                }


            }
        }
    }

    private fun enqueueSmsImport() {
        val request = OneTimeWorkRequestBuilder<SmsSyncWorker>()
            .setInputData(workDataOf(SmsSyncWorker.KEY_FOREGROUND to true))
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            SmsSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}


@Composable
private fun ObserveIncomingSms(observer: SmsContentObserver) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            observer.start()
            try {
                awaitCancellation()
            } finally {
                observer.stop()
            }
        }
    }
}