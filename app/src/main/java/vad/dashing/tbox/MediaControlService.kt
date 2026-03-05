package vad.dashing.tbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MUSIC_WIDGET_DATA_KEY = "musicWidget"

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
    POWERAMP(
        packageName = "com.maxmpz.audioplayer",
        titleRes = R.string.media_player_poweramp,
        iconRes = R.drawable.player_poweramp
    ),
    AIMP(
        packageName = "com.aimp.player",
        titleRes = R.string.media_player_aimp,
        iconRes = R.drawable.player_aimp
    );

    companion object {
        fun fromPackage(packageName: String): SupportedMediaPlayer? {
            return entries.firstOrNull { it.packageName == packageName }
        }
    }
}

data class MediaPlayerState(
    val player: SupportedMediaPlayer,
    val artist: String = "",
    val track: String = "",
    val isPlaying: Boolean = false,
    val hasSession: Boolean = false
)

data class MediaWidgetState(
    val player: SupportedMediaPlayer? = null,
    val artist: String = "",
    val track: String = "",
    val isPlaying: Boolean = false,
    val controlsAvailable: Boolean = false,
    val notificationAccessGranted: Boolean = false
)

fun normalizeMediaPlayerPackages(rawPackages: Collection<String>): Set<String> {
    return rawPackages
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
        _playerStates.value = emptyMap()
    }

    private fun syncControllersLocked(
        activeControllers: List<MediaController> = queryActiveControllersLocked()
    ) {
        val activeByPackage = activeControllers
            .filter { it.packageName in requestedPackages }
            .associateBy { it.packageName }

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
            val preferredController = candidates.firstOrNull { it.packageName == normalizedPreferred }
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
            _playerStates.value = emptyMap()
            return
        }

        val orderedPackages = orderedMediaPlayerPackages(requestedPackages)
        val updatedStates = mutableMapOf<String, MediaPlayerState>()
        orderedPackages.forEach { packageName ->
            val player = SupportedMediaPlayer.fromPackage(packageName) ?: return@forEach
            val controller = controllers[packageName]
            val metadata = controller?.metadata
            val track = metadata.extractTrackTitle()
            val artist = metadata.extractArtistName()
            updatedStates[packageName] = MediaPlayerState(
                player = player,
                artist = artist,
                track = track,
                isPlaying = controller?.playbackState.isPlayingState(),
                hasSession = controller != null
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
