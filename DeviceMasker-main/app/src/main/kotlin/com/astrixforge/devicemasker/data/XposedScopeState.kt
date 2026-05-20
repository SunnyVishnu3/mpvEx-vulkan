package com.astrixforge.devicemasker.data

import androidx.compose.runtime.Immutable

@Immutable
sealed interface XposedScopeState {
    data object Disconnected : XposedScopeState

    @Immutable data class Connected(val packages: Set<String>) : XposedScopeState

    @Immutable data class Error(val message: String) : XposedScopeState
}
