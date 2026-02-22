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
import androidx.compose.runtime.mutableFloatStateOf
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

private val huePalette = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF)
)
private const val MAX_HUE_DEGREES = 300f
private const val MID_TONE_POSITION = 0.5f

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
    var hueSliderPosition by remember {
        mutableFloatStateOf(hueSliderFromColor(colorValue))
    }
    var toneSliderPosition by remember {
        mutableFloatStateOf(toneSliderFromColor(colorValue))
    }
    var alphaChannel by remember {
        mutableIntStateOf(colorAlpha(colorValue))
    }
    var textValue by remember { mutableStateOf(localColorValue.asColorInputText()) }
    var isInputError by remember { mutableStateOf(false) }

    LaunchedEffect(colorValue) {
        if (localColorValue != colorValue) {
            localColorValue = colorValue
            hueSliderPosition = hueSliderFromColor(colorValue, hueSliderPosition)
            toneSliderPosition = toneSliderFromColor(colorValue)
            alphaChannel = colorAlpha(colorValue)
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
                            hueSliderPosition = hueSliderFromColor(parsedColor, hueSliderPosition)
                            toneSliderPosition = toneSliderFromColor(parsedColor)
                            alphaChannel = colorAlpha(parsedColor)
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
                    brush = Brush.horizontalGradient(huePalette)
                )
            }

            Slider(
                value = hueSliderPosition,
                onValueChange = { newPosition ->
                    hueSliderPosition = newPosition.coerceIn(0f, 1f)
                    val updatedColor = colorFromSliders(
                        huePosition = hueSliderPosition,
                        tonePosition = toneSliderPosition,
                        alpha = alphaChannel
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

        val pureHueColor = colorFromSliders(
            huePosition = hueSliderPosition,
            tonePosition = MID_TONE_POSITION,
            alpha = 255
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
                    brush = Brush.horizontalGradient(
                        listOf(Color.White, Color(pureHueColor), Color.Black)
                    )
                )
            }

            Slider(
                value = toneSliderPosition,
                onValueChange = { newTone ->
                    toneSliderPosition = newTone.coerceIn(0f, 1f)
                    val updatedColor = colorFromSliders(
                        huePosition = hueSliderPosition,
                        tonePosition = toneSliderPosition,
                        alpha = alphaChannel
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

        val alphaPercent = ((alphaChannel / 255f) * 100f).roundToInt()
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
                value = alphaChannel.toFloat(),
                onValueChange = { newAlpha ->
                    alphaChannel = newAlpha.roundToInt().coerceIn(0, 255)
                    val updatedColor = colorFromSliders(
                        huePosition = hueSliderPosition,
                        tonePosition = toneSliderPosition,
                        alpha = alphaChannel
                    )
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

private fun colorFromSliders(
    huePosition: Float,
    tonePosition: Float,
    alpha: Int
): Int {
    val hue = (huePosition.coerceIn(0f, 1f) * MAX_HUE_DEGREES)
    val clampedTone = tonePosition.coerceIn(0f, 1f)
    val saturation: Float
    val value: Float

    if (clampedTone <= MID_TONE_POSITION) {
        saturation = (clampedTone / MID_TONE_POSITION).coerceIn(0f, 1f)
        value = 1f
    } else {
        saturation = 1f
        value = ((1f - clampedTone) / MID_TONE_POSITION).coerceIn(0f, 1f)
    }

    return android.graphics.Color.HSVToColor(
        alpha.coerceIn(0, 255),
        floatArrayOf(hue, saturation, value)
    )
}

private fun hueSliderFromColor(colorValue: Int, fallback: Float = 0f): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorValue, hsv)
    if (hsv[1] < 0.01f) {
        return fallback.coerceIn(0f, 1f)
    }
    val clampedHue = hsv[0].coerceIn(0f, MAX_HUE_DEGREES)
    return (clampedHue / MAX_HUE_DEGREES).coerceIn(0f, 1f)
}

private fun toneSliderFromColor(colorValue: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorValue, hsv)
    return toneFromSaturationValue(
        saturation = hsv[1].coerceIn(0f, 1f),
        value = hsv[2].coerceIn(0f, 1f)
    )
}

private fun toneFromSaturationValue(saturation: Float, value: Float): Float {
    if (value <= 0.001f) return 1f
    if (saturation <= 0.001f && value >= 0.999f) return 0f

    val upperCandidate = (saturation * MID_TONE_POSITION).coerceIn(0f, MID_TONE_POSITION)
    val lowerCandidate = (1f - value * MID_TONE_POSITION).coerceIn(MID_TONE_POSITION, 1f)

    val upperDistance = saturationValueDistance(
        saturation,
        value,
        expectedSaturation = (upperCandidate / MID_TONE_POSITION).coerceIn(0f, 1f),
        expectedValue = 1f
    )
    val lowerDistance = saturationValueDistance(
        saturation,
        value,
        expectedSaturation = 1f,
        expectedValue = ((1f - lowerCandidate) / MID_TONE_POSITION).coerceIn(0f, 1f)
    )

    return if (upperDistance <= lowerDistance) upperCandidate else lowerCandidate
}

private fun saturationValueDistance(
    saturation: Float,
    value: Float,
    expectedSaturation: Float,
    expectedValue: Float
): Float {
    val saturationDiff = saturation - expectedSaturation
    val valueDiff = value - expectedValue
    return saturationDiff * saturationDiff + valueDiff * valueDiff
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
