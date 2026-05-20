package com.astrixforge.devicemasker.verifier

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaDrm
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.net.NetworkInterface
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class VerifierActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val evidence = VerifierEvidenceCollector.capture(this)
        persistEvidence(evidence)
        setContentView(render(evidence))
    }

    private fun persistEvidence(evidence: JSONObject) {
        val outputDir = File(filesDir, "verifier").also(File::mkdirs)
        File(outputDir, "latest.json").writeText(evidence.toString(JSON_INDENT))
    }

    private fun render(evidence: JSONObject): ScrollView {
        val textView =
            TextView(this).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                setPadding(CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING, CONTENT_PADDING)
                textSize = TEXT_SIZE_SP
                text = evidence.toString(JSON_INDENT)
            }
        return ScrollView(this).apply { addView(textView) }
    }

    private companion object {
        const val CONTENT_PADDING = 32
        const val TEXT_SIZE_SP = 12f
        const val JSON_INDENT = 2
    }
}

@Suppress("DEPRECATION", "TooManyFunctions")
@SuppressLint("HardwareIds", "MissingPermission")
private object VerifierEvidenceCollector {

    private val FEATURE_NAMES =
        listOf(
            "android.hardware.nfc",
            "android.hardware.nfc.hce",
            "android.hardware.telephony",
            "android.hardware.telephony.data",
            "android.hardware.telephony.radio.access",
            "android.hardware.telephony.subscription",
        )

    fun capture(context: Context): JSONObject {
        val evidence =
            JSONObject()
                .put("packageName", context.packageName)
                .put("capturedAtMillis", System.currentTimeMillis())
                .put("build", buildEvidence())
                .put("settings", settingsEvidence(context))
                .put("telephony", telephonyEvidence(context))
                .put("subscription", subscriptionEvidence(context))
                .put("features", featureEvidence(context.packageManager))
                .put("systemProperties", systemPropertiesEvidence())
                .put("localeTime", localeTimeEvidence())
                .put("procMaps", ProcMapsProbe.capture())
                .put("packageVisibility", PackageVisibilityProbe.capture(context))
                .put("runtime", CrashProbe.capture())
                .put("wifi", wifiEvidence(context))
                .put("bluetooth", bluetoothEvidence())
                .put("networkInterfaces", networkInterfaceEvidence())
                .put("advertising", advertisingEvidence(context))
                .put("location", locationEvidence(context))
                .put("sensors", sensorEvidence(context))
                .put("webView", webViewEvidence(context))
        return evidence.put("checks", VerifierMatrix.fromEvidence(evidence))
    }

