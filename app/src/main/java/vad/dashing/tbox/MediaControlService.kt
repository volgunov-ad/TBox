package vad.dashing.tbox

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.os.Build
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MUSIC_WIDGET_DATA_KEY = "musicWidget"

private const val PLAYER_LAUNCH_AUTO_COLLAPSE_TIMEOUT_MS = 5000L

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
    PCRADIO(
        packageName = "com.maxxt.pcradio",
        titleRes = R.string.media_player_pcradio,
        iconRes = R.drawable.player_pcradio
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
    YANDEX_MAPS(
        packageName = "ru.yandex.yandexmaps",
        titleRes = R.string.media_player_yandex_maps,
        iconRes = R.drawable.player_yandex_maps
    ),
    YANDEX_NAVI(
        packageName = "ru.yandex.yandexnavi",
        titleRes = R.string.media_player_yandex_navi,
        iconRes = R.drawable.player_yandex_navigator
    ),
    //WT_LOCAL_MULTIMEDIA(
    //    packageName = "com.wt.multimedia.local",
    //    titleRes = R.string.media_player_wt_local_multimedia,
    //    iconRes = R.drawable.player_unknown
    //),
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

    private val _playerStates = MutableStateFlow<Map<String, MediaPlayerState>>(emptyMap())
    val playerStates: StateFlow<Map<String, MediaPlayerState>> = _playerStates.asStateFlow()

    private val autoCollapseHandler = Handler(Looper.getMainLooper())
    private var autoCollapseTokenSeq: Long = 0L
    private data class PendingPlayerLaunchAutoCollapse(
        val token: Long,
        val packageName: String,
        val returnToPackage: String?
    )

    private var pendingPlayerLaunchAutoCollapse: PendingPlayerLaunchAutoCollapse? = null
    private var playerLaunchAutoCollapseTimeoutRunnable: Runnable? = null

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
        val launchResult = launchPlayerApp(context.applicationContext, targetPackage)
        if (launchResult.started) {
            scheduleAutoCollapseAfterPlayerLaunch(
                context.applicationContext,
                targetPackage,
                launchResult.returnToPackage
            )
        }
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
        val launchResult = launchPlayerApp(context.applicationContext, targetPackage)
        if (launchResult.started) {
            scheduleAutoCollapseAfterPlayerLaunch(
                context.applicationContext,
                targetPackage,
                launchResult.returnToPackage
            )
        }
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
            val playbackState = controller?.playbackState
            val track = metadata.extractTrackTitle()
            val artist = metadata.extractArtistName()
            updatedStates[packageName] = MediaPlayerState(
                player = player,
                artist = artist,
                track = track,
                durationMs = metadata.extractDurationMs(),
                positionMs = playbackState.extractPositionMs(),
                playbackSpeed = playbackState.extractPlaybackSpeed(),
                positionUpdateTimeMs = playbackState.extractPositionUpdateTimeMs(),
                isPlaying = playbackState.isPlayingState(),
                hasSession = controller != null
            )
        }

        _playerStates.value = updatedStates

        val pending = pendingPlayerLaunchAutoCollapse
        if (pending != null) {
            val state = updatedStates[pending.packageName]
            if (state?.isPlaying == true) {
                pendingPlayerLaunchAutoCollapse = null
                playerLaunchAutoCollapseTimeoutRunnable?.let { runnable ->
                    autoCollapseHandler.removeCallbacks(runnable)
                    playerLaunchAutoCollapseTimeoutRunnable = null
                }
                val ctx = appContext
                if (ctx != null) {
                    autoCollapseHandler.post { restorePreviousApp(ctx, pending.returnToPackage) }
                }
            }
        }
    }

    private fun scheduleAutoCollapseAfterPlayerLaunch(
        context: Context,
        packageName: String,
        returnToPackage: String?
    ) {
        synchronized(this) {
            initializeLocked(context)
            playerLaunchAutoCollapseTimeoutRunnable?.let { autoCollapseHandler.removeCallbacks(it) }
            autoCollapseTokenSeq += 1
            val token = autoCollapseTokenSeq
            pendingPlayerLaunchAutoCollapse =
                PendingPlayerLaunchAutoCollapse(token, packageName, returnToPackage)
            val timeoutRunnable = Runnable {
                synchronized(this@SharedMediaControlService) {
                    val pending = pendingPlayerLaunchAutoCollapse
                    if (pending == null || pending.token != token) return@Runnable
                    pendingPlayerLaunchAutoCollapse = null
                    playerLaunchAutoCollapseTimeoutRunnable = null
                }
            }
            playerLaunchAutoCollapseTimeoutRunnable = timeoutRunnable
            autoCollapseHandler.postDelayed(timeoutRunnable, PLAYER_LAUNCH_AUTO_COLLAPSE_TIMEOUT_MS)
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

private data class LaunchPlayerAppResult(val started: Boolean, val returnToPackage: String?)

private fun launchPlayerApp(context: Context, packageName: String): LaunchPlayerAppResult {
    val appCtx = context.applicationContext
    val exclude = setOf(appCtx.packageName, packageName)
    val returnToPackage = queryRecentForegroundPackage(appCtx, exclude)
    val launchIntent = appCtx.packageManager.getLaunchIntentForPackage(packageName)
        ?: return LaunchPlayerAppResult(false, returnToPackage)
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val started = runCatching {
        appCtx.startActivity(launchIntent)
    }.isSuccess
    return LaunchPlayerAppResult(started, returnToPackage)
}

private fun hasUsageStatsAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun queryRecentForegroundPackage(
    context: Context,
    excludePackages: Set<String>
): String? {
    if (!hasUsageStatsAccess(context)) return null
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val end = System.currentTimeMillis()
    val begin = end - 10 * 60_000L
    val events = usm.queryEvents(begin, end) ?: return null
    val event = UsageEvents.Event()
    val foregrounds = ArrayList<Pair<Long, String>>(64)
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
            foregrounds.add(event.timeStamp to event.packageName)
        }
    }
    if (foregrounds.isEmpty()) return null
    foregrounds.sortByDescending { it.first }
    for ((_, pkg) in foregrounds) {
        if (pkg in excludePackages) continue
        return pkg
    }
    return null
}

private fun restorePreviousApp(context: Context, returnToPackage: String?) {
    if (returnToPackage.isNullOrBlank()) {
        navigateHome(context)
        return
    }
    val launch = context.packageManager.getLaunchIntentForPackage(returnToPackage)
    if (launch == null) {
        navigateHome(context)
        return
    }
    launch.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    )
    val ok = runCatching { context.startActivity(launch) }.isSuccess
    if (!ok) {
        navigateHome(context)
    }
}

private fun navigateHome(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching {
        context.startActivity(intent)
    }
}
