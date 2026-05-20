package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface

/**
 * Advertising Identifier Hooker — libxposed API 101 edition.
 *
 * Spoofs advertising and tracking identifiers:
 * - Google Advertising ID (AdvertisingIdClient.Info.getId())
 * - GSF ID via Gservices.getString/getLong("android_id")
 * - MediaDrm device unique ID (getPropertyByteArray("deviceUniqueId"))
 */
object AdvertisingHooker : BaseSpoofHooker("AdvertisingHooker") {

    private const val GSERVICES_ANDROID_ID_KEY = "android_id"
    private const val GSERVICES_GETTER_PARAMETER_COUNT = 3
    private const val HEX_RADIX = 16

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        hookAdvertisingIdClient(cl, xi, pkg, snapshot)
        hookGservices(cl, xi, pkg, snapshot)
        hookMediaDrm(cl, xi, pkg, snapshot)
    }

    private fun hookAdvertisingIdClient(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        safeHook("AdvertisingIdClient.Info.getId()") {
            val infoClass =
                cl.loadClassOrNull(
                    "com.google.android.gms.ads.identifier.AdvertisingIdClient\$Info"
                ) ?: return@safeHook
            infoClass.methodOrNull("getId")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.ADVERTISING_ID)
                                    ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.ADVERTISING_ID)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookGservices(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val gservicesClass = cl.loadClassOrNull("com.google.android.gsf.Gservices") ?: return
        hookGservicesString(gservicesClass, xi, pkg, snapshot)
        hookGservicesLong(gservicesClass, xi, pkg, snapshot)
    }

    private fun hookGservicesString(
        gservicesClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        safeHook("Gservices.getString(ContentResolver, String)") {
            gservicesClass
                .getDeclaredMethods()
                .filter {
                    it.name == "getString" && it.parameterCount == GSERVICES_GETTER_PARAMETER_COUNT
                }
                .forEach { m ->
                    m.isAccessible = true
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val key =
                                    chain.args.getOrNull(1) as? String ?: return@stableHooker result
                                if (key != GSERVICES_ANDROID_ID_KEY) return@stableHooker result
                                val spoofed =
                                    getConfiguredSpoofValue(snapshot, SpoofType.GSF_ID)
                                        ?: return@stableHooker result
                                reportSpoofEvent(pkg, SpoofType.GSF_ID)
                                spoofed
                            }
                        )
                    xi.deoptimize(m)
                }
        }
    }

    private fun hookGservicesLong(
        gservicesClass: Class<*>,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        safeHook("Gservices.getLong(ContentResolver, String, long)") {
            gservicesClass
                .getDeclaredMethods()
                .filter {
                    it.name == "getLong" && it.parameterCount == GSERVICES_GETTER_PARAMETER_COUNT
                }
                .forEach { m ->
                    m.isAccessible = true
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val key =
                                    chain.args.getOrNull(1) as? String ?: return@stableHooker result
                                if (key != GSERVICES_ANDROID_ID_KEY) return@stableHooker result
                                val spoofed =
                                    getConfiguredSpoofValue(snapshot, SpoofType.GSF_ID)
                                        ?: return@stableHooker result
                                val finalVal =
                                    runCatching { spoofed.toLong(HEX_RADIX) }
                                        .getOrElse { result as Long }
                                reportSpoofEvent(pkg, SpoofType.GSF_ID)
                                finalVal
                            }
                        )
                    xi.deoptimize(m)
                }
        }
    }

    private fun hookMediaDrm(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        safeHook("MediaDrm.getPropertyByteArray(String)") {
            val drmClass = cl.loadClassOrNull("android.media.MediaDrm") ?: return@safeHook
            drmClass.methodOrNull("getPropertyByteArray", String::class.java)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val property =
                                chain.args.firstOrNull() as? String ?: return@stableHooker result
                            if (property != "deviceUniqueId") return@stableHooker result
                            val spoofed =
                                getConfiguredSpoofValue(snapshot, SpoofType.MEDIA_DRM_ID)
                                    ?: return@stableHooker result
                            val bytes = hexToBytes(spoofed) ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.MEDIA_DRM_ID)
                            bytes
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val cleanHex = hex.trim()
        if (cleanHex.length < 2 || cleanHex.length % 2 != 0) return null
        return runCatching {
                cleanHex.chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
            }
            .getOrNull()
    }
}
