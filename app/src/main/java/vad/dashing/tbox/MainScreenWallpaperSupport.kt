package vad.dashing.tbox

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
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
        return@withContext listSortedImageFilesInDir(java.io.File(path))
    }
    val root = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
    val files = root.listFiles().filter { isLikelyImageDocument(it) }
    val pairs = files.mapNotNull { df ->
        val n = df.name ?: return@mapNotNull null
        n to df.uri
    }
    pairs.sortedBy { it.first }
}

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
