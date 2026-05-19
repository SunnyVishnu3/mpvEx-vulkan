package com.astrixforge.devicemasker.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClearFocusOnImeDismiss(isFocused: Boolean, clearFocus: () -> Unit) {
    val isImeVisible = WindowInsets.isImeVisible
    val currentClearFocus by rememberUpdatedState(clearFocus)
    var hadVisibleImeWhileFocused by remember { mutableStateOf(false) }

    LaunchedEffect(isFocused, isImeVisible) {
        if (!isFocused) {
            hadVisibleImeWhileFocused = false
            return@LaunchedEffect
        }

        if (isImeVisible) {
            hadVisibleImeWhileFocused = true
            return@LaunchedEffect
        }

        if (
            shouldClearFocusAfterImeDismiss(
                isFocused = isFocused,
                hadVisibleImeWhileFocused = hadVisibleImeWhileFocused,
                isImeVisible = isImeVisible,
            )
        ) {
            currentClearFocus()
            hadVisibleImeWhileFocused = false
        }
    }
}

internal fun shouldClearFocusAfterImeDismiss(
    isFocused: Boolean,
    hadVisibleImeWhileFocused: Boolean,
    isImeVisible: Boolean,
): Boolean = isFocused && hadVisibleImeWhileFocused && !isImeVisible
