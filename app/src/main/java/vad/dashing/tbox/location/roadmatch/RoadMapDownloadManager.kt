package vad.dashing.tbox.location.roadmatch

import android.content.Context
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.update.YandexDiskClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

enum class RoadMapRegionStatus {
    NOT_INSTALLED,
    QUEUED,
    DOWNLOADING,
    INSTALLED,
    ERROR,
    UNAVAILABLE,
}

data class RoadMapRegionUiState(
    val region: RoadMapRegion,
    val status: RoadMapRegionStatus,
    /** 0…1 while [RoadMapRegionStatus.DOWNLOADING]. */
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val installed: RoadMapInstallEntry? = null,
    /** Catalog [RoadMapRegion.graphVersion] newer than installed pack. */
    val updateAvailable: Boolean = false,
)

data class RoadMapDownloadUiSnapshot(
    val catalogVersion: Int = 0,
    val regions: List<RoadMapRegionUiState> = emptyList(),
    val totalBytesOnDisk: Long = 0L,
    val activeDownloadId: String? = null,
)

/**
 * Phase A: catalog load, install manifest, single-flight download queue.
 * Supports `asset://path` stubs and `https://` packs.
 */
class RoadMapDownloadManager(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val loadManifestJson: suspend () -> String,
    private val saveManifestJson: suspend (String) -> Unit,
) {
    private val yandexDiskClient = YandexDiskClient()
    private val mutex = Mutex()
    private val queue = ArrayDeque<String>()
    private var worker: Job? = null

    private val _snapshot = MutableStateFlow(RoadMapDownloadUiSnapshot())
    val snapshot: StateFlow<RoadMapDownloadUiSnapshot> = _snapshot.asStateFlow()

    private var catalog: RoadMapCatalog = RoadMapCatalog(0, emptyList())
    private var installed: MutableMap<String, RoadMapInstallEntry> = linkedMapOf()
    private val progress = mutableMapOf<String, Float>()
    private val errors = mutableMapOf<String, String>()
    private val queued = linkedSetOf<String>()
    private var activeId: String? = null

    fun mapsDir(): File = File(appContext.filesDir, "road_maps").also { it.mkdirs() }

    fun fileFor(regionId: String): File = File(mapsDir(), "$regionId.tboxroads")

    suspend fun ensureLoaded() {
        mutex.withLock {
            val firstLoad = catalog.regions.isEmpty()
            catalog = loadCatalog()
            if (firstLoad) {
                installed = RoadMapInstallManifest.parse(loadManifestJson()).toMutableMap()
            }
            val pruned = pruneMissingFilesLocked()
            if (pruned) persistManifestLocked()
            publishLocked()
        }
    }

    fun enqueueDownload(regionId: String) {
        scope.launch {
            mutex.withLock {
                val region = catalog.findById(regionId) ?: return@withLock
                if (!region.hasDownloadUrl) {
                    errors[regionId] = "unavailable"
                    publishLocked()
                    return@withLock
                }
                if (installed.containsKey(regionId) && activeId != regionId) {
                    // Re-download / update.
                }
                errors.remove(regionId)
                if (activeId == regionId || queued.contains(regionId)) {
                    publishLocked()
                    return@withLock
                }
                queued.add(regionId)
                queue.addLast(regionId)
                publishLocked()
                ensureWorkerLocked()
            }
        }
    }

    fun cancelQueued(regionId: String) {
        scope.launch {
            mutex.withLock {
                if (queued.remove(regionId)) {
                    queue.removeAll { it == regionId }
                }
                // Active download cancellation: Phase A best-effort — mark error and let worker finish/delete partial.
                if (activeId == regionId) {
                    errors[regionId] = "cancelled"
                }
                publishLocked()
            }
        }
    }

    fun deleteInstalled(regionId: String) {
        scope.launch {
            mutex.withLock {
                fileFor(regionId).delete()
                installed.remove(regionId)
                progress.remove(regionId)
                errors.remove(regionId)
                RoadGraphStore.remove(regionId)
                persistManifestLocked()
                publishLocked()
            }
        }
    }

    fun coveringInstalled(lat: Double, lon: Double): List<RoadMapRegion> {
        val cat = catalog
        val graphs = RoadGraphStore.coveringInstalled(
            lat = lat,
            lon = lon,
            installedIds = installed.keys,
            fileFor = { fileFor(it) },
        )
        if (graphs.isEmpty()) return emptyList()
        val byId = cat.regions.associateBy { it.id }
        return graphs.mapNotNull { byId[it.regionId] }
    }

    private fun ensureWorkerLocked() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val next = mutex.withLock {
                    val id = queue.removeFirstOrNull() ?: return@withLock null
                    queued.remove(id)
                    activeId = id
                    progress[id] = 0f
                    errors.remove(id)
                    publishLocked()
                    id
                } ?: break
                val hadInstalled = mutex.withLock { installed.containsKey(next) }
                val result = runCatching { downloadRegion(next) }
                mutex.withLock {
                    activeId = null
                    progress.remove(next)
                    result.onSuccess { entry ->
                        installed[next] = entry
                        errors.remove(next)
                        persistManifestLocked()
                    }.onFailure { e ->
                        if (errors[next] != "cancelled") {
                            errors[next] = e.message?.take(120) ?: "error"
                        }
                        // Keep existing pack on failed/cancelled update; only remove partial.
                        File(fileFor(next).absolutePath + ".part").delete()
                        if (!hadInstalled) {
                            fileFor(next).delete()
                        }
                    }
                    publishLocked()
                }
            }
        }
    }

    private suspend fun downloadRegion(regionId: String): RoadMapInstallEntry {
        val region = mutex.withLock { catalog.findById(regionId) }
            ?: error("unknown region")
        val dest = fileFor(regionId)
        val tmp = File(dest.absolutePath + ".part")
        tmp.delete()
        withContext(Dispatchers.IO) {
            when {
                region.url.startsWith("asset://") -> {
                    val assetPath = region.url.removePrefix("asset://")
                    appContext.assets.open(assetPath).use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buf = ByteArray(8 * 1024)
                            var written = 0L
                            val total = region.bytes.takeIf { it > 0 } ?: input.available().toLong()
                            while (true) {
                                val n = input.read(buf)
                                if (n <= 0) break
                                output.write(buf, 0, n)
                                written += n
                                val p = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else 0f
                                mutex.withLock {
                                    progress[regionId] = p
                                    publishLocked()
                                }
                            }
                        }
                    }
                }
                RoadMapRemoteUrl.yandexPathOrNull(region.url) != null -> {
                    val path = requireNotNull(RoadMapRemoteUrl.yandexPathOrNull(region.url))
                    yandexDiskClient.downloadToFile(
                        publicKey = BuildConfig.UPDATE_RELEASE_PUBLIC_KEY,
                        path = path,
                        destination = tmp,
                    ) { written, total ->
                        val expected = total?.takeIf { it > 0L } ?: region.bytes
                        val value = if (expected > 0L) {
                            (written.toFloat() / expected).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        scope.launch {
                            mutex.withLock {
                                progress[regionId] = value
                                publishLocked()
                            }
                        }
                    }
                }
                region.url.startsWith("https://") || region.url.startsWith("http://") -> {
                    val conn = (URL(region.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 60_000
                        instanceFollowRedirects = true
                    }
                    try {
                        if (conn.responseCode !in 200..299) {
                            error("HTTP ${conn.responseCode}")
                        }
                        val total = conn.contentLengthLong.takeIf { it > 0 } ?: region.bytes
                        conn.inputStream.use { input ->
                            FileOutputStream(tmp).use { output ->
                                val buf = ByteArray(16 * 1024)
                                var written = 0L
                                while (true) {
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    output.write(buf, 0, n)
                                    written += n
                                    val p = if (total > 0) {
                                        (written.toFloat() / total).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    mutex.withLock {
                                        if (errors[regionId] == "cancelled") {
                                            error("cancelled")
                                        }
                                        progress[regionId] = p
                                        publishLocked()
                                    }
                                }
                            }
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
                else -> error("unsupported url")
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        }
        val size = dest.length()
        // Validate pack early so a corrupt download does not stay "installed".
        val graph = RoadGraph.load(dest)
        RoadGraphStore.put(regionId, graph)
        return RoadMapInstallEntry(
            id = regionId,
            graphVersion = graph.graphVersion.takeIf { it > 0 } ?: region.graphVersion,
            fileName = dest.name,
            bytesOnDisk = size,
            installedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private suspend fun loadCatalog(): RoadMapCatalog {
        val remote = withContext(Dispatchers.IO) {
            runCatching {
                yandexDiskClient.fetchText(
                    publicKey = BuildConfig.UPDATE_RELEASE_PUBLIC_KEY,
                    path = RoadMapRemoteUrl.REMOTE_CATALOG_PATH,
                )
            }.getOrNull()
        }
        if (!remote.isNullOrBlank()) {
            runCatching { RoadMapCatalog.parse(remote) }
                .getOrNull()
                ?.takeIf { it.regions.isNotEmpty() }
                ?.let { return it }
        }
        return loadBundledCatalog()
    }

    private fun loadBundledCatalog(): RoadMapCatalog {
        val json = appContext.assets.open("road_maps/catalog.json")
            .bufferedReader()
            .use { it.readText() }
        return RoadMapCatalog.parse(json)
    }

    /** @return true if manifest changed */
    private fun pruneMissingFilesLocked(): Boolean {
        val missing = installed.keys.filter { !fileFor(it).isFile }
        if (missing.isEmpty()) return false
        for (id in missing) installed.remove(id)
        return true
    }

    private suspend fun persistManifestLocked() {
        saveManifestJson(RoadMapInstallManifest.toJson(installed))
    }

    private fun publishLocked() {
        val states = catalog.regions.map { region ->
            val inst = installed[region.id]
            val err = errors[region.id]
            // Prefer INSTALLED when a pack is on disk so Update/Delete stay available after a failed update.
            val status = when {
                activeId == region.id -> RoadMapRegionStatus.DOWNLOADING
                queued.contains(region.id) -> RoadMapRegionStatus.QUEUED
                inst != null -> RoadMapRegionStatus.INSTALLED
                !region.hasDownloadUrl -> RoadMapRegionStatus.UNAVAILABLE
                err != null -> RoadMapRegionStatus.ERROR
                else -> RoadMapRegionStatus.NOT_INSTALLED
            }
            RoadMapRegionUiState(
                region = region,
                status = status,
                progress = progress[region.id] ?: 0f,
                errorMessage = err,
                installed = inst,
                updateAvailable = inst != null && region.graphVersion > inst.graphVersion,
            )
        }
        _snapshot.value = RoadMapDownloadUiSnapshot(
            catalogVersion = catalog.version,
            regions = states,
            totalBytesOnDisk = installed.values.sumOf { it.bytesOnDisk },
            activeDownloadId = activeId,
        )
    }
}

/** Process-wide holder started from [vad.dashing.tbox.SettingsViewModel] / UI. */
object RoadMapDownloadManagerHolder {
    @Volatile
    private var instance: RoadMapDownloadManager? = null

    fun getOrCreate(
        context: Context,
        scope: CoroutineScope,
        loadManifestJson: suspend () -> String,
        saveManifestJson: suspend (String) -> Unit,
    ): RoadMapDownloadManager {
        instance?.let { return it }
        return synchronized(this) {
            instance?.let { return it }
            RoadMapDownloadManager(
                appContext = context.applicationContext,
                scope = scope,
                loadManifestJson = loadManifestJson,
                saveManifestJson = saveManifestJson,
            ).also { instance = it }
        }
    }

    fun peek(): RoadMapDownloadManager? = instance
}
