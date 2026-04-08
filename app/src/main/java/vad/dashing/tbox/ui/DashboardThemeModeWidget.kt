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
import android.widget.Toast
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
    val pkg = context.packageName
    val adbHint = ThemeSettingsController.adbGrantWriteSecureSettingsCommand(pkg)

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
                    handleThemeApply(
                        context = context,
                        outcome = ThemeSettingsController.applyLightFixed(context),
                        onSuccess = {
                            TboxRepository.updateThemeAutoFollowsSystem(false)
                            TboxRepository.updateCurrentTheme(1)
                        },
                        adbHint = adbHint,
                        deniedLogRu = "Светлая тема: нет прав на запись настроек",
                        syncUiFromDayNightIfGlobalDenied = true,
                    )
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
                    handleThemeApply(
                        context = context,
                        outcome = ThemeSettingsController.applyDarkFixed(context),
                        onSuccess = {
                            TboxRepository.updateThemeAutoFollowsSystem(false)
                            TboxRepository.updateCurrentTheme(2)
                        },
                        adbHint = adbHint,
                        deniedLogRu = "Тёмная тема: нет прав на запись настроек",
                        syncUiFromDayNightIfGlobalDenied = true,
                    )
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
                    handleThemeApply(
                        context = context,
                        outcome = ThemeSettingsController.applyFollowSystem(context),
                        onSuccess = {
                            TboxRepository.updateThemeAutoFollowsSystem(true)
                            val cr = context.contentResolver
                            val dn = ThemeSettingsController.readDayNightRaw(cr)
                            val effective = if (dn == 2) 2 else 1
                            TboxRepository.updateCurrentTheme(effective)
                        },
                        adbHint = adbHint,
                        deniedLogRu = "Авто-тема: нет прав на запись глобальных настроек (night_mode_auto)",
                        syncUiFromDayNightIfGlobalDenied = false,
                    )
                }
            )
        }
    }
}

private fun handleThemeApply(
    context: android.content.Context,
    outcome: ThemeSettingsController.ApplyOutcome,
    onSuccess: () -> Unit,
    adbHint: String,
    deniedLogRu: String,
    syncUiFromDayNightIfGlobalDenied: Boolean,
) {
    when (outcome) {
        ThemeSettingsController.ApplyOutcome.Success -> onSuccess()
        ThemeSettingsController.ApplyOutcome.GlobalDenied -> {
            if (!ThemeSettingsController.canWriteSystemSettings(context)) {
                ThemeSettingsController.openManageWriteSettingsScreen(context)
                toast(context, R.string.widget_theme_mode_toast_opening_write_settings)
            } else {
                toast(context, context.getString(R.string.widget_theme_mode_toast_need_secure_adb, adbHint))
            }
            TboxRepository.addLog("WARN", "Theme widget", deniedLogRu)
        }
        ThemeSettingsController.ApplyOutcome.SystemDenied -> {
            if (!ThemeSettingsController.canWriteSystemSettings(context)) {
                ThemeSettingsController.openManageWriteSettingsScreen(context)
                toast(context, R.string.widget_theme_mode_toast_opening_write_settings)
            } else {
                toast(context, R.string.widget_theme_mode_toast_need_write_settings)
            }
            TboxRepository.addLog("WARN", "Theme widget", "$deniedLogRu (DAY_NIGHT_STATUS)")
        }
        ThemeSettingsController.ApplyOutcome.BothDenied -> {
            if (!ThemeSettingsController.canWriteSystemSettings(context)) {
                ThemeSettingsController.openManageWriteSettingsScreen(context)
                toast(context, R.string.widget_theme_mode_toast_opening_write_settings)
            } else {
                toast(context, context.getString(R.string.widget_theme_mode_toast_need_secure_adb, adbHint))
            }
            TboxRepository.addLog("WARN", "Theme widget", deniedLogRu)
        }
    }
    if (syncUiFromDayNightIfGlobalDenied && outcome == ThemeSettingsController.ApplyOutcome.GlobalDenied) {
        val cr = context.contentResolver
        val dn = ThemeSettingsController.readDayNightRaw(cr)
        val effective = if (dn == 2) 2 else 1
        TboxRepository.updateThemeAutoFollowsSystem(false)
        TboxRepository.updateCurrentTheme(effective)
        toast(context, R.string.widget_theme_mode_toast_partial_day_night_only)
    }
}

private fun toast(context: android.content.Context, resId: Int) {
    Toast.makeText(context.applicationContext, resId, Toast.LENGTH_LONG).show()
}

private fun toast(context: android.content.Context, message: String) {
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
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
