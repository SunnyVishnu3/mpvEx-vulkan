package com.astrixforge.devicemasker.xposed.hooker

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcMapsHookerTest {

    @Test
    fun `sensitive maps path matches self and current process only`() {
        assertTrue(ProcMapsHooker.isSensitiveMapsPath("/proc/self/maps", currentPid = 123))
        assertTrue(ProcMapsHooker.isSensitiveMapsPath("/proc/123/maps", currentPid = 123))
        assertTrue(ProcMapsHooker.isSensitiveMapsPath("/proc/self/smaps", currentPid = 123))
        assertFalse(ProcMapsHooker.isSensitiveMapsPath("/proc/999/maps", currentPid = 123))
        assertFalse(ProcMapsHooker.isSensitiveMapsPath("/proc/self/status", currentPid = 123))
    }

    @Test
    fun `maps line redaction matches hook framework evidence only`() {
        assertTrue(ProcMapsHooker.shouldRedactMapsLine("7f00-7f10 r-xp /data/lib/liblspd.so"))
        assertTrue(
            ProcMapsHooker.shouldRedactMapsLine(
                "7f00-7f10 r-xp /data/app/com.astrixforge.devicemasker/base.apk"
            )
        )
        assertFalse(ProcMapsHooker.shouldRedactMapsLine("7f00-7f10 r-xp /system/lib64/libart.so"))
    }

    @Test
    fun `filter maps text removes hidden lines and preserves trailing newline`() {
        val input = "safe-one\n/data/lib/liblspd.so\nsafe-two\n"

        assertEquals("safe-one\nsafe-two\n", ProcMapsHooker.filterMapsText(input))
    }

    @Test
    fun `path arg extracts string and file path`() {
        assertEquals("/proc/self/maps", ProcMapsHooker.pathFromArg("/proc/self/maps"))
        assertEquals("/proc/self/maps", ProcMapsHooker.pathFromArg(java.io.File("/proc/self/maps")))
    }

    @Test
    fun `proc maps policy keeps byte and nio off unless risky and explicit`() {
        val packageName = "flar2.devcheck"
        val prefs =
            MapSharedPreferences(
                mapOf(
                    SharedPrefsKeys.getRiskyHooksEnabledKey(packageName) to true,
                    SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(packageName) to true,
                )
            )

        val policy = ProcMapsPolicy.fromPrefs(prefs, packageName)

        assertTrue(policy.javaLineRedactionEnabled)
        assertTrue(policy.javaByteRedactionEnabled)
        assertFalse(policy.javaNioRedactionEnabled)
    }

    private class MapSharedPreferences(private val values: Map<String, Any?>) : SharedPreferences {
        override fun getAll(): Map<String, *> = values

        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getStringSet(
            key: String?,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (values[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = error("Not needed")

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }
}
