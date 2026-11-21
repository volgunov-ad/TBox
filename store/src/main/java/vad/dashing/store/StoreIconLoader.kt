package vad.dashing.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.store.catalog.StoreCatalogEntry
import vad.dashing.store.catalog.YandexDiskPath
import vad.dashing.store.disk.UpdateChannel
import vad.dashing.store.disk.YandexDiskClient

/**
 * Loads catalog icons from Yandex Disk into [cacheDir]/store/icons], with a fallback to the
 * installed application icon when the package is present on the device.
 */
class StoreIconLoader(
    private val context: Context,
    private val yandexDiskClient: YandexDiskClient = YandexDiskClient(),
) {
    private val memory = LinkedHashMap<String, Bitmap>(32, 0.75f, true)
    private val mutex = Mutex()

    suspend fun load(
        entry: StoreCatalogEntry,
        channel: UpdateChannel,
        publicKey: String,
    ): Bitmap? {
        val cacheKey = "${channel.storageValue}:${entry.id}:${entry.iconUrl}"
        mutex.withLock {
            memory[cacheKey]?.let { return it }
        }
        val remote = if (entry.hasExplicitIcon()) {
            withContext(Dispatchers.IO) {
                runCatching { downloadRemote(entry, channel, publicKey) }.getOrNull()
            }
        } else {
            null
        }
        val bitmap = remote
            ?: withContext(Dispatchers.IO) { installedIcon(entry.packageName) }
        if (bitmap != null) {
            mutex.withLock {
                while (memory.size >= 64) {
                    val iterator = memory.entries.iterator()
                    if (!iterator.hasNext()) break
                    iterator.next()
                    iterator.remove()
                }
                memory[cacheKey] = bitmap
            }
        }
        return bitmap
    }

    fun clearMemory() {
        memory.clear()
    }

    private suspend fun downloadRemote(
        entry: StoreCatalogEntry,
        channel: UpdateChannel,
        publicKey: String,
    ): Bitmap? {
        val logical = entry.resolveIconLogicalPath() ?: return null
        val path = YandexDiskPath.physicalPath(channel, logical)
        val fileName = path.substringAfterLast('/').ifBlank { "icon.png" }
        val dest = iconCacheFile(channel, entry.id, fileName)
        if (!dest.exists() || dest.length() <= 0L) {
            try {
                yandexDiskClient.downloadToFile(publicKey, path, dest)
            } catch (_: Exception) {
                dest.delete()
                return null
            }
        }
        return BitmapFactory.decodeFile(dest.absolutePath)
    }

    private fun iconCacheFile(channel: UpdateChannel, appId: String, fileName: String): File {
        val dir = File(context.cacheDir, "store/icons/${channel.storageValue}/$appId")
        dir.mkdirs()
        return File(dir, fileName)
    }

    private fun installedIcon(packageName: String): Bitmap? {
        if (packageName.isBlank()) return null
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable)
        }.getOrNull()
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
