package vad.dashing.tbox

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/** Copies pixels so Compose does not hold a live reference to a recycled file-backed [Bitmap]. */
internal fun Bitmap.toOwnedImageBitmap(): ImageBitmap {
    val config = config ?: Bitmap.Config.ARGB_8888
    val owned = copy(config, false)
    if (owned != null && owned !== this) {
        recycle()
        return owned.asImageBitmap()
    }
    return asImageBitmap()
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
