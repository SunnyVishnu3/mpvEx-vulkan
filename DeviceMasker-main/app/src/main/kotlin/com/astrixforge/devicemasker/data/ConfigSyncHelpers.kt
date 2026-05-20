package com.astrixforge.devicemasker.data

import android.content.SharedPreferences
import com.astrixforge.devicemasker.common.DevicePersona
import com.astrixforge.devicemasker.common.JsonConfig
import com.astrixforge.devicemasker.common.PersonaGenerator
import com.astrixforge.devicemasker.common.SharedPrefsKeys
import com.astrixforge.devicemasker.common.SpoofType

internal data class AppSyncState(
    val packageName: String,
    val appEnabled: Boolean,
    val riskyHooksEnabled: Boolean,
    val classLookupHidingEnabled: Boolean,
    val persona: DevicePersona?,
    val spoofTypes: List<SpoofTypeSyncState>,
) {
    fun isHookFamilyEnabled(family: String): Boolean =
        appEnabled &&
            when (family) {
                "anti_detect",
                "package_manager" -> true
                "device" ->
                    hasEnabledType(
                        SpoofType.IMEI,
                        SpoofType.IMSI,
                        SpoofType.ICCID,
                        SpoofType.SIM_COUNTRY_ISO,
                        SpoofType.NETWORK_COUNTRY_ISO,
                        SpoofType.SIM_OPERATOR_NAME,
                        SpoofType.CARRIER_MCC_MNC,
                        SpoofType.NETWORK_OPERATOR,
                        SpoofType.PHONE_NUMBER,
                        SpoofType.SERIAL,
                        SpoofType.ANDROID_ID,
                    )
                "subscription" ->
                    hasEnabledType(
                        SpoofType.ICCID,
                        SpoofType.SIM_COUNTRY_ISO,
                        SpoofType.CARRIER_NAME,
                        SpoofType.CARRIER_MCC_MNC,
                        SpoofType.PHONE_NUMBER,
                    )
                "network" ->
                    hasEnabledType(
                        SpoofType.WIFI_MAC,
                        SpoofType.WIFI_SSID,
                        SpoofType.WIFI_BSSID,
                        SpoofType.BLUETOOTH_MAC,
                        SpoofType.CARRIER_NAME,
                        SpoofType.CARRIER_MCC_MNC,
                    )
                "system",
                "system_feature",
                "sensor",
                "webview" -> hasEnabledType(SpoofType.DEVICE_PROFILE)
                "location" ->
                    hasEnabledType(
                        SpoofType.LOCATION_LATITUDE,
                        SpoofType.LOCATION_LONGITUDE,
                        SpoofType.TIMEZONE,
                        SpoofType.LOCALE,
                    )
                "advertising" ->
                    hasEnabledType(
                        SpoofType.ADVERTISING_ID,
                        SpoofType.GSF_ID,
                        SpoofType.MEDIA_DRM_ID,
                    )
                else -> false
            }

    private fun hasEnabledType(vararg types: SpoofType): Boolean =
        spoofTypes.any { it.enabled && it.type in types }
}

internal data class SpoofTypeSyncState(
    val type: SpoofType,
    val enabled: Boolean,
    val value: String?,
)

internal fun JsonConfig.syncStateFor(packageName: String): AppSyncState? {
    val group = getExplicitGroupForApp(packageName) ?: return null
    val configApp = getAppConfig(packageName)
    val appEnabled = isModuleEnabled && configApp?.isEnabled == true && group.isEnabled
    val riskyHooksEnabled = appEnabled && configApp.riskyHooksEnabled
    val classLookupHidingEnabled = riskyHooksEnabled && configApp.classLookupHidingEnabled
    val persona = if (appEnabled) PersonaGenerator.generate(group, packageName) else null

    return AppSyncState(
        packageName = packageName,
        appEnabled = appEnabled,
        riskyHooksEnabled = riskyHooksEnabled,
        classLookupHidingEnabled = classLookupHidingEnabled,
        persona = persona,
        spoofTypes =
            SpoofType.entries.map { type ->
                val typeEnabled = appEnabled && group.isTypeEnabled(type)
                val value =
                    if (typeEnabled) {
                        group.getValue(type)?.takeIf { it.isNotBlank() } ?: persona?.getValue(type)
                    } else {
                        null
                    }
                SpoofTypeSyncState(
                    type = type,
                    enabled = typeEnabled && value != null,
                    value = value,
                )
            },
    )
}

