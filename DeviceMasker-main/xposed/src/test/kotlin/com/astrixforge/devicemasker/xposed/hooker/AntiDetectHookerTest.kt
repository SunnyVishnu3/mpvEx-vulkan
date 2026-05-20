package com.astrixforge.devicemasker.xposed.hooker

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AntiDetectHookerTest {

    @Test
    fun `filterStackTrace returns original array when no hidden frames exist`() {
        val stack =
            arrayOf(
                StackTraceElement("android.app.Activity", "onCreate", "Activity.java", 1),
                StackTraceElement("com.example.App", "main", "App.kt", 2),
            )

        val filtered = AntiDetectHooker.filterStackTrace(stack)

        assertSame(stack, filtered)
    }

    @Test
    fun `filterStackTrace removes hidden frames without dropping app frames`() {
        val visible = StackTraceElement("com.example.App", "main", "App.kt", 2)
        val stack =
            arrayOf(
                StackTraceElement("io.github.lsposed.SomeHook", "call", "Hook.kt", 1),
                visible,
                StackTraceElement("de.robv.android.xposed.XposedBridge", "hook", "Xposed.java", 3),
            )

        val filtered = AntiDetectHooker.filterStackTrace(stack)

        assertArrayEquals(arrayOf(visible), filtered)
    }
}
