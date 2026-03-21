package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel

@Composable
fun MainScreenSettingsTab(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val mainScreenPanelsList by settingsViewModel.mainScreenDashboards.collectAsStateWithLifecycle()
    val hasMainScreenPanels = mainScreenPanelsList.isNotEmpty()
    val activeMainScreenPanelId by settingsViewModel.activeMainScreenPanelId.collectAsStateWithLifecycle()
    val mainScreenPanelDeleteInProgressId by
        settingsViewModel.mainScreenPanelDeleteInProgressId.collectAsStateWithLifecycle()
    val isMainScreenPanelEnabled by settingsViewModel.isMainScreenPanelEnabled.collectAsStateWithLifecycle()
    val isMainScreenPanelClickAction by
        settingsViewModel.isMainScreenPanelClickAction.collectAsStateWithLifecycle()
    val isMainScreenPanelShowTboxDisconnectIndicator by
        settingsViewModel.isMainScreenPanelShowTboxDisconnectIndicator.collectAsStateWithLifecycle()
    val mainScreenPanelRows by settingsViewModel.mainScreenPanelRows.collectAsStateWithLifecycle()
    val mainScreenPanelCols by settingsViewModel.mainScreenPanelCols.collectAsStateWithLifecycle()

    val newMainPanelDefaultName = stringResource(R.string.floating_dashboard_new_panel_default)
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp)
    ) {
        SettingsTitle(stringResource(R.string.settings_main_screen_panels_title))
        if (hasMainScreenPanels) {
            MainScreenPanelEditor(
                panels = mainScreenPanelsList,
                selectedPanelId = activeMainScreenPanelId,
                onSelectPanelId = { panelId ->
                    settingsViewModel.saveSelectedMainScreenPanelId(panelId)
                },
                onRenamePanel = { panelId, name ->
                    settingsViewModel.saveMainScreenPanelName(panelId, name)
                },
                onAddPanel = {
                    settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName)
                },
                onDeletePanel = { panelId ->
                    settingsViewModel.deleteMainScreenPanelFromSettings(panelId)
                },
                deleteInProgressPanelId = mainScreenPanelDeleteInProgressId,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            Button(
                onClick = {
                    settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(stringResource(R.string.action_add), fontSize = 22.sp)
            }
        }
        SettingSwitch(
            isMainScreenPanelEnabled,
            { enabled ->
                settingsViewModel.saveMainScreenPanelSetting(enabled)
            },
            stringResource(R.string.settings_show_main_screen_panel_title),
            "",
            hasMainScreenPanels
        )
        SettingSwitch(
            isMainScreenPanelClickAction,
            { enabled ->
                settingsViewModel.saveMainScreenPanelClickAction(enabled)
            },
            stringResource(R.string.settings_open_app_on_panel_click_title),
            "",
            hasMainScreenPanels
        )
        SettingSwitch(
            isMainScreenPanelShowTboxDisconnectIndicator,
            { enabled ->
                settingsViewModel.saveMainScreenPanelShowTboxDisconnectIndicator(enabled)
            },
            stringResource(R.string.settings_floating_tbox_disconnect_indicator_title),
            "",
            hasMainScreenPanels
        )
        SettingDropdownGeneric(
            mainScreenPanelRows,
            { rows ->
                settingsViewModel.saveMainScreenPanelRows(rows)
            },
            stringResource(R.string.settings_floating_rows_title),
            "",
            hasMainScreenPanels,
            listOf(1, 2, 3, 4, 5, 6)
        )
        SettingDropdownGeneric(
            mainScreenPanelCols,
            { cols ->
                settingsViewModel.saveMainScreenPanelCols(cols)
            },
            stringResource(R.string.settings_floating_cols_title),
            "",
            hasMainScreenPanels,
            listOf(1, 2, 3, 4, 5, 6)
        )
        MainScreenPanelRelativeLayoutSettings(
            settingsViewModel = settingsViewModel,
            modifier = Modifier.padding(top = 8.dp),
            enabled = hasMainScreenPanels
        )
    }
}
