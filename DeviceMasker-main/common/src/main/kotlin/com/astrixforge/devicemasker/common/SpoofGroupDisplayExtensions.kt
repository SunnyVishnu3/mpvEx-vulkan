package com.astrixforge.devicemasker.common

/** Returns a summary string for display in lists. */
fun SpoofGroup.summary(): String = "${configuredCount()} configured, ${enabledCount()} enabled"
