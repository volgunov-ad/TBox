package vad.dashing.tbox.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import vad.dashing.tbox.FloatingDashboardWidgetConfig

@Composable
fun ExternalAppWidgetItem(
    widgetConfig: FloatingDashboardWidgetConfig,
    appWidgetHost: AppWidgetHost,
    isEditMode: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    elevation: Dp = 0.dp,
    shape: Dp = 0.dp,
    backgroundTransparent: Boolean = false
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val appWidgetId = widgetConfig.appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID
    val appWidgetInfo = remember(appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            null
        } else {
            appWidgetManager.getAppWidgetInfo(appWidgetId)
        }
    }
    val density = LocalDensity.current
    val hostView = remember(appWidgetId, appWidgetInfo) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || appWidgetInfo == null) {
            null
        } else {
            try {
                appWidgetHost.createView(context, appWidgetId, appWidgetInfo).apply {
                    setAppWidget(appWidgetId, appWidgetInfo)
                    setPadding(0, 0, 0, 0)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (backgroundTransparent) Color.Transparent else MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            if (hostView == null) {
                val placeholder = if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    "Виджет не выбран"
                } else {
                    "Виджет недоступен"
                }
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                AndroidView(
                    factory = { hostView },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .onSizeChanged { size ->
                            val minWidth = with(density) { size.width.toDp().value }.roundToInt()
                            val minHeight = with(density) { size.height.toDp().value }.roundToInt()
                            if (minWidth > 0 && minHeight > 0) {
                                val options = Bundle().apply {
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, minWidth)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                                }
                                appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
                            }
                        }
                )
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                )
            }
        }
    }
}
