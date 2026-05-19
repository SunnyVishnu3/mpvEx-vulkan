package com.astrixforge.devicemasker.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for a specific app.
 *
 * This determines which group (if any) is assigned to an app, and whether spoofing is enabled for
 * that app.
 *
 * @property packageName The app's package name
 * @property groupId ID of the assigned group. UI display flows may fall back to the default group,
 *   but runtime sync and hook eligibility require an explicit non-null assignment.
 * @property isEnabled Whether spoofing is enabled for this app
 * @property riskyHooksEnabled Whether opt-in risky hook groups may be registered for this app
 * @property classLookupHidingEnabled Whether global class lookup anti-detection hooks may be used
 */
@Suppress("unused") // Methods used across modules
@Serializable
data class AppConfig(
    val packageName: String,
    @SerialName("profileId") val groupId: String? = null,
    val isEnabled: Boolean = true,
    val riskyHooksEnabled: Boolean = false,
    val classLookupHidingEnabled: Boolean = false,
) {
    /** Creates a copy with a new group assignment. */
    fun withGroup(groupId: String?): AppConfig {
        return copy(groupId = groupId)
    }

    /** Creates a copy with updated enabled state. */
    fun withEnabled(enabled: Boolean): AppConfig {
        return copy(isEnabled = enabled)
    }

    /** Creates a copy with risky hook groups enabled or disabled. */
    fun withRiskyHooksEnabled(enabled: Boolean): AppConfig {
        return copy(
            riskyHooksEnabled = enabled,
            classLookupHidingEnabled = classLookupHidingEnabled && enabled,
        )
    }

    /** Creates a copy with class lookup hiding enabled or disabled. */
    fun withClassLookupHidingEnabled(enabled: Boolean): AppConfig {
        return copy(classLookupHidingEnabled = enabled)
    }
}
