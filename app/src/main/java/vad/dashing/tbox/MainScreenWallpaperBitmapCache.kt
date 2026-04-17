package vad.dashing.tbox

import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap

/**
 * In-memory LRU for decoded main-screen wallpaper [ImageBitmap]s (bounded memory).
 * Keys are [Uri.toString].
 */
internal object MainScreenWallpaperBitmapCache {

    private const val MAX_MEMORY_BYTES = 48 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            return 4 * value.width.coerceAtLeast(1) * value.height.coerceAtLeast(1)
        }
    }

    fun get(uriString: String): ImageBitmap? = synchronized(cache) { cache.get(uriString) }

    fun put(uriString: String, bitmap: ImageBitmap) {
        synchronized(cache) { cache.put(uriString, bitmap) }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }
}
