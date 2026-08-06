package vad.dashing.tbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedHashSet
import java.net.HttpURLConnection
import java.net.URL

const val MUSIC_WIDGET_DATA_KEY = "musicWidget"
const val MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY = "musicButtonsWidgetHorizontal"
const val MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY = "musicButtonsWidgetVertical"

/** Full music tile or buttons-only (H/V) variants that share media player config. */
fun isMusicWidgetDataKey(dataKey: String): Boolean {
    return dataKey == MUSIC_WIDGET_DATA_KEY ||
        dataKey == MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY
}

/** After [launchPlayerApp] from a cold start, re-send play if session still not playing (matches widget auto-play verify). */
private const val LAUNCH_PLAYER_VERIFY_DELAY_MS = 4000L
/** After manual play button launch: if session exists but still paused, send one more play command. */
private const val LAUNCH_PLAYER_MANUAL_LATE_PLAY_RETRY_DELAY_MS = 7000L
/** Poll cadence for early play/session detection after external player launch. */
private const val PLAYER_LAUNCH_STATE_POLL_MS = 500L

enum class SupportedMediaPlayer(
    val packageName: String,
    val titleRes: Int,
    val iconRes: Int
) {
    BLUETOOTH_PHONE(
        packageName = "com.android.bluetooth",
        titleRes = R.string.media_player_bluetooth_phone,
        iconRes = R.drawable.player_bluetooth
    );

    companion object {
        fun fromPackage(packageName: String): SupportedMediaPlayer? {
            val normalizedPackage = packageName.trim().lowercase()
            if (normalizedPackage.isBlank()) return null
            return entries.firstOrNull { it.packageName == normalizedPackage }
        }
    }
}

data class MediaPlayerState(
    /** Non-null when [packageName] matches a built-in entry; otherwise UI uses a generic icon/label. */
    val player: SupportedMediaPlayer?,
    val artist: String = "",
    val track: String = "",
    /** Album / track artwork when MediaMetadata provides it; null → UI falls back to app icon. */
    val albumArt: ImageBitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val positionUpdateTimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val hasSession: Boolean = false
)

data class MediaWidgetState(
    val player: SupportedMediaPlayer? = null,
    val artist: String = "",
    val track: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val positionUpdateTimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val controlsAvailable: Boolean = false,
    val notificationAccessGranted: Boolean = false
)

/**
 * Canonical package name for media widget selection and MediaSession matching.
 * Accepts any plausible Android package id (launcher apps); known players use enum aliases.
 */
fun canonicalMediaPlayerPackage(raw: String): String? {
    val trimmed = raw.trim().lowercase()
    if (trimmed.isBlank()) return null
    val mapped = when (trimmed) {
        "ru.yandex.radio" -> "ru.yandex.mobile.fmradio"
        else -> trimmed
    }
    SupportedMediaPlayer.fromPackage(mapped)?.packageName?.let { return it }
    if (!mapped.contains('.')) return null
    if (mapped.length > 200) return null
    if (mapped.any { ch ->
            ch !in 'a'..'z' && ch !in '0'..'9' && ch != '.' && ch != '_'
        }
    ) {
        return null
    }
    return mapped
}

fun normalizeMediaPlayerPackages(rawPackages: Collection<String>): Set<String> {
    val out = LinkedHashSet<String>()
    for (raw in rawPackages) {
        canonicalMediaPlayerPackage(raw)?.let { out.add(it) }
    }
    return out
}

fun defaultMediaPlayerPackages(): Set<String> = emptySet()

fun orderedMediaPlayerPackages(rawPackages: Collection<String>): List<String> {
    val orderedUnique = LinkedHashSet<String>()
    for (raw in rawPackages) {
        canonicalMediaPlayerPackage(raw)?.let { orderedUnique.add(it) }
    }
    if (orderedUnique.isEmpty()) return emptyList()
    val knownOrdered = SupportedMediaPlayer.entries
        .map { it.packageName }
        .filter { it in orderedUnique }
    val knownSet = knownOrdered.toSet()
    val extras = orderedUnique.filter { it !in knownSet }
    return knownOrdered + extras
}

