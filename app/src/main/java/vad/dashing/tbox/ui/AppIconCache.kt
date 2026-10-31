package vad.dashing.tbox.ui

import android.content.pm.PackageManager
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import vad.dashing.tbox.LauncherAppIconPaths

/**
 * LRU cache bounded by an estimated byte size. An entry newer than [generation] means
 * custom icons or installed packages changed, so everything older is dropped at once.
 */
internal class SizedMemoryCache<K : Any, V : Any>(
    maxBytes: Int,
    private val sizeOf: (V) -> Int,
) {
    private val cache = object : LruCache<K, V>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: K, value: V): Int = this@SizedMemoryCache.sizeOf(value).coerceAtLeast(1)
    }
    private var generation: Long = Long.MIN_VALUE

    fun get(key: K, keyGeneration: Long): V? = synchronized(this) {
        advanceGeneration(keyGeneration)
        cache[key]
    }

    fun put(key: K, keyGeneration: Long, value: V) {
        synchronized(this) {
            advanceGeneration(keyGeneration)
            if (keyGeneration < generation) return
            cache.put(key, value)
        }
    }

    fun clear() {
        synchronized(this) { cache.evictAll() }
    }

    fun size(): Int = synchronized(this) { cache.size() }

    private fun advanceGeneration(keyGeneration: Long) {
        if (keyGeneration > generation) {
            if (generation != Long.MIN_VALUE) cache.evictAll()
            generation = keyGeneration
        }
    }
}

/**
 * Decoded icons and labels for dashboard tiles, so a tile shown again (page switch,
 * panel re-created, activity resumed) does not hit PackageManager and decode again.
 */
internal object AppIconCache {
    data class Key(
        val packageName: String,
        val sizePx: Int,
        val lookup: LauncherAppIconPaths.Lookup,
        val suppressCustomIcon: Boolean,
        val customIconRevision: Int,
        val packagesRevision: Int,
    ) {
        val generation: Long
            get() = (packagesRevision.toLong() shl 32) or (customIconRevision.toLong() and 0xFFFF_FFFFL)
    }

    private val maxBytes: Int =
        (Runtime.getRuntime().maxMemory() / 16).coerceIn(4L shl 20, 32L shl 20).toInt()

    private val icons = SizedMemoryCache<Key, ImageBitmap>(maxBytes) { it.width * it.height * 4 }
    private val labels = SizedMemoryCache<String, String>(256) { 1 }

    fun peek(key: Key): ImageBitmap? = icons.get(key, key.generation)

    /** [load] runs off the main thread; a null result is not cached. */
    fun getOrLoad(key: Key, load: () -> ImageBitmap?): ImageBitmap? {
        icons.get(key, key.generation)?.let { return it }
        val loaded = load() ?: return null
        icons.put(key, key.generation, loaded)
        return loaded
    }

    fun peekLabel(packageName: String, packagesRevision: Int): String? =
        labels.get(packageName, packagesRevision.toLong())

    fun loadLabel(pm: PackageManager, packageName: String, packagesRevision: Int): String {
        peekLabel(packageName, packagesRevision)?.let { return it }
        val label = runCatching {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        }.getOrNull() ?: return packageName
        labels.put(packageName, packagesRevision.toLong(), label)
        return label
    }

    fun clear() {
        icons.clear()
        labels.clear()
    }
}
