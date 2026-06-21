package vad.dashing.tbox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide theme activation state shared by every [SettingsManager] instance.
 * Ensures [themeActivationInProgressFlow] and [preThemeActivationFlush] are visible to UI
 * when activation runs from [BackgroundService] / [DriveModeThemeWatcher].
 */
object ThemeActivationCoordinator {

    private val activationDepth = AtomicInteger(0)
    private val activationMutex = Mutex()

    private val _themeActivationInProgress = MutableStateFlow(false)
    val themeActivationInProgressFlow: StateFlow<Boolean> = _themeActivationInProgress.asStateFlow()

    val themeActivationInProgress: Boolean
        get() = _themeActivationInProgress.value

    /**
     * Invoked before activation starts so pending main-screen edits flush into the outgoing theme cache.
     */
    @Volatile
    var preThemeActivationFlush: (suspend () -> Unit)? = null

    private val _mainScreenWallpaperRevision = MutableStateFlow(0L)
    val mainScreenWallpaperRevisionFlow: StateFlow<Long> = _mainScreenWallpaperRevision.asStateFlow()

    private val _mainScreenUiReady = MutableStateFlow(false)
    val mainScreenUiReadyFlow: StateFlow<Boolean> = _mainScreenUiReady.asStateFlow()

    fun bumpMainScreenWallpaperRevision() {
        _mainScreenWallpaperRevision.value = _mainScreenWallpaperRevision.value + 1L
    }

    /** Called when [SettingsViewModel] registered [preThemeActivationFlush] and can accept theme switches. */
    fun markMainScreenUiReady() {
        _mainScreenUiReady.value = true
    }

    internal fun resetMainScreenUiReadyForTests() {
        _mainScreenUiReady.value = false
        preThemeActivationFlush = null
    }

    suspend fun awaitMainScreenUiReady() {
        if (_mainScreenUiReady.value) return
        mainScreenUiReadyFlow.first { it }
    }

    suspend fun <T> runWithThemeActivation(
        settingsManager: SettingsManager,
        block: suspend () -> T,
    ): T {
        return activationMutex.withLock {
            val wasIdle = activationDepth.getAndIncrement() == 0
            if (wasIdle) {
                val outgoingCacheKey = settingsManager.activeThemeUriFlow.first().trim()
                preThemeActivationFlush?.invoke()
                if (preThemeActivationFlush == null &&
                    ThemeCacheKeys.isLikelyCacheKey(outgoingCacheKey)
                ) {
                    settingsManager.snapshotMainScreenRuntimeToThemeCache(outgoingCacheKey)
                }
                withContext(Dispatchers.Main.immediate) {
                    _themeActivationInProgress.value = true
                }
            }
            try {
                block()
            } finally {
                if (activationDepth.decrementAndGet() == 0) {
                    withContext(Dispatchers.Main.immediate) {
                        yield()
                        _themeActivationInProgress.value = false
                    }
                }
            }
        }
    }
}
