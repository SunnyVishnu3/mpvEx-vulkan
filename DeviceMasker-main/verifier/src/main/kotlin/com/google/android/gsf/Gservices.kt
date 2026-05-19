package com.google.android.gsf

import android.content.ContentResolver

object Gservices {
    @Suppress("UnusedParameter")
    @JvmStatic
    fun getString(resolver: ContentResolver, key: String, defaultValue: String?): String? =
        defaultValue

    @Suppress("UnusedParameter")
    @JvmStatic
    fun getLong(resolver: ContentResolver, key: String, defaultValue: Long): Long = defaultValue
}
