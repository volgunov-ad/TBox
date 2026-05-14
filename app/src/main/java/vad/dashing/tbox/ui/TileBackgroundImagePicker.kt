package vad.dashing.tbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.R
import vad.dashing.tbox.SetTileBackgroundImageResult
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TileBackgroundImageStorage

@Composable
internal fun TileBackgroundImageSettingsSection(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    panelStorageId: String,
    widgetIndex: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val darkSegment = state.advancedColorThemeSegment == 1
    val currentPath = if (darkSegment) {
        state.tileBackgroundImageRelPathDark
    } else {
        state.tileBackgroundImageRelPathLight
    }
    val hasImage = !currentPath.isNullOrBlank() &&
        TileBackgroundImageStorage.isAllowedStoredRelPath(currentPath)
    var pendingPickDark by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val isDark = pendingPickDark
        pendingPickDark = false
        if (uri == null) return@rememberLauncherForActivityResult
        settingsViewModel.setTileBackgroundImageFromUri(
            panelStorageId = panelStorageId,
            widgetIndex = widgetIndex,
            darkTheme = isDark,
            sourceUri = uri
        ) { result, relPath ->
            when (result) {
                SetTileBackgroundImageResult.Success -> {
                    if (isDark) {
                        state.tileBackgroundImageRelPathDark = relPath
                    } else {
                        state.tileBackgroundImageRelPathLight = relPath
                    }
                }
                SetTileBackgroundImageResult.DimensionsTooLarge ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_tile_background_image_too_large),
                        Toast.LENGTH_LONG
                    ).show()
                SetTileBackgroundImageResult.NotImageOrUnreadable ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_tile_background_image_invalid),
                        Toast.LENGTH_LONG
                    ).show()
                SetTileBackgroundImageResult.CopyFailed ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_tile_background_image_copy_failed),
                        Toast.LENGTH_LONG
                    ).show()
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_tile_background_image_title),
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.widget_tile_background_image_desc),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    pendingPickDark = darkSegment
                    pickImage.launch("image/*")
                },
                enabled = state.togglesEnabled,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.widget_tile_background_image_pick),
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    settingsViewModel.setTileBackgroundImageFromUri(
                        panelStorageId = panelStorageId,
                        widgetIndex = widgetIndex,
                        darkTheme = darkSegment,
                        sourceUri = null
                    ) { result, relPath ->
                        if (result == SetTileBackgroundImageResult.Success) {
                            if (darkSegment) {
                                state.tileBackgroundImageRelPathDark = relPath
                            } else {
                                state.tileBackgroundImageRelPathLight = relPath
                            }
                        }
                    }
                },
                enabled = state.togglesEnabled && hasImage,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.widget_tile_background_image_remove),
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
