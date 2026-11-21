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
import vad.dashing.store.catalog.YandexDiskPath
import vad.dashing.store.disk.AppReleaseInfo
import vad.dashing.store.disk.AppReleaseManifest
import vad.dashing.store.disk.UpdateChannel
import vad.dashing.store.disk.YandexDiskClient
import vad.dashing.store.disk.isReleaseNewer
import vad.dashing.store.install.ApkInstaller
import vad.dashing.store.install.ApkVerifier
import vad.dashing.store.install.InstallPermissionHelper

data class InstalledAppInfo(
    val versionName: String,
    val versionCode: Long,
)

data class StoreAppRow(
    val entry: StoreCatalogEntry,
    val remote: AppReleaseInfo? = null,
    val installed: InstalledAppInfo? = null,
    val unsupportedOnHu: Boolean = false,
    val manifestError: String? = null,
) {
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
    ) : StoreActionState

    data class Error(val appId: String?, val message: String) : StoreActionState
}

class StoreRepository(
    private val context: Context,
    private val preferences: StorePreferences = StorePreferences(context),
    private val yandexDiskClient: YandexDiskClient = YandexDiskClient(),
) {
    private val _listState = MutableStateFlow<StoreListState>(StoreListState.Idle)
    val listState: StateFlow<StoreListState> = _listState.asStateFlow()

    private val _actionState = MutableStateFlow<StoreActionState>(StoreActionState.Idle)
    val actionState: StateFlow<StoreActionState> = _actionState.asStateFlow()

    private val _channel = MutableStateFlow(preferences.channel)
    val channel: StateFlow<UpdateChannel> = _channel.asStateFlow()

    private val huKind: HuKind = HuProfile.detect(context.packageManager)
    val hu: HuKind get() = huKind

    private var lastRows: List<StoreAppRow> = emptyList()
    private var downloadInProgress = false

    fun setChannel(channel: UpdateChannel) {
        preferences.channel = channel
        _channel.value = channel
        _actionState.value = StoreActionState.Idle
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
            val publicKey = storePublicKey()
            val catalogJson = withContext(Dispatchers.IO) {
                yandexDiskClient.fetchText(publicKey, YandexDiskPath.catalogPath(channel))
            }
            val catalog = StoreCatalog.parse(catalogJson)
            val storeVersion = currentVersionCode(context.packageName) ?: 0L
            val rows = catalog.apps.mapNotNull { entry ->
                if (entry.minStoreVersionCode > storeVersion) return@mapNotNull null
                val unsupported = !entry.supportsHu(huKind.catalogTag)
                val installed = installedInfo(entry.packageName)
                if (unsupported) {
                    return@mapNotNull StoreAppRow(
                        entry = entry,
                        installed = installed,
                        unsupportedOnHu = true,
                    )
                }
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { loadRelease(publicKey, channel, entry) }
                }
                loaded.fold(
                    onSuccess = { release ->
                        StoreAppRow(
                            entry = entry,
                            remote = release,
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
            val publicKey = storePublicKey()
            val destination = apkDestinationFile(row.entry.id, release)
            withContext(Dispatchers.IO) {
                yandexDiskClient.downloadToFile(
                    publicKey = publicKey,
                    path = YandexDiskPath.appApkPath(channel, row.entry.id, release.apkFileName),
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
            _actionState.value = StoreActionState.ReadyToInstall(
                appId = appId,
                apkFile = destination,
                release = withSize,
                entry = row.entry,
            )
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
        try {
            ApkInstaller.install(context, state.apkFile)
        } catch (error: Exception) {
            _actionState.value = StoreActionState.Error(
                state.appId,
                error.message ?: error.javaClass.simpleName,
            )
        }
    }

    fun openInstalledApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun canInstallPackages(): Boolean = InstallPermissionHelper.canInstallPackages(context)

    fun refreshInstalledStatuses() {
        val ready = _listState.value as? StoreListState.Ready ?: return
        val updated = ready.rows.map { row ->
            row.copy(installed = installedInfo(row.entry.packageName))
        }
        lastRows = updated
        _listState.value = ready.copy(rows = updated)
    }

    private fun loadRelease(
        publicKey: String,
        channel: UpdateChannel,
        entry: StoreCatalogEntry,
    ): AppReleaseInfo {
        val path = YandexDiskPath.physicalPath(channel, entry.resolveManifestLogicalPath())
        val json = yandexDiskClient.fetchText(publicKey, path)
        val manifest = AppReleaseManifest.parse(json)
        val release = manifest.preferredRelease()
            ?: throw IOException("No releases in $path")
        if (release.apkSizeBytes != null && release.apkSizeBytes > 0L) return release
        val apkPath = YandexDiskPath.appApkPath(channel, entry.id, release.apkFileName)
        val size = runCatching {
            yandexDiskClient.fetchPublicResourceSize(publicKey, apkPath)
        }.getOrNull()
        return if (size != null) release.copy(apkSizeBytes = size) else release
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

    private fun storePublicKey(): String {
        val key = BuildConfig.STORE_PUBLIC_KEY
        if (key.isBlank() || key.contains("REPLACE_WITH", ignoreCase = true)) {
            throw IOException("Store source URL is not configured")
        }
        return key
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
