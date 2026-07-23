package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import vad.dashing.tbox.ui.theme.tboxBody
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.ThemeApplyTarget

@Composable
fun ThemeApplyTargetCheckboxList(
    availableTargets: Set<ThemeApplyTarget>,
    selectedTargets: Set<ThemeApplyTarget>,
    onTargetCheckedChange: (ThemeApplyTarget, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ThemeApplyTarget.entries.forEach { target ->
            if (target !in availableTargets) return@forEach
            ThemeApplyTargetCheckboxRow(
                checked = target in selectedTargets,
                enabled = true,
                onCheckedChange = { onTargetCheckedChange(target, it) },
                label = stringResource(target.labelRes()),
            )
        }
        ThemeApplyTarget.entries.forEach { target ->
            if (target in availableTargets) return@forEach
            ThemeApplyTargetCheckboxRow(
                checked = false,
                enabled = false,
                onCheckedChange = {},
                label = stringResource(target.labelRes()),
            )
        }
    }
}

@Composable
private fun ThemeApplyTargetCheckboxRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.tboxBody,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}

private fun ThemeApplyTarget.labelRes(): Int = when (this) {
    ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS -> R.string.themes_target_main_screen_wallpapers
    ThemeApplyTarget.TILE_BACKGROUNDS -> R.string.themes_target_tile_backgrounds
    ThemeApplyTarget.APP_ICONS -> R.string.themes_target_app_icons
    ThemeApplyTarget.MAIN_SCREEN_PANELS -> R.string.themes_target_main_screen_panels
    ThemeApplyTarget.FLOATING_PANELS -> R.string.themes_target_floating_panels
}
