package com.astrixforge.devicemasker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

val ColorScheme.statusActive: Color
    get() = primary

val ColorScheme.statusOnActive: Color
    get() = onPrimary

val ColorScheme.statusInactive: Color
    get() = error

val ColorScheme.statusOnInactive: Color
    get() = onError

val ColorScheme.statusWarning: Color
    get() = tertiary

val ColorScheme.statusOnWarning: Color
    get() = onTertiary
