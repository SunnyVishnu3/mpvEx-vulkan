package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.DualLog
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field

/**
 * System Hooker — spoofs Build.* static fields and SystemProperties using DeviceProfilePreset.
 *
 * ## Strategy change: field mutation + method hooks
 *
 * YukiHookAPI version: directly mutated Build static fields at hook time (`field {
 * }.get().set(...)`). This was unreliable because ART may have already constant-folded the field
 * values into app code.
 *
 * libxposed API 101 version:
 * 1. Directly modify the static fields on Build class (same as before, catches early reads)
 * 2. Hook SystemProperties.get() to intercept later reads via reflection/system property queries
 *
 * Additionally, Build.getSerial() is **not** hooked here — it's handled by DeviceHooker to maintain
 * the clear separation: DeviceHooker owns hardware identifiers, SystemHooker owns device model /
 * fingerprint metadata.
 */
object SystemHooker : BaseSpoofHooker("SystemHooker") {

    private const val MILLIS_PER_SECOND = 1_000L

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        val preset = getConfiguredDeviceProfilePreset(snapshot) ?: return

        applyBuildFieldOverrides(cl, preset)
        hookSystemProperties(cl, xi, preset, pkg)
    }

    // ─────────────────────────────────────────────────────────────
    // Direct Build static field mutation
    // ─────────────────────────────────────────────────────────────

    private fun applyBuildFieldOverrides(cl: ClassLoader, preset: DeviceProfilePreset) {
        val buildClass = cl.loadClassOrNull("android.os.Build") ?: return
        val fieldMappings =
            mapOf(
                "FINGERPRINT" to preset.fingerprint,
                "ID" to preset.buildId,
                "MODEL" to preset.model,
                "MANUFACTURER" to preset.manufacturer,
                "BRAND" to preset.brand,
                "DEVICE" to preset.device,
                "PRODUCT" to preset.product,
                "BOARD" to preset.board,
                "HARDWARE" to preset.board,
                "TIME" to preset.buildTime,
                "SUPPORTED_ABIS" to preset.supportedAbis.toTypedArray(),
                "SUPPORTED_32_BIT_ABIS" to preset.supported32BitAbis().toTypedArray(),
                "SUPPORTED_64_BIT_ABIS" to preset.supported64BitAbis().toTypedArray(),
                "TAGS" to "release-keys",
                "TYPE" to "user",
            )
        fieldMappings.forEach { (fieldName, value) ->
            if (value.isUsableBuildField()) {
                runCatching {
                        val f: Field = buildClass.getDeclaredField(fieldName)
                        f.isAccessible = true
                        f.set(null, value)
                    }
                    .onFailure { t ->
                        DualLog.warn("SystemHooker", "Could not set Build.$fieldName", t)
                    }
            }
        }

        applyBuildVersionFieldOverrides(cl, preset)
    }

    private fun applyBuildVersionFieldOverrides(cl: ClassLoader, preset: DeviceProfilePreset) {
        val versionClass = cl.loadClassOrNull("android.os.Build\$VERSION") ?: return
        mapOf("INCREMENTAL" to preset.incremental, "SECURITY_PATCH" to preset.securityPatch)
            .forEach { (fieldName, value) ->
                if (value.isNotEmpty()) {
                    runCatching {
                            val field = versionClass.getDeclaredField(fieldName)
                            field.isAccessible = true
                            field.set(null, value)
                        }
                        .onFailure { t ->
                            DualLog.warn(
                                "SystemHooker",
                                "Could not set Build.VERSION.$fieldName",
                                t,
                            )
                        }
                }
            }
    }

    // ─────────────────────────────────────────────────────────────
    // SystemProperties.get() hooks (catches reads via reflection / JNI path)
    // ─────────────────────────────────────────────────────────────

    private fun hookSystemProperties(
        cl: ClassLoader,
        xi: XposedInterface,
        preset: DeviceProfilePreset,
        pkg: String,
    ) {
        val spClass = cl.loadClassOrNull("android.os.SystemProperties") ?: return

        val propertyMappings =
            buildMap {
                    put("ro.build.fingerprint", preset.fingerprint)
                    put("ro.build.id", preset.buildId)
                    put("ro.build.version.incremental", preset.incremental)
                    put("ro.build.version.security_patch", preset.securityPatch)
                    put("ro.build.tags", "release-keys")
                    put("ro.build.type", "user")
                    put("ro.product.model", preset.model)
                    put("ro.product.manufacturer", preset.manufacturer)
                    put("ro.product.brand", preset.brand)
                    put("ro.product.device", preset.device)
                    put("ro.product.name", preset.product)
                    put("ro.product.board", preset.board)
                    put("ro.product.cpu.abilist", preset.supportedAbis.joinToString(","))
                    put("ro.product.cpu.abilist32", preset.supported32BitAbis().joinToString(","))
                    put("ro.product.cpu.abilist64", preset.supported64BitAbis().joinToString(","))
                    put("ro.build.product", preset.product)
                    put("ro.vendor.product.model", preset.model)
                    put("ro.vendor.product.brand", preset.brand)
                    put("ro.vendor.product.device", preset.device)
                    put("ro.vendor.product.manufacturer", preset.manufacturer)
                    put("ro.vendor.product.name", preset.product)
                    put("ro.bootimage.build.fingerprint", preset.fingerprint)
                    put("ro.system.build.fingerprint", preset.fingerprint)
                    put("ro.vendor.build.fingerprint", preset.fingerprint)
                }
                .filterValues { it.isNotEmpty() }

        hookStringSystemProperty(spClass, xi, propertyMappings, pkg, String::class.java)
        hookStringSystemProperty(
            spClass,
            xi,
            propertyMappings,
            pkg,
            String::class.java,
            String::class.java,
        )
        hookLongSystemProperty(spClass, xi, preset, pkg)
    }

    private fun hookStringSystemProperty(
        spClass: Class<*>,
        xi: XposedInterface,
        propertyMappings: Map<String, String>,
        pkg: String,
        vararg parameterTypes: Class<*>,
    ) {
        val signature = parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
        safeHook("SystemProperties.get$signature") {
            spClass.methodOrNull("get", *parameterTypes)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val key =
                                chain.args.firstOrNull() as? String ?: return@stableHooker result
                            val mapped = propertyMappings[key] ?: return@stableHooker result
                            reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                            mapped
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookLongSystemProperty(
        spClass: Class<*>,
        xi: XposedInterface,
        preset: DeviceProfilePreset,
        pkg: String,
    ) {
        if (preset.buildTime <= 0L) return
        safeHook("SystemProperties.getLong(String, long)") {
            spClass
                .methodOrNull("getLong", String::class.java, Long::class.javaPrimitiveType!!)
                ?.let { m ->
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val key =
                                    chain.args.firstOrNull() as? String
                                        ?: return@stableHooker result
                                if (key != "ro.build.date.utc") return@stableHooker result
                                reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                                preset.buildTime / MILLIS_PER_SECOND
                            }
                        )
                    xi.deoptimize(m)
                }
        }
    }

    private fun DeviceProfilePreset.supported32BitAbis(): List<String> =
        supportedAbis.filterNot(::is64BitAbi)

    private fun DeviceProfilePreset.supported64BitAbis(): List<String> =
        supportedAbis.filter(::is64BitAbi)

    private fun is64BitAbi(abi: String): Boolean = abi.contains("64")

    private fun Any.isUsableBuildField(): Boolean =
        when (this) {
            is String -> isNotEmpty()
            is Long -> this > 0L
            is Array<*> -> isNotEmpty()
            else -> true
        }
}