fun resolveMediaPlayersForWidget(config: FloatingDashboardWidgetConfig): Set<String> {
    if (!isMusicWidgetDataKey(config.dataKey)) return emptySet()
    val selected = normalizeMediaPlayerPackages(config.mediaPlayers)
    return if (selected.isEmpty()) defaultMediaPlayerPackages() else selected
}

fun resolveSelectedMediaPlayerForWidget(config: FloatingDashboardWidgetConfig): String {
    return canonicalMediaPlayerPackage(config.mediaSelectedPlayer).orEmpty()
}

fun collectMediaPlayersFromWidgetConfigs(
    configs: List<FloatingDashboardWidgetConfig>
): Set<String> {
    return configs
        .asSequence()
        .filter { isMusicWidgetDataKey(it.dataKey) }
        .flatMap { resolveMediaPlayersForWidget(it).asSequence() }
        .toSet()
}

object SharedMediaControlService {
    private val launchPlayerVerifyExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        runCatching {
            TboxRepository.addLog("ERROR", "MediaControl", "Launch verify error: ${throwable.message}")
        }
    }
    private val launchPlayerVerifyScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + launchPlayerVerifyExceptionHandler)

    private var appContext: Context? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeSessionsListenerRegistered: Boolean = false
    private var listenerComponent: ComponentName? = null
    private var notificationAccessGranted: Boolean = false

    private val sourceSelections = mutableMapOf<String, Set<String>>()
    private var requestedPackages: Set<String> = emptySet()

    private val controllers = mutableMapOf<String, MediaController>()
    private val controllerCallbacks = mutableMapOf<String, MediaController.Callback>()

    private data class AlbumArtCacheEntry(
        val key: String,
        val image: ImageBitmap?,
    )

    private val albumArtCache = mutableMapOf<String, AlbumArtCacheEntry>()
    private val pendingAlbumArtUriLoads = mutableSetOf<String>()

    private val _playerStates = MutableStateFlow<Map<String, MediaPlayerState>>(emptyMap())
    val playerStates: StateFlow<Map<String, MediaPlayerState>> = _playerStates.asStateFlow()

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
            activeControllers ->
        synchronized(this) {
            if (requestedPackages.isEmpty()) return@OnActiveSessionsChangedListener
            syncControllersLocked(activeControllers.orEmpty())
            publishPlayerStatesLocked()
        }
    }

    fun updateSourceSelection(
        context: Context,
        sourceId: String,
        mediaPackages: Set<String>
    ) {
        if (sourceId.isBlank()) return
        synchronized(this) {
            initializeLocked(context)
            val normalized = normalizeMediaPlayerPackages(mediaPackages)
            if (normalized.isEmpty()) {
                sourceSelections.remove(sourceId)
            } else {
                sourceSelections[sourceId] = normalized
            }
            refreshRequestedPackagesLocked()
        }
    }

    fun clearSourceSelection(sourceId: String) {
        if (sourceId.isBlank()) return
        synchronized(this) {
            sourceSelections.remove(sourceId)
            refreshRequestedPackagesLocked()
        }
    }

    fun resolveWidgetState(
        selectedPackages: Set<String>,
        currentStates: Map<String, MediaPlayerState> = playerStates.value,
        preferredPackage: String = ""
    ): MediaWidgetState {
        val refreshedStates = synchronized(this) {
            updateNotificationAccessLocked()
            if (requestedPackages.isNotEmpty() &&
                notificationAccessGranted &&
                _playerStates.value.isEmpty()
            ) {
                startMonitoringLocked()
                syncControllersLocked()
                publishPlayerStatesLocked()
            }
            _playerStates.value
        }
        val effectiveStates = if (refreshedStates.isNotEmpty() || currentStates.isEmpty()) {
            refreshedStates
        } else {
            currentStates
        }
        val orderedSelected = orderedMediaPlayerPackages(selectedPackages)
        if (orderedSelected.isEmpty()) {
            return MediaWidgetState(notificationAccessGranted = isNotificationAccessGranted())
        }

        val normalizedPreferred = normalizeMediaPlayerPackages(listOf(preferredPackage)).firstOrNull()
        val prioritizedPackages = if (normalizedPreferred != null && normalizedPreferred in orderedSelected) {
            listOf(normalizedPreferred) + orderedSelected.filterNot { it == normalizedPreferred }
        } else {
            orderedSelected
        }

        val selectedState = if (normalizedPreferred != null) {
            effectiveStates[normalizedPreferred]
        } else {
            val candidates = prioritizedPackages.mapNotNull { effectiveStates[it] }
            candidates.firstOrNull { it.isPlaying }
                ?: candidates.firstOrNull { it.track.isNotBlank() || it.artist.isNotBlank() }
                ?: candidates.firstOrNull { it.hasSession }
        }

        val fallbackPlayer = selectedState?.player
            ?: SupportedMediaPlayer.fromPackage(prioritizedPackages.firstOrNull().orEmpty())

        return MediaWidgetState(
            player = fallbackPlayer,
            artist = selectedState?.artist.orEmpty(),
            track = selectedState?.track.orEmpty(),
            durationMs = selectedState?.durationMs ?: 0L,
            positionMs = selectedState?.positionMs ?: 0L,
            playbackSpeed = selectedState?.playbackSpeed ?: 1f,
            positionUpdateTimeMs = selectedState?.positionUpdateTimeMs ?: 0L,
            isPlaying = selectedState?.isPlaying == true,
            controlsAvailable = selectedState?.hasSession == true,
            notificationAccessGranted = isNotificationAccessGranted()
        )
    }

    fun skipToPrevious(selectedPackages: Set<String>, preferredPackage: String = "") {
        synchronized(this) {
            syncControllersLocked()
            resolveControllerLocked(
                selectedPackages = selectedPackages,
                preferredPackage = preferredPackage,
                strictPreferred = preferredPackage.isNotBlank()
            )
                ?.transportControls
                ?.skipToPrevious()
        }
    }

    fun playPause(
        context: Context,
        selectedPackages: Set<String>,
        preferredPackage: String = "",
        keepPlayerForeground: Boolean = false // anymani: опция независима от автозапуска
    ) {
        var controllerHandled = false
        synchronized(this) {
            syncControllersLocked()
            val controller = resolveControllerLocked(
                selectedPackages = selectedPackages,
                preferredPackage = preferredPackage,
                strictPreferred = preferredPackage.isNotBlank()
            )
            if (controller != null) {
                val isPlaying = controller.playbackState.isPlayingState()
                if (isPlaying) {
                    controller.transportControls.pause()
                } else {
                    controller.transportControls.play()
                }
                controllerHandled = true
            }
        }
        if (controllerHandled) return

        val targetPackage = resolveTargetPackage(
            selectedPackages = selectedPackages,
            preferredPackage = preferredPackage
        ) ?: return
        sendMediaPlayKeyEvent(context.applicationContext, targetPackage)
        launchPlayerApp(
            context.applicationContext,
            targetPackage,
            scheduleColdStartPlayRetry = true,
            keepPlayerForeground = keepPlayerForeground, // anymani: передаём флаг при ручном запуске
            scheduleLateSessionPlayRetry = true
        )
    }

    fun play(
        context: Context,
        selectedPackages: Set<String>,
        preferredPackage: String = "",
        keepPlayerForeground: Boolean = false // anymani: опция для отключения возврата лаунчера
    ) {
        var controllerHandled = false
        synchronized(this) {
            syncControllersLocked()
            val controller = resolveControllerLocked(
                selectedPackages = selectedPackages,
                preferredPackage = preferredPackage,
                strictPreferred = preferredPackage.isNotBlank()
            )
            if (controller != null) {
                if (!controller.playbackState.isPlayingState()) {
                    controller.transportControls.play()
                }
                controllerHandled = true
            }
        }
        if (controllerHandled) return

        val targetPackage = resolveTargetPackage(
            selectedPackages = selectedPackages,
            preferredPackage = preferredPackage
        ) ?: return
        sendMediaPlayKeyEvent(context.applicationContext, targetPackage)
        launchPlayerApp(
            context.applicationContext,
            targetPackage, scheduleColdStartPlayRetry = true,
            keepPlayerForeground = keepPlayerForeground, // anymani: передаём флаг в launchPlayerApp
            scheduleLateSessionPlayRetry = true
        )
    }

    fun skipToNext(selectedPackages: Set<String>, preferredPackage: String = "") {
        synchronized(this) {
            syncControllersLocked()
            resolveControllerLocked(
                selectedPackages = selectedPackages,
                preferredPackage = preferredPackage,
                strictPreferred = preferredPackage.isNotBlank()
            )
                ?.transportControls
                ?.skipToNext()
        }
    }

    internal fun scheduleColdStartPlayRetryIfNeeded(appContext: Context, targetPackage: String) {
        launchPlayerVerifyScope.launch {
            try {
                delay(LAUNCH_PLAYER_VERIFY_DELAY_MS)
                val needsRetry = synchronized(this@SharedMediaControlService) {
                    initializeLocked(appContext)
                    syncControllersLocked()
                    val controller = resolveControllerLocked(
                        selectedPackages = setOf(targetPackage),
                        preferredPackage = targetPackage,
                        strictPreferred = true
                    )
                    controller == null || !controller.playbackState.isPlayingState()
                }
                if (!needsRetry) return@launch
                sendMediaPlayKeyEvent(appContext, targetPackage)
                launchPlayerApp(appContext, targetPackage, scheduleColdStartPlayRetry = false)
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "MediaControl", "Cold start retry failed: ${e.message}")
            }
        }
    }

    internal fun scheduleLateSessionPlayRetryIfNeeded(appContext: Context, targetPackage: String) {
        launchPlayerVerifyScope.launch {
            try {
                val deadline = SystemClock.elapsedRealtime() + LAUNCH_PLAYER_MANUAL_LATE_PLAY_RETRY_DELAY_MS
                var shouldSendFallbackKey = false
                while (SystemClock.elapsedRealtime() < deadline) {
                    var shouldExit = false
                    synchronized(this@SharedMediaControlService) {
                        initializeLocked(appContext)
                        syncControllersLocked()
                        val controller = resolveControllerLocked(
                            selectedPackages = setOf(targetPackage),
                            preferredPackage = targetPackage,
                            strictPreferred = true
                        )
                        when {
                            controller == null -> Unit
                            controller.playbackState.isPlayingState() -> {
                                shouldExit = true
                            }
                            else -> {
                                controller.transportControls.play()
                                shouldSendFallbackKey = true
                                shouldExit = true
                            }
                        }
                    }
                    if (shouldExit) break
                    delay(PLAYER_LAUNCH_STATE_POLL_MS)
                }
                if (shouldSendFallbackKey) {
                    sendMediaPlayKeyEvent(appContext, targetPackage)
                }
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "MediaControl", "Late play retry failed: ${e.message}")
            }
        }
    }

    internal fun scheduleDeferredMainReturnOnPlaybackStartIfNeeded(
        appContext: Context,
        targetPackage: String,
        maxWaitMs: Long = DeferredMainActivityRequest.AFTER_MUSIC_WIDGET_PLAYER_LAUNCH_MS
    ) {
        launchPlayerVerifyScope.launch {
            try {
                val deadline = SystemClock.elapsedRealtime() + maxWaitMs.coerceAtLeast(0L)
                while (SystemClock.elapsedRealtime() < deadline) {
                    val isPlaying = synchronized(this@SharedMediaControlService) {
                        initializeLocked(appContext)
                        syncControllersLocked()
                        val controller = resolveControllerLocked(
                            selectedPackages = setOf(targetPackage),
                            preferredPackage = targetPackage,
                            strictPreferred = true
                        )
                        controller?.playbackState.isPlayingState()
                    }
                    if (isPlaying) {
                        DeferredMainActivityRequest.scheduleReturnAfterExternalPlayerLaunchIfMainWasVisible(
                            context = appContext,
                            delayMs = 0L
                        )
                        return@launch
                    }
                    delay(PLAYER_LAUNCH_STATE_POLL_MS)
                }
                DeferredMainActivityRequest.scheduleReturnAfterExternalPlayerLaunchIfMainWasVisible(
                    context = appContext,
                    delayMs = 0L
                )
            } catch (e: Exception) {
                TboxRepository.addLog("ERROR", "MediaControl", "Deferred main return failed: ${e.message}")
            }
        }
    }

    private fun initializeLocked(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        val contextRef = appContext ?: return
        if (mediaSessionManager == null) {
            mediaSessionManager = contextRef.getSystemService(MediaSessionManager::class.java)
        }
        if (listenerComponent == null) {
            listenerComponent = ComponentName(contextRef, MediaControlNotificationListenerService::class.java)
        }
        updateNotificationAccessLocked()
    }

    private fun refreshRequestedPackagesLocked() {
        requestedPackages = sourceSelections.values
            .flatMap { it }
            .toSet()
        updateNotificationAccessLocked()

        if (requestedPackages.isEmpty()) {
            stopMonitoringLocked()
            return
        }
        if (!notificationAccessGranted) {
            stopMonitoringLocked()
            return
        }

        startMonitoringLocked()
        syncControllersLocked()
        publishPlayerStatesLocked()
    }

    private fun startMonitoringLocked() {
        if (activeSessionsListenerRegistered) return
        if (!notificationAccessGranted) return
        val manager = mediaSessionManager ?: return
        val component = listenerComponent ?: return
        try {
            manager.addOnActiveSessionsChangedListener(activeSessionsListener, component)
            activeSessionsListenerRegistered = true
        } catch (_: SecurityException) {
            activeSessionsListenerRegistered = false
        }
    }

    private fun stopMonitoringLocked() {
        if (activeSessionsListenerRegistered) {
            try {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
            } catch (_: SecurityException) {
                // Ignore
            } finally {
                activeSessionsListenerRegistered = false
            }
        }

        controllers.keys.toList().forEach { packageName ->
            unregisterControllerLocked(packageName)
        }
        albumArtCache.clear()
        pendingAlbumArtUriLoads.clear()
        _playerStates.value = emptyMap()
    }

    private fun syncControllersLocked(
        activeControllers: List<MediaController> = queryActiveControllersLocked()
    ) {
        val activeByPackage = activeControllers
            .mapNotNull { controller ->
                val canonical = canonicalMediaPlayerPackage(controller.packageName)
                    ?: return@mapNotNull null
                if (canonical !in requestedPackages) {
                    null
                } else {
                    canonical to controller
                }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, candidates) ->
                selectPreferredController(candidates)
            }

        val packagesToRemove = controllers.keys
            .filter { packageName ->
                packageName !in requestedPackages || activeByPackage[packageName] == null
            }
        packagesToRemove.forEach { unregisterControllerLocked(it) }

        activeByPackage.forEach { (packageName, controller) ->
            val existing = controllers[packageName]
            if (existing?.sessionToken != controller.sessionToken) {
                unregisterControllerLocked(packageName)
                registerControllerLocked(packageName, controller)
            }
        }
    }

    private fun selectPreferredController(candidates: List<MediaController>): MediaController {
        return candidates.firstOrNull { it.playbackState.isPlayingState() }
            ?: candidates.firstOrNull {
                val metadata = it.metadata
                metadata.extractTrackTitle().isNotBlank() || metadata.extractArtistName().isNotBlank()
            }
            ?: candidates.first()
    }

    private fun queryActiveControllersLocked(): List<MediaController> {
        updateNotificationAccessLocked()
        if (!notificationAccessGranted) return emptyList()
        val manager = mediaSessionManager ?: return emptyList()
        val component = listenerComponent ?: return emptyList()
        return try {
            manager.getActiveSessions(component).orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun registerControllerLocked(packageName: String, controller: MediaController) {
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                synchronized(this@SharedMediaControlService) {
                    publishPlayerStatesLocked()
                }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                synchronized(this@SharedMediaControlService) {
                    publishPlayerStatesLocked()
                }
            }

            override fun onSessionDestroyed() {
                synchronized(this@SharedMediaControlService) {
                    unregisterControllerLocked(packageName)
                    syncControllersLocked()
                    publishPlayerStatesLocked()
                }
            }
        }
        controller.registerCallback(callback)
        controllers[packageName] = controller
        controllerCallbacks[packageName] = callback
    }

    private fun unregisterControllerLocked(packageName: String) {
        val controller = controllers.remove(packageName) ?: return
        controllerCallbacks.remove(packageName)?.let { callback ->
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {
                // Ignore stale callback failures.
            }
        }
    }

    private fun resolveControllerLocked(
        selectedPackages: Set<String>,
        preferredPackage: String = "",
        strictPreferred: Boolean = false
    ): MediaController? {
        val selected = orderedMediaPlayerPackages(selectedPackages)
        val effectiveSelection = selected.ifEmpty {
            orderedMediaPlayerPackages(requestedPackages)
        }
        val normalizedPreferred = normalizeMediaPlayerPackages(listOf(preferredPackage)).firstOrNull()
        val prioritizedSelection = if (normalizedPreferred != null && normalizedPreferred in effectiveSelection) {
            listOf(normalizedPreferred) + effectiveSelection.filterNot { it == normalizedPreferred }
        } else {
            effectiveSelection
        }
        val candidates = prioritizedSelection.mapNotNull { packageName ->
            controllers[packageName]
        }
        if (candidates.isEmpty()) return null
        if (normalizedPreferred != null) {
            val preferredController = candidates.firstOrNull {
                canonicalMediaPlayerPackage(it.packageName) == normalizedPreferred
            }
            if (strictPreferred) {
                return preferredController
            }
            return preferredController
                ?: candidates.firstOrNull { it.playbackState.isPlayingState() }
                ?: candidates.first()
        }
        return candidates.firstOrNull { it.playbackState.isPlayingState() } ?: candidates.first()
    }

    private fun resolveTargetPackage(
        selectedPackages: Set<String>,
        preferredPackage: String
    ): String? {
        val normalizedPreferred = normalizeMediaPlayerPackages(listOf(preferredPackage)).firstOrNull()
        if (normalizedPreferred != null) return normalizedPreferred
        return orderedMediaPlayerPackages(selectedPackages).firstOrNull()
    }

    private fun publishPlayerStatesLocked() {
        if (requestedPackages.isEmpty()) {
            albumArtCache.clear()
            pendingAlbumArtUriLoads.clear()
            _playerStates.value = emptyMap()
            return
        }

        val orderedPackages = orderedMediaPlayerPackages(requestedPackages)
        val updatedStates = mutableMapOf<String, MediaPlayerState>()
        orderedPackages.forEach { packageName ->
            val player = SupportedMediaPlayer.fromPackage(packageName)
            val controller = controllers[packageName]
            val metadata = controller?.metadata
            val playbackState = controller?.playbackState
            val track = metadata.extractTrackTitle()
            val artist = metadata.extractArtistName()
            val albumArt = resolveAlbumArtLocked(packageName, metadata, track, artist)
            updatedStates[packageName] = MediaPlayerState(
                player = player,
                artist = artist,
                track = track,
                albumArt = albumArt,
                durationMs = metadata.extractDurationMs(),
                positionMs = playbackState.extractPositionMs(),
                playbackSpeed = playbackState.extractPlaybackSpeed(),
                positionUpdateTimeMs = playbackState.extractPositionUpdateTimeMs(),
                isPlaying = playbackState.isPlayingState(),
                hasSession = controller != null
            )
        }

        val staleArtPackages = albumArtCache.keys.filter { it !in orderedPackages }
        staleArtPackages.forEach { albumArtCache.remove(it) }
        pendingAlbumArtUriLoads.removeAll { it !in orderedPackages }

        _playerStates.value = updatedStates
    }

    private fun resolveAlbumArtLocked(
        packageName: String,
        metadata: MediaMetadata?,
        track: String,
        artist: String,
    ): ImageBitmap? {
        val artUri = metadata.extractAlbumArtUri()
        val cacheKey = albumArtCacheKey(track = track, artist = artist, artUri = artUri)
        albumArtCache[packageName]?.let { cached ->
            if (cached.key == cacheKey) {
                return cached.image
            }
        }

        val fromBitmap = metadata.extractAlbumArtImageBitmap()
        if (fromBitmap != null) {
            albumArtCache[packageName] = AlbumArtCacheEntry(cacheKey, fromBitmap)
            return fromBitmap
        }

        albumArtCache[packageName] = AlbumArtCacheEntry(cacheKey, null)
        if (artUri.isNotBlank() && packageName !in pendingAlbumArtUriLoads) {
            pendingAlbumArtUriLoads.add(packageName)
            scheduleAlbumArtUriLoad(packageName, cacheKey, artUri)
        }
        return null
    }

    private fun scheduleAlbumArtUriLoad(packageName: String, cacheKey: String, artUri: String) {
        val context = appContext ?: run {
            pendingAlbumArtUriLoads.remove(packageName)
            return
        }
        launchPlayerVerifyScope.launch(Dispatchers.IO) {
            val image = decodeAlbumArtUriToImageBitmap(context, artUri)
            synchronized(this@SharedMediaControlService) {
                pendingAlbumArtUriLoads.remove(packageName)
                val cached = albumArtCache[packageName]
                if (cached == null || cached.key != cacheKey) {
                    return@synchronized
                }
                if (image == null) {
                    return@synchronized
                }
                albumArtCache[packageName] = AlbumArtCacheEntry(cacheKey, image)
                val current = _playerStates.value
                val prev = current[packageName] ?: return@synchronized
                _playerStates.value = current + (packageName to prev.copy(albumArt = image))
            }
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        return synchronized(this) { notificationAccessGranted }
    }

    private fun updateNotificationAccessLocked() {
        val context = appContext
        val component = listenerComponent
        notificationAccessGranted = if (context == null || component == null) {
            false
        } else {
            hasNotificationListenerAccess(context, component)
        }
    }
}

