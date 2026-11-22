package vad.dashing.store.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vad.dashing.store.StoreAppRow
import vad.dashing.store.StoreIconLoader
import vad.dashing.store.StoreListState
import vad.dashing.store.StoreRepository
import vad.dashing.store.catalog.StoreCatalogEntry
import vad.dashing.store.disk.ApkLanguage
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoreRepository(application)
    private val iconLoader = StoreIconLoader(application, repository.cloud())

    val listState = repository.listState
    val actionState = repository.actionState
    val channel = repository.channel
    val cloudSource = repository.cloudSource
    val apkLanguage = repository.apkLanguage
    val hu = repository.hu

    private val _selectedAppId = MutableStateFlow<String?>(null)
    val selectedAppId: StateFlow<String?> = _selectedAppId.asStateFlow()

    private val _icons = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val icons: StateFlow<Map<String, Bitmap>> = _icons.asStateFlow()

    private var downloadJob: Job? = null
    private var iconsJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            repository.refreshCatalog()
            prefetchIcons()
        }
    }

    fun setChannel(channel: UpdateChannel) {
        repository.setChannel(channel)
        iconLoader.clearMemory()
        _icons.value = emptyMap()
        refresh()
    }

    fun setCloudSource(source: CloudSource) {
        repository.setCloudSource(source)
        iconLoader.clearMemory()
        _icons.value = emptyMap()
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
    }

    fun selectedRow(): StoreAppRow? {
        val id = _selectedAppId.value ?: return null
        val ready = listState.value as? StoreListState.Ready ?: return null
        return ready.rows.firstOrNull { it.entry.id == id }
    }

    fun download(appId: String) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            repository.downloadAndVerify(appId)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        repository.cancelDownload()
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

    private fun prefetchIcons() {
        iconsJob?.cancel()
        val ready = listState.value as? StoreListState.Ready ?: return
        val channel = channel.value
        iconsJob = viewModelScope.launch {
            val next = _icons.value.toMutableMap()
            for (row in ready.rows) {
                val entry = row.entry
                if (next.containsKey(entry.id)) continue
                val bitmap = runCatching {
                    iconLoader.load(entry, channel)
                }.getOrNull()
                if (bitmap != null) {
                    next[entry.id] = bitmap
                    _icons.value = next.toMap()
                }
            }
        }
    }

    suspend fun ensureIcon(entry: StoreCatalogEntry): Bitmap? {
        _icons.value[entry.id]?.let { return it }
        val bitmap = iconLoader.load(entry, channel.value) ?: return null
        _icons.value = _icons.value + (entry.id to bitmap)
        return bitmap
    }
}
