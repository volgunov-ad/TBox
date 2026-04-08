package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.provider.Settings
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.utils.ThemeSettingsController

@Composable
internal fun DashboardThemeModeWidgetItem(
    widget: DashboardWidget,
    viewModel: TboxViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val autoFollow by viewModel.themeAutoFollowsSystem.collectAsStateWithLifecycle()

    DashboardWidgetScaffold(
        modifier = Modifier.fillMaxSize(),
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { _, resolvedTextColor ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .semantics { contentDescription = widget.title },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeModeToggleButton(
                label = stringResource(R.string.widget_theme_mode_light),
                selected = !autoFollow && currentTheme == 1,
                textColor = resolvedTextColor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = {
                    if (ThemeSettingsController.applyLightFixed(context)) {
                        TboxRepository.updateThemeAutoFollowsSystem(false)
                        TboxRepository.updateCurrentTheme(1)
                    } else {
                        TboxRepository.addLog(
                            "WARN",
                            "Theme widget",
                            "Не удалось переключить на светлую тему (нет прав на запись настроек)"
                        )
                    }
                }
            )
            ThemeModeToggleButton(
                label = stringResource(R.string.widget_theme_mode_dark),
                selected = !autoFollow && currentTheme == 2,
                textColor = resolvedTextColor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = {
                    if (ThemeSettingsController.applyDarkFixed(context)) {
                        TboxRepository.updateThemeAutoFollowsSystem(false)
                        TboxRepository.updateCurrentTheme(2)
                    } else {
                        TboxRepository.addLog(
                            "WARN",
                            "Theme widget",
                            "Не удалось переключить на тёмную тему (нет прав на запись настроек)"
                        )
                    }
                }
            )
            ThemeModeToggleButton(
                label = stringResource(R.string.widget_theme_mode_auto),
                selected = autoFollow,
                textColor = resolvedTextColor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onClick = {
                    if (ThemeSettingsController.applyFollowSystem(context)) {
                        TboxRepository.updateThemeAutoFollowsSystem(true)
                        val cr = context.contentResolver
                        val effective = runCatching {
                            val dn = Settings.System.getInt(
                                cr,
                                ThemeSettingsController.DAY_NIGHT_STATUS_KEY,
                                1
                            )
                            if (dn == 2) 2 else 1
                        }.getOrDefault(1)
                        TboxRepository.updateCurrentTheme(effective)
                    } else {
                        TboxRepository.addLog(
                            "WARN",
                            "Theme widget",
                            "Не удалось включить авто-тему (нет прав на запись настроек)"
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun ThemeModeToggleButton(
    label: String,
    selected: Boolean,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val highlight = textColor.copy(alpha = 0.22f)
    TextButton(
        onClick = onClick,
        modifier = modifier
            .then(
                if (selected) {
                    Modifier.background(highlight, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = textColor),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
