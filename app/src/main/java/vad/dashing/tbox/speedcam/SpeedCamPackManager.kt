package vad.dashing.tbox.speedcam

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Installs a single SpeedCamOnline iGO/igoext `.txt` from HTTPS or SAF/USB.
 * Layout under [filesDir]/[DIR_NAME]/:
 * - `speedcam.txt` — installed CSV
 * - `manifest.json` — local metadata
 */
class SpeedCamPackManager(
    private val appContext: Context,
    private val settingsManager: SettingsManager,
) {
    data class Snapshot(
        val installed: Boolean = false,
        val pointCount: Int = 0,
        val bytesOnDisk: Long = 0L,
        val installedAtEpochMs: Long = 0L,
        val source: String = "",
        val busy: Boolean = false,
        val progressFraction: Float? = null,
        val statusMessage: String = "",
        val lastError: String? = null,
    )

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    @Volatile
    private var index: SpeedCamIndex? = null

    fun currentIndex(): SpeedCamIndex? = index

    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (index != null && _snapshot.value.installed) return@withLock
            loadFromDiskLocked()
        }
    }

    fun rootDir(): File = File(appContext.filesDir, DIR_NAME).also { it.mkdirs() }

    fun dataFile(): File = File(rootDir(), DATA_FILE_NAME)

    suspend fun downloadFromSite(
        url: String = DEFAULT_DOWNLOAD_URL,
        sourceLabel: String = "speedcamonline",
    ): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                publishBusyLocked(0f, appContext.getString(R.string.speed_cam_status_downloading))
                val part = File(rootDir(), "$DATA_FILE_NAME.part")
                if (part.exists()) part.delete()
                SpeedCamDownloader.downloadToFile(part, url) { readTotal, total ->
                    if (total != null) {
                        publishBusyLocked(
                            (readTotal.toDouble() / total).toFloat().coerceIn(0f, 1f),
                            appContext.getString(R.string.speed_cam_status_downloading),
                        )
                    } else if (readTotal > 0L) {
                        // No Content-Length from SCO — show indeterminate-ish progress by MB.
                        val fake = (readTotal / (4L * 1024L * 1024L).toDouble())
                            .toFloat()
                            .coerceIn(0.05f, 0.85f)
                        publishBusyLocked(
                            fake,
                            appContext.getString(R.string.speed_cam_status_downloading),
                        )
                    }
                }
                installFromFileLocked(part, sourceLabel, deleteSource = true)
            } catch (e: Exception) {
                Log.e(TAG, "downloadFromSite failed", e)
                failLocked(friendlyDownloadError(e))
                false
            }
        }
    }

    suspend fun installFromUri(uri: Uri, sourceLabel: String = "usb"): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    publishBusyLocked(0f, "Reading…")
                    val part = File(rootDir(), "$DATA_FILE_NAME.part")
                    if (part.exists()) part.delete()
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(part).use { out ->
                            input.copyTo(out, 64 * 1024)
                        }
                    } ?: run {
                        failLocked("Cannot open file")
                        return@withLock false
                    }
                    installFromFileLocked(part, sourceLabel, deleteSource = true)
                } catch (e: Exception) {
                    Log.e(TAG, "installFromUri failed", e)
                    failLocked(e.message ?: "import failed")
                    false
                }
            }
        }

    suspend fun deleteInstalled(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                dataFile().delete()
                manifestFile().delete()
                index = null
                settingsManager.saveSpeedCamInstalledJson("")
                _snapshot.value = Snapshot()
                SpeedCamRepository.publish(SpeedCamUiState.EMPTY)
                true
            } catch (e: Exception) {
                Log.e(TAG, "deleteInstalled failed", e)
                failLocked(e.message ?: "delete failed")
                false
            }
        }
    }

    private suspend fun installFromFileLocked(
        part: File,
        sourceLabel: String,
        deleteSource: Boolean,
    ): Boolean {
        publishBusyLocked(0.7f, "Parsing…")
        val text = part.readText(Charsets.UTF_8)
        if (text.trimStart().startsWith("Contact with me", ignoreCase = true)) {
            if (deleteSource) part.delete()
            failLocked("Server returned contact stub (export blocked)")
            return false
        }
        if (!SpeedCamIgoParser.looksLikeIgoCsv(text)) {
            if (deleteSource) part.delete()
            failLocked("Not an iGO speedcam CSV")
            return false
        }
        val points = SpeedCamIgoParser.parse(text)
        if (points.isEmpty()) {
            if (deleteSource) part.delete()
            failLocked("Empty speedcam file")
            return false
        }
        publishBusyLocked(0.9f, "Installing…")
        val finalFile = dataFile()
        val backup = File(rootDir(), "$DATA_FILE_NAME.bak")
        if (finalFile.exists()) {
            backup.delete()
            finalFile.renameTo(backup)
        }
        try {
            part.copyTo(finalFile, overwrite = true)
            if (deleteSource) part.delete()
            val sha = sha256Hex(finalFile)
            val manifest = SpeedCamInstallManifest(
                pointCount = points.size,
                bytesOnDisk = finalFile.length(),
                installedAtEpochMs = System.currentTimeMillis(),
                source = sourceLabel,
                sha256Hex = sha,
                format = "igoext",
            )
            writeManifestLocked(manifest)
            settingsManager.saveSpeedCamInstalledJson(manifestToJson(manifest))
            index = SpeedCamIndex(points)
            backup.delete()
            _snapshot.value = Snapshot(
                installed = true,
                pointCount = points.size,
                bytesOnDisk = finalFile.length(),
                installedAtEpochMs = manifest.installedAtEpochMs,
                source = sourceLabel,
                busy = false,
                statusMessage = "OK",
            )
            return true
        } catch (e: Exception) {
            Log.e(TAG, "install swap failed", e)
            finalFile.delete()
            if (backup.exists()) backup.renameTo(finalFile)
            if (deleteSource) part.delete()
            failLocked(e.message ?: "install failed")
            return false
        }
    }

    private fun loadFromDiskLocked() {
        val file = dataFile()
        if (!file.isFile || file.length() <= 0L) {
            index = null
            _snapshot.value = Snapshot()
            return
        }
        try {
            val text = file.readText(Charsets.UTF_8)
            val points = SpeedCamIgoParser.parse(text)
            index = SpeedCamIndex(points)
            val manifest = readManifestLocked()
                ?: SpeedCamInstallManifest(
                    pointCount = points.size,
                    bytesOnDisk = file.length(),
                    installedAtEpochMs = file.lastModified(),
                    source = "disk",
                )
            _snapshot.value = Snapshot(
                installed = true,
                pointCount = points.size,
                bytesOnDisk = file.length(),
                installedAtEpochMs = manifest.installedAtEpochMs,
                source = manifest.source,
                busy = false,
                statusMessage = "OK",
            )
        } catch (e: Exception) {
            Log.e(TAG, "loadFromDisk failed", e)
            index = null
            _snapshot.value = Snapshot(lastError = e.message)
        }
    }

    private fun manifestFile(): File = File(rootDir(), MANIFEST_FILE_NAME)

    private fun writeManifestLocked(manifest: SpeedCamInstallManifest) {
        manifestFile().writeText(manifestToJson(manifest), Charsets.UTF_8)
    }

    private fun readManifestLocked(): SpeedCamInstallManifest? {
        val f = manifestFile()
        if (!f.isFile) return null
        return runCatching { manifestFromJson(f.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun publishBusyLocked(progress: Float, message: String) {
        _snapshot.value = _snapshot.value.copy(
            busy = true,
            progressFraction = progress,
            statusMessage = message,
            lastError = null,
        )
    }

    private fun failLocked(message: String) {
        _snapshot.value = _snapshot.value.copy(
            busy = false,
            progressFraction = null,
            statusMessage = message,
            lastError = message,
        )
    }

    private fun friendlyDownloadError(e: Exception): String {
        val raw = e.message?.trim().orEmpty()
        return if (SpeedCamDownloader.isTransientNetworkFailure(raw)) {
            appContext.getString(R.string.speed_cam_error_network)
        } else if (raw.isNotEmpty()) {
            raw
        } else {
            appContext.getString(R.string.speed_cam_error_download)
        }
    }

    companion object {
        private const val TAG = "SpeedCamPack"
        const val DIR_NAME = "speedcam"
        const val DATA_FILE_NAME = "speedcam.txt"
        const val MANIFEST_FILE_NAME = "manifest.json"
        /** SpeedCamOnline iGO extended, all types, Russia. */
        const val DEFAULT_DOWNLOAD_URL = SpeedCamDownloader.DEFAULT_URL

        fun manifestToJson(m: SpeedCamInstallManifest): String =
            JSONObject()
                .put("version", m.version)
                .put("pointCount", m.pointCount)
                .put("bytesOnDisk", m.bytesOnDisk)
                .put("installedAtEpochMs", m.installedAtEpochMs)
                .put("source", m.source)
                .put("sha256Hex", m.sha256Hex)
                .put("format", m.format)
                .toString()

        fun manifestFromJson(json: String): SpeedCamInstallManifest {
            val o = JSONObject(json)
            return SpeedCamInstallManifest(
                version = o.optInt("version", 1),
                pointCount = o.optInt("pointCount", 0),
                bytesOnDisk = o.optLong("bytesOnDisk", 0L),
                installedAtEpochMs = o.optLong("installedAtEpochMs", 0L),
                source = o.optString("source", ""),
                sha256Hex = o.optString("sha256Hex", ""),
                format = o.optString("format", "igoext"),
            )
        }

        private fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { b -> "%02x".format(b) }
        }
    }
}

object SpeedCamPackManagerHolder {
    @Volatile
    private var instance: SpeedCamPackManager? = null

    fun get(context: Context, settingsManager: SettingsManager): SpeedCamPackManager {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: SpeedCamPackManager(
                appContext = context.applicationContext,
                settingsManager = settingsManager,
            ).also { instance = it }
        }
    }
}
