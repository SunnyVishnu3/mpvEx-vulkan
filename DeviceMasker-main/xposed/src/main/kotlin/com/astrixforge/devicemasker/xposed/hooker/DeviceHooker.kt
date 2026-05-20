package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface

/**
 * Device Identifier Hooker — libxposed API 101 edition.
 *
 * Hooks TelephonyManager, Build.getSerial(), and Settings.Secure.getString() to spoof:
 * - IMEI (getDeviceId, getImei — both no-arg and slot-indexed variants)
 * - IMSI / SubscriberId (getSubscriberId)
 * - ICCID (getSimSerialNumber)
 * - Serial number (Build.getSerial(), SystemProperties ro.serialno)
 * - Android ID (Settings.Secure "android_id")
 * - SIM country ISO, network country ISO, operator names, MCC/MNC
 * - Phone number (getLine1Number)
 *
 * ## Key improvements over YukiHookAPI version
 * 1. `deoptimize()` called after every hook — bypasses ART inlining on heavily-used getters
 * 2. Individual `safeHook()` per method — one OEM missing a signature cannot cascade failures
 * 3. RemotePreferences read in intercept lambda — always reflects latest UI configuration
 * 4. `loadClassOrNull()` used everywhere — isolated renderer processes don't crash
 *
 * ## API 101 hook pattern
 * Lambda-based interceptor: `xi.hook(m).intercept(stableHooker { chain -> proceed(); return spoofed
 * })`. No static callback classes or hook annotations are needed.
 */
object DeviceHooker : BaseSpoofHooker("DeviceHooker") {

    private const val ANDROID_ID_KEY = "android_id"

    private val SERIAL_PROPERTY_KEYS = setOf("ro.serialno", "ro.boot.serialno", "ril.serialnumber")

    private val SIM_COUNT_METHODS = listOf("getSimCount", "getPhoneCount", "getActiveModemCount")

    private val TELEPHONY_GETTERS =
        listOf(
            TelephonyGetter("getDeviceId", SpoofType.IMEI, hasSlotOverload = true),
            TelephonyGetter("getImei", SpoofType.IMEI, hasSlotOverload = true),
            TelephonyGetter("getSubscriberId", SpoofType.IMSI, hasSlotOverload = true),
            TelephonyGetter("getSimSerialNumber", SpoofType.ICCID, hasSlotOverload = true),
            TelephonyGetter("getSimCountryIso", SpoofType.SIM_COUNTRY_ISO, hasSlotOverload = true),
            TelephonyGetter(
                "getNetworkCountryIso",
                SpoofType.NETWORK_COUNTRY_ISO,
                hasSlotOverload = true,
            ),
            TelephonyGetter(
                "getSimOperatorName",
                SpoofType.SIM_OPERATOR_NAME,
                hasSlotOverload = true,
            ),
            TelephonyGetter("getSimOperator", SpoofType.CARRIER_MCC_MNC, hasSlotOverload = true),
            TelephonyGetter(
                "getNetworkOperator",
                SpoofType.NETWORK_OPERATOR,
                hasSlotOverload = true,
            ),
            TelephonyGetter("getLine1Number", SpoofType.PHONE_NUMBER, hasSlotOverload = false),
        )

