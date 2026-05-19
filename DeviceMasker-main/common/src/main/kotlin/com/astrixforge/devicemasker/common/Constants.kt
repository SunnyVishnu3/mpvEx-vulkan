package com.astrixforge.devicemasker.common

/**
 * Common module constants shared between the app UI and xposed module.
 *
 * This module contains:
 * - Shared models (SpoofGroup, SpoofType, etc.)
 * - JSON configuration management
 */
@Suppress("unused") // Used by :app and :xposed modules
object Constants {
    // Package IDs
    const val PACKAGE_NAME = "com.astrixforge.devicemasker"

    // Content Provider
    const val PROVIDER_AUTHORITY = "$PACKAGE_NAME.provider"

    // Config
    const val CONFIG_VERSION = 1
    const val CONFIG_FILE_NAME = "config.json"

    // Data directories
    const val APP_DATA_DIR = "devicemasker"
}
