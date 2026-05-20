package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface
import java.net.NetworkInterface

/**
 * Network Identifier Hooker — libxposed API 101 edition.
 *
 * Spoofs network-related identifiers:
 * - WiFi MAC address (WifiInfo.getMacAddress, NetworkInterface.getHardwareAddress)
 * - WiFi SSID (WifiInfo.getSSID)
 * - WiFi BSSID (WifiInfo.getBSSID)
 * - Bluetooth MAC address (BluetoothAdapter.getAddress)
 * - Carrier name (TelephonyManager.getNetworkOperatorName)
 * - Carrier MCC/MNC (TelephonyManager.getNetworkOperator)
 *
 * Key improvement: deoptimize() on all hooked methods to prevent ART from inlining these
 * frequently-called getters in networking and WiFi management code paths.
 */
object NetworkHooker : BaseSpoofHooker("NetworkHooker") {

    private const val MAC_HEX_RADIX = 16

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        hookWifiInfo(cl, xi, pkg, snapshot)
        hookNetworkInterface(cl, xi, pkg, snapshot)
        hookBluetoothAdapter(cl, xi, pkg, snapshot)
        hookTelephonyCarrier(cl, xi, pkg, snapshot)
    }

    private fun hookWifiInfo(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val wifiInfoClass = cl.loadClassOrNull("android.net.wifi.WifiInfo") ?: return
        safeHook("WifiInfo.getMacAddress()") {
            wifiInfoClass.methodOrNull("getMacAddress")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.WIFI_MAC)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.WIFI_MAC)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
        safeHook("WifiInfo.getSSID()") {
            wifiInfoClass.methodOrNull("getSSID")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val ssid =
                                getConfiguredSpoofValue(snapshot, SpoofType.WIFI_SSID)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.WIFI_SSID)
                            if (ssid.startsWith("\"")) ssid else "\"$ssid\""
                        }
                    )
                xi.deoptimize(m)
            }
        }
        safeHook("WifiInfo.getBSSID()") {
            wifiInfoClass.methodOrNull("getBSSID")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.WIFI_BSSID)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.WIFI_BSSID)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookNetworkInterface(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val niClass = cl.loadClassOrNull("java.net.NetworkInterface") ?: return
        safeHook("NetworkInterface.getHardwareAddress()") {
            niClass.methodOrNull("getHardwareAddress")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val networkInterface =
                                chain.thisObject as? NetworkInterface ?: return@stableHooker result
                            val interfaceName = networkInterface.name ?: return@stableHooker result
                            if (
                                !interfaceName.startsWith("wlan", ignoreCase = true) &&
                                    !interfaceName.startsWith("wifi", ignoreCase = true) &&
                                    !interfaceName.startsWith("p2p", ignoreCase = true)
                            ) {
                                return@stableHooker result
                            }
                            val mac =
                                getConfiguredSpoofValue(snapshot, SpoofType.WIFI_MAC)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.WIFI_MAC)
                            runCatching {
                                    mac.split(":")
                                        .map { it.toInt(MAC_HEX_RADIX).toByte() }
                                        .toByteArray()
                                }
                                .getOrElse { result }
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookBluetoothAdapter(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val btClass = cl.loadClassOrNull("android.bluetooth.BluetoothAdapter") ?: return
        safeHook("BluetoothAdapter.getAddress()") {
            btClass.methodOrNull("getAddress")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.BLUETOOTH_MAC)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.BLUETOOTH_MAC)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookTelephonyCarrier(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val tmClass = cl.loadClassOrNull("android.telephony.TelephonyManager") ?: return
        safeHook("TelephonyManager.getNetworkOperatorName()") {
            tmClass.methodOrNull("getNetworkOperatorName")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.CARRIER_NAME)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.CARRIER_NAME)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
        safeHook("TelephonyManager.getNetworkOperator()") {
            tmClass.methodOrNull("getNetworkOperator")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.CARRIER_MCC_MNC)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.CARRIER_MCC_MNC)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Shared state
    // ─────────────────────────────────────────────────────────────

}
