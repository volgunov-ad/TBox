package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.ui.theme.scaledWidgetText
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.theme.TboxTextStyles
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.R
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.SupportedMediaPlayer
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.orderedMediaPlayerPackages
import vad.dashing.tbox.resolveMediaPlayersForWidget
import vad.dashing.tbox.resolveSelectedMediaPlayerForWidget
import vad.dashing.tbox.MusicWidgetAlbumArtDisplay
import kotlin.math.abs

@Composable
fun DashboardMusicButtonsWidgetItem(
    widget: DashboardWidget,
    widgetConfig: FloatingDashboardWidgetConfig,
    settingsViewModel: SettingsViewModel,
    canViewModel: CanDataViewModel,
    isVertical: Boolean,
    title: Boolean = true,
    titleOverride: String = "",
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectedPlayerChange: (String) -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    DashboardMusicWidgetItem(
        widget = widget,
        widgetConfig = widgetConfig,
        settingsViewModel = settingsViewModel,
        canViewModel = canViewModel,
        title = title,
        titleOverride = titleOverride,
        onClick = onClick,
        onLongClick = onLongClick,
        onSelectedPlayerChange = onSelectedPlayerChange,
        enableInnerInteractions = enableInnerInteractions,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        buttonsOnly = true,
        controlsVertical = isVertical
    )
}

