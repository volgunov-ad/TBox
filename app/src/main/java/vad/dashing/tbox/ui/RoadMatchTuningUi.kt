package vad.dashing.tbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.location.roadmatch.RoadMatchTuning
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningGroup
import vad.dashing.tbox.location.roadmatch.RoadMatchTuningKey
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxTitle

@Composable
fun RoadMatchTuningEntryButton(
    settingsViewModel: SettingsViewModel,
    enabled: Boolean = true,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = rememberWrappedOnClick { visible = true },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            stringResource(R.string.road_match_tuning_open),
            style = MaterialTheme.typography.tboxButton,
        )
    }
    if (visible) {
        RoadMatchTuningDialog(settingsViewModel) { visible = false }
    }
}

@Composable
private fun RoadMatchTuningDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toastImportReadError = stringResource(R.string.toast_road_match_tuning_import_read_error)
    val toastImportOk = stringResource(R.string.toast_road_match_tuning_import_ok)
    val toastImportErrorFormat = stringResource(R.string.toast_road_match_tuning_import_error_format)
    val toastImportError = stringResource(R.string.toast_road_match_tuning_import_error)
    val toastSavedTo = stringResource(R.string.toast_saved_to)
    val toastExportError = stringResource(R.string.toast_road_match_tuning_export_error)
    val persisted by settingsViewModel.mockRoadMatchTuning.collectAsStateWithLifecycle()
    var tuning by remember(persisted) { mutableStateOf(persisted) }
    var group by remember { mutableStateOf(RoadMatchTuningGroup.COMMON) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    fun save(next: RoadMatchTuning) {
        tuning = next
        settingsViewModel.saveMockRoadMatchTuning(next)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }.orEmpty()
                }.getOrElse { "" }
            }
            if (text.isBlank()) {
                Toast.makeText(
                    context,
                    toastImportReadError,
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val result = settingsViewModel.importRoadMatchTuningFromJson(text)
            if (result.isSuccess) {
                tuning = result.getOrNull() ?: tuning
                Toast.makeText(
                    context,
                    toastImportOk,
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                val msg = when (result.exceptionOrNull()?.message) {
                    "unsupported_format", "unsupported_kind", "missing_tuning" ->
                        toastImportErrorFormat
                    else ->
                        toastImportError.format(
                            result.exceptionOrNull()?.message.orEmpty(),
                        )
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.dialog_file_saving_title)) },
            text = { AppAlertDialogText(stringResource(R.string.dialog_save_road_match_tuning_downloads)) },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        scope.launch {
                            val result = settingsViewModel.exportRoadMatchTuningToDownloads(context)
                            if (result.isSuccess) {
                                Toast.makeText(
                                    context,
                                    toastSavedTo.format(
                                        result.getOrNull().orEmpty(),
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    toastExportError.format(
                                        result.exceptionOrNull()?.message.orEmpty(),
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.dialog_road_match_tuning_import_title)) },
            text = { AppAlertDialogText(stringResource(R.string.dialog_road_match_tuning_import_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showImportDialog = false
                        importLauncher.launch(arrayOf("application/json", "application/*", "*/*"))
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.road_match_tuning_import_choose_file))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.tboxDialogSurfaceFill(),
        title = {
            Column {
                Text(stringResource(R.string.road_match_tuning_title))
                Text(
                    stringResource(R.string.road_match_tuning_desc),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Two rows so 5 groups stay readable on the head unit.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        listOf(
                            RoadMatchTuningGroup.COMMON,
                            RoadMatchTuningGroup.ORDINARY,
                            RoadMatchTuningGroup.RAILS,
                        ),
                        listOf(
                            RoadMatchTuningGroup.TURN_SIGNAL,
                            RoadMatchTuningGroup.FREE_TURNS,
                        ),
                    ).forEach { rowGroups ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowGroups.forEach { candidate ->
                                val selected = candidate == group
                                val label = groupLabel(candidate)
                                if (selected) {
                                    Button(
                                        onClick = { group = candidate },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { group = candidate },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            // Keep second row visually balanced when it has fewer tabs.
                            repeat(3 - rowGroups.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { save(tuning.reset(group)) },
                        enabled = !tuning.isDefault(group),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.road_match_tuning_reset_section))
                    }
                    TextButton(
                        onClick = { save(tuning.reset()) },
                        enabled = !tuning.isDefault(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.road_match_tuning_reset_all))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(R.string.road_match_tuning_export),
                            style = MaterialTheme.typography.tboxButton,
                            maxLines = 1,
                        )
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(R.string.road_match_tuning_import),
                            style = MaterialTheme.typography.tboxButton,
                            maxLines = 1,
                        )
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    RoadMatchTuningKey.entries
                        .filter { it.group == group }
                        .forEach { key ->
                            TuningSlider(
                                key = key,
                                value = tuning[key],
                                onChange = { save(tuning.with(key, it)) },
                            )
                        }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun TuningSlider(
    key: RoadMatchTuningKey,
    value: Double,
    onChange: (Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        if (key.boolean) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    roadMatchTuningTitle(key),
                    style = MaterialTheme.typography.tboxTitle,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = value >= 0.5,
                    onCheckedChange = { onChange(if (it) 1.0 else 0.0) },
                )
            }
            Text(
                roadMatchTuningDescription(key),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            val onLabel = stringResource(R.string.road_match_tune_on)
            val offLabel = stringResource(R.string.road_match_tune_off)
            val defaultSwitch = if (key.defaultValue >= 0.5) onLabel else offLabel
            Text(
                "${key.storageName}: ${stringResource(R.string.road_match_tune_switch_values)} " +
                    "(${stringResource(R.string.road_match_tune_default_value, defaultSwitch)})",
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        val steps = (((key.maxValue - key.minValue) / key.step).roundToInt() - 1).coerceAtLeast(0)
        val display = if (key.integer) {
            value.roundToInt().toString()
        } else {
            val decimals = when {
                key.step >= 1.0 -> 0
                key.step >= 0.1 -> 1
                else -> 2
            }
            "%.${decimals}f".format(Locale.US, value)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(roadMatchTuningTitle(key), style = MaterialTheme.typography.tboxTitle)
            Text(
                "$display${key.unit.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()}",
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            roadMatchTuningDescription(key),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "${key.storageName}: ${formatBound(key.minValue)}…${formatBound(key.maxValue)} " +
                "(${stringResource(R.string.road_match_tune_default_value, formatBound(key.defaultValue))})",
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(key.normalize(it.toDouble())) },
            valueRange = key.minValue.toFloat()..key.maxValue.toFloat(),
            steps = steps,
        )
    }
}

private fun formatBound(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(Locale.US, value)

@Composable
private fun groupLabel(group: RoadMatchTuningGroup): String = stringResource(
    when (group) {
        RoadMatchTuningGroup.COMMON -> R.string.road_match_tune_group_common
        RoadMatchTuningGroup.ORDINARY -> R.string.road_match_tune_group_ordinary
        RoadMatchTuningGroup.RAILS -> R.string.road_match_tune_group_rails
        RoadMatchTuningGroup.TURN_SIGNAL -> R.string.road_match_tune_group_turn_signal
        RoadMatchTuningGroup.FREE_TURNS -> R.string.road_match_tune_group_free_turns
    },
)

@Composable
internal fun roadMatchTuningTitle(key: RoadMatchTuningKey): String =
    stringResource(roadMatchTuningTitleRes(key))

@Composable
internal fun roadMatchTuningDescription(key: RoadMatchTuningKey): String =
    stringResource(roadMatchTuningDescriptionRes(key))

/** Flavor overlay owns the wording. A new language adds the same names under its source set. */
internal fun roadMatchTuningTitleRes(key: RoadMatchTuningKey): Int = when (key) {
        RoadMatchTuningKey.MATCH_CADENCE_MS -> R.string.road_match_tune_title_match_cadence_ms
        RoadMatchTuningKey.PATH_TRIGGER_M -> R.string.road_match_tune_title_path_trigger_m
        RoadMatchTuningKey.TIME_TRIGGER_MS -> R.string.road_match_tune_title_time_trigger_ms
        RoadMatchTuningKey.TURN_TRIGGER_DEG -> R.string.road_match_tune_title_turn_trigger_deg
        RoadMatchTuningKey.MIN_SPEED_KMH -> R.string.road_match_tune_title_min_speed_kmh
        RoadMatchTuningKey.CANDIDATE_RADIUS_M -> R.string.road_match_tune_title_candidate_radius_m
        RoadMatchTuningKey.HEADING_TOLERANCE_DEG -> R.string.road_match_tune_title_heading_tolerance_deg
        RoadMatchTuningKey.CROSS_BLEND -> R.string.road_match_tune_title_cross_blend
        RoadMatchTuningKey.MAX_CROSS_STEP_M -> R.string.road_match_tune_title_max_cross_step_m
        RoadMatchTuningKey.MAX_BEARING_STEP_DEG -> R.string.road_match_tune_title_max_bearing_step_deg
        RoadMatchTuningKey.MAX_BEARING_CATCHUP_DEG -> R.string.road_match_tune_title_max_bearing_catchup_deg
        RoadMatchTuningKey.BEARING_INHIBIT_DEG -> R.string.road_match_tune_title_bearing_inhibit_deg
        RoadMatchTuningKey.HOLD_PREVIOUS_RADIUS_M -> R.string.road_match_tune_title_hold_previous_radius_m
        RoadMatchTuningKey.SWITCH_CONFIRM_COUNT -> R.string.road_match_tune_title_switch_confirm_count
        RoadMatchTuningKey.BEAM_WIDTH -> R.string.road_match_tune_title_beam_width
        RoadMatchTuningKey.MATCH_LAG_MIN_M -> R.string.road_match_tune_title_match_lag_min_m
        RoadMatchTuningKey.MATCH_LAG_MAX_M -> R.string.road_match_tune_title_match_lag_max_m
        RoadMatchTuningKey.MATCH_LAG_SECONDS -> R.string.road_match_tune_title_match_lag_seconds
        RoadMatchTuningKey.LOOK_AHEAD_MIN_M -> R.string.road_match_tune_title_look_ahead_min_m
        RoadMatchTuningKey.LOOK_AHEAD_MAX_M -> R.string.road_match_tune_title_look_ahead_max_m
        RoadMatchTuningKey.LOOK_AHEAD_SECONDS -> R.string.road_match_tune_title_look_ahead_seconds
        RoadMatchTuningKey.GNSS_MAX_ACCURACY_M -> R.string.road_match_tune_title_gnss_max_accuracy_m
        RoadMatchTuningKey.GNSS_MAX_SHADOW_GAP_M -> R.string.road_match_tune_title_gnss_max_shadow_gap_m
        RoadMatchTuningKey.GNSS_CLASS_PENALTY_RELAX -> R.string.road_match_tune_title_gnss_class_penalty_relax
        RoadMatchTuningKey.RANK_SAME_EDGE_BONUS -> R.string.road_match_tune_title_rank_same_edge_bonus
        RoadMatchTuningKey.RANK_CONNECTED_BONUS -> R.string.road_match_tune_title_rank_connected_bonus
        RoadMatchTuningKey.RANK_DISCONNECTED_PENALTY -> R.string.road_match_tune_title_rank_disconnected_penalty
        RoadMatchTuningKey.RANK_DISCONNECTED_LINK_PENALTY -> R.string.road_match_tune_title_rank_disconnected_link_penalty
        RoadMatchTuningKey.RANK_UNHINTED_LINK_PENALTY -> R.string.road_match_tune_title_rank_unhinted_link_penalty
        RoadMatchTuningKey.RANK_UNHINTED_LINK_MIN_SPEED_KMH -> R.string.road_match_tune_title_rank_unhinted_link_min_speed_kmh
        RoadMatchTuningKey.LEASH_BREAK_XT_M -> R.string.road_match_tune_title_leash_break_xt_m
        RoadMatchTuningKey.LEASH_BREAK_YARD_XT_M -> R.string.road_match_tune_title_leash_break_yard_xt_m
        RoadMatchTuningKey.LEASH_BREAK_PATH_M -> R.string.road_match_tune_title_leash_break_path_m
        RoadMatchTuningKey.JUNCTION_RADIUS_M -> R.string.road_match_tune_title_junction_radius_m
        RoadMatchTuningKey.JUNCTION_MIN_ROADS -> R.string.road_match_tune_title_junction_min_roads
        RoadMatchTuningKey.PROMOTE_POS_M -> R.string.road_match_tune_title_promote_pos_m
        RoadMatchTuningKey.PROMOTE_POS_HEADING_M -> R.string.road_match_tune_title_promote_pos_heading_m
        RoadMatchTuningKey.PROMOTE_HEADING_DEG -> R.string.road_match_tune_title_promote_heading_deg
        RoadMatchTuningKey.MAX_ALONG_STEP_M -> R.string.road_match_tune_title_max_along_step_m
        RoadMatchTuningKey.PAST_END_RELEASE_M -> R.string.road_match_tune_title_past_end_release_m
        RoadMatchTuningKey.PATH_ODO_SYNC_ENABLED -> R.string.road_match_tune_title_path_odo_sync_enabled
        RoadMatchTuningKey.PATH_ODO_SYNC_DEAD_M -> R.string.road_match_tune_title_path_odo_sync_dead_m
        RoadMatchTuningKey.PATH_ODO_SYNC_MAX_STEP_M -> R.string.road_match_tune_title_path_odo_sync_max_step_m
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_CITY -> R.string.road_match_tune_title_ordinary_stalk_unbind_city
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_HIGHWAY -> R.string.road_match_tune_title_ordinary_stalk_unbind_highway
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_INTENTIONAL_ONLY -> R.string.road_match_tune_title_ordinary_stalk_unbind_intentional_only
        RoadMatchTuningKey.ORDINARY_STALK_REBIND_AFTER_M -> R.string.road_match_tune_title_ordinary_stalk_rebind_after_m
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_MIN_SPEED_KMH -> R.string.road_match_tune_title_ordinary_stalk_unbind_min_speed_kmh
        RoadMatchTuningKey.RAILS_HARD_SNAP_XT_M -> R.string.road_match_tune_title_rails_hard_snap_xt_m
        RoadMatchTuningKey.RAILS_SOFT_XT_M -> R.string.road_match_tune_title_rails_soft_xt_m
        RoadMatchTuningKey.RAILS_SOFT_BLEND -> R.string.road_match_tune_title_rails_soft_blend
        RoadMatchTuningKey.RAILS_SOFT_MAX_STEP_M -> R.string.road_match_tune_title_rails_soft_max_step_m
        RoadMatchTuningKey.RAILS_BREAK_XT_M -> R.string.road_match_tune_title_rails_break_xt_m
        RoadMatchTuningKey.RAILS_BREAK_YARD_XT_M -> R.string.road_match_tune_title_rails_break_yard_xt_m
        RoadMatchTuningKey.RAILS_RELOCK_RADIUS_M -> R.string.road_match_tune_title_rails_relock_radius_m
        RoadMatchTuningKey.RAILS_RELOCK_HEADING_DEG -> R.string.road_match_tune_title_rails_relock_heading_deg
        RoadMatchTuningKey.RAILS_MIN_ADVANCE_M -> R.string.road_match_tune_title_rails_min_advance_m
        RoadMatchTuningKey.RAILS_ALONG_LEASH_XT_M -> R.string.road_match_tune_title_rails_along_leash_xt_m
        RoadMatchTuningKey.RAILS_ALONG_LEASH_DEAD_M -> R.string.road_match_tune_title_rails_along_leash_dead_m
        RoadMatchTuningKey.RAILS_ALONG_LEASH_GAIN -> R.string.road_match_tune_title_rails_along_leash_gain
        RoadMatchTuningKey.RAILS_ALONG_LEASH_MAX_PULL_M -> R.string.road_match_tune_title_rails_along_leash_max_pull_m
        RoadMatchTuningKey.RAILS_NAV_PATH_FACTOR -> R.string.road_match_tune_title_rails_nav_path_factor
        RoadMatchTuningKey.RAILS_NAV_PATH_SLACK_M -> R.string.road_match_tune_title_rails_nav_path_slack_m
        RoadMatchTuningKey.RAILS_TURN_HINT_BIAS_DEG -> R.string.road_match_tune_title_rails_turn_hint_bias_deg
        RoadMatchTuningKey.RAILS_HIGHWAY_INTENT_BIAS_DEG -> R.string.road_match_tune_title_rails_highway_intent_bias_deg
        RoadMatchTuningKey.TS_FORK_BIAS_ENABLED -> R.string.road_match_tune_title_ts_fork_bias_enabled
        RoadMatchTuningKey.TS_INTENTIONAL_ONLY -> R.string.road_match_tune_title_ts_intentional_only
        RoadMatchTuningKey.TS_TOWARD_MIN_DEG -> R.string.road_match_tune_title_ts_toward_min_deg
        RoadMatchTuningKey.TS_HIGHWAY_TOWARD_MIN_DEG -> R.string.road_match_tune_title_ts_highway_toward_min_deg
        RoadMatchTuningKey.TS_STRAIGHT_DEG -> R.string.road_match_tune_title_ts_straight_deg
        RoadMatchTuningKey.TS_TOWARD_BONUS -> R.string.road_match_tune_title_ts_toward_bonus
        RoadMatchTuningKey.TS_STRAIGHT_PENALTY -> R.string.road_match_tune_title_ts_straight_penalty
        RoadMatchTuningKey.TS_HIGHWAY_TOWARD_BONUS -> R.string.road_match_tune_title_ts_highway_toward_bonus
        RoadMatchTuningKey.TS_HIGHWAY_STRAIGHT_PENALTY -> R.string.road_match_tune_title_ts_highway_straight_penalty
        RoadMatchTuningKey.TS_ARC_WEIGHT -> R.string.road_match_tune_title_ts_arc_weight
        RoadMatchTuningKey.TS_MIN_FLASHES_FOR_INTENT -> R.string.road_match_tune_title_ts_min_flashes_for_intent
        RoadMatchTuningKey.TS_CONTINUOUS_STALK_MS -> R.string.road_match_tune_title_ts_continuous_stalk_ms
        RoadMatchTuningKey.TS_LATCH_HOLD_MS -> R.string.road_match_tune_title_ts_latch_hold_ms
        RoadMatchTuningKey.TS_BIAS_WITHOUT_STICKY -> R.string.road_match_tune_title_ts_bias_without_sticky
        RoadMatchTuningKey.TS_BIAS_WITHOUT_STICKY_MAX_XT_M -> R.string.road_match_tune_title_ts_bias_without_sticky_max_xt_m
        RoadMatchTuningKey.FREE_UNBIND_BEFORE_M -> R.string.road_match_tune_title_free_unbind_before_m
        RoadMatchTuningKey.FREE_REBIND_AFTER_M -> R.string.road_match_tune_title_free_rebind_after_m
        RoadMatchTuningKey.FREE_MIN_INCIDENT_LINES -> R.string.road_match_tune_title_free_min_incident_lines
        RoadMatchTuningKey.FREE_BEARING_CATCHUP_DEG -> R.string.road_match_tune_title_free_bearing_catchup_deg
        RoadMatchTuningKey.FREE_THROTTLE_BEARING_DEG -> R.string.road_match_tune_title_free_throttle_bearing_deg
        RoadMatchTuningKey.FREE_THROTTLE_MAX_RESIDUAL_DEG -> R.string.road_match_tune_title_free_throttle_max_residual_deg
        RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED -> R.string.road_match_tune_title_free_stalk_unbind_enabled
        RoadMatchTuningKey.FREE_STALK_UNBIND_INTENTIONAL_ONLY -> R.string.road_match_tune_title_free_stalk_unbind_intentional_only
        RoadMatchTuningKey.FREE_STALK_REBIND_AFTER_M -> R.string.road_match_tune_title_free_stalk_rebind_after_m
        RoadMatchTuningKey.FREE_STALK_UNBIND_BLOCK_HIGHWAY -> R.string.road_match_tune_title_free_stalk_unbind_block_highway
        RoadMatchTuningKey.FREE_STALK_UNBIND_MIN_SPEED_KMH -> R.string.road_match_tune_title_free_stalk_unbind_min_speed_kmh
}

internal fun roadMatchTuningDescriptionRes(key: RoadMatchTuningKey): Int = when (key) {
        RoadMatchTuningKey.MATCH_CADENCE_MS -> R.string.road_match_tune_desc_match_cadence_ms
        RoadMatchTuningKey.PATH_TRIGGER_M -> R.string.road_match_tune_desc_path_trigger_m
        RoadMatchTuningKey.TIME_TRIGGER_MS -> R.string.road_match_tune_desc_time_trigger_ms
        RoadMatchTuningKey.TURN_TRIGGER_DEG -> R.string.road_match_tune_desc_turn_trigger_deg
        RoadMatchTuningKey.MIN_SPEED_KMH -> R.string.road_match_tune_desc_min_speed_kmh
        RoadMatchTuningKey.CANDIDATE_RADIUS_M -> R.string.road_match_tune_desc_candidate_radius_m
        RoadMatchTuningKey.HEADING_TOLERANCE_DEG -> R.string.road_match_tune_desc_heading_tolerance_deg
        RoadMatchTuningKey.CROSS_BLEND -> R.string.road_match_tune_desc_cross_blend
        RoadMatchTuningKey.MAX_CROSS_STEP_M -> R.string.road_match_tune_desc_max_cross_step_m
        RoadMatchTuningKey.MAX_BEARING_STEP_DEG -> R.string.road_match_tune_desc_max_bearing_step_deg
        RoadMatchTuningKey.MAX_BEARING_CATCHUP_DEG -> R.string.road_match_tune_desc_max_bearing_catchup_deg
        RoadMatchTuningKey.BEARING_INHIBIT_DEG -> R.string.road_match_tune_desc_bearing_inhibit_deg
        RoadMatchTuningKey.HOLD_PREVIOUS_RADIUS_M -> R.string.road_match_tune_desc_hold_previous_radius_m
        RoadMatchTuningKey.SWITCH_CONFIRM_COUNT -> R.string.road_match_tune_desc_switch_confirm_count
        RoadMatchTuningKey.BEAM_WIDTH -> R.string.road_match_tune_desc_beam_width
        RoadMatchTuningKey.MATCH_LAG_MIN_M -> R.string.road_match_tune_desc_match_lag_min_m
        RoadMatchTuningKey.MATCH_LAG_MAX_M -> R.string.road_match_tune_desc_match_lag_max_m
        RoadMatchTuningKey.MATCH_LAG_SECONDS -> R.string.road_match_tune_desc_match_lag_seconds
        RoadMatchTuningKey.LOOK_AHEAD_MIN_M -> R.string.road_match_tune_desc_look_ahead_min_m
        RoadMatchTuningKey.LOOK_AHEAD_MAX_M -> R.string.road_match_tune_desc_look_ahead_max_m
        RoadMatchTuningKey.LOOK_AHEAD_SECONDS -> R.string.road_match_tune_desc_look_ahead_seconds
        RoadMatchTuningKey.GNSS_MAX_ACCURACY_M -> R.string.road_match_tune_desc_gnss_max_accuracy_m
        RoadMatchTuningKey.GNSS_MAX_SHADOW_GAP_M -> R.string.road_match_tune_desc_gnss_max_shadow_gap_m
        RoadMatchTuningKey.GNSS_CLASS_PENALTY_RELAX -> R.string.road_match_tune_desc_gnss_class_penalty_relax
        RoadMatchTuningKey.RANK_SAME_EDGE_BONUS -> R.string.road_match_tune_desc_rank_same_edge_bonus
        RoadMatchTuningKey.RANK_CONNECTED_BONUS -> R.string.road_match_tune_desc_rank_connected_bonus
        RoadMatchTuningKey.RANK_DISCONNECTED_PENALTY -> R.string.road_match_tune_desc_rank_disconnected_penalty
        RoadMatchTuningKey.RANK_DISCONNECTED_LINK_PENALTY -> R.string.road_match_tune_desc_rank_disconnected_link_penalty
        RoadMatchTuningKey.RANK_UNHINTED_LINK_PENALTY -> R.string.road_match_tune_desc_rank_unhinted_link_penalty
        RoadMatchTuningKey.RANK_UNHINTED_LINK_MIN_SPEED_KMH -> R.string.road_match_tune_desc_rank_unhinted_link_min_speed_kmh
        RoadMatchTuningKey.LEASH_BREAK_XT_M -> R.string.road_match_tune_desc_leash_break_xt_m
        RoadMatchTuningKey.LEASH_BREAK_YARD_XT_M -> R.string.road_match_tune_desc_leash_break_yard_xt_m
        RoadMatchTuningKey.LEASH_BREAK_PATH_M -> R.string.road_match_tune_desc_leash_break_path_m
        RoadMatchTuningKey.JUNCTION_RADIUS_M -> R.string.road_match_tune_desc_junction_radius_m
        RoadMatchTuningKey.JUNCTION_MIN_ROADS -> R.string.road_match_tune_desc_junction_min_roads
        RoadMatchTuningKey.PROMOTE_POS_M -> R.string.road_match_tune_desc_promote_pos_m
        RoadMatchTuningKey.PROMOTE_POS_HEADING_M -> R.string.road_match_tune_desc_promote_pos_heading_m
        RoadMatchTuningKey.PROMOTE_HEADING_DEG -> R.string.road_match_tune_desc_promote_heading_deg
        RoadMatchTuningKey.MAX_ALONG_STEP_M -> R.string.road_match_tune_desc_max_along_step_m
        RoadMatchTuningKey.PAST_END_RELEASE_M -> R.string.road_match_tune_desc_past_end_release_m
        RoadMatchTuningKey.PATH_ODO_SYNC_ENABLED -> R.string.road_match_tune_desc_path_odo_sync_enabled
        RoadMatchTuningKey.PATH_ODO_SYNC_DEAD_M -> R.string.road_match_tune_desc_path_odo_sync_dead_m
        RoadMatchTuningKey.PATH_ODO_SYNC_MAX_STEP_M -> R.string.road_match_tune_desc_path_odo_sync_max_step_m
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_CITY -> R.string.road_match_tune_desc_ordinary_stalk_unbind_city
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_HIGHWAY -> R.string.road_match_tune_desc_ordinary_stalk_unbind_highway
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_INTENTIONAL_ONLY -> R.string.road_match_tune_desc_ordinary_stalk_unbind_intentional_only
        RoadMatchTuningKey.ORDINARY_STALK_REBIND_AFTER_M -> R.string.road_match_tune_desc_ordinary_stalk_rebind_after_m
        RoadMatchTuningKey.ORDINARY_STALK_UNBIND_MIN_SPEED_KMH -> R.string.road_match_tune_desc_ordinary_stalk_unbind_min_speed_kmh
        RoadMatchTuningKey.RAILS_HARD_SNAP_XT_M -> R.string.road_match_tune_desc_rails_hard_snap_xt_m
        RoadMatchTuningKey.RAILS_SOFT_XT_M -> R.string.road_match_tune_desc_rails_soft_xt_m
        RoadMatchTuningKey.RAILS_SOFT_BLEND -> R.string.road_match_tune_desc_rails_soft_blend
        RoadMatchTuningKey.RAILS_SOFT_MAX_STEP_M -> R.string.road_match_tune_desc_rails_soft_max_step_m
        RoadMatchTuningKey.RAILS_BREAK_XT_M -> R.string.road_match_tune_desc_rails_break_xt_m
        RoadMatchTuningKey.RAILS_BREAK_YARD_XT_M -> R.string.road_match_tune_desc_rails_break_yard_xt_m
        RoadMatchTuningKey.RAILS_RELOCK_RADIUS_M -> R.string.road_match_tune_desc_rails_relock_radius_m
        RoadMatchTuningKey.RAILS_RELOCK_HEADING_DEG -> R.string.road_match_tune_desc_rails_relock_heading_deg
        RoadMatchTuningKey.RAILS_MIN_ADVANCE_M -> R.string.road_match_tune_desc_rails_min_advance_m
        RoadMatchTuningKey.RAILS_ALONG_LEASH_XT_M -> R.string.road_match_tune_desc_rails_along_leash_xt_m
        RoadMatchTuningKey.RAILS_ALONG_LEASH_DEAD_M -> R.string.road_match_tune_desc_rails_along_leash_dead_m
        RoadMatchTuningKey.RAILS_ALONG_LEASH_GAIN -> R.string.road_match_tune_desc_rails_along_leash_gain
        RoadMatchTuningKey.RAILS_ALONG_LEASH_MAX_PULL_M -> R.string.road_match_tune_desc_rails_along_leash_max_pull_m
        RoadMatchTuningKey.RAILS_NAV_PATH_FACTOR -> R.string.road_match_tune_desc_rails_nav_path_factor
        RoadMatchTuningKey.RAILS_NAV_PATH_SLACK_M -> R.string.road_match_tune_desc_rails_nav_path_slack_m
        RoadMatchTuningKey.RAILS_TURN_HINT_BIAS_DEG -> R.string.road_match_tune_desc_rails_turn_hint_bias_deg
        RoadMatchTuningKey.RAILS_HIGHWAY_INTENT_BIAS_DEG -> R.string.road_match_tune_desc_rails_highway_intent_bias_deg
        RoadMatchTuningKey.TS_FORK_BIAS_ENABLED -> R.string.road_match_tune_desc_ts_fork_bias_enabled
        RoadMatchTuningKey.TS_INTENTIONAL_ONLY -> R.string.road_match_tune_desc_ts_intentional_only
        RoadMatchTuningKey.TS_TOWARD_MIN_DEG -> R.string.road_match_tune_desc_ts_toward_min_deg
        RoadMatchTuningKey.TS_HIGHWAY_TOWARD_MIN_DEG -> R.string.road_match_tune_desc_ts_highway_toward_min_deg
        RoadMatchTuningKey.TS_STRAIGHT_DEG -> R.string.road_match_tune_desc_ts_straight_deg
        RoadMatchTuningKey.TS_TOWARD_BONUS -> R.string.road_match_tune_desc_ts_toward_bonus
        RoadMatchTuningKey.TS_STRAIGHT_PENALTY -> R.string.road_match_tune_desc_ts_straight_penalty
        RoadMatchTuningKey.TS_HIGHWAY_TOWARD_BONUS -> R.string.road_match_tune_desc_ts_highway_toward_bonus
        RoadMatchTuningKey.TS_HIGHWAY_STRAIGHT_PENALTY -> R.string.road_match_tune_desc_ts_highway_straight_penalty
        RoadMatchTuningKey.TS_ARC_WEIGHT -> R.string.road_match_tune_desc_ts_arc_weight
        RoadMatchTuningKey.TS_MIN_FLASHES_FOR_INTENT -> R.string.road_match_tune_desc_ts_min_flashes_for_intent
        RoadMatchTuningKey.TS_CONTINUOUS_STALK_MS -> R.string.road_match_tune_desc_ts_continuous_stalk_ms
        RoadMatchTuningKey.TS_LATCH_HOLD_MS -> R.string.road_match_tune_desc_ts_latch_hold_ms
        RoadMatchTuningKey.TS_BIAS_WITHOUT_STICKY -> R.string.road_match_tune_desc_ts_bias_without_sticky
        RoadMatchTuningKey.TS_BIAS_WITHOUT_STICKY_MAX_XT_M -> R.string.road_match_tune_desc_ts_bias_without_sticky_max_xt_m
        RoadMatchTuningKey.FREE_UNBIND_BEFORE_M -> R.string.road_match_tune_desc_free_unbind_before_m
        RoadMatchTuningKey.FREE_REBIND_AFTER_M -> R.string.road_match_tune_desc_free_rebind_after_m
        RoadMatchTuningKey.FREE_MIN_INCIDENT_LINES -> R.string.road_match_tune_desc_free_min_incident_lines
        RoadMatchTuningKey.FREE_BEARING_CATCHUP_DEG -> R.string.road_match_tune_desc_free_bearing_catchup_deg
        RoadMatchTuningKey.FREE_THROTTLE_BEARING_DEG -> R.string.road_match_tune_desc_free_throttle_bearing_deg
        RoadMatchTuningKey.FREE_THROTTLE_MAX_RESIDUAL_DEG -> R.string.road_match_tune_desc_free_throttle_max_residual_deg
        RoadMatchTuningKey.FREE_STALK_UNBIND_ENABLED -> R.string.road_match_tune_desc_free_stalk_unbind_enabled
        RoadMatchTuningKey.FREE_STALK_UNBIND_INTENTIONAL_ONLY -> R.string.road_match_tune_desc_free_stalk_unbind_intentional_only
        RoadMatchTuningKey.FREE_STALK_REBIND_AFTER_M -> R.string.road_match_tune_desc_free_stalk_rebind_after_m
        RoadMatchTuningKey.FREE_STALK_UNBIND_BLOCK_HIGHWAY -> R.string.road_match_tune_desc_free_stalk_unbind_block_highway
        RoadMatchTuningKey.FREE_STALK_UNBIND_MIN_SPEED_KMH -> R.string.road_match_tune_desc_free_stalk_unbind_min_speed_kmh
}
