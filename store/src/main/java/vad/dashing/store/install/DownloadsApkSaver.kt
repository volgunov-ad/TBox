package vad.dashing.store.install

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

/**
 * Writes an APK into the shared Downloads folder so other apps (e.g. DCLInstall)
 * can pick it from `/sdcard/Download` / the system Downloads UI.
 */
object DownloadsApkSaver {
    /** True when a runtime WRITE_EXTERNAL_STORAGE grant is needed before [save]. */
    fun needsWritePermission(): Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    fun hasWritePermission(context: Context): Boolean {
        if (!needsWritePermission()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun save(context: Context, sourceApk: File, displayName: String): File {
        if (needsWritePermission() && !hasWritePermission(context)) {
            throw IOException("WRITE_EXTERNAL_STORAGE not granted")
        }
        if (!sourceApk.isFile || sourceApk.length() <= 0L) {
            throw IOException("Source APK missing")
        }
        val safeName = displayName.trim().ifBlank { sourceApk.name }
            .replace(Regex("""[^\w.\-]+"""), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, sourceApk, safeName)
        } else {
            saveViaPublicDownloads(sourceApk, safeName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, sourceApk: File, displayName: String): File {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException("MediaStore insert failed for Downloads")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceApk.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Cannot open Downloads output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
        // MediaStore renames on collision ("name (1).apk"); show the name it actually used.
        val savedName = runCatching {
            resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: displayName
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, savedName)
    }

    @Suppress("DEPRECATION")
    private fun saveViaPublicDownloads(sourceApk: File, displayName: String): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists() && !downloads.mkdirs()) {
            throw IOException("Cannot create Downloads directory")
        }
        val dest = File(downloads, displayName)
        sourceApk.copyTo(dest, overwrite = true)
        return dest
    }
}
