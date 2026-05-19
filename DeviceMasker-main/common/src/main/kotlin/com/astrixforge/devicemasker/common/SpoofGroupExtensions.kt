package com.astrixforge.devicemasker.common

/** Returns the count of configured (non-null) identifier values. */
fun SpoofGroup.configuredCount(): Int = identifiers.count { it.value.value != null }

/** Returns the count of enabled identifiers. */
fun SpoofGroup.enabledCount(): Int = identifiers.count { it.value.isEnabled }

/** Alias for [SpoofGroup.withIdentifier] - sets an identifier. */
fun SpoofGroup.setIdentifier(identifier: DeviceIdentifier): SpoofGroup = withIdentifier(identifier)

/** Creates a copy with an updated value for a specific type. */
fun SpoofGroup.withValue(type: SpoofType, value: String?): SpoofGroup {
    val existing = identifiers[type] ?: DeviceIdentifier.createDefault(type)
    return withIdentifier(existing.withValue(value))
}

/** Creates a copy with updated enabled state. */
fun SpoofGroup.withEnabled(enabled: Boolean): SpoofGroup =
    copy(isEnabled = enabled, updatedAt = System.currentTimeMillis())

/** Creates a copy with updated persona metadata. */
fun SpoofGroup.withPersona(seed: String?, generatedAt: Long): SpoofGroup =
    copy(
        personaSeed = seed,
        personaGeneratedAt = generatedAt,
        updatedAt = System.currentTimeMillis(),
    )

/** Regenerates all identifier values. */
fun SpoofGroup.regenerateAll(): SpoofGroup {
    val regeneratedIdentifiers =
        identifiers.mapValues { (_, identifier) ->
            identifier.withValue(null) // Trigger regeneration
        }
    return copy(identifiers = regeneratedIdentifiers, updatedAt = System.currentTimeMillis())
}

/** Checks if an app is assigned to this group. */
fun SpoofGroup.isAppAssigned(packageName: String): Boolean = packageName in assignedApps

/** Creates a copy with an app added to assignedApps. */
fun SpoofGroup.addApp(packageName: String): SpoofGroup =
    copy(assignedApps = assignedApps + packageName, updatedAt = System.currentTimeMillis())

/** Creates a copy with an app removed from assignedApps. */
fun SpoofGroup.removeApp(packageName: String): SpoofGroup =
    copy(assignedApps = assignedApps - packageName, updatedAt = System.currentTimeMillis())

/** Returns the count of assigned apps. */
fun SpoofGroup.assignedAppCount(): Int = assignedApps.size
