package com.astrixforge.devicemasker.common

import com.astrixforge.devicemasker.common.models.Carrier
import com.astrixforge.devicemasker.common.models.LocationConfig
import java.util.Locale

/**
 * Builds a coherent per-package [DevicePersona] from a [SpoofGroup].
 *
 * During migration the group remains the persisted source of truth for UI state. This generator
 * resolves a stronger runtime persona from that group and overlays explicit post-generation
 * overrides where present.
 */
object PersonaGenerator {

    const val PREVIEW_PACKAGE = "persona.preview"

    fun generate(group: SpoofGroup, packageName: String): DevicePersona {
        val rootSeed = group.resolvedPersonaSeed()
        val preset = resolvePreset(group, rootSeed)
        val carrier = resolveCarrier(group, rootSeed)
        val location = resolveLocation(group, rootSeed, carrier)
        val hardware = resolveHardware(group, rootSeed, preset)
        val subscriptions = resolveSubscriptions(group, rootSeed, carrier, preset)
        val networkEnvironment =
            resolveNetworkEnvironment(group, carrier, location, preset, rootSeed)
        val tracking = resolveTracking(group, packageName, rootSeed)

        return DevicePersona(
            groupId = group.id,
            packageName = packageName,
            rootSeed = rootSeed,
            version = group.updatedAt,
            generatedAt = group.personaGeneratedAt,
            deviceProfileId = preset.id,
            hardware = hardware,
            subscriptions = subscriptions,
            location =
                LocationPersona(
                    countryIso = carrier.countryIsoLower,
                    region = carrier.region,
                    timezone = location.timezone,
                    locale = location.locale,
                    latitude = location.latitude,
                    longitude = location.longitude,
                ),
            networkEnvironment = networkEnvironment,
            tracking = tracking,
            browser =
                BrowserPersona(
                    userAgentModel = preset.model,
                    androidRelease = parseAndroidRelease(preset.fingerprint),
                ),
        )
    }

    fun validate(persona: DevicePersona): PersonaValidationResult {
        val issues = mutableListOf<String>()
        val preset = DeviceProfilePreset.findById(persona.deviceProfileId)
        if (preset == null) {
            issues += "Unknown device profile: ${persona.deviceProfileId}"
        } else {
            val tac = persona.hardware.primaryImei.take(TAC_LENGTH)
            if (preset.tacPrefixes.none { tac.startsWith(it.take(TAC_VALIDATION_PREFIX_LENGTH)) }) {
                issues += "Primary IMEI does not align with preset TAC prefixes"
            }
        }

        val subscription = persona.subscriptions.firstOrNull()
        if (subscription != null) {
            if (!subscription.imsi.startsWith(subscription.carrierMccMnc)) {
                issues += "IMSI is not aligned with carrier MCC/MNC"
            }
            if (subscription.networkOperator != subscription.carrierMccMnc) {
                issues += "Network operator does not match carrier MCC/MNC"
            }
        }

        if (persona.networkEnvironment.ssid.isBlank()) {
            issues += "SSID must not be blank"
        }
        if (persona.networkEnvironment.bssid.count { it == ':' } != MAC_BYTES - 1) {
            issues += "BSSID must be MAC-like"
        }
        if (
            setOf(
                    persona.tracking.androidId,
                    persona.tracking.gsfId,
                    persona.tracking.advertisingId,
                    persona.tracking.mediaDrmId,
                )
                .size < MIN_DISTINCT_TRACKING_IDS
        ) {
            issues += "Tracking identifiers must remain distinct"
        }

        return PersonaValidationResult(isValid = issues.isEmpty(), issues = issues)
    }

    fun materializeGroup(group: SpoofGroup, packageName: String = PREVIEW_PACKAGE): SpoofGroup {
        val persona = generate(group, packageName)
        val timestamp = group.personaGeneratedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val updatedIdentifiers =
            SpoofType.entries.associateWith { type ->
                val current = group.getIdentifier(type) ?: DeviceIdentifier.createDefault(type)
                current.copy(value = persona.getValue(type), lastModified = timestamp)
            }

        return group.copy(identifiers = updatedIdentifiers, updatedAt = timestamp)
    }

    private fun resolvePreset(group: SpoofGroup, rootSeed: String): DeviceProfilePreset {
        val explicit =
            group.getExplicitOverrideValue(SpoofType.DEVICE_PROFILE)
                ?: group.getValue(SpoofType.DEVICE_PROFILE)
        return explicit?.let(DeviceProfilePreset::findById)
            ?: pickFrom(rootSeed, "preset", DeviceProfilePreset.PRESETS)
    }

