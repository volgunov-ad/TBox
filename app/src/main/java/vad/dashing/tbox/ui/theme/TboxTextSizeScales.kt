package vad.dashing.tbox.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/**
 * Per-role text size multipliers for chrome and dashboard widgets.
 *
 * Chrome sizes: `baseSp(role) * scale(role)`.
 * Widget tiles: `heightTable(role, height) * scale(widgetRole) * perTileScale`.
 */
@Immutable
data class TboxTextSizeScales(
    val caption: Float = 1f,
    val body: Float = 1f,
    val button: Float = 1f,
    val title: Float = 1f,
    val headline: Float = 1f,
    val tabLabel: Float = 1f,
    val widgetTitle: Float = 1f,
    val widgetValue: Float = 1f,
    val widgetUnit: Float = 1f,
) {
    fun forWidgetRole(role: TboxWidgetTextRole): Float = when (role) {
        TboxWidgetTextRole.TITLE -> widgetTitle
        TboxWidgetTextRole.VALUE -> widgetValue
        TboxWidgetTextRole.UNIT -> widgetUnit
    }

    fun withRole(role: TextSizeRole, value: Float): TboxTextSizeScales {
        val v = normalize(value)
        return when (role) {
            TextSizeRole.Caption -> copy(caption = v)
            TextSizeRole.Body -> copy(body = v)
            TextSizeRole.Button -> copy(button = v)
            TextSizeRole.Title -> copy(title = v)
            TextSizeRole.Headline -> copy(headline = v)
            TextSizeRole.TabLabel -> copy(tabLabel = v)
            TextSizeRole.WidgetTitle -> copy(widgetTitle = v)
            TextSizeRole.WidgetValue -> copy(widgetValue = v)
            TextSizeRole.WidgetUnit -> copy(widgetUnit = v)
        }
    }

    fun scaleFor(role: TextSizeRole): Float = when (role) {
        TextSizeRole.Caption -> caption
        TextSizeRole.Body -> body
        TextSizeRole.Button -> button
        TextSizeRole.Title -> title
        TextSizeRole.Headline -> headline
        TextSizeRole.TabLabel -> tabLabel
        TextSizeRole.WidgetTitle -> widgetTitle
        TextSizeRole.WidgetValue -> widgetValue
        TextSizeRole.WidgetUnit -> widgetUnit
    }

    fun toJsonString(): String = buildString {
        append('{')
        append("\"$KEY_CAPTION\":").append(caption).append(',')
        append("\"$KEY_BODY\":").append(body).append(',')
        append("\"$KEY_BUTTON\":").append(button).append(',')
        append("\"$KEY_TITLE\":").append(title).append(',')
        append("\"$KEY_HEADLINE\":").append(headline).append(',')
        append("\"$KEY_TAB_LABEL\":").append(tabLabel).append(',')
        append("\"$KEY_WIDGET_TITLE\":").append(widgetTitle).append(',')
        append("\"$KEY_WIDGET_VALUE\":").append(widgetValue).append(',')
        append("\"$KEY_WIDGET_UNIT\":").append(widgetUnit)
        append('}')
    }

    companion object {
        const val MIN = 0.70f
        const val MAX = 1.40f
        const val STEP = 0.05f

        val Default = TboxTextSizeScales()

        private const val KEY_CAPTION = "caption"
        private const val KEY_BODY = "body"
        private const val KEY_BUTTON = "button"
        private const val KEY_TITLE = "title"
        private const val KEY_HEADLINE = "headline"
        private const val KEY_TAB_LABEL = "tabLabel"
        private const val KEY_WIDGET_TITLE = "widgetTitle"
        private const val KEY_WIDGET_VALUE = "widgetValue"
        private const val KEY_WIDGET_UNIT = "widgetUnit"

        private val numberField = Regex("\"([a-zA-Z]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")

        fun normalize(value: Float): Float {
            // Use integer hundredths so 0.05 steps stay exact in Float.
            val minSteps = (MIN / STEP).roundToInt()
            val maxSteps = (MAX / STEP).roundToInt()
            val steps = (value / STEP).roundToInt().coerceIn(minSteps, maxSteps)
            return steps * 5 / 100f
        }

        fun fromJson(raw: String?): TboxTextSizeScales {
            if (raw.isNullOrBlank()) return Default
            return try {
                val map = linkedMapOf<String, Float>()
                numberField.findAll(raw).forEach { match ->
                    val key = match.groupValues[1]
                    val value = match.groupValues[2].toFloatOrNull() ?: return@forEach
                    map[key] = value
                }
                if (map.isEmpty()) return Default
                TboxTextSizeScales(
                    caption = normalize(map[KEY_CAPTION] ?: 1f),
                    body = normalize(map[KEY_BODY] ?: 1f),
                    button = normalize(map[KEY_BUTTON] ?: 1f),
                    title = normalize(map[KEY_TITLE] ?: 1f),
                    headline = normalize(map[KEY_HEADLINE] ?: 1f),
                    tabLabel = normalize(map[KEY_TAB_LABEL] ?: 1f),
                    widgetTitle = normalize(map[KEY_WIDGET_TITLE] ?: 1f),
                    widgetValue = normalize(map[KEY_WIDGET_VALUE] ?: 1f),
                    widgetUnit = normalize(map[KEY_WIDGET_UNIT] ?: 1f),
                )
            } catch (_: Exception) {
                Default
            }
        }
    }
}

enum class TextSizeRole {
    Caption,
    Body,
    Button,
    Title,
    Headline,
    TabLabel,
    WidgetTitle,
    WidgetValue,
    WidgetUnit,
}

val LocalTboxTextSizeScales = staticCompositionLocalOf { TboxTextSizeScales.Default }