@Composable
fun DashboardMusicWidgetItem(
    widget: DashboardWidget,
    widgetConfig: FloatingDashboardWidgetConfig,
    settingsViewModel: SettingsViewModel,
    canViewModel: CanDataViewModel,
    title: Boolean = true,
    titleOverride: String = "",
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectedPlayerChange: (String) -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    buttonsOnly: Boolean = false,
    controlsVertical: Boolean = false
) {
    val context = LocalContext.current
    val launcherIconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val iconLookup = rememberLauncherAppIconLookup(settingsViewModel)
    val selectedPlayers = remember(widget.dataKey, widgetConfig.mediaPlayers) {
        resolveMediaPlayersForWidget(widgetConfig)
    }
    val carouselPackages = remember(selectedPlayers) {
        orderedMediaPlayerPackages(selectedPlayers)
    }
    var selectedPackage by remember(widget.id, carouselPackages) {
        mutableStateOf(resolveInitialSelectedPackage(widgetConfig, carouselPackages))
    }
    LaunchedEffect(widget.id, carouselPackages, widgetConfig.mediaSelectedPlayer) {
        if (carouselPackages.isEmpty()) {
            if (selectedPackage.isNotEmpty()) selectedPackage = ""
            return@LaunchedEffect
        }
        // Keep current in-memory carousel choice while it is valid; delayed persistence should not
        // snap UI back and make swipe feel "blocked".
        if (selectedPackage in carouselPackages) return@LaunchedEffect
        selectedPackage = resolveInitialSelectedPackage(widgetConfig, carouselPackages)
    }
    var horizontalDragDistance by remember(widget.id, carouselPackages) {
        mutableFloatStateOf(0f)
    }
    val playerStates by SharedMediaControlService.playerStates.collectAsStateWithLifecycle()
    val mediaState = remember(selectedPlayers, playerStates, selectedPackage) {
        SharedMediaControlService.resolveWidgetState(
            selectedPackages = selectedPlayers,
            currentStates = playerStates,
            preferredPackage = selectedPackage
        )
    }
    val selectedPlayerState = remember(playerStates, selectedPackage) {
        if (selectedPackage.isBlank()) null else playerStates[selectedPackage]
    }
    val selectedPlayer = remember(selectedPackage, mediaState.player) {
        SupportedMediaPlayer.fromPackage(selectedPackage) ?: mediaState.player
    }
    val unknownAppLabel = remember(selectedPackage, context) {
        if (selectedPackage.isBlank() || SupportedMediaPlayer.fromPackage(selectedPackage) != null) {
            null
        } else {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(selectedPackage, 0)
                info.loadLabel(pm).toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val basePlayerLabel = selectedPlayer?.let { stringResource(it.titleRes) }
        ?: unknownAppLabel
        ?: stringResource(R.string.widget_music_player_none)
    val isSelectedPlayerRunning = selectedPlayerState?.hasSession == true
    val playerLabel = if (selectedPackage.isNotBlank() && !isSelectedPlayerRunning) {
        stringResource(R.string.widget_music_player_with_state_off, basePlayerLabel)
    } else {
        basePlayerLabel
    }
    val musicHeaderLabel = titleOverride.trim().ifBlank { playerLabel }
    val line2Text = if (!mediaState.notificationAccessGranted) {
        stringResource(R.string.widget_music_access_required)
    } else {
        selectedPlayerState?.artist?.ifBlank { stringResource(R.string.widget_music_no_artist) }
            ?: stringResource(R.string.widget_music_no_artist)
    }
    val line3Text = if (!mediaState.notificationAccessGranted) {
        stringResource(R.string.widget_music_open_access_settings)
    } else {
        selectedPlayerState?.track?.ifBlank { stringResource(R.string.widget_music_no_track) }
            ?: stringResource(R.string.widget_music_no_track)
    }
    val playPauseIcon = if (selectedPlayerState?.isPlaying == true) R.drawable.pause else R.drawable.play
    val canSendPlay = mediaState.notificationAccessGranted && selectedPackage.isNotBlank()
    val canSendSkip = mediaState.notificationAccessGranted && isSelectedPlayerRunning
    val isPlaying = selectedPlayerState?.isPlaying ?: mediaState.isPlaying
    val durationMs = selectedPlayerState?.durationMs ?: mediaState.durationMs
    val positionMs = selectedPlayerState?.positionMs ?: mediaState.positionMs
    val playbackSpeed = selectedPlayerState?.playbackSpeed ?: mediaState.playbackSpeed
    val positionUpdateTimeMs =
        selectedPlayerState?.positionUpdateTimeMs ?: mediaState.positionUpdateTimeMs
    var progressTick by remember(widget.id, selectedPackage) { mutableStateOf(0L) }
    val playbackProgress = remember(
        buttonsOnly,
        isPlaying,
        durationMs,
        positionMs,
        playbackSpeed,
        positionUpdateTimeMs,
        progressTick
    ) {
        if (buttonsOnly) return@remember 0f
        val estimatedPositionMs = estimatePlaybackPositionMs(
            isPlaying = isPlaying,
            durationMs = durationMs,
            positionMs = positionMs,
            playbackSpeed = playbackSpeed,
            positionUpdateTimeMs = positionUpdateTimeMs,
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime()
        )
        calculatePlaybackProgress(
            isPlaying = isPlaying,
            durationMs = durationMs,
            positionMs = estimatedPositionMs
        )
    }
    LaunchedEffect(
        widget.id,
        selectedPackage,
        widgetConfig.mediaAutoPlayOnInit,
        widgetConfig.mediaAutoPlayOnlyWhenEngineRunning,
        widgetConfig.mediaKeepPlayerForeground // anymani: учитываем опцию в зависимостях
    ) {
        if (!widgetConfig.mediaAutoPlayOnInit) return@LaunchedEffect
        if (selectedPackage.isBlank()) return@LaunchedEffect
        if (widgetConfig.mediaAutoPlayOnlyWhenEngineRunning) {
            // engineRPM uses WhileSubscribed(5000): polling .value never subscribes, so RPM may never
            // update. Collecting the flow waits for real emissions after the subscription starts.
            val engineRunningNow = (canViewModel.engineRPM.value ?: 0f) > 0f
            if (!engineRunningNow) {
                val gotPositiveRpm = withTimeoutOrNull(ENGINE_AUTO_PLAY_WAIT_MS) {
                    canViewModel.engineRPM
                        .filter { (it ?: 0f) > 0f }
                        .first()
                }
                if (gotPositiveRpm == null) {
                    return@LaunchedEffect
                }
            }
        }
        if (!TboxRepository.tryConsumeMediaAutoPlayOnce()) return@LaunchedEffect
        val autoPlayPackage = selectedPackage
        SharedMediaControlService.play(
            context = context,
            selectedPackages = selectedPlayers,
            preferredPackage = autoPlayPackage,
            keepPlayerForeground = widgetConfig.mediaKeepPlayerForeground // anymani: передаём флаг
        )
        /* Избыточный участок кода
        delay(AUTO_PLAY_VERIFY_DELAY_MS)
        val isPlaying = SharedMediaControlService.playerStates.value[autoPlayPackage]?.isPlaying == true
        if (!isPlaying) {
            SharedMediaControlService.play(
                context = context,
                selectedPackages = selectedPlayers,
                preferredPackage = autoPlayPackage,
                keepPlayerForeground = widgetConfig.mediaKeepPlayerForeground // anymani: передаём флаг
            )
        }*/
    }

    LaunchedEffect(widget.id, selectedPackage, isPlaying, durationMs, buttonsOnly) {
        progressTick = 0L
        if (buttonsOnly || !isPlaying || durationMs <= 0L) return@LaunchedEffect
        while (true) {
            delay(PROGRESS_REFRESH_INTERVAL_MS)
            progressTick += 1L
        }
    }

    DashboardWidgetScaffold(
        modifier = Modifier.then(
            if (enableInnerInteractions && carouselPackages.size > 1) {
                Modifier.pointerInput(carouselPackages, selectedPackage) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            horizontalDragDistance += dragAmount
                        },
                        onDragEnd = {
                            if (abs(horizontalDragDistance) >= CAROUSEL_SWIPE_THRESHOLD_PX) {
                                val nextPackage = resolveNextCarouselPackage(
                                    carouselPackages = carouselPackages,
                                    currentPackage = selectedPackage,
                                    moveToPrevious = horizontalDragDistance > 0f
                                )
                                if (nextPackage.isNotBlank() && nextPackage != selectedPackage) {
                                    selectedPackage = nextPackage
                                    onSelectedPlayerChange(nextPackage)
                                }
                            }
                            horizontalDragDistance = 0f
                        },
                        onDragCancel = {
                            horizontalDragDistance = 0f
                        }
                    )
                }
            } else {
                Modifier
            }
        ),
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, _ ->
        val showAlbumArtColumn = !buttonsOnly && widgetConfig.mediaShowAlbumArt
        val albumArtColumnPercent = remember(widgetConfig.mediaAlbumArtColumnWidthPercent) {
            MusicWidgetAlbumArtDisplay.normalizeAlbumArtColumnWidthPercent(
                widgetConfig.mediaAlbumArtColumnWidthPercent
            )
        }
        val albumArtOnRight = remember(widgetConfig.mediaAlbumArtSide) {
            MusicWidgetAlbumArtDisplay.normalizeAlbumArtSide(widgetConfig.mediaAlbumArtSide) ==
                MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT
        }
        val albumArt = selectedPlayerState?.albumArt
        Box(modifier = Modifier.fillMaxSize()) {
            if (showAlbumArtColumn) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val coverModifier = Modifier
                        .fillMaxHeight()
                        .weight(albumArtColumnPercent / 100f)
                        .then(
                            if (albumArtOnRight) {
                                Modifier.padding(start = 6.dp)
                            } else {
                                Modifier.padding(end = 6.dp)
                            }
                        )
                    val mainModifier = Modifier
                        .fillMaxHeight()
                        .weight((100 - albumArtColumnPercent) / 100f)
                    val cover: @Composable () -> Unit = {
                        MusicWidgetAlbumArtCover(
                            albumArt = albumArt,
                            selectedPackage = selectedPackage,
                            launcherIconRevision = launcherIconRevision,
                            iconLookup = iconLookup,
                            suppressCustomIcon = themeActivating,
                            enableInnerInteractions = enableInnerInteractions,
                            onLongClick = onLongClick,
                            onOpenPlayer = {
                                if (enableInnerInteractions) {
                                    openSelectedPlayer(context, selectedPackage)
                                }
                            },
                            modifier = coverModifier
                        )
                    }
                    val main: @Composable () -> Unit = {
                        MusicWidgetMainColumn(
                            modifier = mainModifier,
                            title = title,
                            buttonsOnly = buttonsOnly,
                            musicHeaderLabel = musicHeaderLabel,
                            selectedPackage = selectedPackage,
                            carouselPackages = carouselPackages,
                            availableHeight = availableHeight,
                            resolvedTextColor = resolvedTextColor,
                            launcherIconRevision = launcherIconRevision,
                            iconLookup = iconLookup,
                            themeActivating = themeActivating,
                            showPlayerHeaderIcon = widgetConfig.mediaShowPlayerHeaderIcon,
                            enableInnerInteractions = enableInnerInteractions,
                            onLongClick = onLongClick,
                            mediaStateNotificationAccessGranted = mediaState.notificationAccessGranted,
                            line2Text = line2Text,
                            line3Text = line3Text,
                            controlsVertical = controlsVertical,
                            playPauseIcon = playPauseIcon,
                            canSendPlay = canSendPlay,
                            canSendSkip = canSendSkip,
                            selectedPlayers = selectedPlayers,
                            keepPlayerForeground = widgetConfig.mediaKeepPlayerForeground,
                            playbackProgress = playbackProgress,
                            context = context
                        )
                    }
                    if (albumArtOnRight) {
                        main()
                        cover()
                    } else {
                        cover()
                        main()
                    }
                }
            } else {
                MusicWidgetMainColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    title = title,
                    buttonsOnly = buttonsOnly,
                    musicHeaderLabel = musicHeaderLabel,
                    selectedPackage = selectedPackage,
                    carouselPackages = carouselPackages,
                    availableHeight = availableHeight,
                    resolvedTextColor = resolvedTextColor,
                    launcherIconRevision = launcherIconRevision,
                    iconLookup = iconLookup,
                    themeActivating = themeActivating,
                    showPlayerHeaderIcon = widgetConfig.mediaShowPlayerHeaderIcon,
                    enableInnerInteractions = enableInnerInteractions,
                    onLongClick = onLongClick,
                    mediaStateNotificationAccessGranted = mediaState.notificationAccessGranted,
                    line2Text = line2Text,
                    line3Text = line3Text,
                    controlsVertical = controlsVertical,
                    playPauseIcon = playPauseIcon,
                    canSendPlay = canSendPlay,
                    canSendSkip = canSendSkip,
                    selectedPlayers = selectedPlayers,
                    keepPlayerForeground = widgetConfig.mediaKeepPlayerForeground,
                    playbackProgress = playbackProgress,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun MusicWidgetMainColumn(
    modifier: Modifier,
    title: Boolean,
    buttonsOnly: Boolean,
    musicHeaderLabel: String,
    selectedPackage: String,
    carouselPackages: List<String>,
    availableHeight: Dp,
    resolvedTextColor: Color,
    launcherIconRevision: Int,
    iconLookup: LauncherAppIconPaths.Lookup,
    themeActivating: Boolean,
    showPlayerHeaderIcon: Boolean,
    enableInnerInteractions: Boolean,
    onLongClick: () -> Unit,
    mediaStateNotificationAccessGranted: Boolean,
    line2Text: String,
    line3Text: String,
    controlsVertical: Boolean,
    playPauseIcon: Int,
    canSendPlay: Boolean,
    canSendSkip: Boolean,
    selectedPlayers: Set<String>,
    keepPlayerForeground: Boolean,
    playbackProgress: Float,
    context: Context,
) {
    Column(modifier = modifier) {
        if (title) {
            MusicWidgetPlayerHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                musicHeaderLabel = musicHeaderLabel,
                selectedPackage = selectedPackage,
                carouselPackages = carouselPackages,
                availableHeight = availableHeight,
                resolvedTextColor = resolvedTextColor,
                launcherIconRevision = launcherIconRevision,
                iconLookup = iconLookup,
                suppressCustomIcon = themeActivating,
                showPlayerIcon = showPlayerHeaderIcon,
            )
        }

        if (!buttonsOnly) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
                    .combinedClickableWithSound(
                        enabled = enableInnerInteractions,
                        onClick = {},
                        onLongClick = onLongClick,
                        onDoubleClick = {
                            if (enableInnerInteractions) {
                                openSelectedPlayer(context, selectedPackage)
                            }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!title) {
                    MusicWidgetPlayerAvatar(
                        selectedPackage = selectedPackage,
                        launcherIconRevision = launcherIconRevision,
                        iconLookup = iconLookup,
                        suppressCustomIcon = themeActivating,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    )
                }
                Text(
                    text = line2Text,
                    color = if (mediaStateNotificationAccessGranted) {
                        resolvedTextColor
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = calculateResponsiveTextStyle(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = LocalWidgetTextAlign.current,
                    modifier = Modifier.weight(1f)
                )
                if (!title) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickableWithSound(
                        enabled = enableInnerInteractions,
                        onClick = {
                            if (!mediaStateNotificationAccessGranted) {
                                openNotificationListenerSettings(context)
                            }
                        },
                        onLongClick = onLongClick,
                        onDoubleClick = {
                            if (enableInnerInteractions) {
                                openSelectedPlayer(context, selectedPackage)
                            }
                        }
                    )
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = line3Text,
                    color = resolvedTextColor,
                    style = calculateResponsiveTextStyle(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    maxLines = if (mediaStateNotificationAccessGranted) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = LocalWidgetTextAlign.current
                )
            }
        }

        MusicPlaybackControlButtons(
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (buttonsOnly && !title) 1f else 2.2f),
            isVertical = controlsVertical,
            playPauseIcon = playPauseIcon,
            canSendPlay = canSendPlay,
            canSendSkip = canSendSkip,
            interactionEnabled = enableInnerInteractions,
            onLongClick = onLongClick,
            onPrevious = {
                SharedMediaControlService.skipToPrevious(
                    selectedPackages = selectedPlayers,
                    preferredPackage = selectedPackage
                )
            },
            onPlayPause = {
                SharedMediaControlService.playPause(
                    context = context,
                    selectedPackages = selectedPlayers,
                    preferredPackage = selectedPackage,
                    keepPlayerForeground = keepPlayerForeground
                )
            },
            onNext = {
                SharedMediaControlService.skipToNext(
                    selectedPackages = selectedPlayers,
                    preferredPackage = selectedPackage
                )
            }
        )

        if (!buttonsOnly) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(playbackProgress)
                        .background(resolvedTextColor)
                )
            }
        }
    }
}