internal fun SharedPreferences.Editor.putAppSyncState(state: AppSyncState) {
    putBoolean(SharedPrefsKeys.getAppEnabledKey(state.packageName), state.appEnabled)
    putBoolean(SharedPrefsKeys.getRiskyHooksEnabledKey(state.packageName), state.riskyHooksEnabled)
    putBoolean(
        SharedPrefsKeys.getClassLookupHidingEnabledKey(state.packageName),
        state.classLookupHidingEnabled,
    )
    hookFamilyNames.forEach { family ->
        putBoolean(
            SharedPrefsKeys.getHookFamilyEnabledKey(state.packageName, family),
            state.isHookFamilyEnabled(family),
        )
    }
    putBoolean(SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(state.packageName), false)
    putBoolean(SharedPrefsKeys.getJavaProcMapsNioRedactionEnabledKey(state.packageName), false)
    if (state.persona != null) {
        putString(
            SharedPrefsKeys.getPersonaBlobKey(state.packageName),
            state.persona.toJsonString(),
        )
        putLong(SharedPrefsKeys.getPersonaVersionKey(state.packageName), state.persona.version)
    } else {
        remove(SharedPrefsKeys.getPersonaBlobKey(state.packageName))
        remove(SharedPrefsKeys.getPersonaVersionKey(state.packageName))
    }
    state.spoofTypes.forEach { putSpoofTypeState(state.packageName, it) }
}

internal fun SharedPreferences.Editor.putSpoofTypeState(
    packageName: String,
    state: SpoofTypeSyncState,
) {
    putBoolean(SharedPrefsKeys.getSpoofEnabledKey(packageName, state.type), state.enabled)
    val valueKey = SharedPrefsKeys.getSpoofValueKey(packageName, state.type)
    if (state.value != null) {
        putString(valueKey, state.value)
    } else {
        remove(valueKey)
    }
}

internal fun SharedPreferences.Editor.putAppDisabled(
    packageName: String
): SharedPreferences.Editor = putBoolean(SharedPrefsKeys.getAppEnabledKey(packageName), false)

internal fun keysForSyncedPackage(packageName: String): Set<String> = buildSet {
    add(SharedPrefsKeys.getAppEnabledKey(packageName))
    SpoofType.entries.forEach { type ->
        add(SharedPrefsKeys.getSpoofEnabledKey(packageName, type))
        add(SharedPrefsKeys.getSpoofValueKey(packageName, type))
    }
    add(SharedPrefsKeys.getRiskyHooksEnabledKey(packageName))
    add(SharedPrefsKeys.getClassLookupHidingEnabledKey(packageName))
    hookFamilyNames.forEach { family ->
        add(SharedPrefsKeys.getHookFamilyEnabledKey(packageName, family))
    }
    add(SharedPrefsKeys.getJavaProcMapsByteRedactionEnabledKey(packageName))
    add(SharedPrefsKeys.getJavaProcMapsNioRedactionEnabledKey(packageName))
    add(SharedPrefsKeys.getPersonaBlobKey(packageName))
    add(SharedPrefsKeys.getPersonaVersionKey(packageName))
}

internal val hookFamilyNames =
    listOf(
        "anti_detect",
        "device",
        "subscription",
        "network",
        "system",
        "system_feature",
        "location",
        "sensor",
        "advertising",
        "webview",
        "package_manager",
    )
