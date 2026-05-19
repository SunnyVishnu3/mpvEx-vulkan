package com.astrixforge.devicemasker.data

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Xposed Preferences Writer — libxposed API 101 / XposedService edition.
 *
 * Writes to [SharedPreferences] obtained from [XposedService.getRemotePreferences]. LSPosed
 * delivers these writes live to the hooked process via `getRemotePreferences()` in
 * [com.astrixforge.devicemasker.xposed.XposedEntry] — **no app restart required**.
 *
 * **Architecture contract:**
 * - [XposedService] is obtained asynchronously via [XposedServiceHelper.registerListener]. Writes
 *   are silently dropped if the service is not yet connected (module not active).
 * - All key generation DELEGATES to [SharedPrefsKeys] in `:common` — same keys both sides.
 * - The hook side reads via `xi.getRemotePreferences(PREFS_GROUP)`.
 *
 * **Usage:**
 * 1. Call [XposedPrefs.init] once in `DeviceMaskerApp.onCreate()`.
 * 2. Write config via [ConfigSync] (which calls [getPrefs] internally). Writes no-op gracefully if
 *    the module is not active.
 */
object XposedPrefs {

    private const val TAG = "XposedPrefs"

    /**
     * Preference group name — **must match**
     * [com.astrixforge.devicemasker.xposed.XposedEntry.PREFS_GROUP]. This string identifies the
     * SharedPreferences file/bucket on the LSPosed side.
     */
    const val PREFS_GROUP = "device_masker_config"

    @Volatile internal var xposedService: XposedService? = null
    @Volatile internal var initialized = false
    private val serviceBindCallbacks = CopyOnWriteArrayList<() -> Unit>()
    internal val serviceConnectedState = MutableStateFlow(false)
    internal val scopeState = MutableStateFlow<XposedScopeState>(XposedScopeState.Disconnected)
    val isServiceConnected: StateFlow<Boolean> = serviceConnectedState.asStateFlow()
    val scopedPackages: StateFlow<XposedScopeState> = scopeState.asStateFlow()

    internal fun reset() {
        xposedService = null
        initialized = false
        serviceConnectedState.value = false
        scopeState.value = XposedScopeState.Disconnected
        serviceBindCallbacks.clear()
    }

    /**
     * Registers the [XposedServiceHelper] listener.
     *
     * Must be called once in `DeviceMaskerApp.onCreate()`. Extra calls are ignored locally because
     * libxposed service listener registration should happen only once.
     */
    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
        }
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    xposedService = service
                    serviceConnectedState.value = true
                    refreshScope(service)
                    Timber.tag(TAG).i("XposedService connected (%s)", service.frameworkName)
                    serviceBindCallbacks.forEach { callback ->
                        runCatching(callback).onFailure { e ->
                            Timber.tag(TAG).w(e, "XposedService bind callback failed")
                        }
                    }
                }

                override fun onServiceDied(service: XposedService) {
                    xposedService = null
                    serviceConnectedState.value = false
                    scopeState.value = XposedScopeState.Disconnected
                    Timber.tag(TAG).w("XposedService died")
                }
            }
        )
    }

    fun addServiceBindCallback(callback: () -> Unit) {
        serviceBindCallbacks += callback
        if (xposedService != null) {
            runCatching(callback).onFailure { e ->
                Timber.tag(TAG).w(e, "Immediate XposedService callback failed")
            }
        }
    }

    fun isConnected(): Boolean = xposedService != null

    fun refreshScope() {
        val service = xposedService
        if (service == null) {
            scopeState.value = XposedScopeState.Disconnected
            return
        }
        refreshScope(service)
    }

    private fun refreshScope(service: XposedService) {
        scopeState.value =
            runCatching { XposedScopeState.Connected(service.scope.toSet()) }
                .onFailure { e -> Timber.tag(TAG).w(e, "Failed to read LSPosed scope") }
                .getOrElse { e -> XposedScopeState.Error(e.message.orEmpty()) }
    }

    /**
     * Returns the [SharedPreferences] instance backed by [XposedService.getRemotePreferences].
     *
     * Returns `null` if the module is not active (LSPosed not running, or module disabled). Callers
     * should silently skip writes when `null` is returned.
     */
    fun getPrefs(): SharedPreferences? {
        return runCatching { xposedService?.getRemotePreferences(PREFS_GROUP) }
            .onFailure { e ->
                Timber.tag(TAG).w(e, "getRemotePreferences failed (module inactive?)")
            }
            .getOrNull()
    }
}
