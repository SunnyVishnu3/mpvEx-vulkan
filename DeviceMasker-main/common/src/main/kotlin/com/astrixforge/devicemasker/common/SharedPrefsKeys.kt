package com.astrixforge.devicemasker.common

/**
 * SINGLE SOURCE OF TRUTH for SharedPreference Keys.
 *
 * ⚠️ CRITICAL: Both the app (XposedPrefs) and the xposed module (PrefsReader) MUST use these key
 * generators to ensure keys are IDENTICAL. Any mismatch will cause config to not be read properly.
 *
 * Key Format:
 * - Global: "module_enabled", "debug_enabled", etc.
 * - Per-App: "{prefix}_{sanitizedPackageName}_{type}"
 *
 * Package names are sanitized by replacing '.' with '_' to avoid XML issues.
 */
@Suppress("TooManyFunctions")
object SharedPrefsKeys {

    // ═══════════════════════════════════════════════════════════
    // GLOBAL KEYS
    // ═══════════════════════════════════════════════════════════

    const val KEY_MODULE_ENABLED = "module_enabled"
    const val KEY_DEBUG_ENABLED = "debug_enabled"
    const val KEY_CONFIG_VERSION = "config_version"
    const val KEY_ENABLED_APPS = "enabled_apps"

    // ═══════════════════════════════════════════════════════════
    // PER-APP KEY PREFIXES (kept internal to ensure consistency)
    // ═══════════════════════════════════════════════════════════

    private const val PREFIX_APP_ENABLED = "app_enabled_"
    private const val PREFIX_SPOOF_ENABLED = "spoof_enabled_"
    private const val PREFIX_SPOOF_VALUE = "spoof_"
    private const val PREFIX_RISKY_HOOKS_ENABLED = "risky_hooks_enabled_"
    private const val PREFIX_CLASS_LOOKUP_HIDING_ENABLED = "class_lookup_hiding_enabled_"
    private const val PREFIX_HOOK_FAMILY_ENABLED = "hook_family_enabled_"
    private const val PREFIX_JAVA_PROC_MAPS_BYTE_REDACTION_ENABLED =
        "java_proc_maps_byte_redaction_enabled_"
    private const val PREFIX_JAVA_PROC_MAPS_NIO_REDACTION_ENABLED =
        "java_proc_maps_nio_redaction_enabled_"
    private const val PREFIX_PERSONA_BLOB = "persona_blob_"
    private const val PREFIX_PERSONA_VERSION = "persona_version_"
    private val VALID_KEY_REGEX =
        Regex(
            listOf(
                    KEY_MODULE_ENABLED,
                    KEY_DEBUG_ENABLED,
                    KEY_CONFIG_VERSION,
                    KEY_ENABLED_APPS,
                    "${PREFIX_APP_ENABLED}[a-zA-Z0-9_]+",
                    "${PREFIX_SPOOF_ENABLED}[a-zA-Z0-9_]+_[A-Z_]+",
                    "${PREFIX_SPOOF_VALUE}[a-zA-Z0-9_]+_[A-Z_]+",
                    "${PREFIX_RISKY_HOOKS_ENABLED}[a-zA-Z0-9_]+",
                    "${PREFIX_CLASS_LOOKUP_HIDING_ENABLED}[a-zA-Z0-9_]+",
                    "${PREFIX_HOOK_FAMILY_ENABLED}[a-zA-Z0-9_]+_[a-z_]+",
                    "${PREFIX_JAVA_PROC_MAPS_BYTE_REDACTION_ENABLED}[a-zA-Z0-9_]+",
                    "${PREFIX_JAVA_PROC_MAPS_NIO_REDACTION_ENABLED}[a-zA-Z0-9_]+",
                    "${PREFIX_PERSONA_BLOB}[a-zA-Z0-9_]+",
                    "${PREFIX_PERSONA_VERSION}[a-zA-Z0-9_]+",
                )
                .joinToString(separator = "|", prefix = "^(", postfix = ")$")
        )

