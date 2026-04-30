package vad.dashing.tbox.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
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
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import vad.dashing.tbox.logicalIndexFromMainScreenWallpaperPagerPage
import vad.dashing.tbox.mainScreenWallpaperPagerPageCount
import vad.dashing.tbox.mainScreenWallpaperPagerPageForLogicalIndex
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
    val pagingEnabled by settingsViewModel.mainScreenPagingEnabled.collectAsStateWithLifecycle()
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
    val driveModeLauncherPresetsEnabled by settingsViewModel.launcherDriveModePresetsEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(driveModeLauncherPresetsEnabled) {
        if (!driveModeLauncherPresetsEnabled) return@LaunchedEffect
        combine(
            canViewModel.gearBoxDriveMode,
            settingsViewModel.launcherDriveModePresetBundleIds
        ) { mode, map -> mode.trim() to map }
            .distinctUntilChanged()
            .collectLatest { (mode, map) ->
                if (mode.isEmpty()) return@collectLatest
                val bundleId = map[mode]?.trim().orEmpty()
                if (bundleId.isEmpty()) return@collectLatest
                withContext(Dispatchers.IO) {
                    settingsViewModel.applyLauncherBundleById(bundleId)
                }
            }
    }

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
        val maxWpx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxHpx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val enabledPanels = mainPanels.filter { it.enabled }

        if (pagingEnabled) {
            val pinnedPanels = enabledPanels.filter { it.pageIndex < 0 }
            val pagedPanels = enabledPanels.filter { it.pageIndex >= 0 }
            // Always keep at least one page (page 0) so swipe is available once any panel is assigned.
            val pageCount = (pagedPanels.maxOfOrNull { it.pageIndex } ?: 0) + 1
            val pagerState = rememberPagerState(pageCount = { pageCount })
            MainScreenWallpaperBackground(
                theme = currentTheme,
                settingsViewModel = settingsViewModel,
                syncedPanelPage = pagerState.currentPage,
                modifier = Modifier.fillMaxSize()
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(modifier = Modifier.fillMaxSize()) {
                    pinnedPanels.forEach { panel ->
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
                    pagedPanels.filter { it.pageIndex == page }.forEach { panel ->
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
                }
            }
            if (pageCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pageCount) { i ->
                        val selected = pagerState.currentPage == i
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    cornerBackgroundColor.copy(alpha = if (selected) 0.85f else 0.4f)
                                )
                        )
                    }
                }
            }
        } else {
            MainScreenWallpaperBackground(
                theme = currentTheme,
                settingsViewModel = settingsViewModel,
                modifier = Modifier.fillMaxSize()
            )
            enabledPanels.forEach { panel ->
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
    syncedPanelPage: Int? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
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
    // Keyed by folder so on theme switch we never keep the previous folder's listing for one frame
    // (that paired the wrong sortedNames with the new theme's savedSelectedName and could persist
    // a bogus filename into DataStore via LaunchedEffect below).
    var sortedPairs by remember(folderUriStr) { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
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
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val decodeTargetWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val decodeTargetHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        Box(
            Modifier
                .fillMaxSize()
                .background(canvasColor)
        )
        if (sortedNames.isEmpty() || effectiveName == null) {
            return@BoxWithConstraints
        }
        val targetIdx = sortedNames.indexOf(effectiveName).coerceIn(0, sortedNames.lastIndex)
        val wallpaperCount = sortedNames.size
        val pagerPageCount = mainScreenWallpaperPagerPageCount(wallpaperCount)
        val initialPagerPage = mainScreenWallpaperPagerPageForLogicalIndex(targetIdx, wallpaperCount)
        key(folderUriStr, sortedNames.size) {
            val pagerState = rememberPagerState(
                initialPage = initialPagerPage,
                pageCount = { pagerPageCount },
            )
            val wallpaperBitmapCache = remember(folderUriStr, sortedNames.size) {
                mutableStateMapOf<String, ImageBitmap>()
            }
            val wallpaperLoading = remember(folderUriStr, sortedNames.size) {
                mutableStateMapOf<String, Boolean>()
            }
            val prefetchMutex = remember(folderUriStr, sortedNames.size) { Mutex() }
            var prefetchGeneration by remember(folderUriStr, sortedNames.size) { mutableIntStateOf(0) }
            LaunchedEffect(targetIdx, folderUriStr, sortedNames) {
                val wantPage = mainScreenWallpaperPagerPageForLogicalIndex(targetIdx, wallpaperCount)
                if (pagerState.currentPage != wantPage) {
                    pagerState.animateScrollToPage(wantPage)
                }
            }
            // When panel paging is enabled, the foreground pager consumes gestures; sync wallpaper to panel page.
            LaunchedEffect(syncedPanelPage, wallpaperCount) {
                val page = syncedPanelPage ?: return@LaunchedEffect
                if (wallpaperCount <= 1) return@LaunchedEffect
                val logical = (page % wallpaperCount).coerceAtLeast(0)
                val want = mainScreenWallpaperPagerPageForLogicalIndex(logical, wallpaperCount)
                if (pagerState.currentPage != want) {
                    pagerState.animateScrollToPage(want)
                }
            }
            LaunchedEffect(targetIdx, sortedNames, uriByFileName, decodeTargetWidthPx, decodeTargetHeightPx) {
                prefetchGeneration += 1
                val generation = prefetchGeneration
                prefetchMainScreenWallpaperWindow(
                    context = context,
                    logicalIndex = targetIdx,
                    sortedNames = sortedNames,
                    uriByFileName = uriByFileName,
                    targetWidthPx = decodeTargetWidthPx,
                    targetHeightPx = decodeTargetHeightPx,
                    bitmapCache = wallpaperBitmapCache,
                    loadingState = wallpaperLoading,
                    swipeDirection = 0,
                    generation = generation,
                    currentGeneration = { prefetchGeneration },
                    prefetchMutex = prefetchMutex,
                )
            }
            LaunchedEffect(pagerState, sortedNames, wallpaperCount, decodeTargetWidthPx, decodeTargetHeightPx) {
                var previousTarget = pagerState.targetPage
                var previousSettled = pagerState.settledPage
                snapshotFlow { Triple(pagerState.targetPage, pagerState.currentPage, pagerState.settledPage) }
                    .collectLatest { (targetPage, currentPage, settledPage) ->
                        val pageForPrefetch = when {
                            targetPage != previousTarget -> targetPage
                            currentPage != previousTarget -> currentPage
                            settledPage != previousSettled -> settledPage
                            else -> return@collectLatest
                        }
                        val swipeDirection = when {
                            pageForPrefetch > previousTarget -> 1
                            pageForPrefetch < previousTarget -> -1
                            else -> 0
                        }
                        val logical = logicalIndexFromMainScreenWallpaperPagerPage(pageForPrefetch, wallpaperCount)
                            ?: return@collectLatest
                        previousTarget = targetPage
                        previousSettled = settledPage
                        prefetchGeneration += 1
                        val generation = prefetchGeneration
                        prefetchMainScreenWallpaperWindow(
                            context = context,
                            logicalIndex = logical,
                            sortedNames = sortedNames,
                            uriByFileName = uriByFileName,
                            targetWidthPx = decodeTargetWidthPx,
                            targetHeightPx = decodeTargetHeightPx,
                            bitmapCache = wallpaperBitmapCache,
                            loadingState = wallpaperLoading,
                            swipeDirection = swipeDirection,
                            generation = generation,
                            currentGeneration = { prefetchGeneration },
                            prefetchMutex = prefetchMutex,
                        )
                    }
            }
            LaunchedEffect(pagerState, sortedNames, theme, savedSelectedName, wallpaperCount) {
                snapshotFlow { pagerState.settledPage }
                    .distinctUntilChanged()
                    .collectLatest { page ->
                        if (wallpaperCount > 1) {
                            when (page) {
                                0 -> {
                                    pagerState.scrollToPage(wallpaperCount)
                                    return@collectLatest
                                }
                                wallpaperCount + 1 -> {
                                    pagerState.scrollToPage(1)
                                    return@collectLatest
                                }
                            }
                        }
                        val logical = logicalIndexFromMainScreenWallpaperPagerPage(page, wallpaperCount)
                            ?: return@collectLatest
                        val name = sortedNames[logical]
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
                modifier = Modifier
                    .fillMaxSize()
                    .twoFingerWallpaperSwipe(
                        enabled = syncedPanelPage == null && wallpaperCount > 1,
                        onPrev = {
                            val nextIdx = if (targetIdx - 1 < 0) wallpaperCount - 1 else targetIdx - 1
                            val name = sortedNames[nextIdx]
                            if (theme == 2) {
                                settingsViewModel.saveMainScreenWallpaperDarkSelectedFileName(name)
                            } else {
                                settingsViewModel.saveMainScreenWallpaperLightSelectedFileName(name)
                            }
                        },
                        onNext = {
                            val nextIdx = (targetIdx + 1) % wallpaperCount
                            val name = sortedNames[nextIdx]
                            if (theme == 2) {
                                settingsViewModel.saveMainScreenWallpaperDarkSelectedFileName(name)
                            } else {
                                settingsViewModel.saveMainScreenWallpaperLightSelectedFileName(name)
                            }
                        },
                    ),
                beyondViewportPageCount = 1,
                userScrollEnabled = syncedPanelPage == null,
            ) { pagerPage ->
                val logicalIndex = logicalIndexFromMainScreenWallpaperPagerPage(pagerPage, wallpaperCount)
                if (logicalIndex != null) {
                    MainScreenWallpaperPagerPage(
                        wallpaperIndex = logicalIndex,
                        sortedNames = sortedNames,
                        bitmapCache = wallpaperBitmapCache,
                        wallpaperCrop = wallpaperCrop,
                    )
                }
            }
            if (syncedPanelPage == null && wallpaperCount > 1) {
                MainScreenWallpaperSwitcherOverlay(
                    theme = theme,
                    currentIndex = targetIdx,
                    totalCount = wallpaperCount,
                    onPrev = {
                        val nextIdx = if (targetIdx - 1 < 0) wallpaperCount - 1 else targetIdx - 1
                        val name = sortedNames[nextIdx]
                        if (theme == 2) {
                            settingsViewModel.saveMainScreenWallpaperDarkSelectedFileName(name)
                        } else {
                            settingsViewModel.saveMainScreenWallpaperLightSelectedFileName(name)
                        }
                    },
                    onNext = {
                        val nextIdx = (targetIdx + 1) % wallpaperCount
                        val name = sortedNames[nextIdx]
                        if (theme == 2) {
                            settingsViewModel.saveMainScreenWallpaperDarkSelectedFileName(name)
                        } else {
                            settingsViewModel.saveMainScreenWallpaperLightSelectedFileName(name)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                )
            }
        }
    }
}

private fun Modifier.twoFingerWallpaperSwipe(
    enabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        detectTwoFingerHorizontalSwipe(
            swipeThresholdPx = 140f,
            onSwipeLeft = onNext,
            onSwipeRight = onPrev,
        )
    }
}

private suspend fun PointerInputScope.detectTwoFingerHorizontalSwipe(
    swipeThresholdPx: Float,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    // Simple recognizer: when 2 pointers are down, track average X delta; trigger once per gesture.
    awaitPointerEventScope {
        while (true) {
            var active = false
            var pointerA: PointerId? = null
            var pointerB: PointerId? = null
            var lastAvgX = 0f
            var accumDx = 0f

            // Wait for first down
            val first = awaitFirstDown(requireUnconsumed = false)
            pointerA = first.id

            // Wait until second pointer joins or gesture ends
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    pointerA = pressed[0].id
                    pointerB = pressed[1].id
                    lastAvgX = (pressed[0].position.x + pressed[1].position.x) / 2f
                    active = true
                    break
                }
                if (event.changes.none { it.pressed }) break
            }
            if (!active || pointerA == null || pointerB == null) continue

            // Track movement while both pointers are down
            while (true) {
                val event = awaitPointerEvent()
                val a = event.changes.firstOrNull { it.id == pointerA }
                val b = event.changes.firstOrNull { it.id == pointerB }
                if (a == null || b == null || !a.pressed || !b.pressed) break

                val avgX = (a.position.x + b.position.x) / 2f
                val dx = avgX - lastAvgX
                lastAvgX = avgX
                accumDx += dx

                if (abs(accumDx) >= swipeThresholdPx) {
                    // Consume so it doesn't leak into other handlers.
                    event.changes.forEach { it.consume() }
                    if (accumDx < 0) onSwipeLeft() else onSwipeRight()
                    break
                }
            }
        }
    }
}

@Composable
private fun MainScreenWallpaperSwitcherOverlay(
    theme: Int,
    currentIndex: Int,
    totalCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (theme == 2) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.main_screen_wallpaper_prev_cd),
            )
        }
        Text(
            text = "${currentIndex + 1}/$totalCount",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.main_screen_wallpaper_next_cd),
            )
        }
    }
}

