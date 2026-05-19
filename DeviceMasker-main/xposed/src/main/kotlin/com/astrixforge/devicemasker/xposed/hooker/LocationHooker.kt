package com.astrixforge.devicemasker.xposed.hooker

import android.location.Location
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface

/**
 * Location Hooker — libxposed API 101 edition.
 *
 * Spoofs:
 * - GPS latitude (Location.getLatitude())
 * - GPS longitude (Location.getLongitude())
 * - LocationManager.getLastKnownLocation() — mutates the returned Location object
 * - TimeZone.getDefault()
 * - Locale.getDefault()
 */
object LocationHooker : BaseSpoofHooker("LocationHooker") {
    data class LocationSnapshot(
        val provider: String?,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val time: Long,
        val elapsedRealtimeNanos: Long,
    )

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        hookLocation(cl, xi, pkg, snapshot)
        hookLocationManager(cl, xi, pkg, snapshot)
        hookTimeZone(cl, xi, pkg, snapshot)
        hookLocale(cl, xi, pkg, snapshot)
    }

    private fun hookLocation(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val locationClass = cl.loadClassOrNull("android.location.Location") ?: return
        safeHook("Location.getLatitude()") {
            locationClass.methodOrNull("getLatitude")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val current = (result as? Double) ?: 0.0
                            val spoofed =
                                getSpoofValue(snapshot, SpoofType.LOCATION_LATITUDE) {
                                    current.toString()
                                }
                            val finalVal = spoofed.toDoubleOrNull() ?: current
                            if (finalVal != current)
                                reportSpoofEvent(pkg, SpoofType.LOCATION_LATITUDE)
                            finalVal
                        }
                    )
                xi.deoptimize(m)
            }
        }
        safeHook("Location.getLongitude()") {
            locationClass.methodOrNull("getLongitude")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val current = (result as? Double) ?: 0.0
                            val spoofed =
                                getSpoofValue(snapshot, SpoofType.LOCATION_LONGITUDE) {
                                    current.toString()
                                }
                            val finalVal = spoofed.toDoubleOrNull() ?: current
                            if (finalVal != current)
                                reportSpoofEvent(pkg, SpoofType.LOCATION_LONGITUDE)
                            finalVal
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookLocationManager(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val lmClass = cl.loadClassOrNull("android.location.LocationManager") ?: return
        safeHook("LocationManager.getLastKnownLocation(String)") {
            lmClass.methodOrNull("getLastKnownLocation", String::class.java)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val latStr = getSpoofValue(snapshot, SpoofType.LOCATION_LATITUDE) { "" }
                            val lonStr =
                                getSpoofValue(snapshot, SpoofType.LOCATION_LONGITUDE) { "" }
                            val location =
                                result as? Location
                                    ?: createSpoofLocation(
                                        chain.args.firstOrNull() as? String,
                                        latStr,
                                        lonStr,
                                    )
                                    ?: return@stableHooker result

                            val copy = copyWithSpoof(location, latStr, lonStr)
                            if (copy !== location || result == null) {
                                reportSpoofEvent(pkg, SpoofType.LOCATION_LATITUDE)
                                reportSpoofEvent(pkg, SpoofType.LOCATION_LONGITUDE)
                            }
                            copy
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookTimeZone(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val tzClass = cl.loadClassOrNull("java.util.TimeZone") ?: return
        safeHook("TimeZone.getDefault()") {
            tzClass.methodOrNull("getDefault")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val tzId = getSpoofValue(snapshot, SpoofType.TIMEZONE) { "" }
                            if (tzId.isNotBlank()) {
                                reportSpoofEvent(pkg, SpoofType.TIMEZONE)
                                java.util.TimeZone.getTimeZone(tzId)
                            } else {
                                result
                            }
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookLocale(
        cl: ClassLoader,
        xi: XposedInterface,
        pkg: String,
        snapshot: HookConfigSnapshot,
    ) {
        val localeClass = cl.loadClassOrNull("java.util.Locale") ?: return
        safeHook("Locale.getDefault()") {
            localeClass.methodOrNull("getDefault")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val current = result as? java.util.Locale ?: return@stableHooker result
                            val localeStr = getSpoofValue(snapshot, SpoofType.LOCALE) { "" }
                            if (localeStr.isNotBlank()) {
                                reportSpoofEvent(pkg, SpoofType.LOCALE)
                                buildLocale(localeStr, current)
                            } else {
                                result
                            }
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun buildLocale(localeStr: String, current: java.util.Locale): java.util.Locale {
        return runCatching { java.util.Locale.forLanguageTag(localeStr.replace('_', '-')) }
            .getOrElse { current }
    }

    private fun createSpoofLocation(provider: String?, latStr: String, lonStr: String): Location? {
        val lat = latStr.toDoubleOrNull() ?: return null
        val lon = lonStr.toDoubleOrNull() ?: return null
        return Location(provider ?: "gps").apply {
            latitude = lat
            longitude = lon
            time = System.currentTimeMillis()
        }
    }

    private fun copyWithSpoof(location: Location, latStr: String, lonStr: String): Location {
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        if (lat == null && lon == null) return location
        return Location(location).apply {
            lat?.let { latitude = it }
            lon?.let { longitude = it }
            if (time <= 0L) time = System.currentTimeMillis()
        }
    }

    fun applySpoofForTest(
        original: LocationSnapshot,
        latitude: String,
        longitude: String,
    ): LocationSnapshot {
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        if (lat == null && lon == null) return original
        return original.copy(
            latitude = lat ?: original.latitude,
            longitude = lon ?: original.longitude,
        )
    }
}
