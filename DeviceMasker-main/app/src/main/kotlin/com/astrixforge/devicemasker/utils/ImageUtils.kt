package com.astrixforge.devicemasker.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap

/** Utility functions for image manipulation. */
object ImageUtils {

    /**
     * Convert a Drawable to a Bitmap for use in Compose Image.
     *
     * Handles BitmapDrawable, AdaptiveIconDrawable, and generic Drawables.
     *
     * @param drawable The Drawable to convert
     * @return The converted Bitmap, or null if conversion fails
     */
    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                is AdaptiveIconDrawable -> {
                    val bitmap =
                        createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888,
                        )
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }

                else -> {
                    val bitmap =
                        createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
