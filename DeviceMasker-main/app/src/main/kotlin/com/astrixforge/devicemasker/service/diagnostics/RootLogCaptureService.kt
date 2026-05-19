package com.astrixforge.devicemasker.service.diagnostics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class RootLogCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: TRIGGER_MANUAL
        startAsForeground()
        serviceScope.launch {
            runCapture(trigger)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun runCapture(trigger: String) {
        val outputDir = RootCaptureStore.prepareFreshCaptureDir(this, trigger)
        if (!RootAccessManager.hasGrantedRoot()) {
            RootCaptureStore.writeManifest(
                dir = outputDir,
                trigger = trigger,
                status = "ROOT_UNAVAILABLE",
                message = "Root access is not granted; privileged boot capture was skipped.",
            )
            return
        }

        runCatching {
                RootLogCollector().collect(outputDir, targetPackage = null)
                RootCaptureStore.writeManifest(
                    dir = outputDir,
                    trigger = trigger,
                    status = "COMPLETED",
                    message = "Root capture completed.",
                )
            }
            .onFailure { error ->
                Timber.w(error, "Root log capture failed")
                RootCaptureStore.writeManifest(
                    dir = outputDir,
                    trigger = trigger,
                    status = "FAILED",
                    message = error.message ?: error::class.java.name,
                )
            }
    }

    private fun startAsForeground() {
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.root_capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.root_capture_notification_title))
            .setContentText(getString(R.string.root_capture_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TRIGGER_BOOT = "boot"
        const val TRIGGER_STARTUP = "startup"
        const val TRIGGER_MANUAL = "manual"

        private const val ACTION_CAPTURE = "com.astrixforge.devicemasker.action.ROOT_LOG_CAPTURE"
        private const val EXTRA_TRIGGER = "trigger"
        private const val CHANNEL_ID = "root_log_capture"
        private const val NOTIFICATION_ID = 4101

        fun start(context: Context, trigger: String) {
            val intent =
                Intent(context, RootLogCaptureService::class.java)
                    .setAction(ACTION_CAPTURE)
                    .putExtra(EXTRA_TRIGGER, trigger)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
