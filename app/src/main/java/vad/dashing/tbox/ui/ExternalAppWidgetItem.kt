package vad.dashing.tbox.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.roundToInt
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.mergeAppWidgetSizeOptions
import vad.dashing.tbox.normalizeWidgetScale

private const val EXTERNAL_WIDGET_PERIODIC_REFRESH_MS = 15 * 60 * 1000L
private const val EXTERNAL_WIDGET_LONG_PRESS_HOTSPOT_DP = 36f

private suspend fun awaitAppWidgetInfo(
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
): android.appwidget.AppWidgetProviderInfo? {
    var info = appWidgetManager.getAppWidgetInfo(appWidgetId)
    if (info != null) return info
    repeat(20) {
        delay(150)
        info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info != null) return info
    }
    return null
}

@Composable
fun ExternalAppWidgetItem(
    widgetConfig: FloatingDashboardWidgetConfig,
    appWidgetHost: AppWidgetHost?,
    isEditMode: Boolean,
    handleClick: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    elevation: Dp = 0.dp,
    shape: Dp = 0.dp,
    backgroundColor: Color? = null,
    /** Matches title styling on other dashboard tiles ([DashboardWidgetItem] title row). */
    textColor: Color? = null,
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val appWidgetId = widgetConfig.appWidgetId ?: AppWidgetManager.INVALID_APPWIDGET_ID
    var appWidgetInfo by remember(appWidgetId) {
        mutableStateOf<android.appwidget.AppWidgetProviderInfo?>(
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                null
            } else {
                appWidgetManager.getAppWidgetInfo(appWidgetId)
            }
        )
    }
    LaunchedEffect(appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetInfo = null
            return@LaunchedEffect
        }
        appWidgetInfo = awaitAppWidgetInfo(appWidgetManager, appWidgetId)
    }
    var optionsApplied by remember(appWidgetId) { mutableStateOf(false) }
    var hotspotTouchArmed by remember(appWidgetId) { mutableStateOf(false) }
    val density = LocalDensity.current
    val longPressHotspotPx = with(density) { EXTERNAL_WIDGET_LONG_PRESS_HOTSPOT_DP.dp.toPx() }
    val widgetDisplayScale = normalizeWidgetScale(widgetConfig.scale)
    val hostView = remember(appWidgetId, appWidgetInfo, appWidgetHost) {
        if (
            appWidgetHost == null ||
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            appWidgetInfo == null
        ) {
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

    val clickModifier = if (!isEditMode && handleClick) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .then(clickModifier),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor ?: MaterialTheme.colorScheme.surface
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
                    stringResource(R.string.widget_external_tile_empty)
                } else {
                    stringResource(R.string.widget_external_tile_unavailable)
                }
                // No AppWidget host view: LongPressInterceptLayout is absent, so long-press would
                // not reach the panel's edit handler unless we capture it here.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val titleFont = calculateResponsiveFontSize(
                        containerHeight = maxHeight,
                        textType = TextType.TITLE
                    )
                    val resolvedColor = textColor ?: MaterialTheme.colorScheme.onSurface
                    Text(
                        text = placeholder,
                        fontSize = titleFont,
                        fontWeight = FontWeight.Medium,
                        color = resolvedColor,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        lineHeight = titleFont * 1.3f,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    if (!isEditMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(onLongClick) {
                                    detectTapGestures(
                                        onLongPress = { onLongClick() }
                                    )
                                }
                        )
                    }
                }
            } else {
                key(appWidgetId) {
                    AndroidView(
                        factory = { viewContext ->
                            val frame = ExternalWidgetScaleFrame(viewContext)
                            val intercept = LongPressInterceptLayout(viewContext).apply {
                                onLongPress = onLongClick
                                interceptLongPress = false
                                if (hostView.parent != null) {
                                    (hostView.parent as? ViewGroup)?.removeView(hostView)
                                }
                                addView(
                                    hostView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                            frame.attachIntercept(intercept)
                            frame.displayScale = widgetDisplayScale
                            frame
                        },
                        update = { frame ->
                            val scaleFrame = frame as ExternalWidgetScaleFrame
                            scaleFrame.displayScale = widgetDisplayScale
                            val intercept = scaleFrame.interceptChild ?: return@AndroidView
                            intercept.onLongPress = onLongClick
                            intercept.interceptLongPress = !isEditMode && hotspotTouchArmed
                            val onlyChildIsCurrent =
                                intercept.childCount == 1 && intercept.getChildAt(0) === hostView
                            if (!onlyChildIsCurrent) {
                                intercept.removeAllViews()
                                (hostView.parent as? ViewGroup)?.removeView(hostView)
                                intercept.addView(
                                    hostView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .pointerInput(isEditMode, longPressHotspotPx) {
                                if (isEditMode) {
                                    hotspotTouchArmed = false
                                    return@pointerInput
                                }
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: continue
                                        when {
                                            change.changedToDownIgnoreConsumed() -> {
                                                val x = change.position.x
                                                val y = change.position.y
                                                val right = size.width.toFloat()
                                                val bottom = size.height.toFloat()
                                                val dx = (right - x).coerceAtLeast(0f)
                                                val dy = (bottom - y).coerceAtLeast(0f)
                                                hotspotTouchArmed = hypot(dx, dy) <= longPressHotspotPx
                                            }
                                            change.changedToUpIgnoreConsumed() || !change.pressed -> {
                                                hotspotTouchArmed = false
                                            }
                                        }
                                    }
                                }
                            }
                            .onSizeChanged { size ->
                                val minWidth = with(density) { size.width.toDp().value }.roundToInt()
                                val minHeight = with(density) { size.height.toDp().value }.roundToInt()
                                if (minWidth > 0 && minHeight > 0) {
                                    val merged = mergeAppWidgetSizeOptions(
                                        appWidgetManager,
                                        appWidgetId,
                                        minWidth,
                                        minHeight
                                    )
                                    appWidgetManager.updateAppWidgetOptions(appWidgetId, merged)
                                    optionsApplied = true
                                }
                            }
                    )
                }
            }

            if (isEditMode) {
                val interactionPolicy = LocalDashboardWidgetInteractionPolicy.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(interactionPolicy, onClick, onLongClick) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (interactionPolicy.isActionAllowed(
                                            offset = offset,
                                            width = size.width.toFloat(),
                                            height = size.height.toFloat()
                                        )
                                    ) {
                                        onClick()
                                    }
                                },
                                onLongPress = { offset ->
                                    if (interactionPolicy.isActionAllowed(
                                            offset = offset,
                                            width = size.width.toFloat(),
                                            height = size.height.toFloat()
                                        )
                                    ) {
                                        onLongClick()
                                    }
                                }
                            )
                        }
                )
            }
        }
    }

    LaunchedEffect(appWidgetId, appWidgetInfo, optionsApplied) {
        if (
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            appWidgetInfo == null ||
            !optionsApplied
        ) {
            return@LaunchedEffect
        }

        // First refresh only after view has real size/options; many providers need this.
        ExternalWidgetHostManager.requestProviderRefresh(
            context = context,
            appWidgetId = appWidgetId,
            force = true
        )
        while (true) {
            delay(EXTERNAL_WIDGET_PERIODIC_REFRESH_MS)
            // Soft periodic ping so weather/news providers that do not self-refresh still update.
            ExternalWidgetHostManager.requestProviderRefresh(
                context = context,
                appWidgetId = appWidgetId
            )
        }
    }
}
