package vad.dashing.store.install

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.store.StoreErrors

/**
 * Writes files into the shared Downloads folder so other apps (e.g. DCLInstall)
 * can pick them from `/sdcard/Download` / the system Downloads UI.
 *
 * On API ≤ P (Jetour HUs) this matches Monitor’s Downloads helpers:
 * `getExternalStorageDirectory() + "/Download"`, then [MediaScannerConnection].
 */
object DownloadsSaver {
    /** True when a runtime WRITE_EXTERNAL_STORAGE grant is needed before [save] / [saveFile]. */
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
            saveApkViaMediaStore(context, sourceApk, safeName)
        } else {
            saveViaPublicDownloads(context, sourceApk, safeName, APK_MIME_TYPE)
        }
    }

    /**
     * Saves an arbitrary card file into `Download/<folder>/<fileName>`.
     * Forbidden path characters in [folder] / [fileName] are replaced with `_`.
     */
    suspend fun saveFile(
        context: Context,
        source: File,
        folder: String,
        fileName: String,
    ): File {
        if (needsWritePermission() && !hasWritePermission(context)) {
            throw IOException(StoreErrors.DOWNLOADS_PERMISSION)
        }
        if (!source.isFile) {
            throw IOException(StoreErrors.FILE_WRITE_FAILED)
        }
        val safeFolder = sanitizeSegment(folder).ifBlank { "app" }
        val safeName = sanitizeFileName(fileName).ifBlank { source.name }
        val mime = mimeFor(safeName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveFileViaMediaStore(context, source, safeFolder, safeName, mime)
        } else {
            val dir = File(downloadsDirectory(), safeFolder)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException(StoreErrors.FILE_WRITE_FAILED)
            }
            val dest = File(dir, safeName)
            source.copyTo(dest, overwrite = true)
            if (!dest.isFile || dest.length() != source.length()) {
                dest.delete()
                throw IOException(StoreErrors.FILE_WRITE_FAILED)
            }
            scanPublicFile(context, dest, mime)
            dest
        }
    }

    /** Direct lookup under public Downloads; null on any failure. */
    fun existingFile(folder: String, fileName: String): File? {
        return try {
            val safeFolder = sanitizeSegment(folder).ifBlank { return null }
            val safeName = sanitizeFileName(fileName).ifBlank { return null }
            val file = File(File(downloadsDirectory(), safeFolder), safeName)
            if (file.isFile) file else null
        } catch (_: Exception) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveApkViaMediaStore(context: Context, sourceApk: File, displayName: String): File {
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileViaMediaStore(
        context: Context,
        source: File,
        folder: String,
        displayName: String,
        mime: String,
    ): File {
        val resolver = context.contentResolver
        val relativePath = "Download/$folder"
        val existingUri = findExistingDownloadUri(context, relativePath, displayName)
        val uri: Uri = if (existingUri != null) {
            existingUri
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            resolver.insert(collection, values)
                ?: throw IOException(StoreErrors.FILE_WRITE_FAILED)
        }
        try {
            resolver.openOutputStream(uri, "wt")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException(StoreErrors.FILE_WRITE_FAILED)
            if (existingUri == null) {
                val done = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, done, null, null)
            }
        } catch (error: Exception) {
            if (existingUri == null) {
                resolver.delete(uri, null, null)
            }
            throw if (error is IOException) error else IOException(StoreErrors.FILE_WRITE_FAILED, error)
        }
        return File(File(downloadsDirectory(), folder), displayName)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun findExistingDownloadUri(
        context: Context,
        relativePath: String,
        displayName: String,
    ): Uri? {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(MediaStore.Downloads._ID)
        // RELATIVE_PATH often ends with '/'.
        val selection =
            "(${MediaStore.Downloads.RELATIVE_PATH}=? OR ${MediaStore.Downloads.RELATIVE_PATH}=?) AND " +
                "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.OWNER_PACKAGE_NAME}=?"
        val args = arrayOf(
            relativePath,
            "$relativePath/",
            displayName,
            context.packageName,
        )
        return context.contentResolver.query(collection, projection, selection, args, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                Uri.withAppendedPath(collection, id.toString())
            }
    }

    private suspend fun saveViaPublicDownloads(
        context: Context,
        source: File,
        displayName: String,
        mime: String,
    ): File {
        val downloads = downloadsDirectory()
        if (!downloads.exists() && !downloads.mkdirs()) {
            throw IOException(StoreErrors.DOWNLOADS_WRITE_FAILED)
        }
        val dest = File(downloads, displayName)
        source.copyTo(dest, overwrite = true)
        if (!dest.isFile || dest.length() != source.length()) {
            dest.delete()
            throw IOException(StoreErrors.DOWNLOADS_WRITE_FAILED)
        }
        scanPublicFile(context, dest, mime)
        return dest
    }

    /**
     * Indexes the file for MediaStore-based file managers on API ≤ P (stock «Загрузки» lists
     * the folder itself). Waits briefly for the scanner callback; proceeds if it never fires.
     */
    private suspend fun scanPublicFile(context: Context, file: File, mime: String) {
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(file.absolutePath),
                    arrayOf(mime),
                ) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    private fun sanitizeSegment(raw: String): String =
        raw.trim()
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
            .trim('.', ' ')

    private fun sanitizeFileName(raw: String): String {
        var value = sanitizeSegment(raw)
        if (value.startsWith(".")) value = value.trimStart('.')
        if (value.endsWith(".part", ignoreCase = true)) {
            value = value.dropLast(5)
        }
        return value
    }

    private fun mimeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isBlank()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val SCAN_TIMEOUT_MS = 3_000L
}
