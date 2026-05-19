package com.astrixforge.devicemasker.xposed

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.DevicePersona
import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofType

data class HookConfigSnapshot(
    val packageName: String,
    val version: Long,
    val enabledTypes: Set<SpoofType>,
    val values: Map<SpoofType, String>,
) {
    fun isEnabled(type: SpoofType): Boolean = type in enabledTypes

    fun getValue(type: SpoofType): String? =
        values[type]?.takeIf { it.isNotBlank() }?.takeIf { type in enabledTypes }

    fun getDeviceProfilePreset(): DeviceProfilePreset? =
        getValue(SpoofType.DEVICE_PROFILE)?.let(DeviceProfilePreset::findById)

    companion object {
        fun fromPrefs(prefs: SharedPreferences, packageName: String): HookConfigSnapshot {
            val persona =
                DevicePersona.parseOrNull(
                        prefs.getString(SharedPrefsKeys.getPersonaBlobKey(packageName), null)
                    )
                    ?.takeIf { it.packageName == packageName }
            val enabled = linkedSetOf<SpoofType>()
            val values = linkedMapOf<SpoofType, String>()

            SpoofType.entries.forEach { type ->
                val typeEnabled =
                    prefs.getBoolean(SharedPrefsKeys.getSpoofEnabledKey(packageName, type), false)
                if (typeEnabled) {
                    enabled += type
                    val flatValue =
                        prefs
                            .getString(SharedPrefsKeys.getSpoofValueKey(packageName, type), null)
                            ?.takeIf { it.isNotBlank() }
                    val value = flatValue ?: persona?.getValue(type)?.takeIf { it.isNotBlank() }
                    if (value != null) values[type] = value
                }
            }

            return HookConfigSnapshot(
                packageName = packageName,
                version = prefs.getLong(SharedPrefsKeys.KEY_CONFIG_VERSION, 0L),
                enabledTypes = enabled,
                values = values,
            )
        }
    }
}
