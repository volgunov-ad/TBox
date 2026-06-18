package vad.dashing.tbox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
