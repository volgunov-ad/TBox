package vad.dashing.tbox.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.WIDGET_TITLE_POSITION_BOTTOM
import vad.dashing.tbox.embeddedWidgetSizeHintsMatch
import vad.dashing.tbox.isExternalAppWidgetCellReady
import vad.dashing.tbox.mergeAppWidgetSizeOptions
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeWidgetTitlePosition

private const val EXTERNAL_WIDGET_SIZE_OPTIONS_DEBOUNCE_MS = 200L

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
    showTitle: Boolean = false,
    titleOverride: String = "",
    defaultTitle: String,
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
    val applySizeOptionsScope = rememberCoroutineScope()
    var applySizeOptionsJob by remember(appWidgetId) { mutableStateOf<Job?>(null) }
    DisposableEffect(appWidgetId) {
        onDispose {
            applySizeOptionsJob?.cancel()
            applySizeOptionsJob = null
        }
    }
    val density = LocalDensity.current
    val widgetIconScale = normalizeWidgetScale(widgetConfig.iconScale)
    var hostView by remember(appWidgetId) {
        mutableStateOf<android.appwidget.AppWidgetHostView?>(null)
    }
    var cellWidthDp by remember(appWidgetId) { mutableIntStateOf(0) }
    var cellHeightDp by remember(appWidgetId) { mutableIntStateOf(0) }
    LaunchedEffect(appWidgetId, appWidgetInfo, appWidgetHost) {
        val info = appWidgetInfo
        val host = appWidgetHost
        if (
            host == null ||
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            info == null
        ) {
            hostView = null
            return@LaunchedEffect
        }
        // Wait for a real cell size, then push size options before createView so RemoteViews
        // inflate to the tile (not a default size that MATCH_PARENT later stretches).
        snapshotFlow { cellWidthDp to cellHeightDp }
            .first { (widthDp, heightDp) -> isExternalAppWidgetCellReady(widthDp, heightDp) }
        val widthDp = cellWidthDp
        val heightDp = cellHeightDp
        hostView = null
        val merged = mergeAppWidgetSizeOptions(
            appWidgetManager,
            appWidgetId,
            widthDp,
            heightDp,
        )
        val existing = appWidgetManager.getAppWidgetOptions(appWidgetId)
        if (!embeddedWidgetSizeHintsMatch(existing, merged)) {
            appWidgetManager.updateAppWidgetOptions(appWidgetId, merged)
        }
        delay(ExternalWidgetHostManager.DEFER_HOST_VIEW_MOUNT_MS)
        hostView = try {
            host.createView(context, appWidgetId, info).apply {
                setAppWidget(appWidgetId, info)
                setPadding(0, 0, 0, 0)
            }
        } catch (_: Exception) {
            null
        }
    }
    var forceSizeOptionsRefresh by remember(appWidgetId, hostView) { mutableStateOf(true) }

    val clickModifier = if (!isEditMode && handleClick) {
        Modifier.clickableWithSound(onClick = onClick)
    } else {
        Modifier
    }

    val titleText = titleOverride.trim().ifBlank { defaultTitle }

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
                .background(color = Color.Transparent)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val resolvedColor = textColor ?: MaterialTheme.colorScheme.onSurface
                val containerHeightForTitle = maxHeight
                val titleAtBottom =
                    normalizeWidgetTitlePosition(LocalWidgetTitlePosition.current) ==
                        WIDGET_TITLE_POSITION_BOTTOM
                Column(modifier = Modifier.fillMaxSize()) {
                    if (showTitle && !titleAtBottom) {
                        val titleStyle = calculateResponsiveTextStyle(
                            containerHeight = containerHeightForTitle,
                            textType = TextType.TITLE,
                            forWidgetTitle = true,
                        )
                        Text(
                            text = titleText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 4.dp, end = 4.dp),
                            style = titleStyle,
                            color = resolvedColor,
                            textAlign = LocalWidgetTextAlign.current,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onSizeChanged { size ->
                                val widthDp =
                                    with(density) { size.width.toDp().value }.roundToInt()
                                val heightDp =
                                    with(density) { size.height.toDp().value }.roundToInt()
                                if (isExternalAppWidgetCellReady(widthDp, heightDp)) {
                                    if (cellWidthDp != widthDp) cellWidthDp = widthDp
                                    if (cellHeightDp != heightDp) cellHeightDp = heightDp
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
            if (hostView == null) {
                val placeholder = when {
                    appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ->
                        stringResource(R.string.widget_external_tile_empty)
                    appWidgetInfo == null ->
                        stringResource(R.string.widget_external_tile_unavailable)
                    else -> null // deferred mount / cached create in progress — keep tile quiet
                }
                // No AppWidget host view: LongPressInterceptLayout is absent, so long-press would
                // not reach the panel's edit handler unless we capture it here.
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    if (placeholder != null) {
                        val titleStyle = calculateResponsiveTextStyle(
                            containerHeight = maxHeight,
                            textType = TextType.TITLE
                        )
                        val resolvedColor = textColor ?: MaterialTheme.colorScheme.onSurface
                        Text(
                            text = placeholder,
                            style = titleStyle,
                            color = resolvedColor,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
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
                val mountedHostView = hostView ?: return@BoxWithConstraints
                key(appWidgetId) {
                    AndroidView(
                        factory = { viewContext ->
                            val frame = ExternalWidgetScaleFrame(viewContext)
                            val intercept = LongPressInterceptLayout(viewContext).apply {
                                onLongPress = onLongClick
                                interceptLongPress = !isEditMode
                                if (mountedHostView.parent != null) {
                                    (mountedHostView.parent as? ViewGroup)?.removeView(mountedHostView)
                                }
                                addView(
                                    mountedHostView,
                                    ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                            frame.attachIntercept(intercept)
                            frame.displayScale = widgetIconScale
                            frame
                        },
                        update = { frame ->
                            val scaleFrame = frame as ExternalWidgetScaleFrame
                            scaleFrame.displayScale = widgetIconScale
                            val intercept = scaleFrame.interceptChild ?: return@AndroidView
                            intercept.onLongPress = onLongClick
                            intercept.interceptLongPress = !isEditMode
                            val onlyChildIsCurrent =
                                intercept.childCount == 1 && intercept.getChildAt(0) === mountedHostView
                            if (!onlyChildIsCurrent) {
                                intercept.removeAllViews()
                                (mountedHostView.parent as? ViewGroup)?.removeView(mountedHostView)
                                intercept.addView(
                                    mountedHostView,
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
                            .onSizeChanged { size ->
                                val minWidth = with(density) { size.width.toDp().value }.roundToInt()
                                val minHeight = with(density) { size.height.toDp().value }.roundToInt()
                                if (minWidth <= 0 || minHeight <= 0) return@onSizeChanged
                                val forceRefresh = forceSizeOptionsRefresh
                                applySizeOptionsJob?.cancel()
                                applySizeOptionsJob = applySizeOptionsScope.launch {
                                    if (!forceRefresh) {
                                        delay(EXTERNAL_WIDGET_SIZE_OPTIONS_DEBOUNCE_MS)
                                    }
                                    withContext(Dispatchers.Main) {
                                        val merged = mergeAppWidgetSizeOptions(
                                            appWidgetManager,
                                            appWidgetId,
                                            minWidth,
                                            minHeight
                                        )
                                        val existing = appWidgetManager.getAppWidgetOptions(appWidgetId)
                                        if (forceRefresh || !embeddedWidgetSizeHintsMatch(existing, merged)) {
                                            appWidgetManager.updateAppWidgetOptions(appWidgetId, merged)
                                        }
                                        if (forceRefresh) {
                                            forceSizeOptionsRefresh = false
                                        }
                                    }
                                }
                            }
                    )
                }
            }
                    }
                    if (showTitle && titleAtBottom) {
                        val titleStyle = calculateResponsiveTextStyle(
                            containerHeight = containerHeightForTitle,
                            textType = TextType.TITLE,
                            forWidgetTitle = true,
                        )
                        Text(
                            text = titleText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, bottom = 4.dp, end = 4.dp),
                            style = titleStyle,
                            color = resolvedColor,
                            textAlign = LocalWidgetTextAlign.current,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
}
