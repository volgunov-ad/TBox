package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.SharedMediaControlService
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
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    backgroundTransparent: Boolean = false,
    textColor: Color? = null
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

    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val playerLabel = mediaState.player?.let { stringResource(it.titleRes) }
        ?: stringResource(R.string.widget_music_player_none)
    val artist = mediaState.artist.ifBlank { stringResource(R.string.widget_music_no_artist) }
    val track = mediaState.track.ifBlank { stringResource(R.string.widget_music_no_track) }
    val playPauseIcon = if (mediaState.isPlaying) R.drawable.pause else R.drawable.play

    Card(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(carouselPackages, selectedPackage) {
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (backgroundTransparent) Color.Transparent else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(shape)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(shape)
                )
        ) {
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = mediaState.player?.iconRes ?: R.drawable.player_unknown),
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
                            textType = TextType.TITLE
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                    )
                }
                if (carouselPackages.size > 1) {
                    Text(
                        text = "${carouselPackages.indexOf(selectedPackage).coerceAtLeast(0) + 1}/${carouselPackages.size}",
                        color = resolvedTextColor.copy(alpha = 0.8f),
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ) * 0.85f
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = artist,
                        color = resolvedTextColor.copy(alpha = 0.9f),
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = track,
                        color = resolvedTextColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.VALUE
                        ) * 0.7f,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!mediaState.notificationAccessGranted) {
                    Text(
                        text = stringResource(R.string.widget_music_access_required),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = calculateResponsiveFontSize(
                            containerHeight = availableHeight,
                            textType = TextType.TITLE
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { openNotificationListenerSettings(context) }
                    ) {
                        Text(
                            text = stringResource(R.string.widget_music_open_access_settings),
                            color = resolvedTextColor
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            SharedMediaControlService.skipToPrevious(
                                selectedPackages = selectedPlayers,
                                preferredPackage = selectedPackage
                            )
                        },
                        enabled = mediaState.controlsAvailable
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.skip_previous),
                            contentDescription = stringResource(R.string.widget_music_action_previous),
                            tint = resolvedTextColor
                        )
                    }
                    IconButton(
                        onClick = {
                            SharedMediaControlService.playPause(
                                selectedPackages = selectedPlayers,
                                preferredPackage = selectedPackage
                            )
                        },
                        enabled = mediaState.controlsAvailable
                    ) {
                        Icon(
                            painter = painterResource(id = playPauseIcon),
                            contentDescription = stringResource(R.string.widget_music_action_play_pause),
                            tint = resolvedTextColor
                        )
                    }
                    IconButton(
                        onClick = {
                            SharedMediaControlService.skipToNext(
                                selectedPackages = selectedPlayers,
                                preferredPackage = selectedPackage
                            )
                        },
                        enabled = mediaState.controlsAvailable
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.next_track),
                            contentDescription = stringResource(R.string.widget_music_action_next),
                            tint = resolvedTextColor
                        )
                    }
                }
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

private fun resolveInitialSelectedPackage(
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

private fun resolveNextCarouselPackage(
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

private const val CAROUSEL_SWIPE_THRESHOLD_PX = 80f
