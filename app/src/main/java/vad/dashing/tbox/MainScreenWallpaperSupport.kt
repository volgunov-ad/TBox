package vad.dashing.tbox

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Skip wallpaper files larger than this (bytes) when listing and when applying a pick. */
internal const val MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES = 10L * 1024 * 1024

/** Long edge cap for decoded bitmap (px); keeps memory reasonable on head units. */
private const val MAIN_SCREEN_WALLPAPER_DECODE_MAX_EDGE_PX = 4096

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"
)

internal fun isLikelyImageDocument(file: DocumentFile): Boolean {
    if (!file.isFile) return false
    val len = file.length()
    if (len > 0L && len > MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES) return false
    val mime = file.type?.lowercase() ?: ""
    if (mime.startsWith("image/")) return true
    val name = file.name?.substringAfterLast('.', "")?.lowercase() ?: ""
    return name in IMAGE_EXTENSIONS
}

internal fun isWallpaperFileOverSizeLimit(context: Context, uri: Uri): Boolean {
    return try {
        when (uri.scheme?.lowercase()) {
            "file" -> {
                val f = File(uri.path ?: return false)
                f.isFile && f.length() > MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES
            }
            else -> {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val len = pfd.statSize
                    len > 0 && len > MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES
                } ?: false
            }
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Some third-party file managers return [content] URIs whose path embeds a real filesystem
 * location (e.g. `/root/storage/emulated/0/...`). If that path exists, list it as a normal folder.
 */
internal fun localFileFromEmbeddedStoragePath(uri: Uri): File? {
    if (!uri.scheme.equals("content", ignoreCase = true)) return null
    val rawPath = (uri.encodedPath?.let { Uri.decode(it) } ?: uri.path) ?: return null
    val candidates = LinkedHashSet<String>()
    when {
        rawPath.startsWith("/root/storage/") -> candidates.add(rawPath.removePrefix("/root"))
        rawPath.startsWith("/storage/") -> candidates.add(rawPath)
    }
    if (rawPath.startsWith("/root/") && rawPath.length > 5) {
        val stripped = rawPath.removePrefix("/root")
        if (stripped.startsWith("/")) {
            candidates.add(stripped)
        }
    }
    return candidates.map { File(it) }.firstOrNull { it.exists() }
}

private fun listSortedImageFilesInDir(dir: java.io.File): List<Pair<String, Uri>> {
    if (!dir.isDirectory) return emptyList()
    val pairs = dir.listFiles()?.mapNotNull { f ->
        if (!f.isFile) return@mapNotNull null
        if (f.length() > MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES) return@mapNotNull null
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
    localFileFromEmbeddedStoragePath(folderUri)?.let { f ->
        when {
            f.isDirectory -> return@withContext listSortedImageFilesInDir(f)
            f.isFile -> {
                val ext = f.name.substringAfterLast('.', "").lowercase()
                if (ext in IMAGE_EXTENSIONS && f.length() <= MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES) {
                    return@withContext listOf(f.name to Uri.fromFile(f))
                }
            }
        }
    }
    runCatching { DocumentFile.fromTreeUri(context, folderUri) }
        .getOrNull()
        ?.takeIf { it.isDirectory }
        ?.let { root ->
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
    if (isWallpaperFileOverSizeLimit(context, pickedUri)) {
        return@withContext null
    }
    if (pickedUri.scheme.equals("file", ignoreCase = true)) {
        val f = File(pickedUri.path ?: return@withContext null)
        if (!f.isFile) return@withContext null
        val parent = f.parentFile?.takeIf { it.isDirectory } ?: return@withContext null
        return@withContext WallpaperPickResolution(
            folderUriString = Uri.fromFile(parent).toString(),
            selectedFileName = f.name,
        )
    }
    localFileFromEmbeddedStoragePath(pickedUri)?.takeIf { it.isFile }?.let { f ->
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
        val parentLocal = localFileFromEmbeddedStoragePath(parent.uri)
        val folderStr = if (parentLocal != null && parentLocal.isDirectory) {
            Uri.fromFile(parentLocal).toString()
        } else {
            parent.uri.toString()
        }
        WallpaperPickResolution(
            folderUriString = folderStr,
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

/** Long edge after decode is ~original / inSampleSize; keep long edge ≤ [maxSidePx]. */
private fun computeInSampleSize(width: Int, height: Int, maxSidePx: Int): Int {
    if (width <= 0 || height <= 0 || maxSidePx <= 0) return 1
    var inSample = 1
    val longEdge = max(width, height)
    while (longEdge / inSample > maxSidePx) {
        inSample *= 2
    }
    return inSample.coerceAtLeast(1)
}

/**
 * Decodes a wallpaper with subsampling so long edge is at most [maxDecodeSidePx], and skips
 * files over [MAIN_SCREEN_WALLPAPER_MAX_FILE_BYTES]. Returns null on failure / oversize.
 */
internal suspend fun decodeWallpaperImageBitmapFromUri(
    context: Context,
    uri: Uri,
    maxDecodeSidePx: Int,
): ImageBitmap? = withContext(Dispatchers.IO) {
    if (isWallpaperFileOverSizeLimit(context, uri)) {
        return@withContext null
    }
    val cap = maxDecodeSidePx.coerceIn(512, MAIN_SCREEN_WALLPAPER_DECODE_MAX_EDGE_PX)
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return@runCatching null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // Some providers do not fill bounds; try a plain decode once.
            return@runCatching context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, cap)
        }
        val subsampled = context.contentResolver.openInputStream(uri)?.use { input2 ->
            BitmapFactory.decodeStream(input2, null, opts)?.asImageBitmap()
        }
        if (subsampled != null) {
            subsampled
        } else {
            context.contentResolver.openInputStream(uri)?.use { input3 ->
                BitmapFactory.decodeStream(input3)?.asImageBitmap()
            }
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
