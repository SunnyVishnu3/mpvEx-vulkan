package com.astrixforge.devicemasker.service.diagnostics

import android.content.Context
import androidx.core.content.edit
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RootAccessState {
    UNKNOWN,
    REQUESTING,
    GRANTED,
    DENIED,
    UNAVAILABLE,
}

object RootAccessManager {
    private const val PREFS_NAME = "root_access"
    private const val KEY_STATE = "state"

    private val requestMutex = Mutex()
    private val _state = MutableStateFlow(RootAccessState.UNKNOWN)
    val state: StateFlow<RootAccessState> = _state.asStateFlow()

    fun init(context: Context) {
        _state.value = readStoredState(context.applicationContext)
        refreshCachedGrant(context.applicationContext)
    }

    suspend fun requestRootAccess(context: Context, force: Boolean = false): RootAccessState =
        requestMutex.withLock {
            val appContext = context.applicationContext
            val current = _state.value
            if (!force && current == RootAccessState.DENIED) {
                return@withLock current
            }

            if (Shell.isAppGrantedRoot() == true) {
                return@withLock persist(appContext, RootAccessState.GRANTED)
            }

            _state.value = RootAccessState.REQUESTING
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { Shell.getShell().isRoot }
                        .fold(
                            onSuccess = { granted ->
                                if (granted) RootAccessState.GRANTED else RootAccessState.DENIED
                            },
                            onFailure = { RootAccessState.UNAVAILABLE },
                        )
                }
            persist(appContext, result)
        }

    fun hasGrantedRoot(): Boolean {
        if (Shell.isAppGrantedRoot() == true) {
            _state.value = RootAccessState.GRANTED
            return true
        }
        return false
    }

    fun warningRequired(): Boolean =
        _state.value == RootAccessState.DENIED || _state.value == RootAccessState.UNAVAILABLE

    private fun refreshCachedGrant(context: Context) {
        if (Shell.isAppGrantedRoot() == true) {
            persist(context, RootAccessState.GRANTED)
        }
    }

    private fun readStoredState(context: Context): RootAccessState {
        val value =
            context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_STATE, null)
        return value?.let { runCatching { RootAccessState.valueOf(it) }.getOrNull() }
            ?: RootAccessState.UNKNOWN
    }

    private fun persist(context: Context, state: RootAccessState): RootAccessState {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_STATE, state.name)
        }
        _state.value = state
        return state
    }
}
