package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.ClimateControlType
import vad.dashing.tbox.GlobalCruiseControlType
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

/**
 * First-run / re-open setup: HU type, TBox connect, cruise type, climate type.
 * Dismiss persists the current (defaulted) choices.
 */
@Composable
fun VehicleFeatureSetupDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: (
        cruise: GlobalCruiseControlType,
        climate: ClimateControlType,
    ) -> Unit,
) {
    val headUnitCanMode by settingsViewModel.headUnitCanMode.collectAsStateWithLifecycle()
    val noTboxConnect by settingsViewModel.noTboxConnect.collectAsStateWithLifecycle()
    val storedCruise by settingsViewModel.cruiseControlType.collectAsStateWithLifecycle()
    val storedClimate by settingsViewModel.climateControlType.collectAsStateWithLifecycle()

    var cruise by remember { mutableStateOf(storedCruise) }
    var climate by remember { mutableStateOf(storedClimate) }
    // Positive «Подключаться к TBox» = !noTboxConnect
    var connectToTbox by remember { mutableStateOf(!noTboxConnect) }

    fun finish() {
        settingsViewModel.saveHeadUnitCanMode(headUnitCanMode)
        settingsViewModel.saveNoTboxConnectSetting(!connectToTbox)
        onDismiss(cruise, climate)
    }

    AlertDialog(
        onDismissRequest = { finish() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.tboxDialogSurface(),
        title = {
            AppAlertDialogTitle(stringResource(R.string.settings_vehicle_feature_setup_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = tboxDialogScrollBodyMaxHeight())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_vehicle_feature_setup_intro),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SettingsTitle(stringResource(R.string.settings_hu_type_title))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeButton(
                        text = stringResource(R.string.settings_hu_type_android9),
                        isSelected = headUnitCanMode == HeadUnitCanMode.Android9MbCan,
                        onClick = {
                            settingsViewModel.saveHeadUnitCanMode(HeadUnitCanMode.Android9MbCan)
                        },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        text = stringResource(R.string.settings_hu_type_android10),
                        isSelected = headUnitCanMode == HeadUnitCanMode.Android10Vhal,
                        onClick = {
                            settingsViewModel.saveHeadUnitCanMode(HeadUnitCanMode.Android10Vhal)
                        },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                SettingSwitch(
                    isChecked = connectToTbox,
                    onCheckedChange = { connectToTbox = it },
                    text = stringResource(R.string.settings_vehicle_feature_setup_connect_tbox),
                    description = stringResource(
                        R.string.settings_vehicle_feature_setup_connect_tbox_desc,
                    ),
                    enabled = true,
                )

                SettingsTitle(stringResource(R.string.settings_cruise_type_title))
                Text(
                    text = stringResource(R.string.settings_cruise_type_desc),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeButton(
                        text = stringResource(R.string.settings_cruise_type_ccs),
                        isSelected = cruise == GlobalCruiseControlType.CCS,
                        onClick = { cruise = GlobalCruiseControlType.CCS },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        text = stringResource(R.string.settings_cruise_type_acc),
                        isSelected = cruise == GlobalCruiseControlType.ACC,
                        onClick = { cruise = GlobalCruiseControlType.ACC },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                SettingsTitle(stringResource(R.string.settings_climate_type_title))
                Text(
                    text = stringResource(R.string.settings_climate_type_desc),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeButton(
                        text = stringResource(R.string.settings_climate_type_ordinary),
                        isSelected = climate == ClimateControlType.ORDINARY_AC,
                        onClick = { climate = ClimateControlType.ORDINARY_AC },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        text = stringResource(R.string.settings_climate_type_single),
                        isSelected = climate == ClimateControlType.SINGLE_ZONE,
                        onClick = { climate = ClimateControlType.SINGLE_ZONE },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        text = stringResource(R.string.settings_climate_type_dual),
                        isSelected = climate == ClimateControlType.DUAL_ZONE,
                        onClick = { climate = ClimateControlType.DUAL_ZONE },
                        enabled = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { finish() }) {
                Text(
                    text = stringResource(R.string.settings_vehicle_feature_setup_done),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        },
    )
}
