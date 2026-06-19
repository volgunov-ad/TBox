package vad.dashing.tbox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Fixed app text sizes; wired into [TboxMaterialTypography] and [TboxAppTheme]. */
object TboxTextStyles {
    val Caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 23.4.sp,
    )
    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    )
    val Button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.6.sp,
    )
    val Title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 31.2.sp,
    )
    val Headline = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 33.8.sp,
    )
    val TabLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 44.2.sp,
    )
}

/** Dashboard tile text roles: family/weight from [TboxTextStyles], size adapts to tile height. */
enum class TboxWidgetTextRole {
    TITLE, VALUE, UNIT,
}

/**
 * Height-adaptive widget typography. Size steps match the legacy [calculateResponsiveFontSize]
 * tables; font family and weight come from the corresponding [TboxTextStyles] entry.
 */
object TboxWidgetTypography {
    private val titleSizesSp = floatArrayOf(8f, 10f, 12f, 16f, 20f, 24f, 28f, 32f)
    private val valueSizesSp = floatArrayOf(10f, 14f, 18f, 24f, 30f, 36f, 42f, 48f)
    private val unitSizesSp = floatArrayOf(6f, 8f, 10f, 14f, 18f, 22f, 26f, 30f)

    fun baseStyle(role: TboxWidgetTextRole): TextStyle = when (role) {
        TboxWidgetTextRole.TITLE -> TboxTextStyles.Title
        TboxWidgetTextRole.VALUE -> TboxTextStyles.Body
        TboxWidgetTextRole.UNIT -> TboxTextStyles.Caption
    }

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
        textScale: Float = 1f,
    ): TextStyle {
        val fontSize = fontSizeSpForHeight(containerHeightDp, role) * textScale
        val size = fontSize.sp
        return baseStyle(role).copy(
            fontSize = size,
            lineHeight = size * 1.3f,
        )
    }

    fun fontSizeForHeight(
        containerHeightDp: Float,
        role: TboxWidgetTextRole,
        textScale: Float = 1f,
    ): TextUnit = textStyleForHeight(containerHeightDp, role, textScale).fontSize
}

/** Scales widget tile text while keeping the 1.3 line-height ratio. */
fun TextStyle.scaledWidgetText(factor: Float): TextStyle {
    val size = fontSize * factor
    return copy(fontSize = size, lineHeight = size * 1.3f)
}

val TboxMaterialTypography = Typography(
    displaySmall = TboxTextStyles.TabLabel,
    headlineSmall = TboxTextStyles.Headline,
    titleLarge = TboxTextStyles.Title,
    labelLarge = TboxTextStyles.Button,
    bodyLarge = TboxTextStyles.Body,
    bodySmall = TboxTextStyles.Caption,
    bodyMedium = TboxTextStyles.Caption,
    titleMedium = TboxTextStyles.Button,
)

val Typography.tboxCaption: TextStyle get() = bodySmall
val Typography.tboxBody: TextStyle get() = bodyLarge
val Typography.tboxButton: TextStyle get() = labelLarge
val Typography.tboxTitle: TextStyle get() = titleLarge
val Typography.tboxHeadline: TextStyle get() = headlineSmall
val Typography.tboxTabLabel: TextStyle get() = displaySmall
