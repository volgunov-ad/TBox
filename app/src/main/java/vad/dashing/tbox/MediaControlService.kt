package vad.dashing.tbox

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MUSIC_WIDGET_DATA_KEY = "musicWidget"

/** After launching a player without a MediaController, OEM may register MediaSession late. */
private const val POST_LAUNCH_SESSION_HINT_MS = 15_000L
private val POST_LAUNCH_RESYNC_DELAYS_MS = longArrayOf(400L, 1_200L, 2_500L, 5_000L, 8_000L)

enum class SupportedMediaPlayer(
    val packageName: String,
    val titleRes: Int,
    val iconRes: Int
) {
    YANDEX_MUSIC(
        packageName = "ru.yandex.music",
        titleRes = R.string.media_player_yandex_music,
        iconRes = R.drawable.player_yandex_music
    ),
    YANDEX_MUSIC_AUTO_PLAY(
        packageName = "ru.auto.music",
        titleRes = R.string.media_player_yandex_music_auto_play,
        iconRes = R.drawable.player_yandex_music
    ),
    YANDEX_MAPS(
        packageName = "ru.yandex.yandexmaps",
        titleRes = R.string.media_player_yandex_maps,
        iconRes = R.drawable.player_yandex_music
    ),
    YANDEX_NAVI(
        packageName = "ru.yandex.yandexnavi",
        titleRes = R.string.media_player_yandex_navi,
        iconRes = R.drawable.player_yandex_music
    ),
    POWERAMP(
        packageName = "com.maxmpz.audioplayer",
        titleRes = R.string.media_player_poweramp,
        iconRes = R.drawable.player_poweramp
    ),
    AIMP(
        packageName = "com.aimp.player",
        titleRes = R.string.media_player_aimp,
        iconRes = R.drawable.player_aimp
    ),
    RECORD_RADIO(
        packageName = "com.maxxt.recordradio",
        titleRes = R.string.media_player_record_radio,
        iconRes = R.drawable.player_record_radio
    ),
    JETAUDIO(
        packageName = "com.jetappfactory.jetaudio",
        titleRes = R.string.media_player_jetaudio,
        iconRes = R.drawable.player_jetaudio
    ),
    JETAUDIOPLUS(
        packageName = "com.jetappfactory.jetaudioplus",
        titleRes = R.string.media_player_jetaudioplus,
        iconRes = R.drawable.player_jetaudio
    ),
    FMPLAY(
        packageName = "ru.fmplay",
        titleRes = R.string.media_player_fmplay,
        iconRes = R.drawable.player_fmplay
    ),
    YANDEX_RADIO(
        packageName = "ru.yandex.mobile.fmradio",
        titleRes = R.string.media_player_yandex_radio,
        iconRes = R.drawable.player_yandex_radio
    ),
    WT_LOCAL_MULTIMEDIA(
        packageName = "com.wt.multimedia.local",
        titleRes = R.string.media_player_wt_local_multimedia,
        iconRes = R.drawable.player_unknown
    ),
    ANDROID_CAR_LOCAL_MEDIA_PLAYER(
        packageName = "com.android.car.media.localmediaplayer",
        titleRes = R.string.media_player_android_car_local_media,
        iconRes = R.drawable.player_unknown
    ),
    BLUETOOTH_PHONE(
        packageName = "com.android.bluetooth",
        titleRes = R.string.media_player_bluetooth_phone,
        iconRes = R.drawable.player_bluetooth
    ),
    VKX(
        packageName = "ua.itaysonlab.vkx",
        titleRes = R.string.media_player_vkx,
        iconRes = R.drawable.player_vkx
    );

    companion object {
        fun fromPackage(packageName: String): SupportedMediaPlayer? {
            val normalizedPackage = packageName.trim().lowercase()
            if (normalizedPackage.isBlank()) return null
            val resolvedPackage = when (normalizedPackage) {
                "ru.yandex.radio" -> "ru.yandex.mobile.fmradio"
                // OEM often registers MediaSession with parent package (no .local suffix).
                "com.wt.multimedia",
                "com.wt.multimedia.platform3" -> "com.wt.multimedia.local"
                "com.wt.wtbtservice",
                "com.nforetek.bt",
                "com.wt.openbt.server" -> "com.android.bluetooth"
                else -> normalizedPackage
            }
            return entries.firstOrNull { it.packageName == resolvedPackage }
        }
    }
}

