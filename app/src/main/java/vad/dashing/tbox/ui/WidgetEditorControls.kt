package vad.dashing.tbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import java.util.Locale
import kotlin.math.roundToInt

/** Rounded group of related settings with an optional heading. */
@Composable
internal fun EditorCard(
    title: String?,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(modifier = Modifier.padding(top = if (title.isNullOrBlank()) 0.dp else 8.dp)) {
                content()
            }
        }
    }
}

/** Outlined toggle button used by segments and chips; highlighted when [selected]. */
@Composable
internal fun EditorToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = rememberWrappedOnClick(onClick),
        enabled = enabled,
        modifier = modifier.heightIn(min = if (compact) 40.dp else 48.dp),
        contentPadding = if (compact) {
            PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        Text(
            text = text,
            style = if (compact) MaterialTheme.typography.tboxCaption else MaterialTheme.typography.tboxButton,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Equal-width segmented control. */
@Composable
internal fun EditorSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            EditorToggleButton(
                text = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Wrapping row of single-choice chips with an optional label above. */
@Composable
internal fun <T> EditorChoiceChips(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, text) ->
                EditorToggleButton(
                    text = text,
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    enabled = enabled,
                    compact = true,
                )
            }
        }
    }
}

/** Label with − value + buttons. */
@Composable
internal fun EditorStepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    hint: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.tboxBody,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StepperButton("−", onMinus, enabled && minusEnabled)
        Text(
            text = valueText,
            style = MaterialTheme.typography.tboxTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 6.dp),
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        StepperButton("+", onPlus, enabled && plusEnabled)
    }
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick = rememberWrappedOnClick(onClick),
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 56.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.tboxTitle)
    }
}

/** Integer stepper clamped to [minValue]..[maxValue], with optional quick presets. */
@Composable
internal fun EditorIntStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
    step: Int = 1,
    suffix: String = "",
    enabled: Boolean = true,
    hint: String? = null,
    presets: List<Pair<Int, String>> = emptyList(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        EditorStepperRow(
            label = label,
            valueText = "$value$suffix",
            onMinus = { onValueChange((value - step).coerceIn(minValue, maxValue)) },
            onPlus = { onValueChange((value + step).coerceIn(minValue, maxValue)) },
            enabled = enabled,
            minusEnabled = value > minValue,
            plusEnabled = value < maxValue,
            hint = hint,
        )
        if (presets.isNotEmpty()) {
            EditorChoiceChips(
                options = presets,
                selected = value,
                onSelect = { onValueChange(it.coerceIn(minValue, maxValue)) },
                enabled = enabled,
            )
        }
    }
}

/** Scale stepper (0.1..2.0 by 0.1 by default) shown as «1.0x». */
@Composable
internal fun EditorScaleStepper(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Float = 0.1f,
    maxValue: Float = 2.0f,
    step: Float = 0.1f,
    enabled: Boolean = true,
) {
    fun snap(v: Float): Float = ((v / step).roundToInt() * step).coerceIn(minValue, maxValue)
    EditorStepperRow(
        label = label,
        valueText = String.format(Locale.US, "%.1fx", value),
        onMinus = { onValueChange(snap(value - step)) },
        onPlus = { onValueChange(snap(value + step)) },
        modifier = modifier,
        enabled = enabled,
        minusEnabled = value > minValue + step / 2f,
        plusEnabled = value < maxValue - step / 2f,
    )
}
