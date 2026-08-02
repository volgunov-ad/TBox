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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DEFAULT_PANEL_SHAPE
import vad.dashing.tbox.PanelBackgroundImageStorage
import vad.dashing.tbox.R
import vad.dashing.tbox.SetTileBackgroundImageResult
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TRANSPARENT_PANEL_BACKGROUND_COLOR
import vad.dashing.tbox.normalizePanelShape
import vad.dashing.tbox.resolvePanelBackgroundColorArgb
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle

/**
 * Shared controls for whole-panel background color, image, and corner radius.
 * Used in «Вся панель», main-screen settings, and floating-panel settings.
 */
@Composable
internal fun PanelBackgroundAppearanceSettingsSection(
    panelStorageId: String,
    enabled: Boolean,
    colorThemeSegment: Int,
    onColorThemeSegmentChange: (Int) -> Unit,
    backgroundColorLight: Int?,
    backgroundColorDark: Int?,
    onBackgroundColorLightChange: (Int?) -> Unit,
    onBackgroundColorDarkChange: (Int?) -> Unit,
    backgroundImageRelPathLight: String?,
    backgroundImageRelPathDark: String?,
    onBackgroundImageRelPathLightChange: (String?) -> Unit,
    onBackgroundImageRelPathDarkChange: (String?) -> Unit,
    panelShape: Int,
    onPanelShapeChange: (Int) -> Unit,
    settingsViewModel: SettingsViewModel,
    presetSlots: List<Int>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconLookup = rememberLauncherAppIconLookup(settingsViewModel)
    val panelRevision by settingsViewModel.panelBackgroundImageRevision.collectAsStateWithLifecycle()
    val darkSegment = colorThemeSegment == 1
    val currentPath = if (darkSegment) backgroundImageRelPathDark else backgroundImageRelPathLight
    var hasImage by remember { mutableStateOf(false) }
    var removeImageLabel by remember { mutableIntStateOf(R.string.widget_panel_background_image_remove) }
    LaunchedEffect(currentPath, panelRevision, iconLookup, darkSegment) {
        hasImage = !currentPath.isNullOrBlank() &&
            PanelBackgroundImageStorage.isAllowedStoredRelPath(currentPath) &&
            PanelBackgroundImageStorage.hasResolvableFile(context.filesDir, currentPath, iconLookup)
        removeImageLabel = if (hasImage) {
            if (PanelBackgroundImageStorage.hasThemeCacheFile(context.filesDir, currentPath, iconLookup)) {
                R.string.widget_panel_background_image_remove_from_theme
            } else {
                R.string.widget_panel_background_image_remove
            }
        } else {
            R.string.widget_panel_background_image_remove
        }
    }
    val canPickImage = remember(context) {
        android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            .resolveActivity(context.packageManager) != null
    }
    var pendingPickDark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val isDark = pendingPickDark ?: darkSegment
        pendingPickDark = null
        if (uri == null) return@rememberLauncherForActivityResult
        settingsViewModel.setPanelBackgroundImageFromUri(
            panelStorageId = panelStorageId,
            darkTheme = isDark,
            sourceUri = uri,
        ) { result, relPath ->
            when (result) {
                SetTileBackgroundImageResult.Success -> {
                    if (isDark) onBackgroundImageRelPathDarkChange(relPath)
                    else onBackgroundImageRelPathLightChange(relPath)
                }
                SetTileBackgroundImageResult.DimensionsTooLarge ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_tile_background_image_too_large),
                        Toast.LENGTH_LONG,
                    ).show()
                SetTileBackgroundImageResult.NotImageOrUnreadable ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_tile_background_image_invalid),
                        Toast.LENGTH_LONG,
                    ).show()
                SetTileBackgroundImageResult.CopyFailed ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.widget_tile_background_image_copy_failed),
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_panel_background_section_title),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.widget_panel_background_section_desc),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        WidgetColorThemeSegmentRow(
            selectedSegment = colorThemeSegment,
            onSegmentSelected = onColorThemeSegmentChange,
            enabled = enabled,
        )
        WidgetColorSetting(
            title = stringResource(R.string.widget_panel_background_color_title),
            colorValue = resolvePanelBackgroundColorArgb(
                if (darkSegment) backgroundColorDark else backgroundColorLight,
            ),
            enabled = enabled,
            onColorChange = { color ->
                val stored = if (color == TRANSPARENT_PANEL_BACKGROUND_COLOR) null else color
                if (darkSegment) onBackgroundColorDarkChange(stored)
                else onBackgroundColorLightChange(stored)
            },
            presetSlots = presetSlots,
            onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
            valueTextStyle = MaterialTheme.typography.tboxTitle,
            valueLabelStyle = MaterialTheme.typography.tboxBody,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.widget_panel_background_image_title),
                style = MaterialTheme.typography.tboxButton,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.widget_panel_background_image_desc),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        if (canPickImage) {
                            pendingPickDark = darkSegment
                            pickImage.launch("image/*")
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_main_screen_wallpaper_no_picker),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.widget_panel_background_image_pick),
                        style = MaterialTheme.typography.tboxCaption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        settingsViewModel.setPanelBackgroundImageFromUri(
                            panelStorageId = panelStorageId,
                            darkTheme = darkSegment,
                            sourceUri = null,
                        ) { result, relPath ->
                            if (result == SetTileBackgroundImageResult.Success) {
                                if (darkSegment) onBackgroundImageRelPathDarkChange(relPath)
                                else onBackgroundImageRelPathLightChange(relPath)
                            }
                        }
                    },
                    enabled = enabled && hasImage,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(removeImageLabel),
                        style = MaterialTheme.typography.tboxCaption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_panel_shape, normalizePanelShape(panelShape)),
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.widget_panel_shape_hint),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = normalizePanelShape(panelShape).toFloat(),
                onValueChange = { onPanelShapeChange(normalizePanelShape(it.toInt())) },
                valueRange = DEFAULT_PANEL_SHAPE.toFloat()..50f,
                steps = 49,
                enabled = enabled,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