data class MediaPlayerState(
    val player: SupportedMediaPlayer,
    val artist: String = "",
    val track: String = "",
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

fun normalizeMediaPlayerPackages(rawPackages: Collection<String>): Set<String> {
    return rawPackages
        .map { it.trim().lowercase() }
        .mapNotNull { SupportedMediaPlayer.fromPackage(it)?.packageName }
        .toSet()
}

fun defaultMediaPlayerPackages(): Set<String> {
    return SupportedMediaPlayer.entries.map { it.packageName }.toSet()
}

fun orderedMediaPlayerPackages(rawPackages: Collection<String>): List<String> {
    val normalized = normalizeMediaPlayerPackages(rawPackages)
    return SupportedMediaPlayer.entries
        .map { it.packageName }
        .filter { it in normalized }
}

fun resolveMediaPlayersForWidget(config: FloatingDashboardWidgetConfig): Set<String> {
    if (config.dataKey != MUSIC_WIDGET_DATA_KEY) return emptySet()
    val selected = normalizeMediaPlayerPackages(config.mediaPlayers)
    return if (selected.isEmpty()) defaultMediaPlayerPackages() else selected
}

fun resolveSelectedMediaPlayerForWidget(config: FloatingDashboardWidgetConfig): String {
    return normalizeMediaPlayerPackages(listOf(config.mediaSelectedPlayer)).firstOrNull().orEmpty()
}

fun collectMediaPlayersFromWidgetConfigs(
    configs: List<FloatingDashboardWidgetConfig>
): Set<String> {
    return configs
        .asSequence()
        .filter { it.dataKey == MUSIC_WIDGET_DATA_KEY }
        .flatMap { resolveMediaPlayersForWidget(it).asSequence() }
        .toSet()
}

object SharedMediaControlService {
    private var appContext: Context? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeSessionsListenerRegistered: Boolean = false
    private var listenerComponent: ComponentName? = null
    private var notificationAccessGranted: Boolean = false

    private val sourceSelections = mutableMapOf<String, Set<String>>()
    private var requestedPackages: Set<String> = emptySet()

    private val controllers = mutableMapOf<String, MediaController>()
    private val controllerCallbacks = mutableMapOf<String, MediaController.Callback>()

    /** OEMs often put track/artist only in media notifications, not in MediaMetadata. */
    private var notificationArtistByPackage: Map<String, String> = emptyMap()
    private var notificationTrackByPackage: Map<String, String> = emptyMap()

    /**
     * While the user just launched a player via fallback (no controller yet), treat as session
     * active so the widget does not show "(выключен)" until [POST_LAUNCH_SESSION_HINT_MS] elapses
     * or a real controller appears.
     */
    private val pendingSessionHintUntilElapsed = mutableMapOf<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val postLaunchResyncRunnable = Runnable {
        synchronized(this) {
            if (requestedPackages.isEmpty()) return@Runnable
            syncControllersLocked()
            publishPlayerStatesLocked()
        }
    }

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

    /**
     * Called when [MediaControlNotificationListenerService] posts/removes notifications so we can
     * pick up title/artist text OEMs only expose on the media notification.
     */
    fun onMediaNotificationsMayHaveChanged(context: Context) {
        synchronized(this) {
            initializeLocked(context)
            if (requestedPackages.isEmpty()) return
            refreshNotificationMediaLocked()
            publishPlayerStatesLocked()
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
        preferredPackage: String = ""
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
        launchPlayerApp(context.applicationContext, targetPackage)
        noteLaunchFallbackForPackage(targetPackage)
    }

    fun play(
        context: Context,
        selectedPackages: Set<String>,
        preferredPackage: String = ""
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
        launchPlayerApp(context.applicationContext, targetPackage)
        noteLaunchFallbackForPackage(targetPackage)
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
        pendingSessionHintUntilElapsed.clear()
        mainHandler.removeCallbacks(postLaunchResyncRunnable)
        _playerStates.value = emptyMap()
    }

    private fun syncControllersLocked(
        activeControllers: List<MediaController> = queryActiveControllersLocked()
    ) {
        val activeByPackage = activeControllers
            .mapNotNull { controller ->
                val supportedPackage = SupportedMediaPlayer
                    .fromPackage(controller.packageName)
                    ?.packageName
                if (supportedPackage == null || supportedPackage !in requestedPackages) {
                    null
                } else {
                    supportedPackage to controller
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
        val effectiveSelection = if (selected.isEmpty()) {
            orderedMediaPlayerPackages(requestedPackages)
        } else {
            selected
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
                SupportedMediaPlayer.fromPackage(it.packageName)?.packageName == normalizedPreferred
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

    private fun noteLaunchFallbackForPackage(targetPackage: String) {
        val normalized = normalizeMediaPlayerPackages(listOf(targetPackage)).firstOrNull() ?: return
        if (normalized !in requestedPackages) return
        val until = SystemClock.elapsedRealtime() + POST_LAUNCH_SESSION_HINT_MS
        pendingSessionHintUntilElapsed[normalized] =
            maxOf(pendingSessionHintUntilElapsed[normalized] ?: 0L, until)
        mainHandler.removeCallbacks(postLaunchResyncRunnable)
        POST_LAUNCH_RESYNC_DELAYS_MS.forEach { delayMs ->
            mainHandler.postDelayed(postLaunchResyncRunnable, delayMs)
        }
        synchronized(this) {
            publishPlayerStatesLocked()
        }
    }

    private fun publishPlayerStatesLocked() {
        if (requestedPackages.isEmpty()) {
            _playerStates.value = emptyMap()
            return
        }

        refreshNotificationMediaLocked()

        val orderedPackages = orderedMediaPlayerPackages(requestedPackages)
        val updatedStates = mutableMapOf<String, MediaPlayerState>()
        val nowElapsed = SystemClock.elapsedRealtime()
        orderedPackages.forEach { packageName ->
            val player = SupportedMediaPlayer.fromPackage(packageName) ?: return@forEach
            val controller = controllers[packageName]
            val metadata = controller?.metadata
            val playbackState = controller?.playbackState
            val track = listOf(
                metadata.extractTrackTitle(),
                playbackState.extractPlaybackExtrasTitle(),
                notificationTrackByPackage[packageName].orEmpty()
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            val artist = listOf(
                metadata.extractArtistName(),
                playbackState.extractPlaybackExtrasArtist(),
                notificationArtistByPackage[packageName].orEmpty()
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            val hasController = controller != null
            if (hasController) {
                pendingSessionHintUntilElapsed.remove(packageName)
            }
            val hintUntil = pendingSessionHintUntilElapsed[packageName] ?: 0L
            val hasSession = hasController || nowElapsed < hintUntil
            updatedStates[packageName] = MediaPlayerState(
                player = player,
                artist = artist,
                track = track,
                durationMs = metadata.extractDurationMs(),
                positionMs = playbackState.extractPositionMs(),
                playbackSpeed = playbackState.extractPlaybackSpeed(),
                positionUpdateTimeMs = playbackState.extractPositionUpdateTimeMs(),
                isPlaying = playbackState.isPlayingState(),
                hasSession = hasSession
            )
        }

        _playerStates.value = updatedStates
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

    private fun refreshNotificationMediaLocked() {
        val svc = MediaControlNotificationListenerService.instance
        if (svc == null || !notificationAccessGranted || requestedPackages.isEmpty()) {
            notificationArtistByPackage = emptyMap()
            notificationTrackByPackage = emptyMap()
            return
        }
        val artistByPkg = mutableMapOf<String, String>()
        val trackByPkg = mutableMapOf<String, String>()
        runCatching {
            requestedPackages.forEach { pkg ->
                val sbns = svc.activeNotifications.orEmpty().filter { it.packageName == pkg }
                var bestArtist = ""
                var bestTrack = ""
                var bestScore = -1
                sbns.forEach { sbn ->
                    val notification = sbn.notification ?: return@forEach
                    val extras = notification.extras ?: return@forEach
                    val (artist, track) = extractMediaTextFromNotificationExtras(extras)
                    val score = track.length + artist.length
                    if (score > bestScore) {
                        bestScore = score
                        bestArtist = artist
                        bestTrack = track
                    }
                }
                if (bestArtist.isNotBlank()) artistByPkg[pkg] = bestArtist
                if (bestTrack.isNotBlank()) trackByPkg[pkg] = bestTrack
            }
        }
        notificationArtistByPackage = artistByPkg
        notificationTrackByPackage = trackByPkg
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

private fun PlaybackState?.extractPlaybackExtrasTitle(): String {
    return this?.extras?.let { extractTitleLikeFromMediaBundle(it) }.orEmpty()
}

private fun PlaybackState?.extractPlaybackExtrasArtist(): String {
    return this?.extras?.let { extractArtistLikeFromMediaBundle(it) }.orEmpty()
}

private fun extractTitleLikeFromMediaBundle(bundle: Bundle): String {
    val direct = bundle.pickFirstNonBlankString(
        MediaMetadata.METADATA_KEY_TITLE.toString(),
        MediaMetadata.METADATA_KEY_DISPLAY_TITLE.toString(),
        "android.media.metadata.TITLE",
        "android.media.metadata.DISPLAY_TITLE",
        "title",
        "track",
        "Track",
        "song",
        "media_title"
    )
    if (direct.isNotBlank()) return direct
    return bundle.deepSearchStrings { key, value ->
        val kl = key.lowercase()
        (kl.contains("title") || kl.contains("track") || kl.contains("song")) &&
            !kl.contains("artist") && !kl.contains("album_artist")
    }.firstOrNull().orEmpty()
}

private fun extractArtistLikeFromMediaBundle(bundle: Bundle): String {
    val direct = bundle.pickFirstNonBlankString(
        MediaMetadata.METADATA_KEY_ARTIST.toString(),
        MediaMetadata.METADATA_KEY_ALBUM_ARTIST.toString(),
        MediaMetadata.METADATA_KEY_WRITER.toString(),
        "android.media.metadata.ARTIST",
        "android.media.metadata.ALBUM_ARTIST",
        "artist",
        "Artist",
        "media_artist"
    )
    if (direct.isNotBlank()) return direct
    return bundle.deepSearchStrings { key, value ->
        key.lowercase().contains("artist")
    }.firstOrNull().orEmpty()
}

private fun Bundle.pickFirstNonBlankString(vararg keys: String): String {
    for (key in keys) {
        val v = getCharSequence(key)?.toString()?.trim()
        if (!v.isNullOrBlank()) return v
        val s = getString(key)?.trim()
        if (!s.isNullOrBlank()) return s
    }
    return ""
}

private fun Bundle.deepSearchStrings(predicate: (String, String) -> Boolean): List<String> {
    val out = mutableListOf<String>()
    for (key in keySet()) {
        val cs = getCharSequence(key)?.toString()?.trim()
        if (!cs.isNullOrBlank() && predicate(key, cs)) out.add(cs)
    }
    return out
}

private fun extractMediaTextFromNotificationExtras(extras: Bundle): Pair<String, String> {
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
    val info = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim().orEmpty()
    val summary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim().orEmpty()
    val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()

    var track = title
    var artist = sub
    if (track.isBlank() && text.isNotBlank()) track = text
    if (artist.isBlank() && text.isNotBlank() && text != track) artist = text
    if (artist.isBlank() && info.isNotBlank()) artist = info
    if (artist.isBlank() && summary.isNotBlank()) artist = summary
    if (track.isBlank() && big.isNotBlank()) {
        val lines = big.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isNotEmpty()) track = lines.first()
        if (lines.size >= 2 && artist.isBlank()) artist = lines[1]
    }

    val mediaFromExtras = extractTitleLikeFromMediaBundle(extras) to extractArtistLikeFromMediaBundle(extras)
    if (mediaFromExtras.first.isNotBlank()) track = mediaFromExtras.first
    if (mediaFromExtras.second.isNotBlank()) artist = mediaFromExtras.second

    return Pair(artist, track)
}

private fun MediaMetadata?.extractTrackTitle(): String {
    if (this == null) return ""
    val title = getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
    if (title.isNotBlank()) return title
    val displayTitle = getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
    if (displayTitle.isNotBlank()) return displayTitle
    val subtitle = getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
    if (subtitle.isNotBlank()) return subtitle
    val description = getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION).orEmpty()
    if (description.isNotBlank()) return description
    val album = getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
    if (album.isNotBlank()) return album
    val uri = getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()
    if (uri.isNotBlank()) return uri.substringAfterLast('/').substringBefore('?')
    return ""
}

private fun MediaMetadata?.extractArtistName(): String {
    if (this == null) return ""
    val artist = getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
    if (artist.isNotBlank()) return artist
    val albumArtist = getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
    if (albumArtist.isNotBlank()) return albumArtist
    val writer = getString(MediaMetadata.METADATA_KEY_WRITER).orEmpty()
    if (writer.isNotBlank()) return writer
    return ""
}

private fun MediaMetadata?.extractDurationMs(): Long {
    if (this == null) return 0L
    val duration = getLong(MediaMetadata.METADATA_KEY_DURATION)
    return if (duration > 0L) duration else 0L
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
}

private fun launchPlayerApp(context: Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(launchIntent)
    }
}
