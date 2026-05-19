package com.astrixforge.devicemasker.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppIconCache(
    private val packageManager: PackageManager,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val cache =
        object : LinkedHashMap<String, ImageBitmap>(maxEntries, LOAD_FACTOR, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, ImageBitmap>?
            ): Boolean = size > maxEntries
        }

    suspend fun getIcon(packageName: String): ImageBitmap? {
        synchronized(cache) { cache[packageName] }
            ?.let {
                return it
            }
        val loaded =
            withContext(Dispatchers.IO) {
                runCatching {
                        packageManager
                            .getApplicationIcon(packageName)
                            .toBitmap(width = APP_ICON_SIZE_PX, height = APP_ICON_SIZE_PX)
                            .asImageBitmap()
                    }
                    .getOrNull()
            }
        if (loaded != null) {
            synchronized(cache) { cache[packageName] = loaded }
        }
        return loaded
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    fun remove(packageName: String) {
        synchronized(cache) { cache.remove(packageName) }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 128
        const val LOAD_FACTOR = 0.75f
        const val APP_ICON_SIZE_PX = 80
    }
}

@Composable
fun rememberAppIconCache(): AppIconCache {
    val packageManager = LocalContext.current.packageManager
    return remember(packageManager) { AppIconCache(packageManager) }
}

@Composable
fun CachedAppIcon(
    packageName: String,
    label: String,
    iconCache: AppIconCache,
    modifier: Modifier = Modifier,
    fallback: @Composable (Modifier) -> Unit = { AppIconFallback(it) },
) {
    val iconBitmap by
        produceState<ImageBitmap?>(initialValue = null, packageName, iconCache) {
            value = iconCache.getIcon(packageName)
        }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = label,
            modifier = modifier.size(APP_ICON_SIZE_DP).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        fallback(modifier)
    }
}

private val APP_ICON_SIZE_DP = 40.dp
