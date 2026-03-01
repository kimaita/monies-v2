package com.kimaita.monies

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kimaita.monies.data.DefaultTransactionsRepository
import com.kimaita.monies.ui.PermissionScreen
import com.kimaita.monies.ui.account.AccountScreen
import com.kimaita.monies.ui.analytics.AnalyticsScreen
import com.kimaita.monies.ui.home.HomeScreen
import com.kimaita.monies.ui.profile.ProfileScreen
import com.kimaita.monies.ui.theme.AppTheme
import com.kimaita.monies.ui.transactions.TransactionsScreen
import com.kimaita.monies.worker.SmsSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: DefaultTransactionsRepository
    private val permissionsToRequest = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.READ_PHONE_STATE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {

                var hasRequiredPermissions by remember {
                    mutableStateOf(checkPermissions())
                }

                val coroutineScope = rememberCoroutineScope()

                val permissionsLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val smsGranted = permissions[Manifest.permission.READ_SMS] ?: false
                        hasRequiredPermissions = smsGranted

                        if (smsGranted) {
                            coroutineScope.launch {
                                if (repository.isFirstRun()) {
                                    Timber.i("Prepping FIRST RUN")
                                    // FIRST RUN: Enqueue the heavy-duty WorkManager job.
                                    val syncRequest =
                                        OneTimeWorkRequestBuilder<SmsSyncWorker>().build()
                                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                                        SmsSyncWorker.WORK_NAME,
                                        ExistingWorkPolicy.KEEP,
                                        syncRequest
                                    )
                                }
                            }
                        }
                    }

                // --- Main UI Rendering ---
                if (hasRequiredPermissions) {
                    LaunchedEffect(Unit) {
                        if (!repository.isFirstRun()) {
                            repository.syncAllSms(applicationContext)
                        }else{
                            Timber.i("Prepping FIRST RUN")
                            // FIRST RUN: Enqueue the heavy-duty WorkManager job.
                            val syncRequest =
                                OneTimeWorkRequestBuilder<SmsSyncWorker>().build()
                            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                                SmsSyncWorker.WORK_NAME,
                                ExistingWorkPolicy.KEEP,
                                syncRequest
                            )
                        }
                    }
                    MoniesNavHost()
                } else {
                    LaunchedEffect(Unit) {
                        permissionsLauncher.launch(permissionsToRequest)
                    }
                    PermissionScreen {
                        permissionsLauncher.launch(permissionsToRequest)
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
fun MoniesNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                navController = navController,
//                viewModel = viewModel()
            )
        }
        composable("transactions") {
            TransactionsScreen(
                navController = navController,
                viewModel = hiltViewModel()
            )
        }
        composable("analytics") {
            AnalyticsScreen(
                navController = navController,
                viewModel = hiltViewModel()
            )
        }
        composable("account") {
            ProfileScreen(
                navController = navController,
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = "account/{subjectId}",
            arguments = listOf(navArgument("subjectId") { type = NavType.StringType })
        ) {
            AccountScreen(navController = navController)
        }
    }
}