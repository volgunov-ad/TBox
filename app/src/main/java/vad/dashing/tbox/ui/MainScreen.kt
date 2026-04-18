package vad.dashing.tbox.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingDashboardTileEditRequestBus
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING
import vad.dashing.tbox.MainScreenAddButtonPosition
import vad.dashing.tbox.MainScreenSettingsButtonPosition
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.decodeImageBitmapFromUri
import vad.dashing.tbox.effectiveWallpaperFileName
import vad.dashing.tbox.listSortedWallpaperImagesInFolder
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.loadWidgetsFromConfig

@Composable
fun MainScreen(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
    onOpenConsole: () -> Unit,
    onTboxRestart: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mainPanels by settingsViewModel.mainScreenDashboards.collectAsStateWithLifecycle()
    val settingsBtnPos by settingsViewModel.mainScreenSettingsButtonPosition.collectAsStateWithLifecycle()
    val addBtnPos by settingsViewModel.mainScreenAddButtonPosition.collectAsStateWithLifecycle()
    val cornerBtnSizeDp by settingsViewModel.mainScreenCornerButtonSizeDp.collectAsStateWithLifecycle()
    val cornerBtnBgLight by settingsViewModel.mainScreenCornerButtonBackgroundLight.collectAsStateWithLifecycle()
    val cornerBtnBgDark by settingsViewModel.mainScreenCornerButtonBackgroundDark.collectAsStateWithLifecycle()
    val cornerBtnIconLight by settingsViewModel.mainScreenCornerButtonIconLight.collectAsStateWithLifecycle()
    val cornerBtnIconDark by settingsViewModel.mainScreenCornerButtonIconDark.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    val cornerIconSize = cornerBtnSizeDp.dp
    val cornerBackgroundColor = Color(
        if (currentTheme == 2) cornerBtnBgDark else cornerBtnBgLight
    )
    val cornerIconTint = Color(
        if (currentTheme == 2) cornerBtnIconDark else cornerBtnIconLight
    )
    val newMainPanelDefaultName = stringResource(R.string.floating_dashboard_new_panel_default)

    var floatingOverlayEditRequest by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val pendingFloatingTileEdit by FloatingDashboardTileEditRequestBus.pending
        .collectAsStateWithLifecycle()
    LaunchedEffect(pendingFloatingTileEdit) {
        pendingFloatingTileEdit?.let { req ->
            floatingOverlayEditRequest = req
            FloatingDashboardTileEditRequestBus.clear()
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        MainScreenWallpaperBackground(
            theme = currentTheme,
            settingsViewModel = settingsViewModel,
            modifier = Modifier.fillMaxSize()
        )
        val maxWpx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxHpx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        mainPanels.filter { it.enabled }.forEach { panel ->
            key(panel.id) {
                MainScreenDashboardPanel(
                    panel = panel,
                    containerWidthPx = maxWpx,
                    containerHeightPx = maxHpx,
                    tboxViewModel = tboxViewModel,
                    canViewModel = canViewModel,
                    appDataViewModel = appDataViewModel,
                    settingsViewModel = settingsViewModel,
                    onRebootTbox = onTboxRestart,
                    onTripFinishAndStart = onTripFinishAndStart,
                )
            }
        }

        MainScreenDraggableCornerButton(
            icon = ImageVector.vectorResource(R.drawable.ic_main_open_console),
            contentDescription = stringResource(R.string.main_open_console_cd),
            iconSize = cornerIconSize,
            backgroundColor = cornerBackgroundColor,
            iconTint = cornerIconTint,
            maxWidthPx = maxWpx,
            maxHeightPx = maxHpx,
            normalizedX = settingsBtnPos.x,
            normalizedY = settingsBtnPos.y,
            onSaveNormalized = { x, y ->
                settingsViewModel.saveMainScreenSettingsButton(MainScreenSettingsButtonPosition(x, y))
            },
            onClick = onOpenConsole,
        )

        MainScreenDraggableCornerButton(
            icon = Icons.Filled.Add,
            contentDescription = stringResource(R.string.main_screen_add_panel_cd),
            iconSize = cornerIconSize,
            backgroundColor = cornerBackgroundColor,
            iconTint = cornerIconTint,
            maxWidthPx = maxWpx,
            maxHeightPx = maxHpx,
            normalizedX = addBtnPos.x,
            normalizedY = addBtnPos.y,
            onSaveNormalized = { x, y ->
                settingsViewModel.saveMainScreenAddButton(MainScreenAddButtonPosition(x, y))
            },
            onClick = {
                settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName)
            },
        )

        floatingOverlayEditRequest?.let { (panelId, widgetIndex) ->
            val context = LocalContext.current
            val overlayEditHost = remember(panelId, widgetIndex) {
                ExternalWidgetHostManager.acquireHost(context)
            }
            DisposableEffect(overlayEditHost) {
                onDispose { ExternalWidgetHostManager.releaseHost() }
            }
            LaunchedEffect(Unit) {
                ExternalWidgetHostManager.kickListeningIfNeeded(
                    context,
                    "MainScreen.floatingOverlayEdit.compose"
                )
            }
            val dashboardViewModel: FloatingDashboardViewModel = viewModel(
                key = "floating-overlay-edit-$panelId",
                factory = FloatingDashboardViewModelFactory(panelId)
            )
            val dashboardState by dashboardViewModel.dashboardManager.dashboardState
                .collectAsStateWithLifecycle()
            val panelConfig by settingsViewModel.floatingDashboardConfig(panelId)
                .collectAsStateWithLifecycle()
            val widgetConfigs = panelConfig.widgetsConfig
            val totalTiles = panelConfig.rows * panelConfig.cols
            if (widgetIndex !in 0 until totalTiles) {
                LaunchedEffect(panelId, widgetIndex) {
                    floatingOverlayEditRequest = null
                }
            } else {
                LaunchedEffect(
                    widgetConfigs,
                    panelConfig.rows,
                    panelConfig.cols,
                    context
                ) {
                    val totalWidgets = panelConfig.rows * panelConfig.cols
                    val widgets = loadWidgetsFromConfig(
                        configs = widgetConfigs,
                        widgetCount = totalWidgets,
                        context = context,
                        defaultBackgroundLight = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING,
                        defaultBackgroundDark = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
                    )
                    dashboardViewModel.dashboardManager.updateWidgets(widgets)
                }
                FloatingOverlayFloatingPanelWidgetSelectionDialog(
                    dashboardManager = dashboardViewModel.dashboardManager,
                    settingsViewModel = settingsViewModel,
                    panelId = panelId,
                    widgetIndex = widgetIndex,
                    currentWidgets = dashboardState.widgets,
                    currentWidgetConfigs = widgetConfigs,
                    onDismiss = { floatingOverlayEditRequest = null },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreenWallpaperBackground(
    theme: Int,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val canvasBgLight by settingsViewModel.mainScreenCanvasBackgroundLight.collectAsStateWithLifecycle()
    val canvasBgDark by settingsViewModel.mainScreenCanvasBackgroundDark.collectAsStateWithLifecycle()
    val canvasColor = Color(if (theme == 2) canvasBgDark else canvasBgLight)
    val folderLight by settingsViewModel.mainScreenWallpaperLightFolderUri.collectAsStateWithLifecycle()
    val folderDark by settingsViewModel.mainScreenWallpaperDarkFolderUri.collectAsStateWithLifecycle()
    val selectedLight by settingsViewModel.mainScreenWallpaperLightSelectedFile.collectAsStateWithLifecycle()
    val selectedDark by settingsViewModel.mainScreenWallpaperDarkSelectedFile.collectAsStateWithLifecycle()
    val epoch by settingsViewModel.mainScreenWallpaperEpoch.collectAsStateWithLifecycle()
    val wallpaperCrop by settingsViewModel.isMainScreenWallpaperCrop.collectAsStateWithLifecycle()
    val folderUriStr = if (theme == 2) folderDark else folderLight
    val savedSelectedName = if (theme == 2) selectedDark else selectedLight
    val folderUri = remember(folderUriStr) {
        if (folderUriStr.isBlank()) null else Uri.parse(folderUriStr)
    }
    var sortedPairs by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
    LaunchedEffect(folderUriStr, epoch) {
        sortedPairs = if (folderUri == null) {
            emptyList()
        } else {
            listSortedWallpaperImagesInFolder(context, folderUri)
        }
    }
    val sortedNames = remember(sortedPairs) { sortedPairs.map { it.first } }
    val uriByFileName = remember(sortedPairs) { sortedPairs.toMap() }
    val effectiveName = remember(sortedNames, savedSelectedName) {
        effectiveWallpaperFileName(sortedNames, savedSelectedName)
    }
    LaunchedEffect(effectiveName, savedSelectedName, sortedNames, theme) {
        val want = effectiveName ?: return@LaunchedEffect
        if (want != savedSelectedName) {
            if (theme == 2) {
                settingsViewModel.saveMainScreenWallpaperDarkSelectedFileName(want)
            } else {
                settingsViewModel.saveMainScreenWallpaperLightSelectedFileName(want)
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(canvasColor)
        )
        if (sortedNames.isEmpty() || effectiveName == null) {
            return@Box
        }
        val targetIdx = sortedNames.indexOf(effectiveName).coerceIn(0, sortedNames.lastIndex)
        key(folderUriStr, sortedNames.size) {
            val pagerState = rememberPagerState(
                initialPage = targetIdx,
                pageCount = { sortedNames.size },
            )
            LaunchedEffect(targetIdx, folderUriStr, sortedNames) {
                if (pagerState.currentPage != targetIdx) {
                    pagerState.scrollToPage(targetIdx)
                }
            }
            LaunchedEffect(pagerState, sortedNames, theme, savedSelectedName) {
                snapshotFlow { pagerState.settledPage }
                    .distinctUntilChanged()
                    .collect { page ->
                        if (page !in sortedNames.indices) return@collect
                        val name = sortedNames[page]
                        if (name != savedSelectedName) {
                            if (theme == 2) {
                                settingsViewModel.saveMainScreenWallpaperDarkSelectedFileName(name)
                            } else {
                                settingsViewModel.saveMainScreenWallpaperLightSelectedFileName(name)
                            }
                        }
                    }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                MainScreenWallpaperPagerPage(
                    page = page,
                    sortedNames = sortedNames,
                    uriByFileName = uriByFileName,
                    context = context,
                    wallpaperCrop = wallpaperCrop,
                )
            }
        }
    }
}

@Composable
private fun MainScreenWallpaperPagerPage(
    page: Int,
    sortedNames: List<String>,
    uriByFileName: Map<String, Uri>,
    context: Context,
    wallpaperCrop: Boolean,
) {
    val nameKey = sortedNames[page]
    val uriForSlide = uriByFileName[nameKey]
    var slideBitmap by remember(page, uriForSlide) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uriForSlide) {
        slideBitmap = if (uriForSlide == null) {
            null
        } else {
            decodeImageBitmapFromUri(context, uriForSlide)
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (slideBitmap != null) {
            Image(
                bitmap = slideBitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (wallpaperCrop) ContentScale.Crop else ContentScale.Fit
            )
        }
    }
}

@Composable
private fun MainScreenDraggableCornerButton(
    icon: ImageVector,
    contentDescription: String,
    iconSize: Dp,
    backgroundColor: Color,
    iconTint: Color,
    maxWidthPx: Float,
    maxHeightPx: Float,
    normalizedX: Float,
    normalizedY: Float,
    onSaveNormalized: (Float, Float) -> Unit,
    onClick: () -> Unit,
) {
    val savedState by rememberUpdatedState(Pair(normalizedX, normalizedY))

    val density = LocalDensity.current
    val btnPx = with(density) { iconSize.toPx() }

    val maxW = maxWidthPx
    val maxH = maxHeightPx

    var offsetPx by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(normalizedX, normalizedY, maxW, maxH) {
        if (maxW <= 0f || maxH <= 0f) return@LaunchedEffect
        val rangeW = (maxW - btnPx).coerceAtLeast(0f)
        val rangeH = (maxH - btnPx).coerceAtLeast(0f)
        offsetPx = Offset(
            x = (normalizedX * rangeW).coerceIn(0f, rangeW),
            y = (normalizedY * rangeH).coerceIn(0f, rangeH)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(offsetPx.x.roundToInt(), offsetPx.y.roundToInt())
                }
                .size(iconSize)
                .then(
                    if (backgroundColor.alpha > 0) {
                        Modifier.clip(CircleShape).background(backgroundColor)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .pointerInput(maxW, maxH, btnPx) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val rangeW = (maxW - btnPx).coerceAtLeast(0f)
                            val rangeH = (maxH - btnPx).coerceAtLeast(0f)
                            offsetPx = Offset(
                                x = (offsetPx.x + dragAmount.x).coerceIn(0f, rangeW),
                                y = (offsetPx.y + dragAmount.y).coerceIn(0f, rangeH)
                            )
                        },
                        onDragEnd = {
                            val rangeW = (maxW - btnPx).coerceAtLeast(1f)
                            val rangeH = (maxH - btnPx).coerceAtLeast(1f)
                            onSaveNormalized(
                                (offsetPx.x / rangeW).coerceIn(0f, 1f),
                                (offsetPx.y / rangeH).coerceIn(0f, 1f)
                            )
                        },
                        onDragCancel = {
                            val s = savedState
                            val rangeW = (maxW - btnPx).coerceAtLeast(0f)
                            val rangeH = (maxH - btnPx).coerceAtLeast(0f)
                            offsetPx = Offset(
                                x = (s.first * rangeW).coerceIn(0f, rangeW),
                                y = (s.second * rangeH).coerceIn(0f, rangeH)
                            )
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.fillMaxSize(0.62f)
            )
        }
    }
}
