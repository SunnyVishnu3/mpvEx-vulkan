# =============================================================================
# Device Masker — Consumer ProGuard Rules (:common module)
# libxposed API 101 edition.
#
# Merged into :app's R8 run automatically by AGP.
# Protects shared models, generators, enums, and keys.
# =============================================================================

# =============================================================================
# ANNOTATIONS — Required for kotlinx.serialization at runtime
# =============================================================================
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes AnnotationDefault
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes Exceptions

# =============================================================================
# SHARED MODELS — @Serializable data classes, JsonConfig, AppConfig, SpoofGroup
#
# kotlinx.serialization needs: constructors, $$serializer, Companion.serializer()
# Data class fields are accessed via generated code, not direct reflection.
# =============================================================================
-keepclassmembers class com.astrixforge.devicemasker.common.** {
    <init>(...);
}
-keepclassmembers class com.astrixforge.devicemasker.common.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.astrixforge.devicemasker.common.**$$serializer { *; }

# =============================================================================
# ENUMS — SpoofType (24 values), SpoofCategory, CorrelationGroup, etc.
# values() and valueOf() are called by serialization and hook-side SpoofType lookup.
# =============================================================================
-keepclassmembers enum com.astrixforge.devicemasker.common.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# =============================================================================
# KOTLIN OBJECT SINGLETONS
# SharedPrefsKeys, NetworkTypeMapper, generators, etc.
# R8 full mode can strip the INSTANCE field from objects that appear unreferenced.
# =============================================================================
-keepclassmembers class com.astrixforge.devicemasker.common.** {
    public static final ** INSTANCE;
}

# =============================================================================
# GENERATORS — IMEI, IMSI, MAC, ICCID, Serial, etc.
# Called from SpoofRepository at config time; must not be renamed.
# =============================================================================
-keep class com.astrixforge.devicemasker.common.generators.** { *; }

# =============================================================================
# DeviceProfilePreset — PRESETS list, findById(), groupedByManufacturer()
# =============================================================================
-keep class com.astrixforge.devicemasker.common.DeviceProfilePreset { *; }
-keepclassmembers class com.astrixforge.devicemasker.common.DeviceProfilePreset$Companion {
    public *;
}

# =============================================================================
# MODELS sub-package — Carrier, SIMConfig, DeviceHardwareConfig, LocationConfig
# Fields accessed by generators and UI.
# =============================================================================
-keep class com.astrixforge.devicemasker.common.models.** { *; }

# =============================================================================
# SHARED PREFS KEYS — Single source of truth; accessed by :app and :xposed
# Must not be renamed — hook process uses the same generated key strings.
# =============================================================================
-keep class com.astrixforge.devicemasker.common.SharedPrefsKeys { *; }
-keepclassmembers class com.astrixforge.devicemasker.common.SharedPrefsKeys {
    public static *;
}

# =============================================================================
# NetworkTypeMapper — MCC/MNC to network type mapping
# =============================================================================
-keep class com.astrixforge.devicemasker.common.NetworkTypeMapper { *; }

# =============================================================================
# KOTLIN SPECIFICS
# =============================================================================
-keepattributes *KotlinMetadata*
-dontwarn kotlin.reflect.jvm.internal.**
