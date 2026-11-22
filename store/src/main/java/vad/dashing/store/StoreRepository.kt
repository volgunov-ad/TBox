package vad.dashing.store

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

sealed interface StoreActionState {
    data object Idle : StoreActionState
    data class Downloading(
        val appId: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long? = null,
    ) : StoreActionState {
        val percent: Int?
            get() = if (totalBytes != null && totalBytes > 0L) {
                ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
            } else {
                null
            }
    }

    data class Verifying(val appId: String) : StoreActionState
    data class ReadyToInstall(
        val appId: String,
        val apkFile: File,
        val release: AppReleaseInfo,
        val entry: StoreCatalogEntry,
        /** Public Downloads path of a companion APK (e.g. DCL launcher for DCLInstall). */
        val downloadsSidecarPath: String? = null,
        /** Soft failure while saving the companion APK; main APK is still ready. */
        val downloadsSidecarError: String? = null,
    ) : StoreActionState

    /** Phone-distribution APK saved to public Downloads (not installed on HU). */
    data class SavedToDownloads(
        val appId: String,
        val downloadsPath: String,
        val release: AppReleaseInfo,
        val entry: StoreCatalogEntry,
    ) : StoreActionState

    data class Error(val appId: String?, val message: String) : StoreActionState
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

    private var lastRows: List<StoreAppRow> = emptyList()
    private var downloadInProgress = false

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
        _actionState.value = StoreActionState.Idle
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
        _actionState.value = StoreActionState.Idle
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