@Composable
private fun MusicWidgetAlbumArtCover(
    albumArt: ImageBitmap?,
    selectedPackage: String,
    launcherIconRevision: Int,
    iconLookup: LauncherAppIconPaths.Lookup,
    suppressCustomIcon: Boolean,
    enableInnerInteractions: Boolean,
    onLongClick: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickableWithSound(
                enabled = enableInnerInteractions,
                onClick = onOpenPlayer,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center
    ) {
        if (albumArt != null) {
            Image(
                bitmap = albumArt,
                contentDescription = stringResource(R.string.widget_music_album_art),
                modifier = Modifier.fillMaxSize(),
                // Fit: letterbox/pillarbox with transparent bars so tile bg / image shows through.
                contentScale = ContentScale.Fit
            )
        } else {
            MusicWidgetPlayerAvatar(
                selectedPackage = selectedPackage,
                launcherIconRevision = launcherIconRevision,
                iconLookup = iconLookup,
                suppressCustomIcon = suppressCustomIcon,
                modifier = Modifier
                    .fillMaxHeight(0.7f)
                    .aspectRatio(1f)
            )
        }
    }
}

@Composable
private fun MusicWidgetPlayerHeader(
    modifier: Modifier,
    musicHeaderLabel: String,
    selectedPackage: String,
    carouselPackages: List<String>,
    availableHeight: Dp,
    resolvedTextColor: Color,
    launcherIconRevision: Int,
    iconLookup: LauncherAppIconPaths.Lookup,
    suppressCustomIcon: Boolean,
    showPlayerIcon: Boolean,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showPlayerIcon) {
            MusicWidgetPlayerAvatar(
                selectedPackage = selectedPackage,
                launcherIconRevision = launcherIconRevision,
                iconLookup = iconLookup,
                suppressCustomIcon = suppressCustomIcon,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )
        }
        Text(
            text = musicHeaderLabel,
            color = resolvedTextColor,
            style = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = TextType.UNIT
            ).scaledWidgetText(0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (showPlayerIcon) 8.dp else 0.dp)
        )
        if (carouselPackages.size > 1) {
            Text(
                text = "${carouselPackages.indexOf(selectedPackage).coerceAtLeast(0) + 1}/${carouselPackages.size}",
                color = resolvedTextColor,
                style = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.UNIT
                ).scaledWidgetText(0.8f)
            )
        }
    }
}

