package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface

/**
 * Subscription Info Hooker — new in libxposed API 101 migration (Gap 4.6).
 *
 * Many banking and identity verification apps cross-check TelephonyManager with
 * SubscriptionInfo/SubscriptionManager to detect SIM manipulation. DeviceHooker covers
 * TelephonyManager; this hooker covers the SubscriptionManager path.
 *
 * Hooks:
 * - SubscriptionInfo.getIccId() — ICCID
 * - SubscriptionInfo.getCountryIso() — SIM country ISO
 * - SubscriptionInfo.getCarrierName() / getDisplayName() — carrier display name
 * - SubscriptionInfo.getMcc() / getMccString() — Mobile Country Code
 * - SubscriptionInfo.getMnc() / getMncString() — Mobile Network Code
 * - SubscriptionInfo.getNumber() — phone number
 * - SubscriptionInfo.getSubscriberId() — IMSI (via SubscriptionInfo on newer APIs)
 * - SubscriptionManager.getActiveSubscriptionInfoCount() — profile-consistent active SIM count
 * - SubscriptionManager.getActiveSubscriptionInfoCountMax() — profile-consistent maximum SIM count
 *
 * ## SIM count consistency
 * We do NOT hide SIM slots unless the device preset indicates a single-SIM device. Hiding slots
 * when the device has 2 SIM cards is more detectable than showing 2 consistent SIMs.
 */
object SubscriptionHooker : BaseSpoofHooker("SubscriptionHooker") {

    private const val MCC_LENGTH = 3
    private const val MIN_MCC_MNC_LENGTH = 5
    private const val MAX_MCC_MNC_LENGTH = 6

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        val siClass = cl.loadClassOrNull("android.telephony.SubscriptionInfo") ?: return

        hookSubscriptionInfoGetters(siClass, xi, pkg, snapshot)
        hookSubscriptionManager(cl, xi, pkg, snapshot)
    }

    private fun hookSubscriptionInfoGetters(
        siClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        hookSubscriptionStringGetter(siClass, xi, pkg, snapshot, "getIccId", SpoofType.ICCID)
        hookSubscriptionStringGetter(
            siClass,
            xi,
            pkg,
            snapshot,
            "getCountryIso",
            SpoofType.SIM_COUNTRY_ISO,
        )
        hookSubscriptionStringGetter(
            siClass,
            xi,
            pkg,
            snapshot,
            "getCarrierName",
            SpoofType.CARRIER_NAME,
        )
        hookSubscriptionStringGetter(
            siClass,
            xi,
            pkg,
            snapshot,
            "getDisplayName",
            SpoofType.CARRIER_NAME,
        )
        hookCarrierCodeGetter(siClass, xi, pkg, snapshot, "getMcc") { it.first.toInt() }
        hookCarrierCodeGetter(siClass, xi, pkg, snapshot, "getMnc") { it.second.toInt() }
        hookCarrierCodeGetter(siClass, xi, pkg, snapshot, "getMccString") { it.first }
        hookCarrierCodeGetter(siClass, xi, pkg, snapshot, "getMncString") { it.second }
        hookSubscriptionStringGetter(
            siClass,
            xi,
            pkg,
            snapshot,
            "getNumber",
            SpoofType.PHONE_NUMBER,
        )
    }

    private fun hookSubscriptionStringGetter(
        siClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
        methodName: String,
        spoofType: SpoofType,
    ) {
        safeHook("SubscriptionInfo.$methodName()") {
            siClass.methodOrNull(methodName)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, spoofType)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, spoofType)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookCarrierCodeGetter(
        siClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
        methodName: String,
        codePart: (Pair<String, String>) -> Any,
    ) {
        safeHook("SubscriptionInfo.$methodName()") {
            siClass.methodOrNull(methodName)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val mccMnc =
                                getConfiguredSpoofValue(snapshot, SpoofType.CARRIER_MCC_MNC)
                                    ?: return@stableHooker result
                            val carrier = parseCarrierMccMnc(mccMnc) ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.CARRIER_MCC_MNC)
                            codePart(carrier)
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookSubscriptionManager(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val smClass = cl.loadClassOrNull("android.telephony.SubscriptionManager") ?: return
        hookSubscriptionManagerCount(smClass, xi, pkg, snapshot, "getActiveSubscriptionInfoCount")
        hookSubscriptionManagerCount(
            smClass,
            xi,
            pkg,
            snapshot,
            "getActiveSubscriptionInfoCountMax",
        )
    }

    private fun hookSubscriptionManagerCount(
        smClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
        methodName: String,
    ) {
        safeHook("SubscriptionManager.$methodName()") {
            smClass.methodOrNull(methodName)?.let { m ->
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

    private fun parseCarrierMccMnc(value: String): Pair<String, String>? {
        val digits = value.trim()
        if (
            digits.length !in MIN_MCC_MNC_LENGTH..MAX_MCC_MNC_LENGTH || digits.any { !it.isDigit() }
        )
            return null
        return digits.take(MCC_LENGTH) to digits.drop(MCC_LENGTH)
    }

    private fun DeviceProfilePreset.safeSimCount(): Int = simCount.coerceIn(1, 2)
}
