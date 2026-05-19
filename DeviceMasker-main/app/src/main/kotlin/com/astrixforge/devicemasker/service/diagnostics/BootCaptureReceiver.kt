package com.astrixforge.devicemasker.service.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class BootCaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { RootLogCaptureService.start(context, RootLogCaptureService.TRIGGER_BOOT) }
            .onFailure { Timber.w(it, "Unable to start root capture after boot") }
    }
}
