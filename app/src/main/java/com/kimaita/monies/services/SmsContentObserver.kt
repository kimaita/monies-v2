package com.kimaita.monies.services

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

class SmsContentObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsSyncer: SmsSyncer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pendingSync: Job? = null
    private var observer: ContentObserver? = null

    fun start() {
        if (observer != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                pendingSync?.cancel()
                pendingSync = scope.launch {
                    delay(DEBOUNCE_MS.milliseconds)
                    try {
                        smsSyncer.syncAll()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.e(e, "Foreground SMS sync failed")
                    }
                }
            }
        }
        observer = obs
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI, true, obs,
        )
        Timber.d("SMS content observer registered")
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        pendingSync?.cancel()
        pendingSync = null
        Timber.d("SMS content observer unregistered")
    }

    private companion object {
        const val DEBOUNCE_MS = 1_200L
    }
}