@Composable
private fun MusicPlaybackControlButtons(
    modifier: Modifier,
    isVertical: Boolean,
    playPauseIcon: Int,
    canSendPlay: Boolean,
    canSendSkip: Boolean,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val iconTint = LocalWidgetControlAppearance.current.inactiveContent
    if (isVertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MediaControlActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                iconRes = R.drawable.skip_previous,
                contentDescription = stringResource(R.string.widget_music_action_previous),
                iconTint = iconTint,
                actionEnabled = canSendSkip,
                interactionEnabled = interactionEnabled,
                onLongClick = onLongClick,
                onClick = onPrevious
            )
            MediaControlActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                iconRes = playPauseIcon,
                contentDescription = stringResource(R.string.widget_music_action_play_pause),
                iconTint = iconTint,
                actionEnabled = canSendPlay,
                interactionEnabled = interactionEnabled,
                onLongClick = onLongClick,
                onClick = onPlayPause
            )
            MediaControlActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                iconRes = R.drawable.next_track,
                contentDescription = stringResource(R.string.widget_music_action_next),
                iconTint = iconTint,
                actionEnabled = canSendSkip,
                interactionEnabled = interactionEnabled,
                onLongClick = onLongClick,
                onClick = onNext
            )
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaControlActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                iconRes = R.drawable.skip_previous,
                contentDescription = stringResource(R.string.widget_music_action_previous),
                iconTint = iconTint,
                actionEnabled = canSendSkip,
                interactionEnabled = interactionEnabled,
                onLongClick = onLongClick,
                onClick = onPrevious
            )
            MediaControlActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                iconRes = playPauseIcon,
                contentDescription = stringResource(R.string.widget_music_action_play_pause),
                iconTint = iconTint,
                actionEnabled = canSendPlay,
                interactionEnabled = interactionEnabled,
                onLongClick = onLongClick,
                onClick = onPlayPause
            )
            MediaControlActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                iconRes = R.drawable.next_track,
                contentDescription = stringResource(R.string.widget_music_action_next),
                iconTint = iconTint,
                actionEnabled = canSendSkip,
                interactionEnabled = interactionEnabled,
                onLongClick = onLongClick,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun MusicWidgetPlayerAvatar(
    selectedPackage: String,
    launcherIconRevision: Int,
    iconLookup: LauncherAppIconPaths.Lookup,
    suppressCustomIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconSizePx = remember(context) {
        (48f * context.resources.displayMetrics.density).toInt().coerceIn(32, 96)
    }
    val enumPlayer = remember(selectedPackage) {
        SupportedMediaPlayer.fromPackage(selectedPackage)
    }
    val appIcon = remember(selectedPackage, context, launcherIconRevision, iconSizePx, iconLookup, suppressCustomIcon) {
        if (selectedPackage.isBlank() || enumPlayer != null) {
            null
        } else if (!suppressCustomIcon) {
            decodeLauncherAppCustomIconIfPresent(context, selectedPackage, iconSizePx, iconLookup)
                ?: runCatching {
                    val pm = context.packageManager
                    val info = pm.getApplicationInfo(selectedPackage, 0)
                    info.loadIcon(pm).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
                }.getOrNull()
        } else {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(selectedPackage, 0)
                info.loadIcon(pm).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
            }.getOrNull()
        }
    }
    val clip = Modifier.clip(RoundedCornerShape(4.dp))
    when {
        enumPlayer != null -> {
            Icon(
                painter = painterResource(id = enumPlayer.iconRes),
                contentDescription = stringResource(R.string.widget_music_player_icon),
                tint = Color.Unspecified,
                modifier = modifier.then(clip)
            )
        }
        appIcon != null -> {
            Image(
                bitmap = appIcon,
                contentDescription = stringResource(R.string.widget_music_player_icon),
                modifier = modifier.then(clip),
                contentScale = ContentScale.Fit
            )
        }
        else -> {
            Icon(
                painter = painterResource(id = R.drawable.player_unknown),
                contentDescription = stringResource(R.string.widget_music_player_icon),
                tint = Color.Unspecified,
                modifier = modifier.then(clip)
            )
        }
    }
}

private fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }
}

private fun openSelectedPlayer(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    val launchPackage = resolvePlayerLaunchPackage(packageName)
    val launchIntent = context.packageManager.getLaunchIntentForPackage(launchPackage)
        ?: if (launchPackage != packageName) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            null
        }
        ?: return
    MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
    runCatching {
        context.startActivity(launchIntent)
    }
}

internal fun resolvePlayerLaunchPackage(packageName: String): String {
    return when (SupportedMediaPlayer.fromPackage(packageName)) {
        SupportedMediaPlayer.BLUETOOTH_PHONE -> "com.wt.multimedia.local"
        else -> packageName
    }
}

internal fun resolveInitialSelectedPackage(
    widgetConfig: FloatingDashboardWidgetConfig,
    carouselPackages: List<String>
): String {
    if (carouselPackages.isEmpty()) return ""
    val selectedFromSettings = resolveSelectedMediaPlayerForWidget(widgetConfig)
    return if (selectedFromSettings in carouselPackages) {
        selectedFromSettings
    } else {
        carouselPackages.first()
    }
}

internal fun resolveNextCarouselPackage(
    carouselPackages: List<String>,
    currentPackage: String,
    moveToPrevious: Boolean
): String {
    if (carouselPackages.isEmpty()) return ""
    val currentIndex = carouselPackages.indexOf(currentPackage).takeIf { it >= 0 } ?: 0
    val nextIndex = if (moveToPrevious) {
        if (currentIndex == 0) carouselPackages.lastIndex else currentIndex - 1
    } else {
        if (currentIndex == carouselPackages.lastIndex) 0 else currentIndex + 1
    }
    return carouselPackages[nextIndex]
}

