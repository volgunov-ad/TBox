package vad.dashing.tbox

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"
)

internal fun isLikelyImageDocument(file: DocumentFile): Boolean {
    if (!file.isFile) return false
    val mime = file.type?.lowercase() ?: ""
    if (mime.startsWith("image/")) return true
    val name = file.name?.substringAfterLast('.', "")?.lowercase() ?: ""
    return name in IMAGE_EXTENSIONS
}

/**
 * Lists image files in a SAF tree root, sorted by display name (same order as [String.compareTo]).
 */
private fun listSortedImageFilesInDir(dir: java.io.File): List<Pair<String, Uri>> {
    if (!dir.isDirectory) return emptyList()
    val pairs = dir.listFiles()?.mapNotNull { f ->
        if (!f.isFile) return@mapNotNull null
        val n = f.name
        val ext = n.substringAfterLast('.', "").lowercase()
        if (ext !in IMAGE_EXTENSIONS) return@mapNotNull null
        n to Uri.fromFile(f)
    } ?: emptyList()
    return pairs.sortedBy { it.first }
}

internal suspend fun listSortedWallpaperImagesInFolder(
    context: Context,
    folderUri: Uri,
): List<Pair<String, Uri>> = withContext(Dispatchers.IO) {
    if (folderUri.scheme == "file") {
        val path = folderUri.path ?: return@withContext emptyList()
        return@withContext listSortedImageFilesInDir(File(path))
    }
    DocumentFile.fromTreeUri(context, folderUri)?.takeIf { it.isDirectory }?.let { root ->
        val files = root.listFiles().filter { isLikelyImageDocument(it) }
        return@withContext files.mapNotNull { df ->
            val n = df.name ?: return@mapNotNull null
            n to df.uri
        }.sortedBy { it.first }
    }
    DocumentFile.fromSingleUri(context, folderUri)?.let { doc ->
        when {
            doc.isDirectory -> {
                val files = doc.listFiles().filter { isLikelyImageDocument(it) }
                return@withContext files.mapNotNull { df ->
                    val n = df.name ?: return@mapNotNull null
                    n to df.uri
                }.sortedBy { it.first }
            }
            doc.isFile && isLikelyImageDocument(doc) -> {
                val n = doc.name ?: return@withContext emptyList()
                return@withContext listOf(n to doc.uri)
            }
            else -> return@withContext emptyList()
        }
    }
    emptyList()
}

/**
 * User picks one image; we try to use its parent directory as the wallpaper source (carousel).
 * If the parent is not available (typical single-document [content] URIs), falls back to that file only.
 */
internal suspend fun resolveWallpaperSourceFromPickedImageUri(
    context: Context,
    pickedUri: Uri,
): WallpaperPickResolution? = withContext(Dispatchers.IO) {
    if (pickedUri.scheme.equals("file", ignoreCase = true)) {
        val f = File(pickedUri.path ?: return@withContext null)
        if (!f.isFile) return@withContext null
        val parent = f.parentFile?.takeIf { it.isDirectory } ?: return@withContext null
        return@withContext WallpaperPickResolution(
            folderUriString = Uri.fromFile(parent).toString(),
            selectedFileName = f.name,
        )
    }
    val doc = DocumentFile.fromSingleUri(context, pickedUri) ?: return@withContext null
    if (!doc.isFile || !isLikelyImageDocument(doc)) return@withContext null
    val name = doc.name ?: return@withContext null
    val parent = runCatching { doc.parentFile }.getOrNull()
    if (parent != null && parent.isDirectory) {
        WallpaperPickResolution(
            folderUriString = parent.uri.toString(),
            selectedFileName = name,
        )
    } else {
        WallpaperPickResolution(
            folderUriString = pickedUri.toString(),
            selectedFileName = name,
        )
    }
}

internal data class WallpaperPickResolution(
    val folderUriString: String,
    val selectedFileName: String,
)

internal suspend fun decodeImageBitmapFromUri(
    context: Context,
    uri: Uri,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)?.asImageBitmap()
        }
    }.getOrNull()
}

/**
 * Picks [savedFileName] if it still exists in [sortedNames]; otherwise the first name; or null if empty.
 */
internal fun effectiveWallpaperFileName(sortedNames: List<String>, savedFileName: String): String? {
    if (sortedNames.isEmpty()) return null
    if (savedFileName.isNotBlank() && sortedNames.contains(savedFileName)) {
        return savedFileName
    }
    return sortedNames.first()
}
