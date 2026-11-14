package com.kimaita.monies

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kimaita.monies.data.TransactionsRepository
import com.kimaita.monies.models.ThemeMode
import com.kimaita.monies.services.SmsContentObserver
import com.kimaita.monies.services.SmsSyncer
import com.kimaita.monies.ui.MainScaffold
import com.kimaita.monies.ui.SmsSyncProgress
import com.kimaita.monies.ui.StartupScreen
import com.kimaita.monies.ui.onboarding.FirstRunImportScreen
import com.kimaita.monies.ui.onboarding.OnboardingScreen
import com.kimaita.monies.ui.onboarding.PermissionScreen
import com.kimaita.monies.ui.rememberSmsImportProgress
import com.kimaita.monies.ui.shouldStartFirstRunImport
import com.kimaita.monies.ui.startupScreen
import com.kimaita.monies.ui.theme.AppTheme
import com.kimaita.monies.worker.SmsSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: TransactionsRepository

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

    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            AppTheme(themeMode) {
                val scope = rememberCoroutineScope()
                var onboardingComplete by remember { mutableStateOf<Boolean?>(null) }
                var hasRequiredPermissions by remember { mutableStateOf(checkPermissions()) }

                // One read gives the theme and the onboarding flag together, so the first real frame is themed.
                LaunchedEffect(Unit) {
                    val startup = repository.startupState()
                    themeMode = startup.theme
                    onboardingComplete = startup.onboardingComplete
                    repository.themeMode.collect { themeMode = it }
                }

                // The user may grant the permission in system settings, so re-check on every resume.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    hasRequiredPermissions = checkPermissions()
                }
                var askedForPermissions by rememberSaveable { mutableStateOf(false) }
                val permissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    askedForPermissions = true
                    hasRequiredPermissions = checkPermissions()
                }

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
                            // After a permanent denial the system dialog no longer shows: open app settings.
                            if (askedForPermissions && !shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS))
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", packageName, null)
                                    )
                                )
                            else permissionsLauncher.launch(permissionsToRequest)
                        })

                    else -> {
                        val importProgress by rememberSmsImportProgress(applicationContext)
                        // "No cursor yet" only matters while no first-run work exists; it is re-read then,
                        // never kept from before an import.
                        val noImportWork = importProgress == SmsSyncProgress.Idle
                        val firstRun by produceState<Boolean?>(null, noImportWork) {
                            value = null
                            value = repository.isFirstRun()
                        }
                        val screen = startupScreen(importProgress, firstRun)

                        // A sync on start waits out a running import, and the import is started only while no
                        // first-run work exists.
                        val startImport = shouldStartFirstRunImport(importProgress, firstRun)
                        LaunchedEffect(startImport) {
                            if (startImport) SmsSyncWorker.enqueueImport(applicationContext, firstRun = true)
                        }
                        LaunchedEffect(screen == StartupScreen.Blank) {
                            if (screen == StartupScreen.App) smsSyncer.syncAll()
                        }
                        ObserveIncomingSms(smsContentObserver)

                        // Everything the layers draw comes from the lambda argument, so the outgoing one keeps
                        // its last progress while fading; the key keeps progress ticks from re-fading.
                        updateTransition(screen, label = "import-handoff").Crossfade(
                            contentKey = { it::class },
                        ) { s ->
                            when (s) {
                                StartupScreen.Blank -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface)
                                )

                                is StartupScreen.Import -> FirstRunImportScreen(
                                    processed = s.processed,
                                    total = s.total,
                                    year = s.year,
                                    failed = s.failed,
                                    onRetry = { SmsSyncWorker.enqueueImport(applicationContext, firstRun = true) },
                                )

                                StartupScreen.App -> MainScaffold()
                            }
                        }
                    }
                }


            }
        }
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