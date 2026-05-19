package com.astrixforge.devicemasker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** DeviceMasker shapes following Material 3 guidelines. */
val AppShapes =
    Shapes(
        // Extra small - Chips, small buttons
        extraSmall = RoundedCornerShape(4.dp),

        // Small - Text fields, list items
        small = RoundedCornerShape(8.dp),

        // Medium - Cards, dialogs
        medium = RoundedCornerShape(12.dp),

        // Large - Bottom sheets, navigation drawers
        large = RoundedCornerShape(16.dp),

        // Large increased - prominent cards and hero elements
        largeIncreased = RoundedCornerShape(20.dp),

        // Extra large - Full-screen components
        extraLarge = RoundedCornerShape(28.dp),

        // Extra large increased - expressive emphasis surfaces
        extraLargeIncreased = RoundedCornerShape(32.dp),

        // Extra extra large - high-emphasis expressive containers
        extraExtraLarge = RoundedCornerShape(48.dp),
    )

object AppShapeScale {
    val none = RoundedCornerShape(0.dp)
    val extraSmall = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val largeIncreased = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val extraLargeIncreased = RoundedCornerShape(32.dp)
    val extraExtraLarge = RoundedCornerShape(48.dp)
    val full = RoundedCornerShape(50)

    val expressiveStart =
        RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 12.dp, bottomEnd = 12.dp)
    val expressiveEnd =
        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 28.dp, bottomEnd = 28.dp)
    val expressiveTop =
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
}