private fun PlaybackState?.isPlayingState(): Boolean {
    return when (this?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING -> true
        else -> false
    }
}

private fun MediaMetadata?.extractTrackTitle(): String {
    if (this == null) return ""
    val title = getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
    if (title.isNotBlank()) return title
    return getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
}

private fun MediaMetadata?.extractArtistName(): String {
    if (this == null) return ""
    val artist = getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
    if (artist.isNotBlank()) return artist
    return getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
}

private fun MediaMetadata?.extractDurationMs(): Long {
    if (this == null) return 0L
    val duration = getLong(MediaMetadata.METADATA_KEY_DURATION)
    return if (duration > 0L) duration else 0L
}

private fun albumArtCacheKey(track: String, artist: String, artUri: String): String {
    return "$track\u0000$artist\u0000$artUri"
}

private fun MediaMetadata?.extractAlbumArtUri(): String {
    if (this == null) return ""
    val keys = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,
    )
    for (key in keys) {
        val value = getString(key).orEmpty().trim()
        if (value.isNotBlank()) return value
    }
    return ""
}

private fun MediaMetadata?.extractAlbumArtImageBitmap(): ImageBitmap? {
    if (this == null) return null
    val keys = listOf(
        MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON,
    )
    for (key in keys) {
        val bitmap = runCatching { getBitmap(key) }.getOrNull() ?: continue
        val image = bitmap.toOwnedScaledImageBitmapKeepingSource(
            MusicWidgetAlbumArtDisplay.MAX_ALBUM_ART_EDGE_PX,
        )
        if (image != null) return image
    }
    return null
}

