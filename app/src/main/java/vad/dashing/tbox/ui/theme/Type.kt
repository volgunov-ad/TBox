package vad.dashing.tbox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private fun tboxTextStyle(
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    lineHeight: TextUnit,
): TextStyle = TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
)

/** Fixed app text sizes for a given [fontFamily], optionally scaled by [scales]. */
class TboxTextStyleSet internal constructor(
    val Caption: TextStyle,
    val Body: TextStyle,
    val Button: TextStyle,
    val Title: TextStyle,
    val Headline: TextStyle,
    val TabLabel: TextStyle,
    val WidgetTitle: TextStyle,
    val WidgetValue: TextStyle,
    val WidgetUnit: TextStyle,
)

fun tboxTextStyles(
    fontFamily: FontFamily = FontFamily.Default,
    scales: TboxTextSizeScales = TboxTextSizeScales.Default,
): TboxTextStyleSet =
    TboxTextStyleSet(
        Caption = tboxTextStyle(
            fontFamily,
            FontWeight.Normal,
            20.sp * scales.caption,
            26.sp * scales.caption,
        ),
        Body = tboxTextStyle(
            fontFamily,
            FontWeight.Normal,
            24.sp * scales.body,
            31.2.sp * scales.body,
        ),
        Button = tboxTextStyle(
            fontFamily,
            FontWeight.Normal,
            24.sp * scales.button,
            31.2.sp * scales.button,
        ),
        Title = tboxTextStyle(
            fontFamily,
            FontWeight.Medium,
            26.sp * scales.title,
            33.8.sp * scales.title,
        ),
        Headline = tboxTextStyle(
            fontFamily,
            FontWeight.Medium,
            28.sp * scales.headline,
            36.4.sp * scales.headline,
        ),
        TabLabel = tboxTextStyle(
            fontFamily,
            FontWeight.Normal,
            34.sp * scales.tabLabel,
            44.2.sp * scales.tabLabel,
        ),
        WidgetTitle = tboxTextStyle(
            fontFamily,
            FontWeight.Medium,
            24.sp * scales.widgetTitle,
            31.2.sp * scales.widgetTitle,
        ),
        WidgetValue = tboxTextStyle(
            fontFamily,
            FontWeight.Medium,
            20.sp * scales.widgetValue,
            26.sp * scales.widgetValue,
        ),
        WidgetUnit = tboxTextStyle(
            fontFamily,
            FontWeight.Medium,
            18.sp * scales.widgetUnit,
            23.4.sp * scales.widgetUnit,
        ),
    )

/** Resolved text styles for the active theme (font family + size scales from settings). */
val LocalTboxTextStyles = staticCompositionLocalOf { tboxTextStyles() }

/** Default-family styles for non-Compose callers and legacy defaults. */
object TboxTextStyles {
    private val defaults = tboxTextStyles(FontFamily.Default)
    val Caption = defaults.Caption
    val Body = defaults.Body
    val Button = defaults.Button
    val Title = defaults.Title
    val Headline = defaults.Headline
    val TabLabel = defaults.TabLabel
    val WidgetTitle = defaults.WidgetTitle
    val WidgetValue = defaults.WidgetValue
    val WidgetUnit = defaults.WidgetUnit
}

/** Dashboard tile text roles: family from [MaterialTheme.typography], size adapts to tile height. */
enum class TboxWidgetTextRole {
    TITLE, VALUE, UNIT,
}

/**
 * Height-adaptive widget typography. Size steps match the legacy [calculateResponsiveFontSize]
 * tables; font family and weight come from [TboxTextStyleSet.WidgetTitle] / [WidgetValue] / [WidgetUnit].
 *
 * Final size: `heightTable * globalRoleScale * perTileScale`.
 */
object TboxWidgetTypography {
    private val titleSizesSp = floatArrayOf(8f, 10f, 12f, 16f, 20f, 24f, 28f, 32f)
    private val valueSizesSp = floatArrayOf(10f, 14f, 18f, 24f, 30f, 36f, 42f, 48f)
    private val unitSizesSp = floatArrayOf(6f, 8f, 10f, 14f, 18f, 22f, 26f, 30f)

    fun fontSizeSpForHeight(containerHeightDp: Float, role: TboxWidgetTextRole): Float {
        val sizes = when (role) {
            TboxWidgetTextRole.TITLE -> titleSizesSp
            TboxWidgetTextRole.VALUE -> valueSizesSp
            TboxWidgetTextRole.UNIT -> unitSizesSp
        }
        return when {
            containerHeightDp < 20f -> sizes[0]
            containerHeightDp < 40f -> sizes[1]
            containerHeightDp < 60f -> sizes[2]
            containerHeightDp < 80f -> sizes[3]
            containerHeightDp < 100f -> sizes[4]
            containerHeightDp < 120f -> sizes[5]
            containerHeightDp < 150f -> sizes[6]
            else -> sizes[7]
        }
    }

    fun textStyleForHeight(
        containerHeightDp: Float,
        role: TboxWidgetTextRole,
        baseStyle: TextStyle,
        textScale: Float = 1f,
        globalRoleScale: Float = 1f,
    ): TextStyle {
        val fontSize = fontSizeSpForHeight(containerHeightDp, role) * globalRoleScale * textScale
        val size = fontSize.sp
        return baseStyle.copy(
            fontSize = size,
            lineHeight = size * 1.3f,
        )
    }

    fun fontSizeForHeight(
        containerHeightDp: Float,
        role: TboxWidgetTextRole,
        baseStyle: TextStyle,
        textScale: Float = 1f,
        globalRoleScale: Float = 1f,
    ): TextUnit = textStyleForHeight(
        containerHeightDp,
        role,
        baseStyle,
        textScale,
        globalRoleScale,
    ).fontSize
}

/** Scales widget tile text while keeping the 1.3 line-height ratio. */
fun TextStyle.scaledWidgetText(factor: Float): TextStyle {
    val size = fontSize * factor
    return copy(fontSize = size, lineHeight = size * 1.3f)
}

fun tboxMaterialTypography(
    fontFamily: FontFamily = FontFamily.Default,
    scales: TboxTextSizeScales = TboxTextSizeScales.Default,
): Typography {
    val styles = tboxTextStyles(fontFamily, scales)
    return Typography(
        displaySmall = styles.TabLabel,
        headlineSmall = styles.Headline,
        titleLarge = styles.Title,
        labelLarge = styles.Button,
        bodyLarge = styles.Body,
        bodySmall = styles.Caption,
        bodyMedium = styles.Caption,
        titleMedium = styles.Button,
    )
}

val TboxMaterialTypography: Typography = tboxMaterialTypography()

val Typography.tboxCaption: TextStyle get() = bodySmall
val Typography.tboxBody: TextStyle get() = bodyLarge
val Typography.tboxButton: TextStyle get() = labelLarge
val Typography.tboxTitle: TextStyle get() = titleLarge
val Typography.tboxHeadline: TextStyle get() = headlineSmall
val Typography.tboxTabLabel: TextStyle get() = displaySmall
