package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt
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
    val persisted by settingsViewModel.mockRoadMatchTuning.collectAsStateWithLifecycle()
    var tuning by remember(persisted) { mutableStateOf(persisted) }
    var group by remember { mutableStateOf(RoadMatchTuningGroup.COMMON) }
    val isRu = remember { Locale.getDefault().language.equals("ru", ignoreCase = true) }
    fun save(next: RoadMatchTuning) {
        tuning = next
        settingsViewModel.saveMockRoadMatchTuning(next)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.94f),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RoadMatchTuningGroup.entries.forEach { candidate ->
                        val selected = candidate == group
                        val label = groupLabel(candidate, isRu)
                        if (selected) {
                            Button(
                                onClick = { group = candidate },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { group = candidate },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
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
                                isRu = isRu,
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
    isRu: Boolean,
    onChange: (Double) -> Unit,
) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tuningTitle(key, isRu), style = MaterialTheme.typography.tboxTitle)
            Text(
                "$display${key.unit.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()}",
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "${key.storageName}: ${formatBound(key.minValue)}…${formatBound(key.maxValue)} " +
                "(${if (isRu) "по умолчанию" else "default"} ${formatBound(key.defaultValue)})",
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

private fun groupLabel(group: RoadMatchTuningGroup, ru: Boolean): String = when (group) {
    RoadMatchTuningGroup.COMMON -> if (ru) "Общие" else "Common"
    RoadMatchTuningGroup.ORDINARY -> "Ordinary"
    RoadMatchTuningGroup.RAILS -> "Rails"
    RoadMatchTuningGroup.FREE_TURNS -> "FreeTurns"
}

private fun tuningTitle(key: RoadMatchTuningKey, ru: Boolean): String {
    if (!ru) {
        return key.storageName
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replaceFirstChar { it.uppercase() }
    }
    return when (key) {
        RoadMatchTuningKey.MATCH_CADENCE_MS -> "Период внутреннего цикла"
        RoadMatchTuningKey.PATH_TRIGGER_M -> "Порог запуска по пути"
        RoadMatchTuningKey.TIME_TRIGGER_MS -> "Порог запуска по времени"
        RoadMatchTuningKey.TURN_TRIGGER_DEG -> "Порог запуска по повороту"
        RoadMatchTuningKey.MIN_SPEED_KMH -> "Минимальная скорость"
        RoadMatchTuningKey.CANDIDATE_RADIUS_M -> "Радиус поиска кандидатов"
        RoadMatchTuningKey.HEADING_TOLERANCE_DEG -> "Допуск курса к ребру"
        RoadMatchTuningKey.CROSS_BLEND -> "Доля поперечной подтяжки"
        RoadMatchTuningKey.MAX_CROSS_STEP_M -> "Максимальный поперечный шаг"
        RoadMatchTuningKey.MAX_BEARING_STEP_DEG -> "Обычная подтяжка курса"
        RoadMatchTuningKey.MAX_BEARING_CATCHUP_DEG -> "Быстрая подтяжка курса"
        RoadMatchTuningKey.BEARING_INHIBIT_DEG -> "Запрет подтяжки при отклонении"
        RoadMatchTuningKey.HOLD_PREVIOUS_RADIUS_M -> "Радиус удержания старого ребра"
        RoadMatchTuningKey.SWITCH_CONFIRM_COUNT -> "Подтверждений смены ребра"
        RoadMatchTuningKey.BEAM_WIDTH -> "Число гипотез"
        RoadMatchTuningKey.MATCH_LAG_MIN_M -> "Минимальное отставание ранжирования"
        RoadMatchTuningKey.MATCH_LAG_MAX_M -> "Максимальное отставание ранжирования"
        RoadMatchTuningKey.MATCH_LAG_SECONDS -> "Отставание ранжирования по времени"
        RoadMatchTuningKey.LOOK_AHEAD_MIN_M -> "Минимальный прогноз вперёд"
        RoadMatchTuningKey.LOOK_AHEAD_MAX_M -> "Максимальный прогноз вперёд"
        RoadMatchTuningKey.LOOK_AHEAD_SECONDS -> "Прогноз вперёд по времени"
        RoadMatchTuningKey.GNSS_MAX_ACCURACY_M -> "GNSS: предел точности"
        RoadMatchTuningKey.GNSS_MAX_SHADOW_GAP_M -> "GNSS: предел разрыва с тенью"
        RoadMatchTuningKey.GNSS_CLASS_PENALTY_RELAX -> "GNSS: ослабление штрафа класса"
        RoadMatchTuningKey.LEASH_BREAK_XT_M -> "Leash: отрыв поперёк"
        RoadMatchTuningKey.LEASH_BREAK_YARD_XT_M -> "Leash: отрыв во дворе"
        RoadMatchTuningKey.LEASH_BREAK_PATH_M -> "Leash: путь до отрыва"
        RoadMatchTuningKey.JUNCTION_RADIUS_M -> "Радиус анализа перекрёстка"
        RoadMatchTuningKey.JUNCTION_MIN_ROADS -> "Минимум дорог перекрёстка"
        RoadMatchTuningKey.PROMOTE_POS_M -> "Free particle: разрыв позиции"
        RoadMatchTuningKey.PROMOTE_POS_HEADING_M -> "Free particle: разрыв с курсом"
        RoadMatchTuningKey.PROMOTE_HEADING_DEG -> "Free particle: разрыв курса"
        RoadMatchTuningKey.MAX_ALONG_STEP_M -> "Максимальная подтяжка вдоль"
        RoadMatchTuningKey.PAST_END_RELEASE_M -> "Отпускание после конца ребра"
        RoadMatchTuningKey.RAILS_HARD_SNAP_XT_M -> "Rails: граница жёсткого snap"
        RoadMatchTuningKey.RAILS_SOFT_XT_M -> "Rails: граница мягкого snap"
        RoadMatchTuningKey.RAILS_SOFT_BLEND -> "Rails: доля мягкого snap"
        RoadMatchTuningKey.RAILS_SOFT_MAX_STEP_M -> "Rails: максимальный мягкий шаг"
        RoadMatchTuningKey.RAILS_BREAK_XT_M -> "Rails: отрыв от коридора"
        RoadMatchTuningKey.RAILS_BREAK_YARD_XT_M -> "Rails: отрыв во дворе"
        RoadMatchTuningKey.RAILS_RELOCK_RADIUS_M -> "Rails: радиус перезахвата"
        RoadMatchTuningKey.RAILS_RELOCK_HEADING_DEG -> "Rails: допуск курса перезахвата"
        RoadMatchTuningKey.RAILS_MIN_ADVANCE_M -> "Rails: минимум продвижения"
        RoadMatchTuningKey.RAILS_ALONG_LEASH_XT_M -> "Rails: ширина along-leash"
        RoadMatchTuningKey.RAILS_ALONG_LEASH_DEAD_M -> "Rails: мёртвая зона along-leash"
        RoadMatchTuningKey.RAILS_ALONG_LEASH_GAIN -> "Rails: сила along-leash"
        RoadMatchTuningKey.RAILS_ALONG_LEASH_MAX_PULL_M -> "Rails: предел along-leash"
        RoadMatchTuningKey.RAILS_NAV_PATH_FACTOR -> "Rails: множитель пути навигатора"
        RoadMatchTuningKey.RAILS_NAV_PATH_SLACK_M -> "Rails: запас пути навигатора"
        RoadMatchTuningKey.RAILS_TURN_HINT_BIAS_DEG -> "Rails: смещение по поворотнику"
        RoadMatchTuningKey.RAILS_HIGHWAY_INTENT_BIAS_DEG -> "Rails: смещение на трассе"
        RoadMatchTuningKey.FREE_UNBIND_BEFORE_M -> "FreeTurns: отпустить до узла"
        RoadMatchTuningKey.FREE_REBIND_AFTER_M -> "FreeTurns: привязать после узла"
        RoadMatchTuningKey.FREE_MIN_INCIDENT_LINES -> "FreeTurns: минимум линий узла"
        RoadMatchTuningKey.FREE_BEARING_CATCHUP_DEG -> "FreeTurns: подтяжка курса"
        RoadMatchTuningKey.FREE_THROTTLE_BEARING_DEG -> "FreeTurns: подтяжка между match"
        RoadMatchTuningKey.FREE_THROTTLE_MAX_RESIDUAL_DEG -> "FreeTurns: предел отклонения"
    }
}
