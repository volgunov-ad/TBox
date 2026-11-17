package vad.dashing.tbox

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal object DataStoreFiles {
    private const val DATASTORE_DIR = "datastore"
    private const val DATASTORE_FILE_SUFFIX = ".preferences_pb"
    private const val BACKUP_DIR = "datastore_backup"
    private const val BACKUP_FILE_SUFFIX = ".preferences_pb.bak"
    private const val DIAGNOSTICS_DIR = "diagnostics"
    private const val CORRUPT_DIR_NAME = "corrupt"
    private const val MARKER_FILE_NAME = "datastore_corruption.log"

    fun mainFile(filesDir: File, storeName: String): File =
        File(File(filesDir, DATASTORE_DIR), "$storeName$DATASTORE_FILE_SUFFIX")

    fun backupFile(filesDir: File, storeName: String): File =
        File(File(filesDir, BACKUP_DIR), "$storeName$BACKUP_FILE_SUFFIX")

    fun corruptDir(filesDir: File): File =
        File(File(filesDir, DIAGNOSTICS_DIR), CORRUPT_DIR_NAME)

    fun markerFile(filesDir: File): File =
        File(File(filesDir, DIAGNOSTICS_DIR), MARKER_FILE_NAME)
}

internal object DataStoreCorruptionDiagnostics {
    private const val MAX_CORRUPT_COPIES = 3

    fun copyCorruptFile(filesDir: File, storeName: String): File? = try {
        val main = DataStoreFiles.mainFile(filesDir, storeName)
        if (main.isFile) {
            val dir = DataStoreFiles.corruptDir(filesDir)
            if (dir.isDirectory || dir.mkdirs()) {
                rotateOldCopies(dir, storeName)
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                main.copyTo(File(dir, "$storeName.$stamp.corrupt"), overwrite = true)
            } else {
                null
            }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    fun appendMarker(
        filesDir: File,
        storeName: String,
        corruptCopy: File?,
        restored: Boolean,
        error: Throwable?,
    ) = try {
        val marker = DataStoreFiles.markerFile(filesDir)
        marker.parentFile?.let { it.mkdirs() }
        val corruptInfo = when {
            corruptCopy != null -> "${corruptCopy.length()}B"
            DataStoreFiles.mainFile(filesDir, storeName).isFile -> "unreadable"
            else -> "missing"
        }
        val errorInfo =
            error?.let { " error=${it.javaClass.simpleName}:${it.message ?: ""}" } ?: ""
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        marker.appendText("$stamp store=$storeName corrupt=$corruptInfo restored=$restored$errorInfo\n")
    } catch (_: Exception) {
    }

    private fun rotateOldCopies(dir: File, storeName: String) {
        val copies =
            dir.listFiles { file -> file.isFile && file.name.startsWith("$storeName.") } ?: return
        val excess = copies.size - MAX_CORRUPT_COPIES + 1
        if (excess > 0) {
            copies.sortedBy { it.name }.take(excess).forEach { it.delete() }
        }
    }
}

internal object DataStoreBackupManager {
    const val BACKUP_INTERVAL_MS = 5 * 60_000L

    fun startPeriodicBackup(scope: CoroutineScope) {
        scope.launch {
            val context = AppContextHolder.appContextOrNull ?: return@launch
            while (isActive) {
                delay(BACKUP_INTERVAL_MS)
                try {
                    backupAll(context)
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun backupAll(context: Context) {
        val filesDir = context.filesDir
        backupIfChanged(filesDir, DATASTORE_NAME)
        backupIfChanged(filesDir, APP_DATA_NAME)
    }

    suspend fun backupIfChanged(filesDir: File, storeName: String): Boolean =
        withContext(Dispatchers.IO) {
            val main = DataStoreFiles.mainFile(filesDir, storeName)
            if (!main.isFile) return@withContext false
            val backup = DataStoreFiles.backupFile(filesDir, storeName)
            if (backup.isFile && fileSignature(backup) == fileSignature(main)) {
                return@withContext false
            }
            if (readPreferencesFromFile(main) == null) return@withContext false
            atomicCopy(main, backup)
            true
        }

    suspend fun restorePreferences(filesDir: File, storeName: String): Preferences? =
        readPreferencesFromFile(DataStoreFiles.backupFile(filesDir, storeName))

    private fun fileSignature(file: File): Pair<Long, Long> =
        Pair(file.length(), file.lastModified())

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
            tmp.delete()
        }
    }
}

internal suspend fun readPreferencesFromFile(file: File): Preferences? =
    withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() == 0L) return@withContext null
        val tmp =
            File(
                file.parentFile,
                "${file.nameWithoutExtension}.read_${System.nanoTime()}.preferences_pb",
            )
        try {
            file.copyTo(tmp, overwrite = true)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val store =
                    PreferenceDataStoreFactory.create(scope = scope, produceFile = { tmp })
                val preferences = store.data.first()
                if (preferences.asMap().isEmpty()) null else preferences
            } finally {
                scope.cancel()
            }
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

internal suspend fun handleDataStoreCorruption(
    filesDir: File,
    storeName: String,
    error: Throwable?,
): Preferences = withContext(Dispatchers.IO) {
    val corruptCopy = DataStoreCorruptionDiagnostics.copyCorruptFile(filesDir, storeName)
    val restored = DataStoreBackupManager.restorePreferences(filesDir, storeName)
    DataStoreCorruptionDiagnostics.appendMarker(
        filesDir,
        storeName,
        corruptCopy,
        restored != null,
        error,
    )
    restored ?: emptyPreferences()
}

internal fun resetOnCorruption(storeName: String): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { error ->
        Log.e("DataStore", "Corrupt $storeName, resetting", error)
        TboxRepository.addLog("ERROR", "DataStore", "Corrupt $storeName reset: ${error.message}")
        val filesDir = AppContextHolder.appContextOrNull?.filesDir
        if (filesDir != null) {
            runBlocking { handleDataStoreCorruption(filesDir, storeName, error) }
        } else {
            emptyPreferences()
        }
    }
