package vad.dashing.tbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

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

internal fun decodeFileToOwnedImageBitmap(file: File): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching {
        BitmapFactory.decodeFile(file.absolutePath)?.toOwnedImageBitmap()
    }.getOrNull()
}

internal fun decodeFileToOwnedScaledImageBitmap(file: File, iconSizePx: Int): ImageBitmap? {
    if (!file.isFile || file.length() <= 0L) return null
    val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    if (decoded.width == iconSizePx && decoded.height == iconSizePx) {
        return decoded.toOwnedImageBitmap()
    }
    val scaled = Bitmap.createScaledBitmap(decoded, iconSizePx, iconSizePx, true)
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

