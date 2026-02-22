package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import java.util.Locale
import kotlin.math.roundToInt

private val colorPalette = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFF0000.toInt(),
    0xFFFFFF00.toInt(),
    0xFF00FF00.toInt(),
    0xFF00FFFF.toInt(),
    0xFF0000FF.toInt(),
    0xFFFF00FF.toInt(),
    0xFF000000.toInt()
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
    var localColorValue by remember { mutableIntStateOf(colorValue) }
    var textValue by remember { mutableStateOf(localColorValue.asColorInputText()) }
    var isInputError by remember { mutableStateOf(false) }

    LaunchedEffect(colorValue) {
        if (localColorValue != colorValue) {
            localColorValue = colorValue
        }
    }

    LaunchedEffect(localColorValue) {
        val normalizedText = localColorValue.asColorInputText()
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
                        if (parsedColor != localColorValue) {
                            localColorValue = parsedColor
                            onColorChange(parsedColor)
                        }
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
                    .background(Color(localColorValue))
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
                    brush = Brush.horizontalGradient(colorPalette.map { Color(it) })
                )
            }

            Slider(
                value = colorSliderPosition(localColorValue),
                onValueChange = { palettePosition ->
                    val updatedColor = colorFromPalette(
                        position = palettePosition,
                        alpha = colorAlpha(localColorValue)
                    )
                    if (updatedColor != localColorValue) {
                        localColorValue = updatedColor
                        onColorChange(updatedColor)
                    }
                },
                valueRange = 0f..1f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val alphaPercent = ((colorAlpha(localColorValue) / 255f) * 100f).roundToInt()
        Text(
            text = stringResource(R.string.widget_color_alpha, alphaPercent),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val opaqueColor = withAlpha(localColorValue, 255)
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
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(opaqueColor).copy(alpha = 0f),
                            Color(opaqueColor).copy(alpha = 1f)
                        )
                    )
                )
            }

            Slider(
                value = colorAlpha(localColorValue).toFloat(),
                onValueChange = { newAlpha ->
                    val updatedColor = withAlpha(localColorValue, newAlpha.roundToInt())
                    if (updatedColor != localColorValue) {
                        localColorValue = updatedColor
                        onColorChange(updatedColor)
                    }
                },
                valueRange = 0f..255f,
                steps = 254,
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

private fun colorFromPalette(position: Float, alpha: Int): Int {
    val clampedPosition = position.coerceIn(0f, 1f)
    val lastIndex = colorPalette.lastIndex
    val scaled = clampedPosition * lastIndex
    val startIndex = scaled.toInt().coerceIn(0, lastIndex)
    val endIndex = (startIndex + 1).coerceAtMost(lastIndex)
    val fraction = scaled - startIndex

    val startColor = colorPalette[startIndex]
    val endColor = colorPalette[endIndex]

    val red = lerpChannel(
        android.graphics.Color.red(startColor),
        android.graphics.Color.red(endColor),
        fraction
    )
    val green = lerpChannel(
        android.graphics.Color.green(startColor),
        android.graphics.Color.green(endColor),
        fraction
    )
    val blue = lerpChannel(
        android.graphics.Color.blue(startColor),
        android.graphics.Color.blue(endColor),
        fraction
    )

    return android.graphics.Color.argb(alpha.coerceIn(0, 255), red, green, blue)
}

private fun colorSliderPosition(colorValue: Int): Float {
    val targetColor = withAlpha(colorValue, 255)
    var bestPosition = 0f
    var bestDistance = Int.MAX_VALUE
    val samples = 200

    for (index in 0..samples) {
        val candidatePosition = index / samples.toFloat()
        val candidateColor = withAlpha(colorFromPalette(candidatePosition, 255), 255)
        val distance = colorDistanceSquared(targetColor, candidateColor)
        if (distance < bestDistance) {
            bestDistance = distance
            bestPosition = candidatePosition
        }
    }

    return bestPosition
}

private fun colorDistanceSquared(firstColor: Int, secondColor: Int): Int {
    val redDiff = android.graphics.Color.red(firstColor) - android.graphics.Color.red(secondColor)
    val greenDiff = android.graphics.Color.green(firstColor) - android.graphics.Color.green(secondColor)
    val blueDiff = android.graphics.Color.blue(firstColor) - android.graphics.Color.blue(secondColor)
    return redDiff * redDiff + greenDiff * greenDiff + blueDiff * blueDiff
}

private fun lerpChannel(start: Int, end: Int, fraction: Float): Int {
    return (start + (end - start) * fraction).roundToInt().coerceIn(0, 255)
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

private fun colorAlpha(colorValue: Int): Int {
    return android.graphics.Color.alpha(colorValue)
}

private fun withAlpha(colorValue: Int, alpha: Int): Int {
    val clampedAlpha = alpha.coerceIn(0, 255)
    return (colorValue and 0x00FFFFFF) or (clampedAlpha shl 24)
}
