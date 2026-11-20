package vad.dashing.tbox

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Best-effort mirror of DataStore recovery artifacts into public
 * `Downloads/Tbm` so they can be pulled without `/data/data/.../files`.
 *
 * Live Preferences DataStore files stay in app-private storage. Only
 * `.bak`, `datastore_corruption.log`, and corrupt snapshot copies are copied here.
 * Restore always reads the private bak under [filesDir].
 */
internal object DataStorePublicMirror {
    const val PUBLIC_SUBDIR = "Tbm"
    private const val MAX_PUBLIC_CORRUPT_COPIES = 3

    /** Unit-test hook: when set, skips Environment / storage-permission checks. */
    @Volatile
    var publicRootOverride: File? = null

    fun resolvePublicRoot(context: Context?): File? {
        publicRootOverride?.let { override ->
            return if (override.isDirectory || override.mkdirs()) override else null
        }
        if (context == null) return null
        if (!AppPermissions.hasStorageAccess(context)) return null
        return runCatching {
            val downloadsPath =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        .absolutePath
                } else {
                    Environment.getExternalStorageDirectory().absolutePath + "/Download"
                }
            val root = File(File(downloadsPath), PUBLIC_SUBDIR)
            if (root.isDirectory || root.mkdirs()) root else null
        }.getOrNull()
    }

    fun publicBackupFile(publicRoot: File, storeName: String): File =
        File(File(publicRoot, "datastore_backup"), DataStoreFiles.backupFileName(storeName))

    fun publicMarkerFile(publicRoot: File): File =
        File(File(publicRoot, "diagnostics"), DataStoreFiles.MARKER_FILE_NAME)

    fun publicCorruptDir(publicRoot: File): File =
        File(File(publicRoot, "diagnostics"), "corrupt")

    fun mirrorBackup(privateBak: File, storeName: String, context: Context? = AppContextHolder.appContextOrNull) {
        if (!privateBak.isFile) return
        val root = resolvePublicRoot(context) ?: return
        runCatching {
            atomicCopy(privateBak, publicBackupFile(root, storeName))
        }
    }

    /** Copy private bak to Downloads/Tbm only when the public file is absent. */
    fun mirrorBackupIfPublicMissing(
        privateBak: File,
        storeName: String,
        context: Context? = AppContextHolder.appContextOrNull,
    ) {
        if (!privateBak.isFile) return
        val root = resolvePublicRoot(context) ?: return
        val publicBak = publicBackupFile(root, storeName)
        if (publicBak.isFile) return
        runCatching {
            atomicCopy(privateBak, publicBak)
        }
    }

    fun mirrorMarker(privateMarker: File, context: Context? = AppContextHolder.appContextOrNull) {
        if (!privateMarker.isFile) return
        val root = resolvePublicRoot(context) ?: return
        runCatching {
            atomicCopy(privateMarker, publicMarkerFile(root))
        }
    }

    fun mirrorCorruptCopy(
        privateCorrupt: File,
        storeName: String,
        context: Context? = AppContextHolder.appContextOrNull,
    ) {
        if (!privateCorrupt.isFile) return
        val root = resolvePublicRoot(context) ?: return
        runCatching {
            val dir = publicCorruptDir(root)
            rotateOldCorruptCopies(dir, storeName)
            val target = File(dir, privateCorrupt.name)
            atomicCopy(privateCorrupt, target)
        }
    }

    private fun rotateOldCorruptCopies(dir: File, storeName: String) {
        if (!dir.isDirectory && !dir.mkdirs()) return
        val copies =
            dir.listFiles { file -> file.isFile && file.name.startsWith("$storeName.") } ?: return
        val excess = copies.size - MAX_PUBLIC_CORRUPT_COPIES + 1
        if (excess > 0) {
            copies.sortedBy { it.name }.take(excess).forEach { it.delete() }
        }
    }

    private fun atomicCopy(source: File, target: File) {
        val parent = target.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) return
        val tmp = File(parent, "${target.name}.tmp")
        try {
            source.copyTo(tmp, overwrite = true)
            FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            if (!tmp.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target.setLastModified(source.lastModified())
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