internal const val CAROUSEL_SWIPE_THRESHOLD_PX = 80f
private const val AUTO_PLAY_VERIFY_DELAY_MS = 3500L
private const val ENGINE_AUTO_PLAY_WAIT_MS = 120_000L
private const val PROGRESS_REFRESH_INTERVAL_MS = 5000L

internal fun estimatePlaybackPositionMs(
    isPlaying: Boolean,
    durationMs: Long,
    positionMs: Long,
    playbackSpeed: Float,
    positionUpdateTimeMs: Long,
    nowElapsedRealtimeMs: Long
): Long {
    val basePositionMs = positionMs.coerceAtLeast(0L)
    if (!isPlaying) return basePositionMs
    if (durationMs <= 0L || basePositionMs <= 0L) return 0L
    val safeUpdateTimeMs = if (positionUpdateTimeMs > 0L) {
        positionUpdateTimeMs
    } else {
        nowElapsedRealtimeMs
    }
    val elapsedSinceUpdateMs = (nowElapsedRealtimeMs - safeUpdateTimeMs).coerceAtLeast(0L)
    val predictedPositionMs = basePositionMs + (elapsedSinceUpdateMs * playbackSpeed.coerceAtLeast(0f)).toLong()
    return predictedPositionMs.coerceIn(0L, durationMs)
}

internal fun calculatePlaybackProgress(
    isPlaying: Boolean,
    durationMs: Long,
    positionMs: Long
): Float {
    if (!isPlaying) return 0f
    if (durationMs <= 0L || positionMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

@Composable
private fun MediaControlActionButton(
    modifier: Modifier,
    iconRes: Int,
    contentDescription: String,
    iconTint: Color,
    actionEnabled: Boolean,
    interactionEnabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val controls = LocalWidgetControlAppearance.current
    WidgetControlChrome(
        background = controls.inactiveBackground,
        shapeDp = controls.shapeDp,
        modifier = modifier
            .combinedClickableWithSound(
                enabled = interactionEnabled,
                onClick = {
                    if (actionEnabled) {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            ),
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = if (actionEnabled) iconTint else iconTint.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxHeight(0.72f)
                .aspectRatio(1f)
        )
    }
}
