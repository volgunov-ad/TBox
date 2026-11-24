package vad.dashing.store.install

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.store.StoreErrors

/**
 * Writes an APK into the shared Downloads folder so other apps (e.g. DCLInstall)
 * can pick it from `/sdcard/Download` / the system Downloads UI.
 *
 * On API ≤ P (Jetour HUs) this matches Monitor’s Downloads helpers:
 * `getExternalStorageDirectory() + "/Download"`, then [MediaScannerConnection].
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

    /**
     * Directory used for public APK copies. Same resolution as Monitor
     * (`ThemeBundleExport.downloadsDir`) for API &lt; R.
     */
    @Suppress("DEPRECATION")
    fun downloadsDirectory(): File {
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        } else {
            Environment.getExternalStorageDirectory().absolutePath + "/Download"
        }
        return File(path)
    }

    suspend fun save(context: Context, sourceApk: File, displayName: String): File {
        if (needsWritePermission() && !hasWritePermission(context)) {
            throw IOException(StoreErrors.DOWNLOADS_PERMISSION)
        }
        if (!sourceApk.isFile || sourceApk.length() <= 0L) {
            throw IOException(StoreErrors.APK_MISSING)
        }
        val safeName = displayName.trim().ifBlank { sourceApk.name }
            .replace(Regex("""[^\w.\-]+"""), "_")
            .let { if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk" }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, sourceApk, safeName)
        } else {
            saveViaPublicDownloads(context, sourceApk, safeName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStore(context: Context, sourceApk: File, displayName: String): File {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IOException(StoreErrors.DOWNLOADS_WRITE_FAILED)
        try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceApk.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException(StoreErrors.DOWNLOADS_WRITE_FAILED)
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
        return File(downloadsDirectory(), savedName)
    }

    private suspend fun saveViaPublicDownloads(
        context: Context,
        sourceApk: File,
        displayName: String,
    ): File {
        val downloads = downloadsDirectory()
        if (!downloads.exists() && !downloads.mkdirs()) {
            throw IOException(StoreErrors.DOWNLOADS_WRITE_FAILED)
        }
        val dest = File(downloads, displayName)
        sourceApk.copyTo(dest, overwrite = true)
        if (!dest.isFile || dest.length() != sourceApk.length()) {
            dest.delete()
            throw IOException(StoreErrors.DOWNLOADS_WRITE_FAILED)
        }
        scanPublicFile(context, dest)
        return dest
    }

    /**
     * Indexes the file for MediaStore-based file managers on API ≤ P (stock «Загрузки» lists
     * the folder itself). Waits briefly for the scanner callback; proceeds if it never fires.
     */
    private suspend fun scanPublicFile(context: Context, file: File) {
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(file.absolutePath),
                    arrayOf(APK_MIME_TYPE),
                ) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val SCAN_TIMEOUT_MS = 3_000L
}
