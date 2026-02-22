package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import java.util.Locale

private val huePalette = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000)
)

fun DashboardWidget.resolveTextColorForTheme(currentTheme: Int): Color {
    return Color(if (currentTheme == 2) textColorDark else textColorLight)
}

@Composable
fun WidgetTextColorSetting(
    title: String,
    colorValue: Int,
    enabled: Boolean,
    onColorChange: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(colorValue.asColorInputText()) }
    var isInputError by remember { mutableStateOf(false) }

    LaunchedEffect(colorValue) {
        val normalizedText = colorValue.asColorInputText()
        if (!textValue.equals(normalizedText, ignoreCase = true)) {
            textValue = normalizedText
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textValue,
                onValueChange = { newText ->
                    textValue = newText
                    if (newText.isBlank()) {
                        isInputError = false
                        return@OutlinedTextField
                    }
                    val parsedColor = parseColorInput(newText)
                    isInputError = parsedColor == null
                    if (parsedColor != null) {
                        onColorChange(parsedColor)
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = enabled,
                isError = isInputError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                label = {
                    Text(stringResource(R.string.widget_color_value_label))
                }
            )

            Spacer(modifier = Modifier.size(12.dp))

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(colorValue))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }

        Text(
            text = stringResource(R.string.widget_color_value_hint),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                drawRect(
                    brush = Brush.horizontalGradient(huePalette)
                )
            }

            Slider(
                value = colorHue(colorValue),
                onValueChange = { newHue ->
                    onColorChange(updateColorHue(colorValue, newHue))
                },
                valueRange = 0f..360f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun colorHue(colorValue: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorValue, hsv)
    return hsv[0].coerceIn(0f, 360f)
}

private fun updateColorHue(colorValue: Int, newHue: Float): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorValue, hsv)
    hsv[0] = newHue.coerceIn(0f, 360f)
    if (hsv[1] <= 0f) hsv[1] = 1f
    if (hsv[2] <= 0f) hsv[2] = 1f
    return android.graphics.Color.HSVToColor(android.graphics.Color.alpha(colorValue), hsv)
}

private fun parseColorInput(value: String): Int? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return null

    return when {
        trimmed.startsWith("#") -> parseHexColor(trimmed.removePrefix("#"))
        trimmed.startsWith("0x", ignoreCase = true) -> parseHexColor(trimmed.substring(2))
        else -> trimmed.toLongOrNull()
            ?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }
            ?.toInt()
    }
}

private fun parseHexColor(rawHex: String): Int? {
    val hex = rawHex.trim()
    if (hex.length != 6 && hex.length != 8) return null

    val parsed = hex.toULongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> (0xFF000000u or parsed.toUInt()).toInt()
        8 -> parsed.toUInt().toInt()
        else -> null
    }
}

private fun Int.asColorInputText(): String {
    return String.format(Locale.US, "#%08X", this)
}
