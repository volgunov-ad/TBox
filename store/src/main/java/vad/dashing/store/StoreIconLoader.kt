package vad.dashing.store

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vad.dashing.store.catalog.StoreCatalogEntry
import vad.dashing.store.catalog.YandexDiskPath
import vad.dashing.store.disk.StoreCloudFacade
import vad.dashing.store.disk.UpdateChannel

/**
 * Loads catalog icons from cloud (Yandex → Mail.ru) into [cacheDir]/store/icons],
 * with a fallback to the installed application icon when the package is present.
 * Bitmaps are downsampled to [MAX_ICON_PX]: the UI never draws icons larger than that.
 */
class StoreIconLoader(
    private val context: Context,
    private val cloud: StoreCloudFacade,
) {
    private val memory = LinkedHashMap<String, Bitmap>(32, 0.75f, true)

    suspend fun load(
        entry: StoreCatalogEntry,
        channel: UpdateChannel,
    ): Bitmap? {
        val cacheKey = "${channel.storageValue}:${entry.id}:${entry.iconUrl}"
        synchronized(memory) {
            memory[cacheKey]?.let { return it }
        }
        val remote = if (entry.hasExplicitIcon()) {
            withContext(Dispatchers.IO) {
                try {
                    downloadRemote(entry, channel)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            null
        }
        val bitmap = remote
            ?: withContext(Dispatchers.IO) { installedIcon(entry.packageName) }
        if (bitmap != null) {
            synchronized(memory) {
                while (memory.size >= MAX_MEMORY_ENTRIES) {
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
        synchronized(memory) { memory.clear() }
    }

    private suspend fun downloadRemote(
        entry: StoreCatalogEntry,
        channel: UpdateChannel,
    ): Bitmap? {
        val logical = entry.resolveIconLogicalPath() ?: return null
        val path = YandexDiskPath.physicalPath(channel, logical)
        val fileName = path.substringAfterLast('/').ifBlank { "icon.png" }
        val dest = iconCacheFile(channel, entry.id, fileName)
        val cached = dest.isFile && dest.length() > 0L
        val stale = cached && System.currentTimeMillis() - dest.lastModified() > ICON_TTL_MS
        if (!cached || stale) {
            try {
                cloud.downloadToFile(path, dest)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A stale icon is still better than a letter placeholder.
                if (!cached) return null
            }
        }
        val bitmap = decodeSampled(dest)
        if (bitmap == null) dest.delete()
        return bitmap
    }

    private fun decodeSampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_ICON_PX &&
            bounds.outHeight / (sample * 2) >= MAX_ICON_PX
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
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
        val width = drawable.intrinsicWidth.takeIf { it > 0 }?.coerceAtMost(MAX_ICON_PX) ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 }?.coerceAtMost(MAX_ICON_PX) ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private companion object {
        const val MAX_ICON_PX = 256
        const val MAX_MEMORY_ENTRIES = 64
        const val ICON_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
