package vad.dashing.tbox

import android.net.Uri
import android.os.Build
import android.os.Environment
import java.io.File

/** True when app may read arbitrary paths under primary external storage (API 30+). */
internal fun hasManageAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

/** Short display for settings: absolute path for `file://`, full URI string otherwise. */
internal fun displayWallpaperFolderSummary(uriString: String): String {
    if (uriString.isBlank()) return ""
    return runCatching {
        val u = Uri.parse(uriString)
        when {
            u.scheme.equals("file", ignoreCase = true) -> u.path ?: uriString
            else -> uriString
        }
    }.getOrDefault(uriString)
}

/**
 * Normalizes user input to a persisted `file://` URI of an existing directory, or null.
 * Accepts absolute paths or `file://` URIs. Uses canonical path.
 */
internal fun normalizeFilesystemWallpaperFolderPath(raw: String): String? {
    var s = raw.trim().trim('"')
    if (s.isEmpty()) return null
    if (s.startsWith("file:", ignoreCase = true)) {
        s = runCatching { Uri.parse(s).path }.getOrNull() ?: return null
    }
    val dir = runCatching { File(s).canonicalFile }.getOrNull() ?: return null
    if (!dir.isDirectory) return null
    return Uri.fromFile(dir).toString()
}