@Composable
private fun MainScreenWallpaperPagerPage(
    wallpaperIndex: Int,
    sortedNames: List<String>,
    bitmapCache: SnapshotStateMap<String, ImageBitmap>,
    wallpaperCrop: Boolean,
) {
    val nameKey = sortedNames[wallpaperIndex]
    val slideBitmap = bitmapCache[nameKey]
    val wallpaperAlpha by animateFloatAsState(
        targetValue = if (slideBitmap != null) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "main_screen_wallpaper_fade_in",
    )
    Box(Modifier.fillMaxSize()) {
        if (slideBitmap != null) {
            Image(
                bitmap = slideBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer(alpha = wallpaperAlpha),
                contentScale = if (wallpaperCrop) ContentScale.Crop else ContentScale.Fit
            )
        }
    }
}

private suspend fun prefetchMainScreenWallpaperWindow(
    context: Context,
    logicalIndex: Int,
    sortedNames: List<String>,
    uriByFileName: Map<String, Uri>,
    targetWidthPx: Int,
    targetHeightPx: Int,
    bitmapCache: SnapshotStateMap<String, ImageBitmap>,
    loadingState: SnapshotStateMap<String, Boolean>,
    swipeDirection: Int = 0,
    generation: Int,
    currentGeneration: () -> Int,
    prefetchMutex: Mutex,
) {
    if (sortedNames.isEmpty()) {
        bitmapCache.clear()
        loadingState.clear()
        return
    }
    prefetchMutex.withLock {
        val keepNames = logicalWindowNames(logicalIndex, sortedNames)
        val orderedNames = prioritizedWindowNames(
            logicalIndex = logicalIndex,
            sortedNames = sortedNames,
            swipeDirection = swipeDirection,
        )
        for (name in orderedNames) {
            if (bitmapCache.containsKey(name) || loadingState[name] == true) continue
            val uri = uriByFileName[name] ?: continue
            loadingState[name] = true
            try {
                val decoded = decodeImageBitmapFromUri(
                    context = context,
                    uri = uri,
                    targetWidthPx = targetWidthPx,
                    targetHeightPx = targetHeightPx,
                )
                if (decoded != null && name in keepNames) {
                    bitmapCache[name] = decoded
                }
            } finally {
                loadingState.remove(name)
            }
        }
        if (generation != currentGeneration()) return
        delay(150)
        if (generation != currentGeneration()) return
        bitmapCache.keys.toList().filter { it !in keepNames }.forEach { bitmapCache.remove(it) }
        loadingState.keys.toList().filter { it !in keepNames }.forEach { loadingState.remove(it) }
    }
}

