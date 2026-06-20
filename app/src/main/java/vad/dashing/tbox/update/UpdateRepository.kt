package vad.dashing.tbox.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.SettingsManager

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateReleaseInfo) : UpdateUiState
    data class Downloading(val percent: Int?) : UpdateUiState
    data object Verifying : UpdateUiState
    data class ReadyToInstall(val apkFile: File, val info: UpdateReleaseInfo) : UpdateUiState
    data class Error(val message: String, val cachedInfo: UpdateReleaseInfo? = null) : UpdateUiState
}

class UpdateRepository(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val yandexDiskClient: YandexDiskClient = YandexDiskClient(),
) {
    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var lastAvailableInfo: UpdateReleaseInfo? = null
    private var preparedApkFile: File? = null

    suspend fun checkForUpdate(force: Boolean = false) {
        if (!force && _uiState.value is UpdateUiState.Checking) return
        if (!isNetworkAvailable()) {
            _uiState.value = UpdateUiState.Error(
                message = "network_unavailable",
                cachedInfo = lastAvailableInfo,
            )
            return
        }
        _uiState.value = UpdateUiState.Checking
        try {
            val channel = settingsManager.updateChannelFlow.first()
            val publicKey = publicKeyForChannel(channel)
            val manifestJson = withContext(Dispatchers.IO) {
                yandexDiskClient.fetchText(publicKey, "/version.json")
            }
            val manifest = UpdateManifest.parse(manifestJson)
            val flavor = BuildConfig.FLAVOR
            val release = manifest.releaseFor(flavor)
                ?: throw IOException("No update entry for flavor $flavor")
            val currentVersionCode = currentVersionCode()
            if (!isUpdateNewer(release.versionCode, currentVersionCode)) {
                resetUpdateCacheAndState()
                _uiState.value = UpdateUiState.UpToDate
                return
            }
            if (currentVersionCode < release.minSupportedVersionCode) {
                throw IOException("Current version is below minSupportedVersionCode")
            }
            lastAvailableInfo = release
            val cached = findCachedVerifiedApk(release)
            if (cached != null) {
                preparedApkFile = cached
                _uiState.value = UpdateUiState.ReadyToInstall(cached, release)
            } else {
                _uiState.value = UpdateUiState.Available(release)
            }
        } catch (error: Exception) {
            _uiState.value = UpdateUiState.Error(
                message = error.message ?: error.javaClass.simpleName,
                cachedInfo = lastAvailableInfo,
            )
        }
    }

    suspend fun downloadAndVerify() {
        val info = lastAvailableInfo
            ?: (_uiState.value as? UpdateUiState.Available)?.info
            ?: (_uiState.value as? UpdateUiState.Error)?.cachedInfo
            ?: return
        if (!isNetworkAvailable()) {
            _uiState.value = UpdateUiState.Error("network_unavailable", info)
            return
        }
        lastAvailableInfo = info
        _uiState.value = UpdateUiState.Downloading(percent = null)
        try {
            val channel = settingsManager.updateChannelFlow.first()
            val publicKey = publicKeyForChannel(channel)
            val destination = apkDestinationFile(info)
            withContext(Dispatchers.IO) {
                yandexDiskClient.downloadToFile(
                    publicKey = publicKey,
                    path = "/${info.apkFileName}",
                    destination = destination,
                    onProgress = { downloaded, total ->
                        val percent = if (total != null && total > 0L) {
                            ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }
                        _uiState.value = UpdateUiState.Downloading(percent)
                    },
                )
            }
            _uiState.value = UpdateUiState.Verifying
            withContext(Dispatchers.IO) {
                verifyDownloadedApk(destination, info)
            }
            preparedApkFile = destination
            _uiState.value = UpdateUiState.ReadyToInstall(destination, info)
        } catch (error: Exception) {
            preparedApkFile?.takeIf { it.exists() }?.delete()
            preparedApkFile = null
            _uiState.value = UpdateUiState.Error(
                message = error.message ?: error.javaClass.simpleName,
                cachedInfo = info,
            )
        }
    }

    fun installPreparedApk() {
        val state = _uiState.value
        val apkFile = when (state) {
            is UpdateUiState.ReadyToInstall -> state.apkFile
            else -> preparedApkFile
        } ?: return
        ApkInstaller.install(context, apkFile)
        resetUpdateCacheAndState()
    }

    fun resetAfterChannelChange() {
        resetUpdateCacheAndState()
    }

    fun canInstallPackages(): Boolean = InstallPermissionHelper.canInstallPackages(context)

    fun shouldShowMenuEntry(): Boolean = when (val state = _uiState.value) {
        is UpdateUiState.Available,
        is UpdateUiState.Downloading,
        is UpdateUiState.Verifying,
        is UpdateUiState.ReadyToInstall -> true
        is UpdateUiState.Error -> state.cachedInfo != null
        else -> false
    }

    private fun publicKeyForChannel(channel: UpdateChannel): String {
        val key = when (channel) {
            UpdateChannel.RELEASE -> BuildConfig.UPDATE_RELEASE_PUBLIC_KEY
            UpdateChannel.DEVELOPMENT -> BuildConfig.UPDATE_DEV_PUBLIC_KEY
        }
        if (key.isBlank() || key.contains("REPLACE_WITH", ignoreCase = true)) {
            throw IOException("Update source URL is not configured")
        }
        return key
    }

    private fun currentVersionCode(): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun apkDestinationFile(info: UpdateReleaseInfo): File {
        val updatesDir = File(context.cacheDir, "updates")
        updatesDir.mkdirs()
        return File(updatesDir, info.apkFileName)
    }

    private fun findCachedVerifiedApk(info: UpdateReleaseInfo): File? {
        val file = apkDestinationFile(info)
        if (!file.exists()) return null
        return runCatching {
            verifyDownloadedApk(file, info)
            file
        }.getOrNull()
    }

    private fun verifyDownloadedApk(file: File, info: UpdateReleaseInfo) {
        if (!file.exists() || file.length() <= 0L) {
            throw IOException("Downloaded APK is missing")
        }
        if (!ApkVerifier.verifySha256(file, info.sha256)) {
            file.delete()
            throw IOException("APK checksum mismatch")
        }
        if (!ApkVerifier.verifyPackageName(context, file, context.packageName)) {
            file.delete()
            throw IOException("APK package name mismatch")
        }
        val expectedSigningSha = BuildConfig.UPDATE_SIGNING_CERT_SHA256
        if (expectedSigningSha.isNotBlank()) {
            val actual = signingCertSha256(file)
            if (!actual.equals(expectedSigningSha, ignoreCase = true)) {
                file.delete()
                throw IOException("APK signing certificate mismatch")
            }
        }
    }

    private fun signingCertSha256(apkFile: File): String {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: throw IOException("Cannot read APK signing info")
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.signatures
        } ?: throw IOException("APK has no signatures")
        if (signatures.isEmpty()) throw IOException("APK has no signatures")
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(signatures.first().toByteArray())) as X509Certificate
        return MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun resetUpdateCacheAndState() {
        clearCachedApk()
        lastAvailableInfo = null
        preparedApkFile = null
        if (_uiState.value !is UpdateUiState.Checking) {
            _uiState.value = UpdateUiState.Idle
        }
    }

    private fun clearCachedApk() {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            file.delete()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
