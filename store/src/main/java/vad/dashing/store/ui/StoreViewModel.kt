package vad.dashing.store.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vad.dashing.store.DownloadTarget
import vad.dashing.store.HuKind
import vad.dashing.store.StoreAppRow
import vad.dashing.store.StoreIconLoader
import vad.dashing.store.StoreListState
import vad.dashing.store.StoreRepository
import vad.dashing.store.dexopt.StoreAppCompiler
import vad.dashing.store.disk.ApkLanguage
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoreRepository(application)
    private val iconLoader = StoreIconLoader(application, repository.cloud())
    private val compiler = StoreAppCompiler(application)

    val listState = repository.listState
    val actionState = repository.actionState
    val channel = repository.channel
    val cloudSource = repository.cloudSource
    val apkLanguage = repository.apkLanguage
    val appChangelogs = repository.appChangelogs
    val hu = repository.hu
    val compileStates = compiler.states

    /** Not a head unit (phone, tablet, emulator): Downloads is the main action. */
    val isHeadUnit: Boolean get() = hu != HuKind.UNKNOWN

    private val _selectedAppId = MutableStateFlow<String?>(null)
    val selectedAppId: StateFlow<String?> = _selectedAppId.asStateFlow()

    private val _icons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val icons: StateFlow<Map<String, Bitmap>> = _icons.asStateFlow()

    private var refreshJob: Job? = null
    private var downloadJob: Job? = null
    private var iconsJob: Job? = null
    private val changelogJobs = mutableMapOf<String, Job>()
    private var compileJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        val previous = refreshJob
        iconsJob?.cancel()
        refreshJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            repository.refreshCatalog()
            prefetchIcons()
        }
    }

    fun setChannel(channel: UpdateChannel) {
        if (channel == this.channel.value) return
        cancelDownload()
        repository.setChannel(channel)
        resetIcons()
        refresh()
    }

    fun setCloudSource(source: CloudSource) {
        if (source == cloudSource.value) return
        cancelDownload()
        repository.setCloudSource(source)
        resetIcons()
        refresh()
    }

    fun setApkLanguage(language: ApkLanguage) {
        repository.setApkLanguage(language)
    }

    fun setAppApkFlavor(appId: String, flavor: String) {
        repository.setAppApkFlavor(appId, flavor)
    }

    fun selectApp(appId: String?) {
        _selectedAppId.value = appId
        repository.dismissActionResult()
    }

    /** Called by the detail screen while its changelog is still [AppChangelogState.Idle]. */
    fun loadChangelog(appId: String) {
        if (changelogJobs[appId]?.isActive == true) return
        changelogJobs[appId] = viewModelScope.launch {
            try {
                repository.loadAppChangelog(appId)
            } finally {
                changelogJobs.remove(appId)
            }
        }
    }

    /** Re-run when the detail card opens or the installed version changes. */
    fun checkCompileStatus(packageName: String) {
        if (!isHeadUnit || packageName.isBlank()) return
        viewModelScope.launch { compiler.check(packageName) }
    }

    fun compile(packageName: String) {
        if (!isHeadUnit || compileJob?.isActive == true) return
        compileJob = viewModelScope.launch { compiler.compile(packageName) }
    }

    fun download(appId: String, target: DownloadTarget = DownloadTarget.INSTALL) {
        val previous = downloadJob
        downloadJob = viewModelScope.launch {
            previous?.cancelAndJoin()
            repository.downloadAndVerify(appId, target)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel(CancellationException("Canceled by user"))
        repository.cancelDownload()
    }

    fun reportError(appId: String?, message: String) {
        repository.reportActionError(appId, message)
    }

    fun dismissActionResult() {
        repository.dismissActionResult()
    }

    fun install() {
        repository.installPreparedApk()
    }

    fun openApp(packageName: String): Boolean = repository.openInstalledApp(packageName)

    fun uninstallApp(packageName: String): Boolean = repository.uninstallApp(packageName)

    fun phoneApkBrowseUrl(row: StoreAppRow): String? = repository.phoneApkBrowseUrl(row)

    fun canInstallPackages(): Boolean = repository.canInstallPackages()

    fun onResume() {
        repository.refreshInstalledStatuses()
    }

    private fun resetIcons() {
        iconsJob?.cancel()
        iconLoader.clearMemory()
        _icons.value = emptyMap()
    }

    private fun prefetchIcons() {
        iconsJob?.cancel()
        val ready = listState.value as? StoreListState.Ready ?: return
        val channel = channel.value
        iconsJob = viewModelScope.launch {
            for (row in ready.rows) {
                val entry = row.entry
                if (_icons.value.containsKey(entry.id)) continue
                val bitmap = try {
                    iconLoader.load(entry, channel)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    null
                }
                if (bitmap != null) {
                    _icons.value = _icons.value + (entry.id to bitmap)
                }
            }
        }
    }
}