private fun decodeAlbumArtUriToImageBitmap(context: Context, artUri: String): ImageBitmap? {
    val trimmed = artUri.trim()
    if (trimmed.isBlank()) return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase().orEmpty()
    val decoded = when (scheme) {
        "content", "file", "android.resource" -> {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
        "http", "https" -> {
            runCatching {
                val connection = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4_000
                    readTimeout = 4_000
                    instanceFollowRedirects = true
                }
                try {
                    if (connection.responseCode !in 200..299) return null
                    connection.inputStream.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
        else -> null
    } ?: return null
    val image = decoded.toOwnedScaledImageBitmapKeepingSource(
        MusicWidgetAlbumArtDisplay.MAX_ALBUM_ART_EDGE_PX,
    )
    if (!decoded.isRecycled) {
        runCatching { decoded.recycle() }
    }
    return image
}

private fun PlaybackState?.extractPositionMs(): Long {
    val position = this?.position ?: 0L
    return if (position > 0L) position else 0L
}

private fun PlaybackState?.extractPlaybackSpeed(): Float {
    return this?.playbackSpeed?.takeIf { it > 0f } ?: 1f
}

private fun PlaybackState?.extractPositionUpdateTimeMs(): Long {
    val updateTime = this?.lastPositionUpdateTime ?: 0L
    if (updateTime > 0L) return updateTime
    return SystemClock.elapsedRealtime()
}

private fun hasNotificationListenerAccess(
    context: Context,
    listenerComponent: ComponentName
): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ).orEmpty()
    if (enabledListeners.isBlank()) return false

    return enabledListeners
        .split(':')
        .mapNotNull { ComponentName.unflattenFromString(it) }
        .any { it == listenerComponent }
}

private fun sendMediaPlayKeyEvent(context: Context, packageName: String) {
    try {
        val keyDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setPackage(packageName)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
        val keyUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setPackage(packageName)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        }
        context.sendOrderedBroadcast(keyDown, null)
        context.sendOrderedBroadcast(keyUp, null)
    } catch (e: Exception) {
        TboxRepository.addLog("ERROR", "MediaControl", "Media play key broadcast failed: ${e.message}")
    }
}

private fun launchPlayerApp(
    context: Context,
    packageName: String,
    scheduleColdStartPlayRetry: Boolean = true,
    keepPlayerForeground: Boolean = false, // anymani: флаг для контроля возврата лаунчера
    scheduleLateSessionPlayRetry: Boolean = false
) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
    val shouldReturnToMain = !keepPlayerForeground && MainActivityForegroundTracker.isMainActivityInForeground.value

    val launched = runCatching {
        context.startActivity(launchIntent)
    }.isSuccess
    if (!launched) return

    // Return only if MainActivity was visible at the moment the player launch started.
    if (shouldReturnToMain) {
        SharedMediaControlService.scheduleDeferredMainReturnOnPlaybackStartIfNeeded(
            appContext = context.applicationContext,
            targetPackage = packageName
        )
    }
    if (scheduleColdStartPlayRetry) {
        SharedMediaControlService.scheduleColdStartPlayRetryIfNeeded(context.applicationContext, packageName)
    }
    if (scheduleLateSessionPlayRetry) {
        SharedMediaControlService.scheduleLateSessionPlayRetryIfNeeded(context.applicationContext, packageName)
    }
}
