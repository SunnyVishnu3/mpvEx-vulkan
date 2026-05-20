package com.astrixforge.devicemasker.testing

import android.content.SharedPreferences

/** In-memory [SharedPreferences] implementation for unit tests. */
class FakeSharedPreferences(private var commitResult: Boolean = true) : SharedPreferences {

    private val booleans = mutableMapOf<String, Boolean>()
    private val strings = mutableMapOf<String, String>()
    private val stringSets = mutableMapOf<String, Set<String>>()
    private val longs = mutableMapOf<String, Long>()
    private val ints = mutableMapOf<String, Int>()
    private val floats = mutableMapOf<String, Float>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = booleans + strings + stringSets + longs + ints + floats

    override fun getString(key: String?, defValue: String?): String? = strings[key] ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        stringSets[key]?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = ints[key] ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = longs[key] ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = floats[key] ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = booleans[key] ?: defValue

    override fun contains(key: String?): Boolean =
        key in booleans ||
            key in strings ||
            key in stringSets ||
            key in longs ||
            key in ints ||
            key in floats

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener?.let { listeners += it }
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        listener?.let { listeners -= it }
    }

    fun setCommitResult(result: Boolean) {
        commitResult = result
    }

    inner class Editor : SharedPreferences.Editor {
        private val pendingBooleans = mutableMapOf<String, Boolean>()
        private val pendingStrings = mutableMapOf<String, String?>()
        private val pendingStringSets = mutableMapOf<String, Set<String>?>()
        private val pendingLongs = mutableMapOf<String, Long>()
        private val pendingInts = mutableMapOf<String, Int>()
        private val pendingFloats = mutableMapOf<String, Float>()
        private val pendingRemoves = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            pendingStrings[key!!] = value
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pendingStringSets[key!!] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            pendingInts[key!!] = value
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            pendingLongs[key!!] = value
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            pendingFloats[key!!] = value
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            pendingBooleans[key!!] = value
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            pendingRemoves += key!!
        }

        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            if (!commitResult) return false
            applyPending()
            return true
        }

        override fun apply() {
            applyPending()
        }

        private fun applyPending() {
            if (clearAll) {
                booleans.clear()
                strings.clear()
                stringSets.clear()
                longs.clear()
                ints.clear()
                floats.clear()
            }
            pendingRemoves.forEach { key ->
                booleans.remove(key)
                strings.remove(key)
                stringSets.remove(key)
                longs.remove(key)
                ints.remove(key)
                floats.remove(key)
            }
            booleans.putAll(pendingBooleans)
            pendingStrings.forEach { (k, v) ->
                if (v == null) strings.remove(k) else strings[k] = v
            }
            pendingStringSets.forEach { (k, v) ->
                if (v == null) stringSets.remove(k) else stringSets[k] = v
            }
            longs.putAll(pendingLongs)
            ints.putAll(pendingInts)
            floats.putAll(pendingFloats)
        }
    }
}
