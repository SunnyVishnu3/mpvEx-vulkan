package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface

/**
 * Spoofs package-manager feature checks that are directly implied by the selected device profile.
 */
object SystemFeatureHooker : BaseSpoofHooker("SystemFeatureHooker") {

    private val NFC_FEATURES =
        setOf(
            "android.hardware.nfc",
            "android.hardware.nfc.hce",
            "android.hardware.nfc.hcef",
            "android.hardware.nfc.uicc",
            "android.hardware.nfc.ese",
        )

    private val TELEPHONY_FEATURES =
        setOf(
            "android.hardware.telephony",
            "android.hardware.telephony.calling",
            "android.hardware.telephony.data",
            "android.hardware.telephony.gsm",
            "android.hardware.telephony.ims",
            "android.hardware.telephony.messaging",
            "android.hardware.telephony.radio.access",
            "android.hardware.telephony.subscription",
            "android.software.telecom",
        )

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        val preset = getConfiguredDeviceProfilePreset(snapshot) ?: return
        val pmClass = cl.loadClassOrNull("android.app.ApplicationPackageManager") ?: return

        hookHasSystemFeature(pmClass, xi, preset, pkg, String::class.java)
        hookHasSystemFeature(
            pmClass,
            xi,
            preset,
            pkg,
            String::class.java,
            Int::class.javaPrimitiveType!!,
        )
    }

    private fun hookHasSystemFeature(
        pmClass: Class<*>,
        xi: XposedInterface,
        preset: DeviceProfilePreset,
        pkg: String,
        vararg parameterTypes: Class<*>,
    ) {
        val signature = parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
        safeHook("PackageManager.hasSystemFeature$signature") {
            pmClass.methodOrNull("hasSystemFeature", *parameterTypes)?.let { method ->
                xi.hook(method)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val feature =
                                chain.args.firstOrNull() as? String ?: return@stableHooker result
                            val spoofed =
                                featureOverride(feature, preset) ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                            spoofed
                        }
                    )
                xi.deoptimize(method)
            }
        }
    }

    private fun featureOverride(feature: String, preset: DeviceProfilePreset): Boolean? =
        when (feature) {
            in NFC_FEATURES -> preset.hasNfc
            in TELEPHONY_FEATURES -> preset.simCount > 0
            else -> null
        }
}