    suspend fun refreshCatalog() {
        if (_listState.value is StoreListState.Loading) return
        if (!isNetworkAvailable()) {
            _listState.value = StoreListState.Error("network_unavailable")
            return
        }
        _listState.value = StoreListState.Loading
        try {
            val channel = _channel.value
            val catalogJson = withContext(Dispatchers.IO) {
                cloud.fetchText(YandexDiskPath.catalogPath(channel))
            }
            val catalog = StoreCatalog.parse(catalogJson)
            val storeVersion = currentVersionCode(context.packageName) ?: 0L
            val rows = catalog.apps.mapNotNull { entry ->
                if (entry.minStoreVersionCode > storeVersion) return@mapNotNull null
                val unsupported = !entry.supportsHu(huKind.catalogTag)
                // Phone APKs are not installed on the HU — skip PackageManager lookup.
                val installed = if (entry.isPhoneDistribution) {
                    null
                } else {
                    installedInfo(entry.packageName)
                }
                if (unsupported) {
                    return@mapNotNull StoreAppRow(
                        entry = entry,
                        installed = installed,
                        unsupportedOnHu = true,
                    )
                }
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { loadReleases(channel, entry) }
                }
                loaded.fold(
                    onSuccess = { releases ->
                        val preferred = _apkLanguage.value.flavor
                        val selected = when {
                            releases.any { it.flavor.equals(preferred, true) } -> preferred
                            else -> AppReleaseManifest(1, releases)
                                .preferredRelease()?.flavor
                        }
                        StoreAppRow(
                            entry = entry,
                            remotes = releases,
                            selectedFlavor = selected,
                            installed = installed,
                        )
                    },
                    onFailure = { error ->
                        StoreAppRow(
                            entry = entry,
                            installed = installed,
                            manifestError = error.message ?: error.javaClass.simpleName,
                        )
                    },
                )
            }
            lastRows = rows
            _listState.value = StoreListState.Ready(rows, catalog.publishedAt)
        } catch (error: Exception) {
            _listState.value = StoreListState.Error(
                error.message ?: error.javaClass.simpleName,
            )
        }
    }

    suspend fun downloadAndVerify(appId: String) {
        if (downloadInProgress) return
        val row = lastRows.firstOrNull { it.entry.id == appId }
            ?: return
        if (row.unsupportedOnHu) return
        val release = row.remote ?: return
        if (!isNetworkAvailable()) {
            _actionState.value = StoreActionState.Error(appId, "network_unavailable")
            return
        }
        downloadInProgress = true
        _actionState.value = StoreActionState.Downloading(appId)
        try {
            val channel = _channel.value
            val destination = apkDestinationFile(row.entry.id, release)
            withContext(Dispatchers.IO) {
                cloud.downloadToFile(
                    diskPath = YandexDiskPath.appApkPath(channel, row.entry.id, release.apkFileName),
                    destination = destination,
                    onProgress = { downloaded, total ->
                        if (!isActive) return@downloadToFile
                        _actionState.value = StoreActionState.Downloading(
                            appId = appId,
                            downloadedBytes = downloaded,
                            totalBytes = total ?: release.apkSizeBytes,
                        )
                    },
                )
            }
            _actionState.value = StoreActionState.Verifying(appId)
            withContext(Dispatchers.IO) {
                verifyDownloadedApk(destination, row.entry, release)
            }
            val withSize = release.copy(
                apkSizeBytes = release.apkSizeBytes
                    ?: destination.length().takeIf { it > 0L },
            )
            if (row.entry.isPhoneDistribution) {
                val saved = withContext(Dispatchers.IO) {
                    saveApkToDownloads(row.entry, withSize, destination)
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
            withContext(Dispatchers.IO) {
                clearPartialDownloads(row.entry.id, release)
            }
            _actionState.value = StoreActionState.Error(
                appId,
                error.message ?: error.javaClass.simpleName,
            )
        } finally {
            downloadInProgress = false
        }
    }

    fun cancelDownload() {
        if (_actionState.value !is StoreActionState.Downloading) return
        _actionState.value = StoreActionState.Idle
    }

    fun installPreparedApk() {
        val state = _actionState.value as? StoreActionState.ReadyToInstall ?: return
        if (state.entry.isPhoneDistribution) {
            _actionState.value = StoreActionState.Error(
                state.appId,
                "phone_apk_not_for_hu",
            )
            return
        }
        try {
            ApkInstaller.install(context, state.apkFile)
        } catch (error: Exception) {
            _actionState.value = StoreActionState.Error(
                state.appId,
                error.message ?: error.javaClass.simpleName,
            )
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
        if (current is AppChangelogState.Ready || current is AppChangelogState.Loading) return
        _appChangelogs.value = _appChangelogs.value + (appId to AppChangelogState.Loading)
        val preferred = storeUiLang()
        val order = if (preferred == "en") listOf("en", "ru") else listOf("ru", "en")
        val channel = _channel.value
        try {
            for (lang in order) {
                val logical = row.entry.resolveChangelogLogicalPath(lang)
                val path = YandexDiskPath.physicalPath(channel, logical)
                val text = withContext(Dispatchers.IO) {
                    runCatching { cloud.fetchText(path).trim() }.getOrNull()
                }
                if (!text.isNullOrBlank()) {
                    _appChangelogs.value = _appChangelogs.value + (
                        appId to AppChangelogState.Ready(text = text, lang = lang)
                        )
                    return
                }
            }
            _appChangelogs.value = _appChangelogs.value + (appId to AppChangelogState.Absent)
        } catch (error: Exception) {
            _appChangelogs.value = _appChangelogs.value + (
                appId to AppChangelogState.Error(error.message ?: error.javaClass.simpleName)
                )
        }
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
        context.startActivity(intent)
        return true
    }

    /** Opens the system uninstall dialog. False if refused (e.g. Store itself). */
    fun uninstallApp(packageName: String): Boolean =
        ApkUninstaller.uninstall(context, packageName)

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

    private fun saveApkToDownloads(
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
        return try {
            val sidecarRow = lastRows.firstOrNull { it.entry.id == sidecarId }
            val sidecarEntry = sidecarRow?.entry
                ?: throw IOException("Catalog entry not found: $sidecarId")
            val release = sidecarRow.remote
                ?: loadRelease(channel, sidecarEntry, _apkLanguage.value.flavor)
            val cacheFile = apkDestinationFile(sidecarEntry.id, release)
            cloud.downloadToFile(
                diskPath = YandexDiskPath.appApkPath(channel, sidecarEntry.id, release.apkFileName),
                destination = cacheFile,
            )
            verifyDownloadedApk(cacheFile, sidecarEntry, release)
            val saved = saveApkToDownloads(sidecarEntry, release, cacheFile)
            SidecarResult(path = saved.absolutePath)
        } catch (error: Exception) {
            SidecarResult(error = error.message ?: error.javaClass.simpleName)
        }
    }

    private fun verifyDownloadedApk(
        file: File,
        entry: StoreCatalogEntry,
        release: AppReleaseInfo,
    ) {
        if (!file.exists() || file.length() <= 0L) {
            throw IOException("Downloaded APK is missing")
        }
        if (!ApkVerifier.verifySha256(file, release.sha256)) {
            file.delete()
            throw IOException("APK checksum mismatch")
        }
        if (!ApkVerifier.verifyPackageName(context, file, entry.packageName)) {
            file.delete()
            throw IOException("APK package name mismatch")
        }
        if (entry.signingCertSha256.isNotEmpty()) {
            val actual = ApkVerifier.signingCertSha256(context, file)?.lowercase()
            if (actual == null || entry.signingCertSha256.none { it.equals(actual, true) }) {
                file.delete()
                throw IOException("APK signing certificate mismatch")
            }
        }
    }

    private fun apkDestinationFile(appId: String, release: AppReleaseInfo): File {
        val dir = File(context.cacheDir, "store/$appId")
        dir.mkdirs()
        return File(dir, release.apkFileName)
    }

    private fun clearPartialDownloads(appId: String, release: AppReleaseInfo) {
        val file = apkDestinationFile(appId, release)
        file.delete()
        File(file.parentFile, "${file.name}.part").delete()
    }

    private fun installedInfo(packageName: String): InstalledAppInfo? {
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

    private fun currentVersionCode(packageName: String): Long? =
        installedInfo(packageName)?.versionCode

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
