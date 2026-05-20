package com.astrixforge.devicemasker.service.logmonitor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.flow.StateFlow

interface ILogMonitorRepository {
    val rows: StateFlow<List<LogMonitorRow>>
    val status: StateFlow<LogCaptureStatus>

    fun startCapture()

    fun stopCapture()

    fun clear()
}

class LogMonitorRepository(private val context: Context) : ILogMonitorRepository {
    private val monitorStore = store(context.applicationContext)

    override val rows: StateFlow<List<LogMonitorRow>> = monitorStore.rows
    override val status: StateFlow<LogCaptureStatus> = monitorStore.status

    override fun startCapture() {
        monitorStore.setStatus(LogCaptureStatus.STARTING)
        ContextCompat.startForegroundService(
            context,
            Intent(context, LiveLogCaptureService::class.java)
                .setAction(LiveLogCaptureService.ACTION_START),
        )
    }

    override fun stopCapture() {
        monitorStore.setStatus(LogCaptureStatus.STOPPING)
        context.startService(
            Intent(context, LiveLogCaptureService::class.java)
                .setAction(LiveLogCaptureService.ACTION_STOP)
        )
    }

    override fun clear() {
        monitorStore.clear()
    }

    companion object {
        @Volatile private var sharedStore: LogMonitorStore? = null

        fun store(context: Context): LogMonitorStore {
            val currentStore = sharedStore
            if (currentStore != null) return currentStore
            return synchronized(this) {
                sharedStore
                    ?: LogMonitorStore(
                            sessionFile =
                                File(context.filesDir, "logs/monitor/live_monitor_session.jsonl")
                        )
                        .also { sharedStore = it }
            }
        }
    }
}
