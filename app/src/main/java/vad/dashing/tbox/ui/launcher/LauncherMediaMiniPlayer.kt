package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.ui.LaunchableAppEntry
import vad.dashing.tbox.ui.theme.tboxCaption

private const val LAUNCHER_MEDIA_SOURCE_ID = "launcher_mini_player"

@Composable
fun LauncherMediaMiniPlayer(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var defaultRevision by remember { mutableIntStateOf(0) }
    var pickerVisible by remember { mutableStateOf(false) }
    val playerStates by SharedMediaControlService.playerStates.collectAsStateWithLifecycle()
    val defaultPackage = remember(context, defaultRevision) {
        LauncherAppConfigStore.defaultMediaPackage(context)
    }
    val mediaPackages = remember(context, defaultRevision, playerStates) {
        (discoverAllMediaPlayerPackages(context, defaultPackage) + playerStates.keys).distinct()
    }
    val monitorPackages = remember(mediaPackages, defaultPackage) {
        buildSet {
            defaultPackage?.let { add(it) }
            addAll(mediaPackages)
        }
    }

    DisposableEffect(context, monitorPackages) {
        if (monitorPackages.isNotEmpty()) {
            SharedMediaControlService.updateSourceSelection(context, LAUNCHER_MEDIA_SOURCE_ID, monitorPackages)
        }
        onDispose {
            SharedMediaControlService.clearSourceSelection(LAUNCHER_MEDIA_SOURCE_ID)
        }
    }

    val mediaPickerApps = remember(context, mediaPackages) {
        val pm = context.packageManager
        mediaPackages.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = runCatching {
                    pm.getApplicationIcon(info).toBitmap().asImageBitmap()
                }.getOrNull()
                LaunchableAppEntry(packageName = pkg, label = label, icon = icon, activityName = null)
            }.getOrNull()
        }.sortedBy { it.label.lowercase() }
    }

    val preferredPackage = defaultPackage?.takeIf { it in monitorPackages }
        ?: mediaPackages.firstOrNull().orEmpty()
    val mediaState = remember(monitorPackages, playerStates, preferredPackage) {
        SharedMediaControlService.resolveWidgetState(
            selectedPackages = monitorPackages,
            currentStates = playerStates,
            preferredPackage = preferredPackage,
        )
    }
    val activePkg = preferredPackage.ifBlank { mediaPackages.firstOrNull().orEmpty() }
    val title = mediaState.track.ifBlank { stringResource(R.string.launcher_media_no_track) }
    val artist = mediaState.artist
    val isPlaying = mediaState.isPlaying
    val albumArtBitmap = remember(activePkg, playerStates, title) {
        runCatching {
            SharedMediaControlService.albumArtFor(activePkg)?.asImageBitmap()
        }.getOrNull()
    }

    LaunchedEffect(pickerVisible) {
        LauncherOverlayElevator.setHoldSource("media_picker", pickerVisible)
    }

    LauncherAppPickerDialog(
        visible = pickerVisible,
        title = stringResource(R.string.launcher_media_bind_player),
        apps = mediaPickerApps,
        onDismiss = { pickerVisible = false },
        onPick = { entry ->
            LauncherAppConfigStore.setDefaultMediaPackage(context, entry.packageName)
            defaultRevision++
            pickerVisible = false
        },
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LauncherColors.LeftPanelCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LauncherColors.LeftPanelBg),
                contentAlignment = Alignment.Center,
            ) {
                if (albumArtBitmap != null && mediaState.notificationAccessGranted) {
                    Image(
                        bitmap = albumArtBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text("♪", fontSize = 20.sp, color = LauncherColors.LeftTextSecondary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!mediaState.notificationAccessGranted) {
                    Text(
                        text = stringResource(R.string.widget_music_access_required),
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.AccentCyan,
                        fontSize = 11.sp,
                        maxLines = 2,
                        modifier = Modifier.clickable { openNotificationListenerSettings(context) },
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.LeftTextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (artist.isNotBlank()) {
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.tboxCaption,
                            color = LauncherColors.LeftTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (mediaState.supportsLike && mediaState.notificationAccessGranted) {
                IconButton(
                    onClick = {
                        SharedMediaControlService.toggleLike(
                            selectedPackages = monitorPackages,
                            preferredPackage = activePkg,
                        )
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (mediaState.isLiked == true) R.drawable.ic_launcher_heart
                            else R.drawable.ic_launcher_heart_outline,
                        ),
                        contentDescription = null,
                        tint = if (mediaState.isLiked == true) Color(0xFFE57373) else LauncherColors.LeftTextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(
                onClick = { pickerVisible = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.launcher_media_bind_player),
                    tint = LauncherColors.LeftTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherMediaIconButton(
                iconRes = R.drawable.skip_previous,
                enabled = mediaState.notificationAccessGranted && activePkg.isNotBlank(),
                onClick = {
                    SharedMediaControlService.skipToPrevious(
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                    )
                },
            )
            LauncherMediaIconButton(
                iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                iconSize = 28.dp,
                buttonSize = 44.dp,
                enabled = mediaState.notificationAccessGranted && activePkg.isNotBlank(),
                onClick = {
                    SharedMediaControlService.playPause(
                        context = context,
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                        launchAppIfNeeded = false,
                    )
                },
            )
            LauncherMediaIconButton(
                iconRes = R.drawable.next_track,
                enabled = mediaState.notificationAccessGranted && activePkg.isNotBlank(),
                onClick = {
                    SharedMediaControlService.skipToNext(
                        selectedPackages = monitorPackages,
                        preferredPackage = activePkg,
                    )
                },
            )
        }
    }
}

@Composable
private fun LauncherMediaIconButton(
    iconRes: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    buttonSize: androidx.compose.ui.unit.Dp = 40.dp,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) LauncherColors.LeftTextPrimary else LauncherColors.TextMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

private fun openNotificationListenerSettings(context: Context) {
    LauncherOverlayElevator.bringLauncherToFront(context)
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
