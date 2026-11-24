package vad.dashing.store

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vad.dashing.store.catalog.StoreCatalog
import vad.dashing.store.catalog.StoreCatalogEntry
import vad.dashing.store.catalog.StorePublicShareUrl
import vad.dashing.store.catalog.YandexDiskPath
import vad.dashing.store.disk.ApkLanguage
import vad.dashing.store.disk.AppChangelogState
import vad.dashing.store.disk.AppReleaseInfo
import vad.dashing.store.disk.AppReleaseManifest
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.StoreCloudFacade
import vad.dashing.store.disk.UpdateChannel
import vad.dashing.store.disk.isReleaseNewer
import vad.dashing.store.install.ApkInstaller
import vad.dashing.store.install.ApkSigner
import vad.dashing.store.install.ApkUninstaller
import vad.dashing.store.install.ApkVerifier
import vad.dashing.store.install.DownloadsApkSaver
import vad.dashing.store.install.InstallPermissionHelper

data class InstalledAppInfo(
    val versionName: String,
    val versionCode: Long,
)

data class StoreAppRow(
    val entry: StoreCatalogEntry,
    val remotes: List<AppReleaseInfo> = emptyList(),
    val selectedFlavor: String? = null,
    val installed: InstalledAppInfo? = null,
    val unsupportedOnHu: Boolean = false,
    val manifestError: String? = null,
) {
    val remote: AppReleaseInfo?
        get() {
            if (remotes.isEmpty()) return null
            val preferred = selectedFlavor
            if (!preferred.isNullOrBlank()) {
                remotes.firstOrNull { it.flavor.equals(preferred, ignoreCase = true) }
                    ?.let { return it }
            }
            return AppReleaseManifest(1, remotes).preferredRelease(preferred)
        }

    val languageFlavors: List<String>
        get() = remotes
            .map { it.flavor.lowercase() }
            .filter { it == "ru" || it == "en" }
            .distinct()

    val hasLanguageChoice: Boolean get() = languageFlavors.size >= 2

    val updateAvailable: Boolean
        get() {
            val remoteCode = remote?.versionCode ?: return false
            val installedCode = installed?.versionCode ?: return true
            return isReleaseNewer(remoteCode, installedCode)
        }
}

sealed interface StoreListState {
    data object Idle : StoreListState
    data object Loading : StoreListState
    data class Ready(val rows: List<StoreAppRow>, val publishedAt: String = "") : StoreListState
    data class Error(val message: String) : StoreListState
}

/** Where a downloaded APK goes: the system installer on this device, or public Downloads. */
enum class DownloadTarget {
    INSTALL,
    DOWNLOADS,
}

sealed interface StoreActionState {
    data object Idle : StoreActionState
    data class Downloading(
        val appId: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long? = null,
        val target: DownloadTarget = DownloadTarget.INSTALL,
    ) : StoreActionState {
        val percent: Int?
            get() = if (totalBytes != null && totalBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                null
            }
    }

    data class Verifying(
        val appId: String,
        val target: DownloadTarget = DownloadTarget.INSTALL,
    ) : StoreActionState
    data class ReadyToInstall(
        val appId: String,
        val apkFile: File,
        val release: AppReleaseInfo,
        val entry: StoreCatalogEntry,
        /** Signer of [apkFile] read while verifying; null when it was not needed or unreadable. */
        val apkSigner: ApkSigner? = null,
        /** Public Downloads path of a companion APK (e.g. DCL launcher for DCLInstall). */
        val downloadsSidecarPath: String? = null,
        /** Soft failure while saving the companion APK; main APK is still ready. */
        val downloadsSidecarError: String? = null,
    ) : StoreActionState

    /** APK saved to public Downloads (phone apps, or any app to install elsewhere). */
    data class SavedToDownloads(
        val appId: String,
        val downloadsPath: String,
        val release: AppReleaseInfo,
        val entry: StoreCatalogEntry,
    ) : StoreActionState