    // ═══════════════════════════════════════════════════════════
    // KEY GENERATORS
    // ═══════════════════════════════════════════════════════════

    /**
     * Sanitizes a package name for use in preference keys. Replaces '.' with '_' to avoid XML/key
     * issues.
     */
    fun sanitize(packageName: String): String {
        return packageName.replace('.', '_')
    }

    /** Gets the key for app enabled status. Example: "app_enabled_com_example_app" */
    fun getAppEnabledKey(packageName: String): String {
        return "$PREFIX_APP_ENABLED${sanitize(packageName)}"
    }

    /** Gets the key for spoof type enabled status. Example: "spoof_enabled_com_example_app_IMEI" */
    fun getSpoofEnabledKey(packageName: String, type: SpoofType): String {
        return "$PREFIX_SPOOF_ENABLED${sanitize(packageName)}_${type.name}"
    }

    /** Gets the key for spoof value. Example: "spoof_com_example_app_IMEI" */
    fun getSpoofValueKey(packageName: String, type: SpoofType): String {
        return "$PREFIX_SPOOF_VALUE${sanitize(packageName)}_${type.name}"
    }

    /** Gets the key for per-app risky hook group opt-in. */
    fun getRiskyHooksEnabledKey(packageName: String): String {
        return "$PREFIX_RISKY_HOOKS_ENABLED${sanitize(packageName)}"
    }

    /** Gets the key for per-app class lookup hiding opt-in. */
    fun getClassLookupHidingEnabledKey(packageName: String): String {
        return "$PREFIX_CLASS_LOOKUP_HIDING_ENABLED${sanitize(packageName)}"
    }

    /** Gets the key for enabling or disabling a hook family for crash isolation. */
    fun getHookFamilyEnabledKey(packageName: String, family: String): String {
        return "$PREFIX_HOOK_FAMILY_ENABLED${sanitize(packageName)}_$family"
    }

    /** Gets the key for opt-in Java byte-level proc maps redaction. */
    fun getJavaProcMapsByteRedactionEnabledKey(packageName: String): String {
        return "$PREFIX_JAVA_PROC_MAPS_BYTE_REDACTION_ENABLED${sanitize(packageName)}"
    }

    /** Gets the key for opt-in Java NIO proc maps redaction. */
    fun getJavaProcMapsNioRedactionEnabledKey(packageName: String): String {
        return "$PREFIX_JAVA_PROC_MAPS_NIO_REDACTION_ENABLED${sanitize(packageName)}"
    }

    /** Gets the device profile preset key for an app. */
    fun getDeviceProfileKey(packageName: String): String {
        return getSpoofValueKey(packageName, SpoofType.DEVICE_PROFILE)
    }

    /** Gets the key for the coherent per-package persona JSON blob. */
    fun getPersonaBlobKey(packageName: String): String {
        return "$PREFIX_PERSONA_BLOB${sanitize(packageName)}"
    }

    /** Gets the key for the coherent per-package persona version. */
    fun getPersonaVersionKey(packageName: String): String {
        return "$PREFIX_PERSONA_VERSION${sanitize(packageName)}"
    }

    // ═══════════════════════════════════════════════════════════
    // VALIDATION (for debugging sync issues)
    // ═══════════════════════════════════════════════════════════

    /**
     * Validates that a key matches expected format. Useful for debugging sync issues between app
     * and xposed module.
     */
    fun isValidKey(key: String): Boolean {
        return VALID_KEY_REGEX.matches(key)
    }
}

/** Checks whether [key] is a persona JSON blob key. */
fun isPersonaBlobKey(key: String): Boolean = key.startsWith("persona_blob_")

/** Checks whether [key] is a persona version key. */
fun isPersonaVersionKey(key: String): Boolean = key.startsWith("persona_version_")
