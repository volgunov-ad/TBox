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
            Text(roadMatchTuningTitle(key, isRu), style = MaterialTheme.typography.tboxTitle)
            Text(
                "$display${key.unit.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()}",
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            roadMatchTuningDescription(key, isRu),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
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

internal fun roadMatchTuningTitle(key: RoadMatchTuningKey, ru: Boolean): String {
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

internal fun roadMatchTuningDescription(key: RoadMatchTuningKey, ru: Boolean): String {
    val text = when (key) {
        RoadMatchTuningKey.MATCH_CADENCE_MS ->
            "Частота расчёта тени и matcher. Меньше — быстрее реакция, но выше нагрузка." to
                "Shadow and matcher calculation interval. Lower reacts faster but uses more CPU."
        RoadMatchTuningKey.PATH_TRIGGER_M ->
            "Новый полный поиск после такого пробега. Меньше — чаще обновление ребра." to
                "Run a full match after this distance. Lower values update the edge more often."
        RoadMatchTuningKey.TIME_TRIGGER_MS ->
            "Максимальная пауза между полными поисками, даже если машина проехала мало." to
                "Maximum delay between full matches even when little distance was travelled."
        RoadMatchTuningKey.TURN_TRIGGER_DEG ->
            "Изменение курса, которое запускает matcher досрочно. Меньше — раньше реакция на поворот." to
                "Heading change that triggers an early match. Lower reacts to turns sooner."
        RoadMatchTuningKey.MIN_SPEED_KMH ->
            "Ниже этой скорости коррекция дороги не выполняется, чтобы точка не дёргалась на месте." to
                "Road correction is paused below this speed to avoid movement while stopped."
        RoadMatchTuningKey.CANDIDATE_RADIUS_M ->
            "На каком расстоянии искать дороги. Большой радиус помогает при уходе, но добавляет ложные варианты." to
                "Distance used to search for roads. Larger helps recovery but adds false candidates."
        RoadMatchTuningKey.HEADING_TOLERANCE_DEG ->
            "Допустимое расхождение курса машины и направления новой дороги." to
                "Allowed heading difference between the vehicle and a new road candidate."
        RoadMatchTuningKey.CROSS_BLEND ->
            "Какая доля боковой ошибки убирается за коррекцию. Больше — сильнее притяжка к линии." to
                "Fraction of lateral error removed per correction. Higher snaps harder to the road."
        RoadMatchTuningKey.MAX_CROSS_STEP_M ->
            "Ограничение одного бокового сдвига. Защищает от резкого прыжка на дорогу." to
                "Maximum lateral move per correction. Limits sudden jumps onto a road."
        RoadMatchTuningKey.MAX_BEARING_STEP_DEG ->
            "Обычный максимальный поворот курса к направлению ребра за один шаг." to
                "Normal maximum heading rotation toward the edge in one correction."
        RoadMatchTuningKey.MAX_BEARING_CATCHUP_DEG ->
            "Ускоренная подтяжка курса после уверенного захвата или смены ребра." to
                "Faster heading pull after a confident lock or confirmed edge switch."
        RoadMatchTuningKey.BEARING_INHIBIT_DEG ->
            "При большем расхождении обычная подтяжка курса блокируется: вероятно, машина уже поворачивает." to
                "Normal heading pull stops above this mismatch because the vehicle may be turning away."
        RoadMatchTuningKey.HOLD_PREVIOUS_RADIUS_M ->
            "До какого бокового удаления разрешено удерживать ранее выбранную дорогу." to
                "Maximum lateral distance at which the previously selected edge may be retained."
        RoadMatchTuningKey.SWITCH_CONFIRM_COUNT ->
            "Сколько последовательных побед кандидата нужно для смены дороги. Больше — стабильнее, но медленнее." to
                "Consecutive wins required to switch roads. Higher is steadier but slower."
        RoadMatchTuningKey.BEAM_WIDTH ->
            "Сколько лучших вариантов дорог matcher хранит одновременно." to
                "Number of best road hypotheses retained by the matcher."
        RoadMatchTuningKey.MATCH_LAG_MIN_M ->
            "Минимальная точка просмотра назад при выборе дороги после развилки." to
                "Minimum look-behind distance used when ranking roads near a fork."
        RoadMatchTuningKey.MATCH_LAG_MAX_M ->
            "Максимальное отставание точки ранжирования от текущей тени." to
                "Maximum distance the ranking point may trail the live shadow."
        RoadMatchTuningKey.MATCH_LAG_SECONDS ->
            "Скоростная часть отставания: сколько секунд пути смотреть назад." to
                "Speed-based look-behind: how many seconds of travel to rank behind."
        RoadMatchTuningKey.LOOK_AHEAD_MIN_M ->
            "Минимальная дистанция прогноза по графу для следующего связного ребра." to
                "Minimum graph look-ahead distance for the next connected edge."
        RoadMatchTuningKey.LOOK_AHEAD_MAX_M ->
            "Максимальная дистанция прогноза по графу; ограничивает слишком далёкий выбор." to
                "Maximum graph look-ahead distance, limiting overly distant choices."
        RoadMatchTuningKey.LOOK_AHEAD_SECONDS ->
            "Сколько секунд движения использовать для прогноза вперёд по текущей скорости." to
                "Seconds of travel used for graph look-ahead at the current speed."
        RoadMatchTuningKey.GNSS_MAX_ACCURACY_M ->
            "Максимальная заявленная точность GNSS, при которой координате можно усиленно доверять." to
                "Maximum GNSS accuracy value that still permits stronger position trust."
        RoadMatchTuningKey.GNSS_MAX_SHADOW_GAP_M ->
            "Если живая GNSS-точка дальше от тени, доверие GNSS для выбора дороги отключается." to
                "GNSS road-choice trust is disabled when the live fix is farther from the shadow."
        RoadMatchTuningKey.GNSS_CLASS_PENALTY_RELAX ->
            "Насколько хороший GNSS ослабляет преимущество дорог высокого класса. 0 — не ослабляет, 1 — почти убирает." to
                "How much good GNSS relaxes major-road preference. 0 keeps it; 1 nearly removes it."
        RoadMatchTuningKey.LEASH_BREAK_XT_M ->
            "Боковое удаление, после которого Ordinary может отпустить обычную дорогу." to
                "Lateral distance at which Ordinary may release a normal road."
        RoadMatchTuningKey.LEASH_BREAK_YARD_XT_M ->
            "Более осторожный порог отпускания для дворовых и жилых проездов." to
                "More conservative release distance for yard and residential roads."
        RoadMatchTuningKey.LEASH_BREAK_PATH_M ->
            "Сколько нужно проехать в сторону от ребра, прежде чем разорвать поводок." to
                "Distance travelled away from the edge before the leash may break."
        RoadMatchTuningKey.JUNCTION_RADIUS_M ->
            "Радиус поиска направлений вокруг машины при распознавании сложного перекрёстка." to
                "Radius used to inspect nearby headings when detecting a complex junction."
        RoadMatchTuningKey.JUNCTION_MIN_ROADS ->
            "Минимум разных направлений, чтобы Ordinary включил свободную контрольную точку." to
                "Minimum distinct road directions required to start Ordinary's free particle."
        RoadMatchTuningKey.PROMOTE_POS_M ->
            "Разрыв между свободной и привязанной точками, при котором свободная сразу побеждает." to
                "Position gap at which the free particle immediately replaces the snapped pose."
        RoadMatchTuningKey.PROMOTE_POS_HEADING_M ->
            "Меньший разрыв позиции, достаточный при одновременном сильном расхождении курса." to
                "Smaller position gap accepted together with a large heading disagreement."
        RoadMatchTuningKey.PROMOTE_HEADING_DEG ->
            "Расхождение курсов, необходимое для принятия свободной точки по комбинированному условию." to
                "Heading disagreement required by the combined free-particle promotion rule."
        RoadMatchTuningKey.MAX_ALONG_STEP_M ->
            "Максимальная подтяжка вперёд или назад вдоль однозначного ребра за шаг." to
                "Maximum forward or backward correction along an unambiguous edge."
        RoadMatchTuningKey.PAST_END_RELEASE_M ->
            "Боковая ошибка за концом ребра, после которой matcher перестаёт тянуть к его endpoint." to
                "Lateral error beyond an edge end that stops snapping back to its endpoint."
        RoadMatchTuningKey.RAILS_HARD_SNAP_XT_M ->
            "Внутри этого расстояния Rails полностью ставит точку на линию дороги." to
                "Within this distance Rails places the published pose directly on the road."
        RoadMatchTuningKey.RAILS_SOFT_XT_M ->
            "До этого удаления Rails мягко подтягивает к линии; дальше публикует свободную точку." to
                "Rails pulls softly up to this distance, then publishes the free pose."
        RoadMatchTuningKey.RAILS_SOFT_BLEND ->
            "Сила мягкой боковой подтяжки Rails между жёсткой и внешней границами." to
                "Strength of Rails soft lateral pull between the hard and outer limits."
        RoadMatchTuningKey.RAILS_SOFT_MAX_STEP_M ->
            "Максимальный боковой шаг мягкой коррекции Rails." to
                "Maximum lateral step of a Rails soft correction."
        RoadMatchTuningKey.RAILS_BREAK_XT_M ->
            "Боковое удаление, при котором Rails может разорвать дорожный коридор." to
                "Lateral distance at which Rails may leave the road corridor."
        RoadMatchTuningKey.RAILS_BREAK_YARD_XT_M ->
            "Отдельный, обычно меньший, порог схода Rails для дворовых дорог." to
                "Separate, normally smaller, Rails corridor break limit for yard roads."
        RoadMatchTuningKey.RAILS_RELOCK_RADIUS_M ->
            "Радиус поиска дороги после схода Rails с коридора." to
                "Road search radius after Rails has left its corridor."
        RoadMatchTuningKey.RAILS_RELOCK_HEADING_DEG ->
            "Максимальное расхождение курса для повторного захвата Rails." to
                "Maximum heading mismatch allowed for Rails re-lock."
        RoadMatchTuningKey.RAILS_MIN_ADVANCE_M ->
            "Минимальный новый путь перед следующим продвижением Rails по графу." to
                "Minimum new travel distance before Rails advances on the graph again."
        RoadMatchTuningKey.RAILS_ALONG_LEASH_XT_M ->
            "Максимальная боковая ошибка, при которой разрешена продольная подтяжка Rails." to
                "Maximum lateral error that still permits Rails along-edge pull."
        RoadMatchTuningKey.RAILS_ALONG_LEASH_DEAD_M ->
            "Продольное отставание меньше этого значения не исправляется." to
                "Along-edge lag below this dead zone is not corrected."
        RoadMatchTuningKey.RAILS_ALONG_LEASH_GAIN ->
            "Доля продольного отставания, исправляемая за один шаг." to
                "Fraction of along-edge lag corrected in one step."
        RoadMatchTuningKey.RAILS_ALONG_LEASH_MAX_PULL_M ->
            "Максимальная продольная подтяжка Rails за один шаг." to
                "Maximum Rails along-edge pull in a single step."
        RoadMatchTuningKey.RAILS_NAV_PATH_FACTOR ->
            "Во сколько раз увеличить реально пройденный путь при поиске достижимого ребра." to
                "Multiplier applied to travelled distance when searching reachable graph edges."
        RoadMatchTuningKey.RAILS_NAV_PATH_SLACK_M ->
            "Дополнительный запас метров к бюджету поиска следующего ребра Rails." to
                "Extra metres added to the Rails reachable-edge search budget."
        RoadMatchTuningKey.RAILS_TURN_HINT_BIAS_DEG ->
            "Насколько поворотник смещает прогноз курса к нужной ветке в городе." to
                "Heading bias from an intentional turn signal when selecting a city branch."
        RoadMatchTuningKey.RAILS_HIGHWAY_INTENT_BIAS_DEG ->
            "Усиленное смещение по поворотнику для съезда на скоростной дороге." to
                "Stronger intentional turn-signal bias for highway exits."
        RoadMatchTuningKey.FREE_UNBIND_BEFORE_M ->
            "За сколько метров до подходящего узла FreeTurns полностью отпускает дорогу." to
                "Distance before an eligible junction where FreeTurns fully releases the road."
        RoadMatchTuningKey.FREE_REBIND_AFTER_M ->
            "Сколько проехать за узлом свободно перед повторным поиском дороги." to
                "Free travel distance beyond the junction before matching resumes."
        RoadMatchTuningKey.FREE_MIN_INCIDENT_LINES ->
            "Сколько рёбер должно сходиться в узле для свободного окна. Меньше — больше перекрёстков." to
                "Incident edges required for a free window. Lower values affect more junctions."
        RoadMatchTuningKey.FREE_BEARING_CATCHUP_DEG ->
            "Максимальная подтяжка курса FreeTurns на полном match-шаге." to
                "Maximum FreeTurns heading pull on a full matching step."
        RoadMatchTuningKey.FREE_THROTTLE_BEARING_DEG ->
            "Подтяжка курса FreeTurns между полными поисками дороги." to
                "FreeTurns heading pull between full road searches."
        RoadMatchTuningKey.FREE_THROTTLE_MAX_RESIDUAL_DEG ->
            "При большем расхождении межшаговая подтяжка отключается, чтобы не тянуть через разворот." to
                "Inter-step pull stops above this mismatch to avoid pulling through a U-turn."
    }
    return if (ru) text.first else text.second
}
