package vad.dashing.tbox.location.roadmatch

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

enum class OfflineRegionReadiness {
    /** Pack file not found next to the catalog JSON. */
    MISSING_FILE,
    /** `bytes` / `sha256` missing — install refused. */
    UNVERIFIED,
    NOT_INSTALLED,
    UPDATE,
    ALREADY_INSTALLED,
    ERROR,
}

data class OfflineRegionUiState(
    val offline: RoadMapOfflineRegion,
    val readiness: OfflineRegionReadiness,
    val detail: String? = null,
) {
    val selectable: Boolean
        get() = when (readiness) {
            OfflineRegionReadiness.NOT_INSTALLED,
            OfflineRegionReadiness.UPDATE,
            OfflineRegionReadiness.ALREADY_INSTALLED,
            -> true
            else -> false
        }
}

data class OfflineImportProgress(
    val currentId: String?,
    val currentIndex: Int,
    val totalCount: Int,
    /** 0…1 for the current region (copy + hash phase). */
    val regionProgress: Float,
)

data class OfflineImportSummary(
    val succeeded: List<String>,
    val failed: List<Pair<String, String>>,
)

/**
 * Stage G: read a USB/SAF catalog JSON, resolve sibling packs, verify size/SHA-256,
 * and atomically install via [RoadMapDownloadManager.installBundleFromLocalZip].
 */
