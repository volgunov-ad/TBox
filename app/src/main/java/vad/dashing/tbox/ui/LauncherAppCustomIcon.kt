package vad.dashing.tbox.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import vad.dashing.tbox.SettingsManager

/**
 * Decodes a user-saved launcher shortcut / music-player custom icon from
 * [SettingsManager.LAUNCHER_APP_ICONS_DIR], scaled to [iconSizePx] square, or null if missing/invalid.
 */
fun decodeLauncherAppCustomIconIfPresent(
    context: Context,
    packageName: String,
    iconSizePx: Int,
): ImageBitmap? = runCatching {
    if (packageName.isBlank()) return@runCatching null
    val f = File(context.filesDir, "${SettingsManager.LAUNCHER_APP_ICONS_DIR}/$packageName")
    if (!f.isFile || f.length() <= 0L) return@runCatching null
    val decoded = BitmapFactory.decodeFile(f.absolutePath) ?: return@runCatching null
    if (decoded.width == iconSizePx && decoded.height == iconSizePx) {
        return@runCatching decoded.asImageBitmap()
    }
    val scaled = Bitmap.createScaledBitmap(decoded, iconSizePx, iconSizePx, true)
    if (scaled != decoded) decoded.recycle()
    scaled.asImageBitmap()
}.getOrNull()
