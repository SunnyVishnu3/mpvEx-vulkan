package com.astrixforge.devicemasker.common

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * A named collection of spoofed device identifiers.
 *
 * Groups allow users to save and switch between different device identities. Each group contains
 * values for all supported spoof types and can be assigned to specific apps or set as the default.
 *
 * @property id Unique identifier for this group
 * @property name User-defined group name
 * @property description Optional description
 * @property isEnabled Whether spoofing is active for this group (master switch)
 * @property isDefault Whether this is the default group for new apps
 * @property createdAt Timestamp when group was created (epoch millis)
 * @property updatedAt Timestamp of last modification (epoch millis)
 * @property identifiers Map of SpoofType to DeviceIdentifier values
 * @property assignedApps Set of assigned app package names
 * @property selectedCarrierMccMnc MCC/MNC of selected carrier for SIM spoofing (e.g., "40410")
 */
@Suppress("unused") // Methods used across modules
@Serializable
data class SpoofGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val identifiers: Map<SpoofType, DeviceIdentifier> = emptyMap(),
    val assignedApps: Set<String> = emptySet(),
    val selectedCarrierMccMnc: String? = null, // Selected carrier for SIM spoofing
    val personaSeed: String? = null,
    val personaGeneratedAt: Long = 0L,
) {
    /**
     * Gets a specific identifier value from this group.
     *
     * @param type The spoof type to look up
     * @return The DeviceIdentifier or null if not set
     */
    fun getIdentifier(type: SpoofType): DeviceIdentifier? {
        return identifiers[type]
    }

    /**
     * Gets the spoofed value for a specific type.
     *
     * @param type The spoof type to look up
     * @return The spoofed value or null if not set
     */
    fun getValue(type: SpoofType): String? {
        return identifiers[type]?.value
    }

    /**
     * Gets an explicit override value if present.
     *
     * In the current model, this returns the stored value, allowing the generator to distinguish
     * between persisted state and runtime-derived defaults.
     *
     * @param type The spoof type to look up
     * @return The override value or null
     */
    fun getExplicitOverrideValue(type: SpoofType): String? {
        return getValue(type)
    }

    /**
     * Resolves a deterministic persona seed for this group.
     *
     * Returns the [personaSeed] if set, otherwise falls back to a stable derivation from [id].
     *
     * @return A stable root seed for persona generation
     */
    fun resolvedPersonaSeed(): String = personaSeed ?: id

    /**
     * Checks if a specific spoof type is enabled in this group.
     *
     * @param type The spoof type to check
     * @return True if the type is enabled
     */
    fun isTypeEnabled(type: SpoofType): Boolean {
        return identifiers[type]?.isEnabled ?: true
    }

    /** Creates a copy with an updated identifier. */
    fun withIdentifier(identifier: DeviceIdentifier): SpoofGroup {
        val newIdentifiers = identifiers.toMutableMap()
        newIdentifiers[identifier.type] = identifier
        return copy(identifiers = newIdentifiers, updatedAt = System.currentTimeMillis())
    }

    /** Gets last modification timestamp (alias for updatedAt). */
    val lastModified: Long
        get() = updatedAt

    // ═══════════════════════════════════════════════════════════
    // ASSIGNED APPS MANAGEMENT
    // ═══════════════════════════════════════════════════════════

    companion object {
        /**
         * Creates a new group with default values.
         *
         * @param name The group name
         * @param isDefault Whether this should be the default group
         * @return A new SpoofGroup with all identifier types initialized
         */
        fun createNew(name: String, isDefault: Boolean = false): SpoofGroup {
            val defaultIdentifiers =
                SpoofType.entries.associateWith { type -> DeviceIdentifier.createDefault(type) }

            return SpoofGroup(name = name, isDefault = isDefault, identifiers = defaultIdentifiers)
        }

        /** Creates a "Default" group that is automatically applied. */
        fun createDefaultGroup(): SpoofGroup {
            return createNew(name = "Default", isDefault = true)
        }
    }
}