class RoadMapOfflineImportManager(
    private val appContext: Context,
    private val downloadManager: RoadMapDownloadManager,
) {
    private val cancelRequested = AtomicBoolean(false)

    fun cancel() {
        cancelRequested.set(true)
    }

    suspend fun readCatalog(catalogUri: Uri): Result<RoadMapOfflineCatalog> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = appContext.contentResolver.openInputStream(catalogUri)?.use {
                    it.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("cannot open catalog")
                RoadMapOfflineCatalogParser.parse(json)
            }
        }

    /**
     * Try to take persistable read permission; ignore failures (some USB providers
     * only grant temporary access for the session).
     */
    fun tryPersistReadPermission(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun buildRegionStates(
        catalog: RoadMapOfflineCatalog,
        catalogUri: Uri,
        folderUri: Uri? = null,
    ): List<OfflineRegionUiState> {
        return catalog.regions.map { offline ->
            val region = offline.region
            if (offline.sha256.length != 64 || !HEX.matches(offline.sha256) || region.bytes <= 0L) {
                return@map OfflineRegionUiState(
                    offline = offline,
                    readiness = OfflineRegionReadiness.UNVERIFIED,
                    detail = "sha256/bytes required",
                )
            }
            val packUri = resolvePackUri(catalogUri, folderUri, offline.relativeFile)
            if (packUri == null) {
                return@map OfflineRegionUiState(
                    offline = offline,
                    readiness = OfflineRegionReadiness.MISSING_FILE,
                    detail = offline.relativeFile,
                )
            }
            val installed = downloadManager.installedEntry(region.id)
            when {
                installed == null -> OfflineRegionUiState(
                    offline = offline,
                    readiness = OfflineRegionReadiness.NOT_INSTALLED,
                )
                region.graphVersion > installed.graphVersion -> OfflineRegionUiState(
                    offline = offline,
                    readiness = OfflineRegionReadiness.UPDATE,
                    detail = "${installed.graphVersion}|${region.graphVersion}",
                )
                else -> OfflineRegionUiState(
                    offline = offline,
                    readiness = OfflineRegionReadiness.ALREADY_INSTALLED,
                    detail = installed.graphVersion.toString(),
                )
            }
        }
    }

    /**
     * Copy → verify bytes/SHA-256 → atomic install, one region at a time.
     * Previous installs are left intact until swap succeeds.
     */
    suspend fun importSelected(
        catalogUri: Uri,
        folderUri: Uri?,
        catalog: RoadMapOfflineCatalog,
        regionIds: Set<String>,
        onProgress: (OfflineImportProgress) -> Unit,
    ): OfflineImportSummary = withContext(Dispatchers.IO) {
        cancelRequested.set(false)
        val selected = catalog.regions.filter { it.region.id in regionIds }
        val succeeded = ArrayList<String>()
        val failed = ArrayList<Pair<String, String>>()
        val total = selected.size
        selected.forEachIndexed { index, offline ->
            coroutineContext.ensureActive()
            if (cancelRequested.get()) {
                failed.add(offline.region.id to "cancelled")
                return@forEachIndexed
            }
            onProgress(
                OfflineImportProgress(
                    currentId = offline.region.id,
                    currentIndex = index,
                    totalCount = total,
                    regionProgress = 0f,
                ),
            )
            val result = runCatching {
                importOne(catalogUri, folderUri, offline) { p ->
                    onProgress(
                        OfflineImportProgress(
                            currentId = offline.region.id,
                            currentIndex = index,
                            totalCount = total,
                            regionProgress = p,
                        ),
                    )
                }
            }
            result.onSuccess { succeeded.add(offline.region.id) }
                .onFailure { e ->
                    val msg = when {
                        cancelRequested.get() || e.message?.contains("cancel", true) == true ->
                            "cancelled"
                        e.message?.contains("space", true) == true -> "no space"
                        else -> (e.message ?: "error").take(120)
                    }
                    failed.add(offline.region.id to msg)
                }
        }
        OfflineImportSummary(succeeded = succeeded, failed = failed)
    }

    private suspend fun importOne(
        catalogUri: Uri,
        folderUri: Uri?,
        offline: RoadMapOfflineRegion,
        onProgress: (Float) -> Unit,
    ) {
        val region = offline.region
        if (offline.sha256.length != 64 || !HEX.matches(offline.sha256) || region.bytes <= 0L) {
            error("sha256/bytes required")
        }
        val packUri = resolvePackUri(catalogUri, folderUri, offline.relativeFile)
            ?: error("file missing: ${offline.relativeFile}")

        val need = region.bytes + (4L * 1024L * 1024L)
        if (downloadManager.usableSpaceBytes() < need) {
            error("no space")
        }

        val tmp = File(downloadManager.mapsDir(), "${region.id}.download.part")
        tmp.delete()
        try {
            copyAndHash(
                source = packUri,
                dest = tmp,
                expectedBytes = region.bytes,
                expectedSha256 = offline.sha256,
                onProgress = onProgress,
            )
            throwIfCancel()
            downloadManager.installBundleFromLocalZip(
                zipFile = tmp,
                regionId = region.id,
                expectedGraphVersion = region.graphVersion,
                checkCancelled = { throwIfCancel() },
            )
        } finally {
            tmp.delete()
            RoadMapBundle.stagingDir(downloadManager.mapsDir(), region.id).deleteRecursively()
        }
    }

    private fun throwIfCancel() {
        if (cancelRequested.get()) throw kotlinx.coroutines.CancellationException("cancelled")
    }

    private fun copyAndHash(
        source: Uri,
        dest: File,
        expectedBytes: Long,
        expectedSha256: String,
        onProgress: (Float) -> Unit,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L
        appContext.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    throwIfCancel()
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    written += n
                    if (written > expectedBytes) {
                        error("size mismatch")
                    }
                    onProgress((written.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f))
                }
            }
        } ?: error("cannot open pack")
        if (written != expectedBytes) error("size mismatch")
        val actual = digest.digest().joinToString("") { b -> "%02x".format(b) }
        if (actual != expectedSha256) error("hash mismatch")
        onProgress(1f)
    }

    /**
     * Resolve [relativeFile] under the catalog's parent folder, or under [folderUri]
     * when the user picked a document tree (sibling listing fallback).
     */
    fun resolvePackUri(catalogUri: Uri, folderUri: Uri?, relativeFile: String): Uri? {
        val safe = RoadMapOfflineCatalogParser.sanitizeRelativePackPath(relativeFile) ?: return null
        folderUri?.let { tree ->
            resolveUnderTree(tree, safe)?.let { return it }
        }
        resolveBesideCatalog(catalogUri, safe)?.let { return it }
        // file:// catalog on a real filesystem path
        if (catalogUri.scheme.equals("file", ignoreCase = true)) {
            val catalogFile = File(catalogUri.path ?: return null)
            val parent = catalogFile.parentFile ?: return null
            val pack = File(parent, safe)
            if (pack.isFile) return Uri.fromFile(pack)
        }
        return null
    }

    private fun resolveBesideCatalog(catalogUri: Uri, relativeFile: String): Uri? {
        val catalogDoc = DocumentFile.fromSingleUri(appContext, catalogUri) ?: return null
        val parent = runCatching { catalogDoc.parentFile }.getOrNull() ?: return null
        return navigateRelative(parent, relativeFile)?.takeIf { it.isFile }?.uri
    }

    private fun resolveUnderTree(treeUri: Uri, relativeFile: String): Uri? {
        val root = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: DocumentFile.fromSingleUri(appContext, treeUri)
            ?: return null
        if (!root.isDirectory) return null
        return navigateRelative(root, relativeFile)?.takeIf { it.isFile }?.uri
    }

    private fun navigateRelative(root: DocumentFile, relativeFile: String): DocumentFile? {
        var current: DocumentFile = root
        val parts = relativeFile.split('/')
        for ((i, part) in parts.withIndex()) {
            val next = current.listFiles().firstOrNull { it.name == part } ?: return null
            if (i < parts.lastIndex) {
                if (!next.isDirectory) return null
                current = next
            } else {
                return next
            }
        }
        return null
    }

    companion object {
        private val HEX = Regex("^[0-9a-f]{64}$")
    }
}
