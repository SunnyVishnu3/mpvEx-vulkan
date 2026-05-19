package com.astrixforge.devicemasker.xposed.hooker

import android.hardware.Sensor
import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface

/**
 * Sensor Hooker — libxposed API 101 edition.
 *
 * Spoofs sensor-related information to match the active DeviceProfilePreset:
 * - SensorManager.getSensorList(int) — filters sensor list to mask unique hardware fingerprints
 * - Sensor.getVendor() — replaced with preset manufacturer for consistency
 * - Sensor.getName() — removes OEM-specific prefixes that leak device model
 * - Sensor.getVersion() — normalized to prevent version-based fingerprinting
 *
 * Sensor list filtering prevents apps from fingerprinting the number and type of sensors, which are
 * device-specific and hard to spoof individually.
 */
object SensorHooker : BaseSpoofHooker("SensorHooker") {

    private const val TYPE_ALL = -1
    private const val SENSOR_LIST_FINGERPRINT_THRESHOLD = 10
    private const val MAX_NORMALIZED_SENSOR_VERSION = 3
    private const val NORMALIZED_SENSOR_VERSION = 1

    // Sensor types that should always be present on modern Android devices
    private val ESSENTIAL_SENSOR_TYPES =
        setOf(
            1, // TYPE_ACCELEROMETER
            2, // TYPE_MAGNETIC_FIELD
            4, // TYPE_GYROSCOPE
            5, // TYPE_LIGHT
            8, // TYPE_PROXIMITY
            11, // TYPE_ROTATION_VECTOR
        )

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        // Load preset at hook-registration time. If DEVICE_PROFILE is disabled, missing, or
        // invalid, leave all sensor APIs untouched for this process.
        val presetId = getConfiguredSpoofValue(snapshot, SpoofType.DEVICE_PROFILE) ?: return
        val preset = DeviceProfilePreset.findById(presetId) ?: return

        hookSensorManager(cl, xi, pkg)
        hookSensorMetadata(cl, xi, preset)
    }

    private fun hookSensorManager(cl: ClassLoader, xi: XposedInterface, pkg: String) {
        val smClass = cl.loadClassOrNull("android.hardware.SensorManager") ?: return
        safeHook("SensorManager.getSensorList(int)") {
            smClass.methodOrNull("getSensorList", Int::class.javaPrimitiveType!!)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val type =
                                chain.args.firstOrNull() as? Int ?: return@stableHooker result
                            filteredSensorListOrOriginal(type, result, pkg)
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookSensorMetadata(
        cl: ClassLoader,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
    ) {
        val sensorClass = cl.loadClassOrNull("android.hardware.Sensor") ?: return
        hookSensorVendor(sensorClass, xi, preset)
        hookSensorVersion(sensorClass, xi)
        hookSensorName(sensorClass, xi)
    }

    private fun hookSensorVendor(
        sensorClass: Class<*>,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
    ) {
        safeHook("Sensor.getVendor()") {
            sensorClass.methodOrNull("getVendor")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            if (preset != null && preset.manufacturer.isNotEmpty())
                                preset.manufacturer
                            else result
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookSensorVersion(sensorClass: Class<*>, xi: XposedInterface) {
        safeHook("Sensor.getVersion()") {
            sensorClass.methodOrNull("getVersion")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val version = result as? Int ?: return@stableHooker result
                            if (version > MAX_NORMALIZED_SENSOR_VERSION) NORMALIZED_SENSOR_VERSION
                            else result
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookSensorName(sensorClass: Class<*>, xi: XposedInterface) {
        safeHook("Sensor.getName()") {
            sensorClass.methodOrNull("getName")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val sensor = chain.thisObject as? Sensor
                            val name = result as? String ?: return@stableHooker result
                            normalizedSensorName(sensor?.type, name)
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun normalizedSensorName(type: Int?, original: String): String {
        val generic =
            when (type) {
                1 -> "Accelerometer"
                2 -> "Magnetometer"
                4 -> "Gyroscope"
                5 -> "Light Sensor"
                8 -> "Proximity Sensor"
                11 -> "Rotation Vector"
                else -> null
            }
        if (generic != null) return generic

        var name = original
        val prefixes = listOf("Goldfish ", "Qualcomm ", "MediaTek ", "Samsung ")
        for (prefix in prefixes) {
            name = name.replace(prefix, "")
        }
        return name
    }

    private fun filteredSensorListOrOriginal(type: Int, result: Any?, pkg: String): Any? {
        if (type != TYPE_ALL) return result

        @Suppress("UNCHECKED_CAST") val sensors = result as? List<Any> ?: return result
        if (sensors.size <= SENSOR_LIST_FINGERPRINT_THRESHOLD) return result

        val filtered =
            sensors.filter { sensor -> sensor !is Sensor || sensor.type in ESSENTIAL_SENSOR_TYPES }
        if (sensors.size != filtered.size) reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
        return filtered
    }
}
