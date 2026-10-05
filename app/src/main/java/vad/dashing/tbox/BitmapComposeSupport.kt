package vad.dashing.tbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Caps for decoding UI images (tile/panel backgrounds, custom icons) on low-RAM HUs.
 * Full phone photos in `.tboxtheme` otherwise decode to tens of MB and can reboot the unit.
 */
internal const val UI_IMAGE_DECODE_MAX_EDGE_PX = 1920
internal const val UI_IMAGE_DECODE_MAX_PIXELS = 3_000_000L

/** Copies pixels so Compose does not hold a live reference to a recycled file-backed [Bitmap]. */
internal fun Bitmap.toOwnedImageBitmap(): ImageBitmap? {
    if (isRecycled) return null
    val config = config ?: Bitmap.Config.ARGB_8888
    val owned = copy(config, false) ?: return null
    if (owned !== this) {
        recycle()
    }
    return owned.asImageBitmap()
}

/**
 * Decodes [file] with [inSampleSize] so neither edge exceeds [maxEdgePx] and total pixels
 * stay under [maxPixels]. Returns null on missing/unreadable files.
 */
internal fun decodeFileToOwnedImageBitmap(
    file: File,
    maxEdgePx: Int = UI_IMAGE_DECODE_MAX_EDGE_PX,
    maxPixels: Long = UI_IMAGE_DECODE_MAX_PIXELS,
): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val safeMaxEdge = maxEdgePx.coerceAtLeast(1)
        var sampleSize = computeUiImageInSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            maxEdgePx = safeMaxEdge,
        )
        while (
            (bounds.outWidth.toLong() / sampleSize) *
                (bounds.outHeight.toLong() / sampleSize) > maxPixels.coerceAtLeast(1L)
        ) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)?.toOwnedImageBitmap()
    }.getOrNull()
}

internal fun decodeFileToOwnedScaledImageBitmap(file: File, iconSizePx: Int): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    val safeSize = iconSizePx.coerceAtLeast(1)
    val decoded = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val sampleSize = computeUiImageInSampleSize(
            srcWidth = bounds.outWidth,
            srcHeight = bounds.outHeight,
            maxEdgePx = safeSize,
        )
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }.getOrNull() ?: return null
    if (decoded.width == safeSize && decoded.height == safeSize) {
        return decoded.toOwnedImageBitmap()
    }
    val scaled = Bitmap.createScaledBitmap(decoded, safeSize, safeSize, true)
    if (scaled != decoded) decoded.recycle()
    return scaled.toOwnedImageBitmap()
}

/**
 * Owned Compose bitmap for MediaMetadata / URI art.
 * Does **not** recycle [this] — MediaMetadata may still hold the original.
 */
internal fun Bitmap.toOwnedScaledImageBitmapKeepingSource(maxEdgePx: Int): ImageBitmap? {
    if (isRecycled) return null
    val maxDim = maxOf(width, height)
    val owned = if (maxDim > maxEdgePx && maxEdgePx > 0) {
        val scale = maxEdgePx.toFloat() / maxDim.toFloat()
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(this, w, h, true)
    } else {
        copy(config ?: Bitmap.Config.ARGB_8888, false) ?: return null
    }
    return owned.asImageBitmap()
}

/**
 * If [file] is an image larger than [maxEdgePx] on either edge, rewrites it scaled in place
 * (JPEG quality 85, or PNG when the source has alpha / was PNG). No-op when already within limits.
 *
 * Used when materializing `.tboxtheme` tile/panel backgrounds so a phone photo cannot stay on disk
 * at full resolution for a ~500×900 overlay.
 */
internal fun shrinkImageFileIfOversized(
    file: File,
    maxEdgePx: Int = UI_IMAGE_DECODE_MAX_EDGE_PX,
): Boolean {
    if (!file.isFile || file.length() <= 0L) return false
    val safeMaxEdge = maxEdgePx.coerceAtLeast(1)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    if (bounds.outWidth <= safeMaxEdge && bounds.outHeight <= safeMaxEdge) return false

    val sampleSize = computeUiImageInSampleSize(
        srcWidth = bounds.outWidth,
        srcHeight = bounds.outHeight,
        maxEdgePx = safeMaxEdge,
    )
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return false
    val maxDim = maxOf(decoded.width, decoded.height)
    val scaled = if (maxDim > safeMaxEdge) {
        val scale = safeMaxEdge.toFloat() / maxDim.toFloat()
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        val s = Bitmap.createScaledBitmap(decoded, w, h, true)
        if (s != decoded) decoded.recycle()
        s
    } else {
        decoded
    }
    val preferPng = bounds.outMimeType?.contains("png", ignoreCase = true) == true ||
        scaled.hasAlpha()
    val format = if (preferPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val tmp = File(file.parentFile, "${file.name}.shrink.tmp")
    return try {
        FileOutputStream(tmp).use { out ->
            if (!scaled.compress(format, 85, out)) {
                return false
            }
        }
        if (!tmp.renameTo(file)) {
            tmp.inputStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.delete()
        }
        true
    } catch (_: Exception) {
        if (tmp.exists()) tmp.delete()
        false
    } finally {
        if (!scaled.isRecycled) scaled.recycle()
    }
}

internal fun computeUiImageInSampleSize(
    srcWidth: Int,
    srcHeight: Int,
    maxEdgePx: Int,
): Int {
    val safeMax = maxEdgePx.coerceAtLeast(1)
    var inSampleSize = 1
    while (
        srcWidth / inSampleSize > safeMax ||
        srcHeight / inSampleSize > safeMax
    ) {
        inSampleSize *= 2
        if (inSampleSize > 1024) break
    }
    return inSampleSize.coerceAtLeast(1)
}
