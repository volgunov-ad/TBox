package vad.dashing.tbox.location.roadmatch

import android.content.Context
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.update.YandexDiskClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

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
    /** True while remote/bundled catalog JSON is being fetched. */
    val catalogLoading: Boolean = false,
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
    private var catalogLoading: Boolean = false
    /** Job of the in-flight [downloadRegion]; cancelled by [cancelQueued]. */
    private var activeDownloadJob: Job? = null
    /** Thread-safe cancel requests (progress callbacks run off the mutex). */
    private val cancelRequested = ConcurrentHashMap.newKeySet<String>()
    /** Finalized installs win a cancel race that arrives after the atomic swap. */
    private val completedDownloads = ConcurrentHashMap<String, RoadMapInstallEntry>()

    fun mapsDir(): File = File(appContext.filesDir, "road_maps").also { it.mkdirs() }

    fun fileFor(regionId: String): File = File(mapsDir(), "$regionId.tboxroads")

    fun bundleDirFor(regionId: String): File = RoadMapBundle.installDir(mapsDir(), regionId)

    suspend fun ensureLoaded() {
        mutex.withLock {
            catalogLoading = true
            publishLocked()
            try {
                val firstLoad = catalog.regions.isEmpty()
                catalog = loadCatalog()
                if (firstLoad) {
                    installed = RoadMapInstallManifest.parse(loadManifestJson()).toMutableMap()
                }
                val pruned = pruneMissingFilesLocked()
                if (pruned) persistManifestLocked()
            } finally {
                catalogLoading = false
                publishLocked()
            }
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
            val jobToCancel: Job?
            mutex.withLock {
                if (queued.remove(regionId)) {
                    queue.removeAll { it == regionId }
                }
                jobToCancel = if (activeId == regionId) {
                    cancelRequested.add(regionId)
                    errors[regionId] = "cancelled"
                    activeDownloadJob
                } else {
                    null
                }
                publishLocked()
            }
            // Cancel outside the mutex so the download coroutine can finish cleanup
            // (progress updates also take the mutex).
            jobToCancel?.cancel(CancellationException("cancelled"))
        }
    }

    fun deleteInstalled(regionId: String) {
        scope.launch {
            mutex.withLock {
                fileFor(regionId).delete()
                bundleDirFor(regionId).deleteRecursively()
                RoadMapBundle.stagingDir(mapsDir(), regionId).deleteRecursively()
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
        val byId = cat.regions.associateBy { it.id }
        return installed.keys.mapNotNull { id ->
            val covered = when {
                bundleDirFor(id).isDirectory -> runCatching {
                    RoadMapBundle.loadIndex(bundleDirFor(id)).contains(lat, lon)
                }.getOrDefault(false)
                fileFor(id).isFile -> runCatching {
                    RoadGraph.peekHeader(fileFor(id)).contains(lat, lon)
                }.getOrDefault(false)
                else -> false
            }
            byId[id].takeIf { covered }
        }
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
                    cancelRequested.remove(id)
                    publishLocked()
                    id
                } ?: break
                val hadInstalled = mutex.withLock { installed.containsKey(next) }
                val result = try {
                    coroutineScope {
                        val job = async { downloadRegion(next) }
                        mutex.withLock { activeDownloadJob = job }
                        try {
                            Result.success(job.await())
                        } finally {
                            mutex.withLock {
                                if (activeDownloadJob === job) activeDownloadJob = null
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    // Child download cancelled by user — continue the worker.
                    // If the whole worker/scope is cancelling, propagate.
                    if (!isActive) throw e
                    Result.failure(e)
                } catch (e: Exception) {
                    Result.failure(e)
                }
                val completedEntry = completedDownloads.remove(next)
                val effectiveResult = completedEntry?.let { Result.success(it) } ?: result
                mutex.withLock {
                    activeId = null
                    progress.remove(next)
                    val cancelled = completedEntry == null &&
                        isCancelledFailure(next, effectiveResult.exceptionOrNull())
                    effectiveResult.onSuccess { entry ->
                        if (cancelled) {
                            // Cancel raced with completion — do not keep a partial/new install.
                            errors.remove(next)
                            File(fileFor(next).absolutePath + ".part").delete()
                            RoadMapBundle.stagingDir(mapsDir(), next).deleteRecursively()
                            if (!hadInstalled) {
                                fileFor(next).delete()
                                bundleDirFor(next).deleteRecursively()
                            }
                        } else {
                            installed[next] = entry
                            errors.remove(next)
                            persistManifestLocked()
                        }
                    }.onFailure {
                        if (cancelled) {
                            errors.remove(next)
                        } else {
                            errors[next] = friendlyDownloadError(it)
                        }
                        File(fileFor(next).absolutePath + ".part").delete()
                        RoadMapBundle.stagingDir(mapsDir(), next).deleteRecursively()
                        if (!hadInstalled) {
                            fileFor(next).delete()
                            bundleDirFor(next).deleteRecursively()
                        }
                    }
                    cancelRequested.remove(next)
                    publishLocked()
                }
            }
        }
    }

    private fun isCancelledFailure(regionId: String, error: Throwable?): Boolean {
        if (regionId in cancelRequested || errors[regionId] == "cancelled") return true
        if (error is CancellationException) return true
        val msg = error?.message.orEmpty()
        return msg.contains("cancelled", ignoreCase = true) ||
            msg.contains("canceled", ignoreCase = true)
    }

    private suspend fun throwIfCancelled(regionId: String) {
        coroutineContext.ensureActive()
        if (regionId in cancelRequested) throw CancellationException("cancelled")
    }

    private suspend fun downloadRegion(regionId: String): RoadMapInstallEntry {
        val region = mutex.withLock { catalog.findById(regionId) }
            ?: error("unknown region")
        val dest = fileFor(regionId)
        val tmp = File(dest.absolutePath + ".part")
        tmp.delete()
        val sizeNameVersion = withContext(Dispatchers.IO) {
            throwIfCancelled(regionId)
            when {
                region.url.startsWith("asset://") -> {
                    val assetPath = region.url.removePrefix("asset://")
                    appContext.assets.open(assetPath).use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buf = ByteArray(8 * 1024)
                            var written = 0L
                            val total = region.bytes.takeIf { it > 0 } ?: input.available().toLong()
                            while (true) {
                                throwIfCancelled(regionId)
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
                        // Checked on each chunk; Job.cancel also aborts OkHttp via YandexDiskClient.
                        if (regionId in cancelRequested) {
                            throw CancellationException("cancelled")
                        }
                        val expected = total?.takeIf { it > 0L } ?: region.bytes
                        val value = if (expected > 0L) {
                            (written.toFloat() / expected).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        scope.launch {
                            mutex.withLock {
                                if (regionId in cancelRequested) return@withLock
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
                                    throwIfCancelled(regionId)
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
            throwIfCancelled(regionId)
            if (RoadMapBundle.isBundle(tmp)) {
                val stage = RoadMapBundle.stagingDir(mapsDir(), regionId)
                val finalDir = bundleDirFor(regionId)
                val backup = File(finalDir.absolutePath + ".old")
                val context = coroutineContext
                val index = RoadMapBundle.extractAndValidate(
                    zipFile = tmp,
                    stagingDir = stage,
                    expectedRegionId = regionId,
                ) {
                    context.ensureActive()
                    if (regionId in cancelRequested) throw CancellationException("cancelled")
                }
                throwIfCancelled(regionId)
                backup.deleteRecursively()
                if (finalDir.exists() && !finalDir.renameTo(backup)) {
                    throw IllegalStateException("cannot back up installed bundle")
                }
                try {
                    if (!stage.renameTo(finalDir)) {
                        require(stage.copyRecursively(finalDir, overwrite = true)) {
                            "cannot install bundle"
                        }
                        stage.deleteRecursively()
                    }
                    // Bundle supersedes legacy monolithic pack.
                    fileFor(regionId).delete()
                    backup.deleteRecursively()
                } catch (t: Throwable) {
                    finalDir.deleteRecursively()
                    if (backup.exists()) backup.renameTo(finalDir)
                    throw t
                } finally {
                    tmp.delete()
                }
                val result = Triple(
                    RoadMapBundle.directorySize(finalDir),
                    finalDir.name,
                    index.graphVersion,
                )
                completedDownloads[regionId] = RoadMapInstallEntry(
                    id = regionId,
                    graphVersion = result.third,
                    fileName = result.second,
                    bytesOnDisk = result.first,
                    installedAtEpochMs = System.currentTimeMillis(),
                )
                return@withContext result
            }
            // Validate before replacing any installed pack so a huge/corrupt download
            // cannot wipe a working file (OOM on Moscow Oblast previously hit here).
            val graph = try {
                RoadGraph.load(tmp)
            } catch (oom: OutOfMemoryError) {
                tmp.delete()
                throw IllegalStateException("pack too large for device memory", oom)
            }
            throwIfCancelled(regionId)
            val graphVersion = graph.graphVersion.takeIf { it > 0 } ?: region.graphVersion
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            // Do not RoadGraphStore.put here — large oblast packs stay on disk until match.
            Triple(dest.length(), dest.name, graphVersion).also { result ->
                completedDownloads[regionId] = RoadMapInstallEntry(
                    id = regionId,
                    graphVersion = result.third,
                    fileName = result.second,
                    bytesOnDisk = result.first,
                    installedAtEpochMs = System.currentTimeMillis(),
                )
            }
        }
        return completedDownloads[regionId] ?: RoadMapInstallEntry(
            id = regionId,
            graphVersion = sizeNameVersion.third,
            fileName = sizeNameVersion.second,
            bytesOnDisk = sizeNameVersion.first,
            installedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun friendlyDownloadError(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.firstOrNull {
            it is OutOfMemoryError || it.message?.contains("allocate", ignoreCase = true) == true
        } ?: e
        if (root is OutOfMemoryError ||
            e.message?.contains("pack too large", ignoreCase = true) == true ||
            root.message?.contains("allocate", ignoreCase = true) == true
        ) {
            return "out of memory for this pack"
        }
        return (e.message ?: root.message ?: "error").take(120)
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
        val missing = installed.keys.filter {
            !fileFor(it).isFile && !bundleDirFor(it).isDirectory
        }
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
            catalogLoading = catalogLoading,
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
