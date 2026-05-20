package com.astrixforge.devicemasker.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeDismissFocusHandlerTest {
    @Test
    fun dismissesFocusedSearchAfterVisibleImeIsHidden() {
        assertTrue(
            shouldClearFocusAfterImeDismiss(
                isFocused = true,
                hadVisibleImeWhileFocused = true,
                isImeVisible = false,
            )
        )
    }

    @Test
    fun keepsFocusBeforeImeHasBeenVisible() {
        assertFalse(
            shouldClearFocusAfterImeDismiss(
                isFocused = true,
                hadVisibleImeWhileFocused = false,
                isImeVisible = false,
            )
        )
    }

    @Test
    fun keepsFocusWhileImeIsVisible() {
        assertFalse(
            shouldClearFocusAfterImeDismiss(
                isFocused = true,
                hadVisibleImeWhileFocused = true,
                isImeVisible = true,
            )
        )
    }

    @Test
    fun ignoresImeDismissWhenFieldIsNotFocused() {
        assertFalse(
            shouldClearFocusAfterImeDismiss(
                isFocused = false,
                hadVisibleImeWhileFocused = true,
                isImeVisible = false,
            )
        )
    }
}
