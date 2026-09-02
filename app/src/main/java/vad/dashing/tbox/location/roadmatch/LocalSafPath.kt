package vad.dashing.tbox.location.roadmatch

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.util.Locale

/**
 * Map a SAF / MediaStore URI to a real file when the platform still exposes a path
 * (Android 9 Downloads, USB mass-storage, `file://`).
 *
 * [androidx.documentfile.provider.DocumentFile.getParentFile] is null for a single
 * [android.content.Intent.ACTION_OPEN_DOCUMENT] URI, so sibling ZIP packs next to a
 * catalog JSON are invisible unless we recover the directory on the filesystem.
 */
internal object LocalSafPath {
    fun fileForUri(context: Context, uri: Uri): File? {
        return when (uri.scheme?.lowercase(Locale.US)) {
            "file" -> uri.path?.let(::File)
            "content" -> queryDataColumn(context, uri) ?: fileFromDocumentUri(uri)
            else -> null
        }?.takeIf { it.isFile }
    }

    fun parentDirForUri(context: Context, uri: Uri): File? {
        return fileForUri(context, uri)?.parentFile?.takeIf { it.isDirectory }
    }

    /**
     * @param primaryRoot [Environment.getExternalStorageDirectory] on device;
     * injected in tests.
     */
    fun fileFromDocumentId(documentId: String, primaryRoot: File): File? {
        val id = documentId.trim()
        if (id.isEmpty()) return null
        if (id.startsWith("raw:", ignoreCase = true)) {
            val path = id.substring(4)
            return if (path.startsWith("/")) File(path) else null
        }
        val colon = id.indexOf(':')
        if (colon <= 0 || colon == id.lastIndex) return null
        val volume = id.substring(0, colon)
        val relative = id.substring(colon + 1).trimStart('/').replace('\\', '/')
        if (relative.isEmpty() || relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
            return null
        }
        return when (volume.lowercase(Locale.US)) {
            "primary", "home" -> File(primaryRoot, relative)
            else -> {
                if (!VOLUME_ID.matches(volume)) return null
                File("/storage/$volume/$relative")
            }
        }
    }

    private fun fileFromDocumentUri(uri: Uri): File? {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        val primary = Environment.getExternalStorageDirectory() ?: return null
        return fileFromDocumentId(documentId, primary)?.takeIf { it.isFile }
            ?: usbFallback(documentId)
    }

    private fun usbFallback(documentId: String): File? {
        val colon = documentId.indexOf(':')
        if (colon <= 0) return null
        val volume = documentId.substring(0, colon)
        val relative = documentId.substring(colon + 1).trimStart('/')
        if (!VOLUME_ID.matches(volume) || relative.isEmpty()) return null
        return listOf(
            File("/storage/$volume/$relative"),
            File("/mnt/media_rw/$volume/$relative"),
        ).firstOrNull { it.isFile }
    }

    private fun queryDataColumn(context: Context, uri: Uri): File? {
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (idx < 0 || !cursor.moveToFirst()) return@use null
                cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let(::File)
            }
        }.getOrNull()
    }

    private val VOLUME_ID = Regex("^[A-Za-z0-9_-]+$")
}