    private fun buildEvidence(): JSONObject =
        JSONObject()
            .putResult("BRAND") { Build.BRAND }
            .putResult("MANUFACTURER") { Build.MANUFACTURER }
            .putResult("MODEL") { Build.MODEL }
            .putResult("DEVICE") { Build.DEVICE }
            .putResult("PRODUCT") { Build.PRODUCT }
            .putResult("BOARD") { Build.BOARD }
            .putResult("HARDWARE") { Build.HARDWARE }
            .putResult("FINGERPRINT") { Build.FINGERPRINT }
            .putResult("ID") { Build.ID }
            .putResult("TIME") { Build.TIME }
            .putResult("SERIAL") { Build.getSerial() }
            .put("SUPPORTED_ABIS", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("SUPPORTED_32_BIT_ABIS", JSONArray(Build.SUPPORTED_32_BIT_ABIS.toList()))
            .put("SUPPORTED_64_BIT_ABIS", JSONArray(Build.SUPPORTED_64_BIT_ABIS.toList()))
            .put(
                "VERSION",
                JSONObject()
                    .putResult("RELEASE") { Build.VERSION.RELEASE }
                    .putResult("SDK_INT") { Build.VERSION.SDK_INT }
                    .putResult("INCREMENTAL") { Build.VERSION.INCREMENTAL }
                    .putResult("SECURITY_PATCH") { Build.VERSION.SECURITY_PATCH },
            )

    private fun settingsEvidence(context: Context): JSONObject =
        JSONObject().putResult("android_id") {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }

    private fun telephonyEvidence(context: Context): JSONObject {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return JSONObject()
            .putResult("deviceId") { telephony.deviceId }
            .putResult("deviceIdSlot0") { telephony.getDeviceId(DEFAULT_SLOT_INDEX) }
            .putResult("imei") { telephony.imei }
            .putResult("imeiSlot0") { telephony.getImei(DEFAULT_SLOT_INDEX) }
            .putResult("subscriberId") { telephony.subscriberId }
            .putResult("subscriberIdSlot0") {
                telephony.javaClass
                    .getMethod("getSubscriberId", Int::class.javaPrimitiveType!!)
                    .invoke(telephony, DEFAULT_SLOT_INDEX)
            }
            .putResult("simSerialNumber") { telephony.simSerialNumber }
            .putResult("simSerialNumberSlot0") {
                telephony.javaClass
                    .getMethod("getSimSerialNumber", Int::class.javaPrimitiveType!!)
                    .invoke(telephony, DEFAULT_SLOT_INDEX)
            }
            .putResult("simCountryIso") { telephony.simCountryIso }
            .putResult("networkCountryIso") { telephony.networkCountryIso }
            .putResult("simOperatorName") { telephony.simOperatorName }
            .putResult("networkOperator") { telephony.networkOperator }
            .putResult("line1Number") { telephony.line1Number }
            .putResult("simCount") { telephony.invokeIntGetter("getSimCount") }
            .putResult("phoneCount") { telephony.phoneCount }
            .putResult("activeModemCount") { telephony.invokeIntGetter("getActiveModemCount") }
            .putResult("networkOperatorName") { telephony.networkOperatorName }
    }

    private fun subscriptionEvidence(context: Context): JSONObject {
        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
        return JSONObject()
            .putResult("activeSubscriptionInfoCount") {
                subscriptions.invokeIntGetter("getActiveSubscriptionInfoCount")
            }
            .putResult("activeSubscriptionInfoCountMax") {
                subscriptions.invokeIntGetter("getActiveSubscriptionInfoCountMax")
            }
    }

    private fun featureEvidence(packageManager: PackageManager): JSONObject =
        JSONObject().also { output ->
            FEATURE_NAMES.forEach { feature ->
                output.putResult(feature) { packageManager.hasSystemFeature(feature) }
            }
        }

    private fun wifiEvidence(context: Context): JSONObject {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        return JSONObject()
            .putResult("macAddress") { wifi.connectionInfo.macAddress }
            .putResult("ssid") { wifi.connectionInfo.ssid }
            .putResult("bssid") { wifi.connectionInfo.bssid }
    }

    private fun bluetoothEvidence(): JSONObject =
        JSONObject().putResult("address") {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@putResult null
            adapter.javaClass.getMethod("getAddress").invoke(adapter)
        }

    @SuppressLint("PrivateApi")
    private fun systemPropertiesEvidence(): JSONObject =
        JSONObject()
            .putResult("ro.serialno") { systemProperty("ro.serialno") }
            .putResult("ro.product.model") { systemProperty("ro.product.model") }
            .putResult("ro.product.manufacturer") { systemProperty("ro.product.manufacturer") }
            .putResult("ro.product.brand") { systemProperty("ro.product.brand") }
            .putResult("ro.product.device") { systemProperty("ro.product.device") }
            .putResult("ro.product.name") { systemProperty("ro.product.name") }
            .putResult("ro.product.board") { systemProperty("ro.product.board") }
            .putResult("ro.build.fingerprint") { systemProperty("ro.build.fingerprint") }
            .putResult("ro.build.id") { systemProperty("ro.build.id") }
            .putResult("ro.build.version.incremental") {
                systemProperty("ro.build.version.incremental")
            }
            .putResult("ro.build.version.security_patch") {
                systemProperty("ro.build.version.security_patch")
            }
            .putResult("ro.product.cpu.abilist") { systemProperty("ro.product.cpu.abilist") }
            .putResult("ro.build.date.utc") {
                val clazz = Class.forName("android.os.SystemProperties")
                clazz
                    .getMethod("getLong", String::class.java, Long::class.javaPrimitiveType)
                    .invoke(null, "ro.build.date.utc", 0L)
            }

    private fun localeTimeEvidence(): JSONObject =
        JSONObject()
            .putResult("timezoneId") { TimeZone.getDefault().id }
            .putResult("localeTag") { Locale.getDefault().toLanguageTag() }
            .putResult("localeString") { Locale.getDefault().toString() }

    private fun networkInterfaceEvidence(): JSONObject {
        val output = JSONArray()
        NetworkInterface.getNetworkInterfaces()?.asSequence()?.forEach { networkInterface ->
            output.put(
                JSONObject().put("name", networkInterface.name).putResult("hardwareAddress") {
                    networkInterface.hardwareAddress?.joinToString(":") { byte ->
                        "%02X".format(byte.toInt() and BYTE_MASK)
                    }
                }
            )
        }
        return JSONObject().put("interfaces", output)
    }

    private fun advertisingEvidence(context: Context): JSONObject =
        JSONObject()
            .putResult("advertisingIdClientInfoId") {
                com.google.android.gms.ads.identifier.AdvertisingIdClient.Info(
                        "00000000-0000-0000-0000-000000000000"
                    )
                    .id
            }
            .putResult("gservicesAndroidIdString") {
                com.google.android.gsf.Gservices.getString(
                    context.contentResolver,
                    "android_id",
                    "0",
                )
            }
            .putResult("gservicesAndroidIdLongHex") {
                java.lang.Long.toHexString(
                    com.google.android.gsf.Gservices.getLong(
                        context.contentResolver,
                        "android_id",
                        0L,
                    )
                )
            }
            .putResult("mediaDrmDeviceUniqueIdHex") {
                MediaDrm(WIDEVINE_UUID).getPropertyByteArray("deviceUniqueId").joinToString("") {
                    "%02x".format(it.toInt() and BYTE_MASK)
                }
            }

    private fun locationEvidence(context: Context): JSONObject {
        val manager = context.getSystemService(LocationManager::class.java)
        val synthetic =
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = ORIGINAL_LATITUDE
                longitude = ORIGINAL_LONGITUDE
            }
        return JSONObject()
            .putResult("syntheticLocationLatitude") { synthetic.latitude }
            .putResult("syntheticLocationLongitude") { synthetic.longitude }
            .putResult("gpsLastKnown") {
                val location = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("latitude", location.latitude)
                        .put("longitude", location.longitude)
                        .put("provider", location.provider)
                }
            }
    }

    private fun sensorEvidence(context: Context): JSONObject {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensors = manager.getSensorList(Sensor.TYPE_ALL)
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val first = sensors.firstOrNull()
        return JSONObject()
            .put("allSensorCount", sensors.size)
            .putResult("firstSensorName") { first?.name }
            .putResult("firstSensorVendor") { first?.vendor }
            .putResult("firstSensorVersion") { first?.version }
            .putResult("defaultAccelerometerName") { accelerometer?.name }
            .putResult("defaultAccelerometerVendor") { accelerometer?.vendor }
            .putResult("defaultAccelerometerVersion") { accelerometer?.version }
    }

    private fun webViewEvidence(context: Context): JSONObject =
        JSONObject()
            .putResult("defaultUserAgent") { WebSettings.getDefaultUserAgent(context) }
            .putResult("instanceUserAgent") {
                val webView = WebView(context)
                try {
                    webView.settings.userAgentString
                } finally {
                    webView.destroy()
                }
            }

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): Any? {
        val clazz = Class.forName("android.os.SystemProperties")
        return clazz.getMethod("get", String::class.java).invoke(null, name)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun JSONObject.putResult(name: String, value: () -> Any?): JSONObject {
        val result =
            try {
                value() ?: JSONObject.NULL
            } catch (e: SecurityException) {
                "ERROR:${e.javaClass.simpleName}:${e.message.orEmpty()}"
            } catch (e: ReflectiveOperationException) {
                "ERROR:${e.javaClass.simpleName}:${e.message.orEmpty()}"
            } catch (e: UnsupportedOperationException) {
                "ERROR:${e.javaClass.simpleName}:${e.message.orEmpty()}"
            } catch (e: IllegalStateException) {
                "ERROR:${e.javaClass.simpleName}:${e.message.orEmpty()}"
            } catch (e: RuntimeException) {
                // Verifier probes intentionally isolate framework/provider runtime failures so one
                // blocked surface cannot prevent the rest of the matrix from being written.
                "ERROR:${e.javaClass.simpleName}:${e.message.orEmpty()}"
            }
        return put(name, result)
    }

    private fun TelephonyManager.invokeIntGetter(methodName: String): Int =
        javaClass.getMethod(methodName).invoke(this) as Int

    private fun SubscriptionManager.invokeIntGetter(methodName: String): Int =
        javaClass.getMethod(methodName).invoke(this) as Int

    private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    private const val BYTE_MASK = 0xff
    private const val ORIGINAL_LATITUDE = 1.234567
    private const val ORIGINAL_LONGITUDE = 2.345678
    private const val DEFAULT_SLOT_INDEX = 0
}

