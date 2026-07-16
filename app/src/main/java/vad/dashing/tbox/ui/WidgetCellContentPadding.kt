package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.normalizeWidgetPaddingPercent

/**
 * Insets tile content from cell edges by the configured percent of cell width (start/end)
 * and height (top/bottom).
 */
@Composable
fun WidgetCellContentPadding(
    widgetConfig: FloatingDashboardWidgetConfig,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val topPct = normalizeWidgetPaddingPercent(widgetConfig.paddingTopPercent) / 100f
        val bottomPct = normalizeWidgetPaddingPercent(widgetConfig.paddingBottomPercent) / 100f
        val startPct = normalizeWidgetPaddingPercent(widgetConfig.paddingStartPercent) / 100f
        val endPct = normalizeWidgetPaddingPercent(widgetConfig.paddingEndPercent) / 100f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = maxWidth * startPct,
                    top = maxHeight * topPct,
                    end = maxWidth * endPct,
                    bottom = maxHeight * bottomPct,
                )
        ) {
            content()
        }
    }
}
