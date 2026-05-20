package com.astrixforge.devicemasker.xposed.hooker

import com.astrixforge.devicemasker.common.DeviceProfilePreset
import com.astrixforge.devicemasker.common.SpoofType
import com.astrixforge.devicemasker.xposed.HookConfigSnapshot
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * WebView Hooker — libxposed API 101 edition.
 *
 * Spoofs WebView User-Agent string to match the active DeviceProfilePreset. WebView UA is a rich
 * source of device fingerprint data: it contains the Android version, device model, and WebView
 * build version.
 *
 * Hooks:
 * - WebView.getSettings() — discovers concrete WebSettings provider classes
 * - WebSettings.getUserAgentString() — spoof UA returned to app code when concrete hookable
 * - WebSettings.setUserAgentString(String) — intercept custom UA before it is set when concrete
 * - WebSettings.getDefaultUserAgent(Context) — static method returning the system default UA
 *
 * UA format: "Mozilla/5.0 (Linux; Android 16; Pixel 9 Pro) AppleWebKit/537.36 ..." We replace the
 * "Android XX; DEVICE" segment with the preset model.
 *
 * ## deoptimize() note
 * getUserAgentString() is called by JavaScript-to-Java bridge during WebView init — often AOT
 * compiled. Must deoptimize to ensure our hook fires consistently.
 */
object WebViewHooker : BaseSpoofHooker("WebViewHooker") {
    private val hookedSettingsClasses: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap())

    fun hook(cl: ClassLoader, xi: XposedInterface, pkg: String, snapshot: HookConfigSnapshot) {
        // Load preset once at hook-registration time
        val presetId = getSpoofValue(snapshot, SpoofType.DEVICE_PROFILE) { "" }
        val preset = if (presetId.isNotEmpty()) DeviceProfilePreset.findById(presetId) else null

        hookWebSettings(cl, xi, preset, pkg)
        hookWebViewSettingsDiscovery(cl, xi, preset, pkg)
        hookWebViewDefaultUA(cl, xi, preset, pkg)
    }

    private fun hookWebSettings(
        cl: ClassLoader,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
        pkg: String,
    ) {
        val wsClass = cl.loadClassOrNull("android.webkit.WebSettings") ?: return
        hookWebSettingsGetter(wsClass, xi, preset, pkg)
        hookWebSettingsSetter(wsClass, xi, preset, pkg)
    }

    private fun hookWebViewSettingsDiscovery(
        cl: ClassLoader,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
        pkg: String,
    ) {
        safeHook("WebView.getSettings()") {
            val webViewClass = cl.loadClassOrNull("android.webkit.WebView") ?: return@safeHook
            webViewClass.methodOrNull("getSettings")?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val settingsClass = result?.javaClass ?: return@stableHooker result
                            hookConcreteWebSettings(settingsClass, xi, preset, pkg)
                            result
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    private fun hookConcreteWebSettings(
        settingsClass: Class<*>,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
        pkg: String,
    ) {
        val className = settingsClass.name
        if (!hookedSettingsClasses.add(className)) return
        hookWebSettingsGetter(settingsClass, xi, preset, pkg, inheritedLookup = true)
        hookWebSettingsSetter(settingsClass, xi, preset, pkg, inheritedLookup = true)
    }

    private fun hookWebSettingsGetter(
        wsClass: Class<*>,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
        pkg: String,
        inheritedLookup: Boolean = false,
    ) {
        safeHook("WebSettings.getUserAgentString()") {
            wsClass
                .webSettingsMethodOrNull("getUserAgentString", inheritedLookup = inheritedLookup)
                ?.let { m ->
                    if (Modifier.isAbstract(m.modifiers)) return@safeHook
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                val ua = result as? String ?: return@stableHooker result
                                val model = preset?.model ?: return@stableHooker result
                                val spoofed = modifyUserAgent(ua, model)
                                if (spoofed != ua) reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                                spoofed
                            }
                        )
                    xi.deoptimize(m)
                }
        }
    }

    private fun hookWebSettingsSetter(
        wsClass: Class<*>,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
        pkg: String,
        inheritedLookup: Boolean = false,
    ) {
        safeHook("WebSettings.setUserAgentString(String)") {
            wsClass
                .webSettingsMethodOrNull(
                    "setUserAgentString",
                    String::class.java,
                    inheritedLookup = inheritedLookup,
                )
                ?.let { m ->
                    if (Modifier.isAbstract(m.modifiers)) return@safeHook
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val model = preset?.model ?: return@stableHooker chain.proceed()
                                val ua =
                                    chain.args.firstOrNull() as? String
                                        ?: return@stableHooker chain.proceed()
                                if (ua.isNotEmpty() && !ua.contains(model)) {
                                    val args = chain.args.toTypedArray()
                                    args[0] = modifyUserAgent(ua, model)
                                    reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                                    return@stableHooker chain.proceed(args)
                                }
                                chain.proceed()
                            }
                        )
                    xi.deoptimize(m)
                }
        }
    }

    private fun hookWebViewDefaultUA(
        cl: ClassLoader,
        xi: XposedInterface,
        preset: DeviceProfilePreset?,
        pkg: String,
    ) {
        safeHook("WebSettings.getDefaultUserAgent(Context)") {
            val webSettingsClass =
                cl.loadClassOrNull("android.webkit.WebSettings") ?: return@safeHook
            val contextClass = cl.loadClassOrNull("android.content.Context") ?: return@safeHook
            webSettingsClass.methodOrNull("getDefaultUserAgent", contextClass)?.let { m ->
                xi.hook(m)
                    .intercept(
                        stableHooker { chain ->
                            val result = chain.proceed()
                            val model = preset?.model ?: return@stableHooker result
                            val ua = result as? String ?: return@stableHooker result
                            val spoofed = modifyUserAgent(ua, model)
                            if (spoofed != ua) reportSpoofEvent(pkg, SpoofType.DEVICE_PROFILE)
                            spoofed
                        }
                    )
                xi.deoptimize(m)
            }
        }
    }

    internal fun modifyUserAgent(originalUA: String, model: String): String {
        if (model.isBlank()) return originalUA

        val androidMarker = "Android "
        val androidIndex = originalUA.indexOf(androidMarker)
        if (androidIndex < 0) return originalUA

        val deviceStart = originalUA.indexOf("; ", startIndex = androidIndex)
        if (deviceStart < 0) return originalUA

        val segmentStart = deviceStart + 2
        val deviceEnd =
            firstPositiveIndex(
                originalUA.indexOf(" Build/", startIndex = segmentStart),
                originalUA.indexOf("; wv", startIndex = segmentStart),
                originalUA.indexOf(")", startIndex = segmentStart),
            )
        if (deviceEnd < 0) return originalUA

        return originalUA.replaceRange(segmentStart, deviceEnd, model)
    }

    private fun firstPositiveIndex(vararg indexes: Int): Int {
        var best = -1
        for (index in indexes) {
            if (index < 0) continue
            if (best !in 0..index) best = index
        }
        return best
    }

    private fun Class<*>.webSettingsMethodOrNull(
        name: String,
        vararg params: Class<*>,
        inheritedLookup: Boolean,
    ): Method? {
        methodOrNull(name, *params)?.let {
            return it
        }
        if (!inheritedLookup) return null
        return try {
            getMethod(name, *params).also { it.isAccessible = true }
        } catch (_: NoSuchMethodException) {
            null
        }
    }
}