    data class Error(val appId: String?, val message: String) : StoreActionState

    val activeAppId: String?
        get() = when (this) {
            is Downloading -> appId
            is Verifying -> appId
            else -> null
        }
}

/** Machine-readable error keys the UI maps to localized strings. */
object StoreErrors {
    const val NETWORK_UNAVAILABLE = "network_unavailable"
    const val PHONE_APK_NOT_FOR_HU = "phone_apk_not_for_hu"
    const val DOWNLOADS_PERMISSION = "downloads_permission_denied"
    const val NO_ACTIVITY = "no_activity"
    const val CHECKSUM_MISMATCH = "checksum_mismatch"
    const val PACKAGE_MISMATCH = "package_mismatch"
    const val CERT_MISMATCH = "cert_mismatch"
    const val CERT_UNREADABLE = "cert_unreadable"
    const val CERT_CONFLICTS_INSTALLED = "cert_conflicts_installed"
    const val APK_MISSING = "apk_missing"
    const val DOWNLOADS_WRITE_FAILED = "downloads_write_failed"
}

class StoreRepository(
    private val context: Context,
    private val preferences: StorePreferences = StorePreferences(context),
) {
    private val cloud = StoreCloudFacade(
        yandexPublicKey = BuildConfig.STORE_PUBLIC_KEY,
        mailRuWeblink = BuildConfig.STORE_MAILRU_WEBLINK,
        sourceProvider = { preferences.cloudSource },
    )

    private val _listState = MutableStateFlow<StoreListState>(StoreListState.Idle)
    val listState: StateFlow<StoreListState> = _listState.asStateFlow()

    private val _actionState = MutableStateFlow<StoreActionState>(StoreActionState.Idle)
    val actionState: StateFlow<StoreActionState> = _actionState.asStateFlow()

    private val _channel = MutableStateFlow(preferences.channel)
    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()

    private val _cloudSource = MutableStateFlow(preferences.cloudSource)
    val cloudSource: StateFlow<CloudSource> = _cloudSource.asStateFlow()

    private val _apkLanguage = MutableStateFlow(preferences.apkLanguage)
    val apkLanguage: StateFlow<ApkLanguage> = _apkLanguage.asStateFlow()

    private val _appChangelogs = MutableStateFlow<Map<String, AppChangelogState>>(emptyMap())
    val appChangelogs: StateFlow<Map<String, AppChangelogState>> = _appChangelogs.asStateFlow()

    private val huKind: HuKind = HuProfile.detect(context.packageManager)
    val hu: HuKind get() = huKind

    @Volatile private var lastRows: List<StoreAppRow> = emptyList()
    @Volatile private var downloadInProgress = false

    /** UI language of this Store APK (`ru` / `en` product flavor). */
    fun storeUiLang(): String =
        if (BuildConfig.FLAVOR.equals("en", ignoreCase = true)) "en" else "ru"

    fun setChannel(channel: UpdateChannel) {
        preferences.channel = channel
        _channel.value = channel
        _actionState.value = StoreActionState.Idle
        _appChangelogs.value = emptyMap()
    }

    fun setCloudSource(source: CloudSource) {
        preferences.cloudSource = source
        _cloudSource.value = source
        _actionState.value = StoreActionState.Idle
        _appChangelogs.value = emptyMap()
    }

    fun setApkLanguage(language: ApkLanguage) {
        preferences.apkLanguage = language
        _apkLanguage.value = language
        if (_actionState.value.activeAppId == null) {
            _actionState.value = StoreActionState.Idle
        }
        reselectFlavors(language.flavor)
    }

    /** Per-app override when the catalog entry publishes both `ru` and `en`. */
    fun setAppApkFlavor(appId: String, flavor: String) {
        val ready = _listState.value as? StoreListState.Ready ?: return
        val updated = ready.rows.map { row ->
            if (row.entry.id != appId) return@map row
            if (row.remotes.none { it.flavor.equals(flavor, ignoreCase = true) }) return@map row
            row.copy(selectedFlavor = flavor.lowercase())
        }
        lastRows = updated
        _listState.value = ready.copy(rows = updated)
        val action = _actionState.value
        if (action.activeAppId == null) {
            _actionState.value = StoreActionState.Idle
        }
    }

    fun cloud(): StoreCloudFacade = cloud

    private fun reselectFlavors(preferred: String) {
        val ready = _listState.value as? StoreListState.Ready ?: return
        val updated = ready.rows.map { row ->
            if (!row.hasLanguageChoice) return@map row
            val flavor = if (row.remotes.any { it.flavor.equals(preferred, true) }) {
                preferred
            } else {
                row.selectedFlavor
            }
            row.copy(selectedFlavor = flavor)
        }
        lastRows = updated
        _listState.value = ready.copy(rows = updated)
    }

    /**
     * Reloads catalog and per-app manifests. The caller cancels a previous refresh
     * before starting a new one (channel / source switch while loading).
     */
    suspend fun refreshCatalog() {
        if (!isNetworkAvailable()) {
            _listState.value = StoreListState.Error(StoreErrors.NETWORK_UNAVAILABLE)
            return
        }
        _listState.value = StoreListState.Loading
        _appChangelogs.value = emptyMap()
        try {
            val channel = _channel.value
            val catalogJson = withContext(Dispatchers.IO) {
                cloud.fetchText(YandexDiskPath.catalogPath(channel))
            }
            val catalog = StoreCatalog.parse(catalogJson)
            val storeVersion = BuildConfig.VERSION_CODE.toLong()
            val entries = catalog.apps.filter { it.minStoreVersionCode <= storeVersion }
            val rows = coroutineScope {
                entries.map { entry ->
                    async(Dispatchers.IO) { buildRow(channel, entry) }
                }.awaitAll()
            }
            lastRows = rows
            _listState.value = StoreListState.Ready(rows, catalog.publishedAt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _listState.value = StoreListState.Error(errorText(error))
        }
    }

    private fun buildRow(channel: UpdateChannel, entry: StoreCatalogEntry): StoreAppRow {
        val unsupported = !entry.supportsHu(huKind.catalogTag)
        // Phone APKs are not installed on the HU — skip PackageManager lookup.
        val installed = if (entry.isPhoneDistribution) null else installedInfo(entry.packageName)
        // Releases are loaded for unsupported apps too: they can still be saved to Downloads.
        return try {
            val releases = loadReleases(channel, entry)
            val preferred = _apkLanguage.value.flavor
            val selected = when {
                releases.any { it.flavor.equals(preferred, true) } -> preferred
                else -> AppReleaseManifest(1, releases).preferredRelease()?.flavor
            }
            StoreAppRow(
                entry = entry,
                remotes = releases,
                selectedFlavor = selected,
                installed = installed,
                unsupportedOnHu = unsupported,
            )
        } catch (error: Exception) {
            StoreAppRow(
                entry = entry,
                installed = installed,
                unsupportedOnHu = unsupported,
                manifestError = if (unsupported) null else errorText(error),
            )
        }
    }

    /**
     * Downloads and verifies the selected release of [appId].
     * Phone-distribution apps always go to Downloads; others go to [target].
     */
    suspend fun downloadAndVerify(appId: String, target: DownloadTarget = DownloadTarget.INSTALL) {
        if (downloadInProgress) return
        val row = lastRows.firstOrNull { it.entry.id == appId } ?: return
        val release = row.remote ?: return
        val effectiveTarget = if (row.entry.isPhoneDistribution) DownloadTarget.DOWNLOADS else target
        if (effectiveTarget == DownloadTarget.INSTALL && row.unsupportedOnHu) return
        if (!isNetworkAvailable()) {
            _actionState.value = StoreActionState.Error(appId, StoreErrors.NETWORK_UNAVAILABLE)
            return
        }
        if (effectiveTarget == DownloadTarget.DOWNLOADS || row.entry.copyApkToDownloadsAppId.isNotBlank()) {
            if (!DownloadsApkSaver.hasWritePermission(context)) {
                _actionState.value = StoreActionState.Error(appId, StoreErrors.DOWNLOADS_PERMISSION)
                return
            }
        }
        downloadInProgress = true
        _actionState.value = StoreActionState.Downloading(appId, target = effectiveTarget)
        val channel = _channel.value
        val destination = apkDestinationFile(row.entry.id, release)
        try {
            withContext(Dispatchers.IO) {
                pruneApkCache()
                val progress = ProgressThrottle()
                cloud.downloadToFile(
                    diskPath = YandexDiskPath.appApkPath(channel, row.entry.id, release.apkFileName),
                    destination = destination,
                    onProgress = { downloaded, total ->
                        val totalBytes = total ?: release.apkSizeBytes
                        if (isActive && progress.shouldEmit(downloaded, totalBytes)) {
                            _actionState.value = StoreActionState.Downloading(
                                appId = appId,
                                downloadedBytes = downloaded,
                                totalBytes = totalBytes,
                                target = effectiveTarget,
                            )
                        }
                    },
                )
            }
            _actionState.value = StoreActionState.Verifying(appId, effectiveTarget)
            val apkSigner = withContext(Dispatchers.IO) {
                verifyDownloadedApk(
                    destination,
                    row.entry,
                    release,
                    checkInstalledSigner = effectiveTarget == DownloadTarget.INSTALL,
                )
            }
            val withSize = release.copy(
                apkSizeBytes = release.apkSizeBytes
                    ?: destination.length().takeIf { it > 0L },
            )
            if (effectiveTarget == DownloadTarget.DOWNLOADS) {
                val saved = withContext(Dispatchers.IO) {
                    try {
                        saveApkToDownloads(row.entry, withSize, destination)
                    } finally {
                        destination.delete()
                    }
                }
                _actionState.value = StoreActionState.SavedToDownloads(
                    appId = appId,
                    downloadsPath = saved.absolutePath,
                    release = withSize,
                    entry = row.entry,
                )
            } else {
                val sidecar = withContext(Dispatchers.IO) {
                    downloadSidecarToDownloads(channel, row.entry)
                }
                _actionState.value = StoreActionState.ReadyToInstall(
                    appId = appId,
                    apkFile = destination,
                    release = withSize,
                    entry = row.entry,
                    apkSigner = apkSigner,
                    downloadsSidecarPath = sidecar.path,
                    downloadsSidecarError = sidecar.error,
                )
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                clearPartialDownloads(row.entry.id, release)
            }
            _actionState.value = StoreActionState.Idle
            throw error
        } catch (error: Exception) {
            withContext(NonCancellable + Dispatchers.IO) {
                clearPartialDownloads(row.entry.id, release)
            }
            _actionState.value = StoreActionState.Error(appId, errorText(error))
        } finally {
            downloadInProgress = false
        }
    }

    fun cancelDownload() {
        if (_actionState.value !is StoreActionState.Downloading) return
        _actionState.value = StoreActionState.Idle
    }

    fun reportActionError(appId: String?, message: String) {
        _actionState.value = StoreActionState.Error(appId, message)
    }

    /** Clears one-shot results (errors, "saved to Downloads"); keeps a prepared install. */
    fun dismissActionResult() {
        val state = _actionState.value
        if (state is StoreActionState.Error || state is StoreActionState.SavedToDownloads) {
            _actionState.value = StoreActionState.Idle
        }
    }

    fun installPreparedApk() {
        val state = _actionState.value as? StoreActionState.ReadyToInstall ?: return
        if (state.entry.isPhoneDistribution) {
            _actionState.value = StoreActionState.Error(
                state.appId,
                StoreErrors.PHONE_APK_NOT_FOR_HU,
            )
            return
        }
        if (!state.apkFile.isFile) {
            _actionState.value = StoreActionState.Error(state.appId, StoreErrors.APK_MISSING)
            return
        }
        // Main thread: reuse the signer read during verification (reading it verifies the whole APK).
        // The installed package may have changed since then.
        val installed = ApkVerifier.installedSigner(context, state.entry.packageName)
        if (installed != null && state.apkSigner?.sharesLineageWith(installed) == false) {
            _actionState.value = StoreActionState.Error(state.appId, StoreErrors.CERT_CONFLICTS_INSTALLED)
            return
        }
        try {
            ApkInstaller.install(context, state.apkFile)
        } catch (error: ActivityNotFoundException) {
            _actionState.value = StoreActionState.Error(state.appId, StoreErrors.NO_ACTIVITY)
        } catch (error: Exception) {
            _actionState.value = StoreActionState.Error(state.appId, errorText(error))
        }
    }

    /**
     * Loads `changelog.<lang>.txt` for [appId] when the detail card opens.
     * Prefers the Store UI language, then the other language; does not use
     * `version.json` release notes (those remain a short fallback in the UI).
     */
    suspend fun loadAppChangelog(appId: String) {
        val row = lastRows.firstOrNull { it.entry.id == appId } ?: return
        val current = _appChangelogs.value[appId]
        if (current != null && current !is AppChangelogState.Idle) return
        val channel = _channel.value
        setChangelog(appId, AppChangelogState.Loading)
        val preferred = storeUiLang()
        val order = if (preferred == "en") listOf("en", "ru") else listOf("ru", "en")
        var finished = false
        try {
            var lastError: Exception? = null
            for (lang in order) {
                val logical = row.entry.resolveChangelogLogicalPath(lang)
                val path = YandexDiskPath.physicalPath(channel, logical)
                val text = try {
                    withContext(Dispatchers.IO) { cloud.fetchText(path).trim() }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error
                    null
                }
                if (!text.isNullOrBlank()) {
                    if (_channel.value == channel) {
                        setChangelog(appId, AppChangelogState.Ready(text = text, lang = lang))
                    }
                    finished = true
                    return
                }
            }
            if (_channel.value == channel) {
                // A missing file is the normal case; only a network failure is an error.
                val state = if (lastError != null && !isNetworkAvailable()) {
                    AppChangelogState.Error(StoreErrors.NETWORK_UNAVAILABLE)
                } else {
                    AppChangelogState.Absent
                }
                setChangelog(appId, state)
            }
            finished = true
        } finally {
            if (!finished && _appChangelogs.value[appId] is AppChangelogState.Loading) {
                _appChangelogs.value = _appChangelogs.value - appId
            }
        }
    }

    private fun setChangelog(appId: String, state: AppChangelogState) {
        _appChangelogs.value = _appChangelogs.value + (appId to state)
    }

    fun clearAppChangelog(appId: String) {
        _appChangelogs.value = _appChangelogs.value - appId
    }

    /** Browser URL for the current remote APK (QR on phone-distribution cards). */
    fun phoneApkBrowseUrl(row: StoreAppRow): String? {
        if (!row.entry.isPhoneDistribution) return null
        val release = row.remote ?: return null
        return StorePublicShareUrl.apkBrowseUrl(
            yandexPublicKey = BuildConfig.STORE_PUBLIC_KEY,
            mailRuPublicUrl = BuildConfig.STORE_MAILRU_PUBLIC_URL,
            cloudSource = _cloudSource.value,
            channel = _channel.value,
            appId = row.entry.id,
            apkFileName = release.apkFileName,
        )
    }

    fun openInstalledApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** Opens the system uninstall dialog. False if refused (e.g. Store itself). */
    fun uninstallApp(packageName: String): Boolean =
        runCatching { ApkUninstaller.uninstall(context, packageName) }.getOrDefault(false)

    fun canInstallPackages(): Boolean = InstallPermissionHelper.canInstallPackages(context)

    fun refreshInstalledStatuses() {
        val ready = _listState.value as? StoreListState.Ready ?: return
        val updated = ready.rows.map { row ->
            if (row.entry.isPhoneDistribution) {
                row.copy(installed = null)
            } else {
                row.copy(installed = installedInfo(row.entry.packageName))
            }
        }
        lastRows = updated
        _listState.value = ready.copy(rows = updated)
        val prepared = _actionState.value as? StoreActionState.ReadyToInstall ?: return
        val installed = updated.firstOrNull { it.entry.id == prepared.appId }?.installed ?: return
        if (installed.versionCode >= prepared.release.versionCode) {
            // Installed from the system dialog: the cached APK is no longer needed.
            prepared.apkFile.delete()
            _actionState.value = StoreActionState.Idle
        }
    }

    private fun loadReleases(
        channel: UpdateChannel,
        entry: StoreCatalogEntry,
    ): List<AppReleaseInfo> {
        val path = YandexDiskPath.physicalPath(channel, entry.resolveManifestLogicalPath())
        val json = cloud.fetchText(path)
        val manifest = AppReleaseManifest.parse(json)
        if (manifest.releases.isEmpty()) {
            throw IOException("No releases in $path")
        }
        return manifest.releases.map { release ->
            if (release.apkSizeBytes != null && release.apkSizeBytes > 0L) return@map release
            val apkPath = YandexDiskPath.appApkPath(channel, entry.id, release.apkFileName)
            val size = runCatching {
                cloud.fetchPublicResourceSize(apkPath)
            }.getOrNull()
            if (size != null) release.copy(apkSizeBytes = size) else release
        }
    }

    private fun loadRelease(
        channel: UpdateChannel,
        entry: StoreCatalogEntry,
        preferredFlavor: String? = null,
    ): AppReleaseInfo {
        val releases = loadReleases(channel, entry)
        return AppReleaseManifest(1, releases).preferredRelease(
            preferredFlavor ?: _apkLanguage.value.flavor,
        ) ?: throw IOException("No releases for ${entry.id}")
    }

    private data class SidecarResult(
        val path: String? = null,
        val error: String? = null,
    )

    private fun downloadsDisplayName(entry: StoreCatalogEntry, release: AppReleaseInfo): String =
        buildString {
            append(entry.id)
            if (release.versionName.isNotBlank()) {
                append('-')
                append(release.versionName.replace(Regex("""[^\w.\-]+"""), "_"))
            }
            append(".apk")
        }

    private suspend fun saveApkToDownloads(
        entry: StoreCatalogEntry,
        release: AppReleaseInfo,
        cacheFile: File,
    ): File = DownloadsApkSaver.save(
        context,
        cacheFile,
        downloadsDisplayName(entry, release),
    )

    /**
     * For tools like DCLInstall: also fetch companion app APK into public Downloads
     * so the user can pick it inside that tool. Does not install the companion.
     */
    private suspend fun downloadSidecarToDownloads(
        channel: UpdateChannel,
        entry: StoreCatalogEntry,
    ): SidecarResult {
        val sidecarId = entry.copyApkToDownloadsAppId.trim()
        if (sidecarId.isBlank()) return SidecarResult()
        var cacheFile: File? = null
        return try {
            val sidecarRow = lastRows.firstOrNull { it.entry.id == sidecarId }
            val sidecarEntry = sidecarRow?.entry
                ?: throw IOException("Catalog entry not found: $sidecarId")
            val release = sidecarRow.remote
                ?: loadRelease(channel, sidecarEntry, _apkLanguage.value.flavor)
            val file = apkDestinationFile(sidecarEntry.id, release)
            cacheFile = file
            cloud.downloadToFile(
                diskPath = YandexDiskPath.appApkPath(channel, sidecarEntry.id, release.apkFileName),
                destination = file,
            )
            verifyDownloadedApk(file, sidecarEntry, release)
            val saved = saveApkToDownloads(sidecarEntry, release, file)
            SidecarResult(path = saved.absolutePath)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SidecarResult(error = errorText(error))
        } finally {
            cacheFile?.let { file ->
                file.delete()
                File(file.parentFile, "${file.name}.part").delete()
            }
        }
    }

    /**
     * Size, SHA-256, package name and the catalog certificate pin. With [checkInstalledSigner]
     * (APK for this device) also refuses a key the system installer would reject as an update
     * of the installed package. Returns the APK signer when it had to be read.
     */
    private fun verifyDownloadedApk(
        file: File,
        entry: StoreCatalogEntry,
        release: AppReleaseInfo,
        checkInstalledSigner: Boolean = false,
    ): ApkSigner? {
        if (!file.exists() || file.length() <= 0L) {
            throw IOException(StoreErrors.APK_MISSING)
        }
        if (!ApkVerifier.verifySha256(file, release.sha256)) {
            rejectApk(file, StoreErrors.CHECKSUM_MISMATCH)
        }
        if (!ApkVerifier.verifyPackageName(context, file, entry.packageName)) {
            rejectApk(file, StoreErrors.PACKAGE_MISMATCH)
        }
        val pinned = entry.signingCertSha256.isNotEmpty()
        val installed = if (checkInstalledSigner) {
            ApkVerifier.installedSigner(context, entry.packageName)
        } else {
            null
        }
        if (!pinned && installed == null) return null
        val signer = ApkVerifier.apkSigner(context, file)
            ?: rejectApk(file, StoreErrors.CERT_UNREADABLE)
        if (pinned && entry.signingCertSha256.none { it.equals(signer.current, ignoreCase = true) }) {
            rejectApk(file, StoreErrors.CERT_MISMATCH)
        }
        if (installed != null && !signer.sharesLineageWith(installed)) {
            rejectApk(file, StoreErrors.CERT_CONFLICTS_INSTALLED)
        }
        return signer
    }

    private fun rejectApk(file: File, error: String): Nothing {
        file.delete()
        throw IOException(error)
    }

    private fun apkCacheRoot(): File = File(context.cacheDir, "store/apk")

    private fun apkDestinationFile(appId: String, release: AppReleaseInfo): File {
        val dir = File(apkCacheRoot(), appId)
        dir.mkdirs()
        return File(dir, release.apkFileName)
    }

    /**
     * Only one download runs at a time and a new one replaces the prepared APK,
     * so older cached APKs (and the pre-0.1.2 `store/<appId>` layout) are dropped.
     */
    private fun pruneApkCache() {
        apkCacheRoot().deleteRecursively()
        File(context.cacheDir, "store").listFiles()?.forEach { child ->
            if (child.isDirectory && child.name != "icons" && child.name != "apk") {
                child.deleteRecursively()
            }
        }
    }

    private fun clearPartialDownloads(appId: String, release: AppReleaseInfo) {
        val file = apkDestinationFile(appId, release)
        file.delete()
        File(file.parentFile, "${file.name}.part").delete()
    }

    private fun installedInfo(packageName: String): InstalledAppInfo? {
        if (packageName.isBlank()) return null
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            InstalledAppInfo(
                versionName = info.versionName.orEmpty(),
                versionCode = code,
            )
        }.getOrNull()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun errorText(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}

/** Limits progress updates to whole-percent steps (or 256 KiB when size is unknown). */
internal class ProgressThrottle {
    private var lastPercent = -1
    private var lastBytes = -1L

    fun shouldEmit(downloaded: Long, total: Long?): Boolean {
        if (total != null && total > 0L) {
            val percent = ((downloaded * 100L) / total).toInt()
            if (percent == lastPercent) return false
            lastPercent = percent
            return true
        }
        if (lastBytes >= 0L && downloaded - lastBytes < UNKNOWN_SIZE_STEP) return false
        lastBytes = downloaded
        return true
    }

    private companion object {
        const val UNKNOWN_SIZE_STEP = 256L * 1024L
    }
}