private object VerifierMatrix {
    private const val STATUS_OBSERVED = "OBSERVED"
    private const val STATUS_PLATFORM_RESTRICTED = "PLATFORM_RESTRICTED"
    private const val STATUS_PERMISSION_DENIED = "PERMISSION_DENIED"
    private const val STATUS_UNSUPPORTED = "UNSUPPORTED"
    private const val STATUS_ERROR = "ERROR"

    private val PLATFORM_RESTRICTED_KEYS =
        setOf(
            "build.SERIAL",
            "telephony.deviceId",
            "telephony.deviceIdSlot0",
            "telephony.imei",
            "telephony.imeiSlot0",
            "telephony.subscriberId",
            "telephony.subscriberIdSlot0",
            "telephony.simSerialNumber",
            "telephony.simSerialNumberSlot0",
        )

    fun fromEvidence(evidence: JSONObject): JSONArray =
        JSONArray()
            .put(check(evidence, "ANDROID_ID", "settings", "android_id"))
            .put(check(evidence, "SERIAL", "build", "SERIAL"))
            .put(check(evidence, "IMEI", "telephony", "imei"))
            .put(check(evidence, "IMEI_SLOT_0", "telephony", "imeiSlot0"))
            .put(check(evidence, "IMSI", "telephony", "subscriberId"))
            .put(check(evidence, "ICCID", "telephony", "simSerialNumber"))
            .put(check(evidence, "PHONE_NUMBER", "telephony", "line1Number"))
            .put(check(evidence, "SIM_COUNTRY_ISO", "telephony", "simCountryIso"))
            .put(check(evidence, "NETWORK_COUNTRY_ISO", "telephony", "networkCountryIso"))
            .put(check(evidence, "SIM_OPERATOR_NAME", "telephony", "simOperatorName"))
            .put(check(evidence, "NETWORK_OPERATOR", "telephony", "networkOperator"))
            .put(check(evidence, "DEVICE_MODEL", "build", "MODEL"))
            .put(check(evidence, "WIFI_MAC", "wifi", "macAddress"))
            .put(check(evidence, "WIFI_SSID", "wifi", "ssid"))
            .put(check(evidence, "WIFI_BSSID", "wifi", "bssid"))
            .put(check(evidence, "BLUETOOTH_MAC", "bluetooth", "address"))
            .put(check(evidence, "ADVERTISING_ID", "advertising", "advertisingIdClientInfoId"))
            .put(check(evidence, "GSF_ID", "advertising", "gservicesAndroidIdString"))
            .put(check(evidence, "MEDIA_DRM_ID", "advertising", "mediaDrmDeviceUniqueIdHex"))
            .put(check(evidence, "LOCATION_LATITUDE", "location", "syntheticLocationLatitude"))
            .put(check(evidence, "LOCATION_LONGITUDE", "location", "syntheticLocationLongitude"))
            .put(check(evidence, "LOCATION_LAST_KNOWN", "location", "gpsLastKnown"))
            .put(
                check(
                    evidence,
                    "SENSOR_DEFAULT_ACCELEROMETER",
                    "sensors",
                    "defaultAccelerometerName",
                )
            )
            .put(check(evidence, "WEBVIEW_DEFAULT_UA", "webView", "defaultUserAgent"))
            .put(check(evidence, "WEBVIEW_INSTANCE_UA", "webView", "instanceUserAgent"))
            .put(check(evidence, "TIMEZONE", "localeTime", "timezoneId"))
            .put(check(evidence, "LOCALE", "localeTime", "localeString"))

    private fun check(
        evidence: JSONObject,
        name: String,
        sectionName: String,
        key: String,
    ): JSONObject {
        val value = evidence.optJSONObject(sectionName)?.opt(key) ?: JSONObject.NULL
        val path = "$sectionName.$key"
        return JSONObject()
            .put("name", name)
            .put("path", path)
            .put("actual", value)
            .put("status", classify(path, value))
            .put(
                "error",
                if (value is String && value.startsWith("ERROR:")) value else JSONObject.NULL,
            )
    }

    private fun classify(path: String, value: Any?): String =
        when {
            value == null || value == JSONObject.NULL -> STATUS_UNSUPPORTED
            value !is String || !value.startsWith("ERROR:") -> STATUS_OBSERVED
            path in PLATFORM_RESTRICTED_KEYS && value.contains("SecurityException") ->
                STATUS_PLATFORM_RESTRICTED
            value.contains("SecurityException") -> STATUS_PERMISSION_DENIED
            else -> STATUS_ERROR
        }
}