private fun logicalWindowNames(logicalIndex: Int, sortedNames: List<String>): Set<String> {
    val count = sortedNames.size
    if (count <= 1) return sortedNames.toSet()
    if (count == 2) return setOf(sortedNames[0], sortedNames[1])
    if (count <= 5) return sortedNames.toSet()
    val current = logicalIndex.mod(count)
    val prev2 = (current - 2 + count) % count
    val prev1 = (current - 1 + count) % count
    val next1 = (current + 1) % count
    val next2 = (current + 2) % count
    return setOf(
        sortedNames[prev2],
        sortedNames[prev1],
        sortedNames[current],
        sortedNames[next1],
        sortedNames[next2],
    )
}

private fun prioritizedWindowNames(
    logicalIndex: Int,
    sortedNames: List<String>,
    swipeDirection: Int,
): List<String> {
    val count = sortedNames.size
    if (count <= 1) return sortedNames
    if (count == 2) return sortedNames
    if (count <= 5) return sortedNames
    val current = logicalIndex.mod(count)
    val prev1 = (current - 1 + count) % count
    val prev2 = (current - 2 + count) % count
    val next1 = (current + 1) % count
    val next2 = (current + 2) % count
    return when {
        swipeDirection > 0 -> listOf(
            sortedNames[current],
            sortedNames[next1],
            sortedNames[next2],
            sortedNames[prev1],
            sortedNames[prev2],
        )
        swipeDirection < 0 -> listOf(
            sortedNames[current],
            sortedNames[prev1],
            sortedNames[prev2],
            sortedNames[next1],
            sortedNames[next2],
        )
        else -> listOf(
            sortedNames[current],
            sortedNames[prev1],
            sortedNames[next1],
            sortedNames[prev2],
            sortedNames[next2],
        )
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
