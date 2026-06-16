package vad.dashing.tbox

import android.content.Context
import android.net.Uri
import java.io.File

object ThemeFileResolver {

    fun isAccessible(context: Context, uriString: String): Boolean {
        val trimmed = uriString.trim()
        if (trimmed.isEmpty()) return false
        return runCatching {
            when (val uri = Uri.parse(trimmed)) {
                is Uri -> when (uri.scheme) {
                    "file" -> {
                        val path = uri.path ?: return@runCatching false
                        val file = File(path)
                        file.exists() && file.isFile
                    }
                    "content" -> {
                        context.contentResolver.openInputStream(uri)?.use { true } ?: false
                    }
                    else -> false
                }
            }
        }.getOrDefault(false)
    }

    fun displayName(uriString: String): String {
        val trimmed = uriString.trim()
        if (trimmed.isEmpty()) return ""
        return runCatching {
            val uri = Uri.parse(trimmed)
            uri.lastPathSegment ?: trimmed
        }.getOrDefault(trimmed)
    }

    fun openBytes(context: Context, uriString: String): ByteArray? {
        val trimmed = uriString.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val uri = Uri.parse(trimmed)
            when (uri.scheme) {
                "file" -> File(uri.path ?: return@runCatching null).takeIf { it.isFile }?.readBytes()
                "content" -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                else -> null
            }
        }.getOrNull()
    }
}
