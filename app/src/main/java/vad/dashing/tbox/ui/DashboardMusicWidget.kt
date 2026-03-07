package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.SupportedMediaPlayer
import vad.dashing.tbox.orderedMediaPlayerPackages
import vad.dashing.tbox.resolveMediaPlayersForWidget
import vad.dashing.tbox.resolveSelectedMediaPlayerForWidget
import kotlin.math.abs

@Composable
fun DashboardMusicWidgetItem(
    widget: DashboardWidget,
    widgetConfig: FloatingDashboardWidgetConfig,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectedPlayerChange: (String) -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val context = LocalContext.current
    val selectedPlayers = remember(widget.dataKey, widgetConfig.mediaPlayers) {
        resolveMediaPlayersForWidget(widgetConfig)
    }
    val carouselPackages = remember(selectedPlayers) {
        orderedMediaPlayerPackages(selectedPlayers)
    }
    var selectedPackage by remember(widget.id, carouselPackages, widgetConfig.mediaSelectedPlayer) {
        mutableStateOf(resolveInitialSelectedPackage(widgetConfig, carouselPackages))
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

    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val basePlayerLabel = selectedPlayer?.let { stringResource(it.titleRes) }
        ?: stringResource(R.string.widget_music_player_none)
    val isSelectedPlayerRunning = selectedPlayerState?.hasSession == true
    val playerLabel = if (selectedPackage.isNotBlank() && !isSelectedPlayerRunning) {
        stringResource(R.string.widget_music_player_with_state_off, basePlayerLabel)
    } else {
        basePlayerLabel
    }
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
    var autoPlayTriggered by remember(widget.id) { mutableStateOf(false) }

    LaunchedEffect(widget.id, selectedPackage, widgetConfig.mediaAutoPlayOnInit) {
        if (!widgetConfig.mediaAutoPlayOnInit) return@LaunchedEffect
        if (autoPlayTriggered) return@LaunchedEffect
        if (selectedPackage.isBlank()) return@LaunchedEffect
        autoPlayTriggered = true
        val autoPlayPackage = selectedPackage
        SharedMediaControlService.play(
            context = context,
            selectedPackages = selectedPlayers,
            preferredPackage = autoPlayPackage
        )
        delay(AUTO_PLAY_VERIFY_DELAY_MS)
        val isPlaying = SharedMediaControlService.playerStates.value[autoPlayPackage]?.isPlaying == true
        if (!isPlaying) {
            SharedMediaControlService.play(
                context = context,
                selectedPackages = selectedPlayers,
                preferredPackage = autoPlayPackage
            )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = selectedPlayer?.iconRes ?: R.drawable.player_unknown),
                        contentDescription = stringResource(R.string.widget_music_player_icon),
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = playerLabel,
                        color = resolvedTextColor,
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.UNIT
                        ) * 0.8f,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                    if (carouselPackages.size > 1) {
                        Text(
                            text = "${carouselPackages.indexOf(selectedPackage).coerceAtLeast(0) + 1}/${carouselPackages.size}",
                            color = resolvedTextColor,
                            fontSize = calculateResponsiveFontSize(
                                containerHeight = availableHeight,
                                textType = TextType.UNIT
                            ) * 0.8f
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.5f)
                        .combinedClickable(
                            enabled = enableInnerInteractions,
                            onClick = {},
                            onLongClick = onLongClick,
                            onDoubleClick = {
                                openSelectedPlayer(context, selectedPackage)
                            }
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = line2Text,
                        color = if (mediaState.notificationAccessGranted) {
                            resolvedTextColor
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.5f)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            enabled = enableInnerInteractions,
                            onClick = {
                                if (!mediaState.notificationAccessGranted) {
                                    openNotificationListenerSettings(context)
                                }
                            },
                            onLongClick = onLongClick,
                            onDoubleClick = {
                                openSelectedPlayer(context, selectedPackage)
                            }
                        )
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = line3Text,
                        color = resolvedTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ),
                        maxLines = if (mediaState.notificationAccessGranted) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2.5f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MediaControlActionButton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        iconRes = R.drawable.skip_previous,
                        contentDescription = stringResource(R.string.widget_music_action_previous),
                        iconTint = resolvedTextColor,
                        actionEnabled = canSendSkip,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = {
                            SharedMediaControlService.skipToPrevious(
                                selectedPackages = selectedPlayers,
                                preferredPackage = selectedPackage
                            )
                        }
                    )
                    MediaControlActionButton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        iconRes = playPauseIcon,
                        contentDescription = stringResource(R.string.widget_music_action_play_pause),
                        iconTint = resolvedTextColor,
                        actionEnabled = canSendPlay,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = {
                            SharedMediaControlService.playPause(
                                context = context,
                                selectedPackages = selectedPlayers,
                                preferredPackage = selectedPackage
                            )
                        }
                    )
                    MediaControlActionButton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        iconRes = R.drawable.next_track,
                        contentDescription = stringResource(R.string.widget_music_action_next),
                        iconTint = resolvedTextColor,
                        actionEnabled = canSendSkip,
                        interactionEnabled = enableInnerInteractions,
                        onLongClick = onLongClick,
                        onClick = {
                            SharedMediaControlService.skipToNext(
                                selectedPackages = selectedPlayers,
                                preferredPackage = selectedPackage
                            )
                        }
                    )
                }
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
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(launchIntent)
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
private const val AUTO_PLAY_VERIFY_DELAY_MS = 2500L

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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (actionEnabled) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                }
            )
            .combinedClickable(
                enabled = interactionEnabled,
                onClick = {
                    if (actionEnabled) {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
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
