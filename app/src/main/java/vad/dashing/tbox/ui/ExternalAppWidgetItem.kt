package vad.dashing.tbox.ui

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.embeddedWidgetSizeHintsMatch
import vad.dashing.tbox.mergeAppWidgetSizeOptions
import vad.dashing.tbox.normalizeWidgetScale

private const val EXTERNAL_WIDGET_PERIODIC_REFRESH_MS = 15 * 60 * 1000L
private const val EXTERNAL_WIDGET_DEFERRED_CREATE_MS = 400L
private const val EXTERNAL_WIDGET_ID_STAGGER_STEP_MS = 90L
private const val EXTERNAL_WIDGET_SIZE_OPTIONS_DEBOUNCE_MS = 120L
private val EXTERNAL_WIDGET_INITIAL_RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 45_000L)
private val EXTERNAL_WIDGET_CREATE_RETRY_DELAYS_MS = longArrayOf(250L, 600L, 1_500L, 3_000L, 6_000L)

private suspend fun awaitAppWidgetInfo(
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
): android.appwidget.AppWidgetProviderInfo? {
    var info = appWidgetManager.getAppWidgetInfo(appWidgetId)
    if (info != null) return info
    repeat(50) {
        delay(200)
        info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info != null) return info
    }
    return null
}

private suspend fun createExternalHostView(
    context: android.content.Context,
    appWidgetHost: AppWidgetHost,
    appWidgetId: Int,
    appWidgetInfo: android.appwidget.AppWidgetProviderInfo
): AppWidgetHostView? = withContext(Dispatchers.Main) {
    try {
        appWidgetHost.createView(context, appWidgetId, appWidgetInfo).apply {
            setAppWidget(appWidgetId, appWidgetInfo)
            setPadding(0, 0, 0, 0)
        }
    } catch (_: Exception) {
        null
    }
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
    var hostView by remember(appWidgetId) { mutableStateOf<AppWidgetHostView?>(null) }
    val applySizeOptionsScope = rememberCoroutineScope()
    var applySizeOptionsJob by remember(appWidgetId) { mutableStateOf<Job?>(null) }
    DisposableEffect(appWidgetId) {
        onDispose {
            applySizeOptionsJob?.cancel()
            applySizeOptionsJob = null
        }
    }
    LaunchedEffect(appWidgetId, appWidgetHost, appWidgetInfo) {
        hostView = null
        if (
            appWidgetHost == null ||
            appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID ||
            appWidgetInfo == null
        ) {
            return@LaunchedEffect
        }
        val staggerMs = (appWidgetId and 0x7) * EXTERNAL_WIDGET_ID_STAGGER_STEP_MS
        delay(EXTERNAL_WIDGET_DEFERRED_CREATE_MS + staggerMs)
        ExternalWidgetHostManager.awaitListeningReadyWithOptionalKick(
            context = context,
            primaryTimeoutMs = 12_000L,
            afterKickWaitMs = 2_500L
        )
        val info = appWidgetInfo ?: return@LaunchedEffect
        var created: AppWidgetHostView? = null
        for (retryDelayMs in EXTERNAL_WIDGET_CREATE_RETRY_DELAYS_MS) {
            created = createExternalHostView(
                context = context,
                appWidgetHost = appWidgetHost,
                appWidgetId = appWidgetId,
                appWidgetInfo = info
            )
            if (created != null) break
            delay(retryDelayMs)
        }
        hostView = created
    }
    val density = LocalDensity.current
    val widgetDisplayScale = normalizeWidgetScale(widgetConfig.scale)

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
                val hostViewNonNull = checkNotNull(hostView)
                key(appWidgetId) {
                    AndroidView(
                        factory = { viewContext ->
                            val frame = ExternalWidgetScaleFrame(viewContext)
                            val intercept = LongPressInterceptLayout(viewContext).apply {
                                onLongPress = onLongClick
                                interceptLongPress = !isEditMode
                                if (hostViewNonNull.parent != null) {
                                    (hostViewNonNull.parent as? ViewGroup)
                                        ?.removeView(hostViewNonNull)
                                }
                                addView(
                                    hostViewNonNull,
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
                            intercept.interceptLongPress = !isEditMode
                            val onlyChildIsCurrent =
                                intercept.childCount == 1 &&
                                    intercept.getChildAt(0) === hostViewNonNull
                            if (!onlyChildIsCurrent) {
                                intercept.removeAllViews()
                                (hostViewNonNull.parent as? ViewGroup)
                                    ?.removeView(hostViewNonNull)
                                intercept.addView(
                                    hostViewNonNull,
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
                                applySizeOptionsJob?.cancel()
                                applySizeOptionsJob = applySizeOptionsScope.launch {
                                    delay(EXTERNAL_WIDGET_SIZE_OPTIONS_DEBOUNCE_MS)
                                    withContext(Dispatchers.Main) {
                                        val merged = mergeAppWidgetSizeOptions(
                                            appWidgetManager,
                                            appWidgetId,
                                            minWidth,
                                            minHeight
                                        )
                                        val existing = appWidgetManager.getAppWidgetOptions(appWidgetId)
                                        if (!embeddedWidgetSizeHintsMatch(existing, merged)) {
                                            appWidgetManager.updateAppWidgetOptions(appWidgetId, merged)
                                        }
                                        optionsApplied = true
                                    }
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
        var refreshRequestedAt = SystemClock.elapsedRealtime()
        ExternalWidgetHostManager.requestProviderRefresh(
            context = context,
            appWidgetId = appWidgetId,
            force = true
        )
        for (retryDelayMs in EXTERNAL_WIDGET_INITIAL_RETRY_DELAYS_MS) {
            delay(retryDelayMs)
            val hasData = ExternalWidgetHostManager.hasRemoteViewsSince(
                appWidgetId = appWidgetId,
                sinceElapsedRealtimeMs = refreshRequestedAt
            )
            if (hasData) {
                break
            }
            refreshRequestedAt = SystemClock.elapsedRealtime()
            ExternalWidgetHostManager.requestProviderRefresh(
                context = context,
                appWidgetId = appWidgetId,
                force = true
            )
        }
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
