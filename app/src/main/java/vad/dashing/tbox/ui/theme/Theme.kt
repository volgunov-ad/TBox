package vad.dashing.tbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
fun TboxAppTheme(
    theme: Int = 1, // 1 - светлая, 2 - темная
    fontFamilyId: Int = TboxFontFamily.Default.id,
    textSizeScales: TboxTextSizeScales = TboxTextSizeScales.Default,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        2 -> darkColorScheme()
        else -> lightColorScheme()
    }
    val fontFamily = resolveFontFamily(fontFamilyId)
    val scales = remember(textSizeScales) { textSizeScales }
    val textStyles = remember(fontFamily, scales) { tboxTextStyles(fontFamily, scales) }
    val typography = remember(fontFamily, scales) { tboxMaterialTypography(fontFamily, scales) }

    CompositionLocalProvider(
        LocalTboxTextStyles provides textStyles,
        LocalTboxTextSizeScales provides scales,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