    private fun resolveCarrier(group: SpoofGroup, rootSeed: String): Carrier {
        val explicitMccMnc =
            group.selectedCarrierMccMnc
                ?: group.getExplicitOverrideValue(SpoofType.CARRIER_MCC_MNC)
                ?: group.getValue(SpoofType.CARRIER_MCC_MNC)
        return explicitMccMnc?.let(Carrier::getByMccMnc)
            ?: pickFrom(rootSeed, "carrier", Carrier.ALL_CARRIERS)
    }

    private fun resolveLocation(
        group: SpoofGroup,
        rootSeed: String,
        carrier: Carrier,
    ): LocationConfig {
        val timezone =
            group.getExplicitOverrideValue(SpoofType.TIMEZONE) ?: group.getValue(SpoofType.TIMEZONE)
        val locale =
            group.getExplicitOverrideValue(SpoofType.LOCALE) ?: group.getValue(SpoofType.LOCALE)
        val latitude =
            (group.getExplicitOverrideValue(SpoofType.LOCATION_LATITUDE)
                    ?: group.getValue(SpoofType.LOCATION_LATITUDE))
                ?.toDoubleOrNull()
        val longitude =
            (group.getExplicitOverrideValue(SpoofType.LOCATION_LONGITUDE)
                    ?: group.getValue(SpoofType.LOCATION_LONGITUDE))
                ?.toDoubleOrNull()

        return if (timezone != null && locale != null && latitude != null && longitude != null) {
            LocationConfig(
                country = carrier.countryIso,
                timezone = timezone,
                locale = locale,
                latitude = latitude,
                longitude = longitude,
            )
        } else {
            val countryIso = carrier.countryIso.uppercase(Locale.US)
            val resolvedTimezone =
                timezone ?: pickFrom(rootSeed, "timezone:$countryIso", countryTimezones(countryIso))
            val resolvedLocale =
                locale ?: pickFrom(rootSeed, "locale:$countryIso", countryLocales(countryIso))
            val bounds = pickFrom(rootSeed, "bounds:$countryIso", gpsBoundsFor(countryIso))
            LocationConfig(
                country = carrier.countryIso,
                timezone = resolvedTimezone,
                locale = resolvedLocale,
                latitude =
                    latitude
                        ?: deterministicCoordinate(
                            rootSeed,
                            "lat:$countryIso",
                            bounds.minLat,
                            bounds.maxLat,
                        ),
                longitude =
                    longitude
                        ?: deterministicCoordinate(
                            rootSeed,
                            "lon:$countryIso",
                            bounds.minLon,
                            bounds.maxLon,
                        ),
            )
        }
    }

    private fun resolveHardware(
        group: SpoofGroup,
        rootSeed: String,
        preset: DeviceProfilePreset,
    ): HardwarePersona {
        val primaryImei =
            group.getExplicitOverrideValue(SpoofType.IMEI)
                ?: group.getValue(SpoofType.IMEI)
                ?: deterministicImei(rootSeed, "imei-primary", preset)
        val serial =
            group.getExplicitOverrideValue(SpoofType.SERIAL)
                ?: group.getValue(SpoofType.SERIAL)
                ?: deterministicSerial(rootSeed, "serial", preset.manufacturer)
        val wifiMac =
            group.getExplicitOverrideValue(SpoofType.WIFI_MAC)
                ?: group.getValue(SpoofType.WIFI_MAC)
                ?: deterministicMac(rootSeed, "wifi-mac", preset.manufacturer)
        val bluetoothMac =
            group.getExplicitOverrideValue(SpoofType.BLUETOOTH_MAC)
                ?: group.getValue(SpoofType.BLUETOOTH_MAC)
                ?: deterministicMac(rootSeed, "bluetooth-mac", preset.manufacturer)
        val secondaryImei =
            primaryImei
                .takeIf { preset.simCount > 1 }
                ?.let {
                    group.getExplicitOverrideValue(SpoofType.IMEI)?.takeIf { override ->
                        override != primaryImei
                    } ?: deterministicImei(rootSeed, "imei-secondary", preset)
                }

        return HardwarePersona(
            primaryImei = primaryImei,
            secondaryImei = secondaryImei,
            serial = serial,
            wifiMac = wifiMac,
            bluetoothMac = bluetoothMac,
        )
    }

