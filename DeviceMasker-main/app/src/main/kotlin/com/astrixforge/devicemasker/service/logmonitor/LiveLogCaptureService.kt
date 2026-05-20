package com.astrixforge.devicemasker.service.logmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.service.diagnostics.RootAccessManager
import com.astrixforge.devicemasker.ui.MainActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class LiveLogCaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var captureProcess: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture(startId)
        } else {
            startCapture()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        captureProcess?.destroy()
        serviceScope.cancel()
        LogMonitorRepository.store(this).setStatus(LogCaptureStatus.IDLE)
        super.onDestroy()
    }

    private fun startCapture() {
        if (captureJob?.isActive == true) return
        startAsForeground()
        val store = LogMonitorRepository.store(this)
        if (!RootAccessManager.hasGrantedRoot()) {
            store.appendRawLine("DeviceMasker live log monitor root unavailable")
            store.setStatus(LogCaptureStatus.ERROR)
            stopSelf()
            return
        }
        store.setStatus(LogCaptureStatus.RUNNING)
        captureJob =
            serviceScope.launch {
                runCatching { tailRootLogcat(store) }
                    .onFailure { error ->
                        Timber.w(error, "Live log capture failed")
                        store.appendRawLine(
                            "DeviceMasker live log monitor failed: ${error.message.orEmpty()}"
                        )
                        store.setStatus(LogCaptureStatus.ERROR)
                    }
            }
    }

    private fun stopCapture(startId: Int) {
        captureProcess?.destroy()
        captureJob?.cancel()
        LogMonitorRepository.store(this).setStatus(LogCaptureStatus.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    private fun tailRootLogcat(store: LogMonitorStore) {
        val process = ProcessBuilder("su", "-c", "logcat -b all -v threadtime").start()
        captureProcess = process
        BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
            lines.forEach { line -> store.appendRawLine(line) }
        }
        process.waitFor()
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
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.logs_monitor_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
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
            .setContentTitle(getString(R.string.logs_monitor_notification_title))
            .setContentText(getString(R.string.logs_monitor_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.astrixforge.devicemasker.action.START_LOG_MONITOR"
        const val ACTION_STOP = "com.astrixforge.devicemasker.action.STOP_LOG_MONITOR"

        private const val CHANNEL_ID = "logs_monitor"
        private const val NOTIFICATION_ID = 4102
    }
}