    /**
     * Registers all TelephonyManager, Build, and Settings.Secure hooks. Called once per target
     * process from [XposedEntry.onPackageLoaded].
     *
     * @param cl The target app's ClassLoader
     * @param xi The XposedInterface hook engine
     * @param pkg The target app's package name
     */
    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        hookTelephonyManager(cl, xi, pkg, snapshot)
        hookBuildSerial(cl, xi, pkg, snapshot)
        hookSettingsSecure(cl, xi, pkg, snapshot)
        hookSystemProperties(cl, xi, pkg, snapshot)
    }

    // ─────────────────────────────────────────────────────────────
    // Telephony Manager hooks
    // ─────────────────────────────────────────────────────────────

    private fun hookTelephonyManager(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val tm = cl.loadClassOrNull("android.telephony.TelephonyManager") ?: return
        val intClass = Int::class.javaPrimitiveType!!

        TELEPHONY_GETTERS.forEach { getter ->
            hookTelephonyGetter(tm, xi, pkg, snapshot, getter)
            if (getter.hasSlotOverload) hookTelephonyGetter(tm, xi, pkg, snapshot, getter, intClass)
        }
        hookSimCountGetters(tm, xi, pkg, snapshot)
    }

    private fun hookTelephonyGetter(
        tm: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
        getter: TelephonyGetter,
        vararg parameterTypes: Class<*>,
    ) {
        val signature = parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
        safeHook("${getter.methodName}$signature") {
            tm.methodOrNull(getter.methodName, *parameterTypes)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, getter.spoofType)
                                    ?: return@stableHooker chain.proceed()
                            reportSpoofEvent(pkg, getter.spoofType)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookSimCountGetters(
        tm: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        SIM_COUNT_METHODS.forEach { methodName ->
            hookSimCountGetter(tm, xi, pkg, snapshot, methodName)
        }
    }

    private fun hookSimCountGetter(
        tm: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
        methodName: String,
    ) {
        safeHook("TelephonyManager.$methodName()") {
            tm.methodOrNull(methodName)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val preset =
                                getConfiguredDeviceProfilePreset(snapshot)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                            preset.safeSimCount()
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Build.getSerial() hook
    // ─────────────────────────────────────────────────────────────

    private fun hookBuildSerial(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        safeHook("Build.getSerial()") {
            val buildClass = cl.loadClassOrNull("android.os.Build") ?: return@safeHook
            buildClass.methodOrNull("getSerial")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.SERIAL)
                                    ?: return@stableHooker chain.proceed()
                            reportSpoofEvent(pkg, SpoofType.SERIAL)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Settings.Secure.getString() hook (intercepts android_id reads)
    // ─────────────────────────────────────────────────────────────

    private fun hookSettingsSecure(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        safeHook("Settings.Secure.getString()") {
            val secureClass =
                cl.loadClassOrNull("android.provider.Settings\$Secure") ?: return@safeHook
            val resolverClass =
                cl.loadClassOrNull("android.content.ContentResolver") ?: return@safeHook
            hookAndroidIdSetting(secureClass, xi, pkg, snapshot, "getString", resolverClass)
        }
        safeHook("Settings.Secure.getStringForUser()") {
            val secureClass =
                cl.loadClassOrNull("android.provider.Settings\$Secure") ?: return@safeHook
            val resolverClass =
                cl.loadClassOrNull("android.content.ContentResolver") ?: return@safeHook
            hookAndroidIdSetting(
                secureClass,
                xi,
                pkg,
                snapshot,
                "getStringForUser",
                resolverClass,
                Int::class.javaPrimitiveType!!,
            )
        }
    }

    private fun hookAndroidIdSetting(
        secureClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
        methodName: String,
        resolverClass: Class<*>,
        vararg extraParameterTypes: Class<*>,
    ) {
        val parameterTypes = arrayOf(resolverClass, String::class.java, *extraParameterTypes)
        secureClass.methodOrNull(methodName, *parameterTypes)?.let { m ->
            xi.hook(m)
                .intercept(
                    stableHooker { chain ->
                        val result = chain.proceed()
                        val key = chain.args.getOrNull(1) as? String ?: return@stableHooker result
                        if (key != ANDROID_ID_KEY) return@stableHooker result
                        val spoofed =
                            getConfiguredSpoofValue(snapshot, SpoofType.ANDROID_ID)
                                ?: return@stableHooker result
                        reportSpoofEvent(pkg, SpoofType.ANDROID_ID)
                        spoofed
                    }
                )
            xi.deoptimize(m)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SystemProperties.get() — intercepts ro.serialno reads
    // ─────────────────────────────────────────────────────────────

    private fun hookSystemProperties(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        safeHook("SystemProperties.get(String)") {
            val spClass = cl.loadClassOrNull("android.os.SystemProperties") ?: return@safeHook
            hookSerialSystemProperty(spClass, xi, pkg, snapshot, String::class.java)
        }
        safeHook("SystemProperties.get(String, String)") {
            val spClass = cl.loadClassOrNull("android.os.SystemProperties") ?: return@safeHook
            hookSerialSystemProperty(
                spClass,
                xi,
                pkg,
                snapshot,
                String::class.java,
                String::class.java,
            )
        }
    }

    private fun hookSerialSystemProperty(
        spClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
        vararg parameterTypes: Class<*>,
    ) {
        spClass.methodOrNull("get", *parameterTypes)?.let { m ->
            xi.hook(m)
                .intercept(
                    stableHooker { chain ->
                        val result = chain.proceed()
                        val key = chain.args.firstOrNull() as? String ?: return@stableHooker result
                        if (key !in SERIAL_PROPERTY_KEYS) return@stableHooker result
                        val spoofed =
                            getConfiguredSpoofValue(snapshot, SpoofType.SERIAL)
                                ?: return@stableHooker result
                        reportSpoofEvent(pkg, SpoofType.SERIAL)
                        spoofed
                    }
                )
            xi.deoptimize(m)
        }
    }

    private data class TelephonyGetter(
        val methodName: String,
        val spoofType: SpoofType,
        val hasSlotOverload: Boolean,
    )

    private fun DeviceProfilePreset.safeSimCount(): Int = simCount.coerceIn(1, 2)
}