    private fun resolveSubscriptions(
        group: SpoofGroup,
        rootSeed: String,
        carrier: Carrier,
        preset: DeviceProfilePreset,
    ): List<SubscriptionPersona> {
        val primary =
            SubscriptionPersona(
                slotIndex = 0,
                carrierName = group.valueFor(SpoofType.CARRIER_NAME, carrier.name),
                carrierMccMnc = group.valueFor(SpoofType.CARRIER_MCC_MNC, carrier.mccMnc),
                simOperatorName = group.valueFor(SpoofType.SIM_OPERATOR_NAME, carrier.name),
                networkOperator = group.valueFor(SpoofType.NETWORK_OPERATOR, carrier.mccMnc),
                simCountryIso = group.valueFor(SpoofType.SIM_COUNTRY_ISO, carrier.countryIsoLower),
                networkCountryIso =
                    group.valueFor(SpoofType.NETWORK_COUNTRY_ISO, carrier.countryIsoLower),
                phoneNumber =
                    group.valueFor(
                        type = SpoofType.PHONE_NUMBER,
                        defaultValue = deterministicPhoneNumber(rootSeed, "phone-primary", carrier),
                    ),
                imsi =
                    group.valueFor(
                        type = SpoofType.IMSI,
                        defaultValue = deterministicImsi(rootSeed, "imsi-primary", carrier),
                    ),
                iccid =
                    group.valueFor(
                        type = SpoofType.ICCID,
                        defaultValue = deterministicIccid(rootSeed, "iccid-primary", carrier),
                    ),
            )

        if (preset.simCount <= 1) {
            return listOf(primary)
        }

        return listOf(
            primary,
            primary.copy(
                slotIndex = 1,
                phoneNumber = deterministicPhoneNumber(rootSeed, "phone-secondary", carrier),
                imsi = deterministicImsi(rootSeed, "imsi-secondary", carrier),
                iccid = deterministicIccid(rootSeed, "iccid-secondary", carrier),
            ),
        )
    }

    private fun SpoofGroup.valueFor(type: SpoofType, defaultValue: String): String =
        getExplicitOverrideValue(type) ?: getValue(type) ?: defaultValue

    private fun resolveNetworkEnvironment(
        group: SpoofGroup,
        carrier: Carrier,
        location: LocationConfig,
        preset: DeviceProfilePreset,
        rootSeed: String,
    ): NetworkEnvironmentPersona {
        val ssid =
            group.getExplicitOverrideValue(SpoofType.WIFI_SSID)
                ?: group.getValue(SpoofType.WIFI_SSID)
                ?: generateSsid(rootSeed, carrier, location)
        val bssid =
            group.getExplicitOverrideValue(SpoofType.WIFI_BSSID)
                ?: group.getValue(SpoofType.WIFI_BSSID)
                ?: generateBssid(rootSeed, carrier)
        val networkType =
            if (preset.has5G) {
                NetworkTypeMapper.getForMccMnc(carrier.mccMnc)
            } else {
                NetworkTypeMapper.NETWORK_TYPE_LTE
            }

        return NetworkEnvironmentPersona(ssid = ssid, bssid = bssid, networkType = networkType)
    }

    private fun resolveTracking(
        group: SpoofGroup,
        packageName: String,
        rootSeed: String,
    ): TrackingPersona =
        TrackingPersona(
            androidId =
                group.getExplicitOverrideValue(SpoofType.ANDROID_ID)
                    ?: deterministicHex(rootSeed, "android-id:$packageName", ID_BYTES),
            gsfId =
                group.getExplicitOverrideValue(SpoofType.GSF_ID)
                    ?: group.getValue(SpoofType.GSF_ID)
                    ?: deterministicHex(rootSeed, "gsf-id", ID_BYTES),
            advertisingId =
                group.getExplicitOverrideValue(SpoofType.ADVERTISING_ID)
                    ?: group.getValue(SpoofType.ADVERTISING_ID)
                    ?: deterministicUuid(rootSeed, "advertising-id").toString(),
            mediaDrmId =
                group.getExplicitOverrideValue(SpoofType.MEDIA_DRM_ID)
                    ?: group.getValue(SpoofType.MEDIA_DRM_ID)
                    ?: deterministicHex(rootSeed, "media-drm-id", MEDIA_DRM_BYTES),
        )
}
