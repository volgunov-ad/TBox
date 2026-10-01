package vad.dashing.voice.tts

import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Recursively copies an assets tree onto the filesystem (needed for espeak-ng-data). */
object AssetTreeCopy {
    fun copyAssetDir(assets: AssetManager, assetPath: String, destRoot: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            copyAssetFile(assets, assetPath, File(destRoot, assetPath))
            return
        }
        File(destRoot, assetPath).mkdirs()
        for (child in children) {
            val childPath = if (assetPath.isEmpty()) child else "$assetPath/$child"
            copyAssetDir(assets, childPath, destRoot)
        }
    }

    private fun copyAssetFile(assets: AssetManager, assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    fun assetExists(assets: AssetManager, path: String): Boolean {
        return try {
            assets.open(path).close()
            true
        } catch (_: IOException) {
            false
        }
    }
}
