package com.astrixforge.devicemasker.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.service.diagnostics.RootAccessManager
import com.astrixforge.devicemasker.service.diagnostics.RootAccessState
import com.astrixforge.devicemasker.service.diagnostics.RootLogCaptureService
import kotlinx.coroutines.delay

private const val STARTUP_ROOT_CAPTURE_DELAY_MILLIS = 1500L

@Composable
internal fun RequestStartupRootCapture(appContext: Context) {
    LaunchedEffect(Unit) {
        withFrameNanos {}
        delay(STARTUP_ROOT_CAPTURE_DELAY_MILLIS)
        requestRootAndCaptureStartup(appContext, force = false)
    }
}

@Composable
internal fun ShowRootWarningWhenUnavailable(
    rootAccessState: RootAccessState,
    showWarning: () -> Unit,
) {
    val currentShowWarning by rememberUpdatedState(showWarning)
    LaunchedEffect(rootAccessState) {
        if (
            rootAccessState == RootAccessState.DENIED ||
                rootAccessState == RootAccessState.UNAVAILABLE
        ) {
            currentShowWarning()
        }
    }
}

@Composable
internal fun ApplyEdgeToEdgeStyle(activity: ComponentActivity, themeMode: ThemeMode) {
    val isSystemDark = isSystemInDarkTheme()
    val isDarkModeActive =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    DisposableEffect(isDarkModeActive) {
        activity.enableEdgeToEdge(
            statusBarStyle = systemBarStyle(isDarkModeActive),
            navigationBarStyle = systemBarStyle(isDarkModeActive),
        )
        onDispose {}
    }
}

internal suspend fun requestRootAndCaptureStartup(appContext: Context, force: Boolean): Boolean {
    val result = RootAccessManager.requestRootAccess(appContext, force = force)
    if (result != RootAccessState.GRANTED) return false

    RootLogCaptureService.start(appContext, RootLogCaptureService.TRIGGER_STARTUP)
    return true
}

private fun systemBarStyle(isDarkModeActive: Boolean): SystemBarStyle =
    if (isDarkModeActive) {
        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    } else {
        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
    }
