package vad.dashing.tbox.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import kotlin.math.roundToInt
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.snapshotFlow
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingPanelEditModeTracker
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
import vad.dashing.tbox.MainScreenPageNextButtonPosition
import vad.dashing.tbox.MainScreenPagePrevButtonPosition
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.isVisibleOnMainScreenPage
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.loadWidgetsFromConfig

internal class MainScreenWallpaperController {
    var stepWallpaper: ((direction: Int) -> Unit)? = null

    fun step(direction: Int) {
        stepWallpaper?.invoke(direction)
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val pageCount by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    val currentPage by settingsViewModel.mainScreenCurrentPage.collectAsStateWithLifecycle()
    val settingsBtnPos by settingsViewModel.mainScreenSettingsButtonPosition.collectAsStateWithLifecycle()
    val addBtnPos by settingsViewModel.mainScreenAddButtonPosition.collectAsStateWithLifecycle()
    val pagePrevBtnPos by settingsViewModel.mainScreenPagePrevButtonPosition.collectAsStateWithLifecycle()
    val pageNextBtnPos by settingsViewModel.mainScreenPageNextButtonPosition.collectAsStateWithLifecycle()
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
    val wallpaperController = remember { MainScreenWallpaperController() }
    var wallpaperCount by remember { mutableIntStateOf(0) }
    val multiPage = pageCount > 1
    val showWallpaperNavButtons = multiPage && wallpaperCount > 1

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        settingsViewModel.flushMainScreenCurrentPage()
        settingsViewModel.flushMainScreenWallpaperSelection()
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
        MainScreenWallpaperBackground(
            theme = currentTheme,
            settingsViewModel = settingsViewModel,
            currentMainScreenPage = currentPage,
            userScrollEnabled = !multiPage,
            wallpaperController = wallpaperController,
            onWallpaperCountChanged = { wallpaperCount = it },
            modifier = Modifier.fillMaxSize()
        )
        val maxWpx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val maxHpx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        if (multiPage) {
            key(pageCount) {
                val pagePagerState = rememberPagerState(
                    initialPage = (currentPage - 1).coerceIn(0, pageCount - 1),
                    pageCount = { pageCount },
                )
                val currentPageState by rememberUpdatedState(currentPage)
                LaunchedEffect(currentPage, pageCount) {
                    val want = (currentPage - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    if (pagePagerState.currentPage != want) {
                        pagePagerState.animateScrollToPage(want)
                    }
                }
                LaunchedEffect(pagePagerState, pageCount) {
                    snapshotFlow { pagePagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { settled ->
                            val page = (settled + 1).coerceIn(1, pageCount)
                            if (page != currentPageState) {
                                settingsViewModel.scheduleSaveMainScreenCurrentPage(page)
                            }
                        }
                }
                HorizontalPager(
                    state = pagePagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { pageIndex ->
                    MainScreenPagePanels(
                        pageNumber = pageIndex + 1,
                        pageCount = pageCount,
                        mainPanels = mainPanels,
                        maxWpx = maxWpx,
                        maxHpx = maxHpx,
                        tboxViewModel = tboxViewModel,
                        canViewModel = canViewModel,
                        appDataViewModel = appDataViewModel,
                        settingsViewModel = settingsViewModel,
                        onRebootTbox = onTboxRestart,
                        onTripFinishAndStart = onTripFinishAndStart,
                    )
                }
            }
        } else {
            MainScreenPagePanels(
                pageNumber = currentPage,
                pageCount = pageCount,
                mainPanels = mainPanels,
                maxWpx = maxWpx,
                maxHpx = maxHpx,
                tboxViewModel = tboxViewModel,
                canViewModel = canViewModel,
                appDataViewModel = appDataViewModel,
                settingsViewModel = settingsViewModel,
                onRebootTbox = onTboxRestart,
                onTripFinishAndStart = onTripFinishAndStart,
            )
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
                settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName, currentPage)
            },
        )

        if (showWallpaperNavButtons) {
            MainScreenDraggableCornerButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.main_screen_wallpaper_prev_cd),
                iconSize = cornerIconSize,
                backgroundColor = cornerBackgroundColor,
                iconTint = cornerIconTint,
                maxWidthPx = maxWpx,
                maxHeightPx = maxHpx,
                normalizedX = pagePrevBtnPos.x,
                normalizedY = pagePrevBtnPos.y,
                onSaveNormalized = { x, y ->
                    settingsViewModel.saveMainScreenPagePrevButton(MainScreenPagePrevButtonPosition(x, y))
                },
                onClick = { wallpaperController.step(-1) },
                onHorizontalSwipe = wallpaperController::step,
            )
            MainScreenDraggableCornerButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.main_screen_wallpaper_next_cd),
                iconSize = cornerIconSize,
                backgroundColor = cornerBackgroundColor,
                iconTint = cornerIconTint,
                maxWidthPx = maxWpx,
                maxHeightPx = maxHpx,
                normalizedX = pageNextBtnPos.x,
                normalizedY = pageNextBtnPos.y,
                onSaveNormalized = { x, y ->
                    settingsViewModel.saveMainScreenPageNextButton(MainScreenPageNextButtonPosition(x, y))
                },
                onClick = { wallpaperController.step(1) },
                onHorizontalSwipe = wallpaperController::step,
            )
        }

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
                DisposableEffect(panelId) {
                    FloatingPanelEditModeTracker.setTileEditDialogOpen(panelId, true)
                    onDispose {
                        FloatingPanelEditModeTracker.setTileEditDialogOpen(panelId, false)
                    }
                }
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
                    currentTheme = currentTheme,
                    onDismiss = { floatingOverlayEditRequest = null },
                )
            }
        }
    }
}

@Composable
private fun MainScreenPagePanels(
    pageNumber: Int,
    pageCount: Int,
    mainPanels: List<MainScreenPanelConfig>,
    maxWpx: Float,
    maxHpx: Float,
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
    onRebootTbox: () -> Unit,
    onTripFinishAndStart: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        mainPanels.filter { it.isVisibleOnMainScreenPage(pageCount, pageNumber) }.forEach { panel ->
            key(panel.id) {
                MainScreenDashboardPanel(
                    panel = panel,
                    containerWidthPx = maxWpx,
                    containerHeightPx = maxHpx,
                    tboxViewModel = tboxViewModel,
                    canViewModel = canViewModel,
                    appDataViewModel = appDataViewModel,
                    settingsViewModel = settingsViewModel,
                    onRebootTbox = onRebootTbox,
                    onTripFinishAndStart = onTripFinishAndStart,
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
    currentMainScreenPage: Int,
    userScrollEnabled: Boolean,
    wallpaperController: MainScreenWallpaperController,
    onWallpaperCountChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val canvasBgLight by settingsViewModel.mainScreenCanvasBackgroundLight.collectAsStateWithLifecycle()
    val canvasBgDark by settingsViewModel.mainScreenCanvasBackgroundDark.collectAsStateWithLifecycle()
    val canvasColor = Color(if (theme == 2) canvasBgDark else canvasBgLight)
    val folderLight by settingsViewModel.mainScreenWallpaperLightFolderUri.collectAsStateWithLifecycle()
    val folderDark by settingsViewModel.mainScreenWallpaperDarkFolderUri.collectAsStateWithLifecycle()
    val wallpaperSelections by settingsViewModel.mainScreenWallpaperSelectionsByPage.collectAsStateWithLifecycle()
    val epoch by settingsViewModel.mainScreenWallpaperEpoch.collectAsStateWithLifecycle()
    val wallpaperCrop by settingsViewModel.isMainScreenWallpaperCrop.collectAsStateWithLifecycle()
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val forLightTheme = theme != 2
    val folderUriStr = if (forLightTheme) folderLight else folderDark
    val folderUri = remember(folderUriStr) {
        if (folderUriStr.isBlank()) null else Uri.parse(folderUriStr)
    }
    var displayedFileName by remember(folderUriStr) { mutableStateOf<String?>(null) }
    // Keyed by folder so on theme switch we never keep the previous folder's listing for one frame
    // (that paired the wrong sortedNames with the new theme's saved selection and could persist
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
    LaunchedEffect(sortedNames.size) {
        onWallpaperCountChanged(sortedNames.size)
    }
    LaunchedEffect(currentMainScreenPage, wallpaperSelections, forLightTheme, sortedNames, folderUriStr) {
        val saved = wallpaperSelections.fileNameFor(currentMainScreenPage, forLightTheme)
        if (saved != null) {
            val effective = effectiveWallpaperFileName(sortedNames, saved)
            if (effective != null) {
                displayedFileName = effective
            }
        } else if (displayedFileName == null && sortedNames.isNotEmpty()) {
            displayedFileName = effectiveWallpaperFileName(sortedNames, "")
        }
    }
    val effectiveName = remember(sortedNames, displayedFileName) {
        displayedFileName?.let { effectiveWallpaperFileName(sortedNames, it) }
            ?: effectiveWallpaperFileName(sortedNames, "")
    }
    LaunchedEffect(effectiveName, wallpaperSelections, sortedNames, currentMainScreenPage, forLightTheme) {
        if (!wallpaperSelections.hasSelectionFor(currentMainScreenPage, forLightTheme)) return@LaunchedEffect
        val saved = wallpaperSelections.fileNameFor(currentMainScreenPage, forLightTheme) ?: return@LaunchedEffect
        val want = effectiveName ?: return@LaunchedEffect
        if (want != saved) {
            settingsViewModel.scheduleSaveMainScreenWallpaperSelection(
                forLightTheme = forLightTheme,
                fileName = want,
                page = currentMainScreenPage,
            )
            displayedFileName = want
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
        val wallpaperNamesKey = sortedNames.joinToString("\u0000")
        val scope = rememberCoroutineScope()
        key(folderUriStr, wallpaperNamesKey) {
            val pagerState = rememberPagerState(
                initialPage = initialPagerPage,
                pageCount = { pagerPageCount },
            )
            DisposableEffect(wallpaperController, wallpaperCount, pagerState) {
                wallpaperController.stepWallpaper = { direction ->
                    scope.launch {
                        if (wallpaperCount <= 1) return@launch
                        val logical = logicalIndexFromMainScreenWallpaperPagerPage(
                            pagerState.settledPage,
                            wallpaperCount,
                        ) ?: logicalIndexFromMainScreenWallpaperPagerPage(
                            pagerState.currentPage,
                            wallpaperCount,
                        ) ?: return@launch
                        val newLogical = when {
                            direction < 0 && logical <= 0 -> wallpaperCount - 1
                            direction > 0 && logical >= wallpaperCount - 1 -> 0
                            else -> logical + direction
                        }
                        val targetPage = mainScreenWallpaperPagerPageForLogicalIndex(newLogical, wallpaperCount)
                        pagerState.animateScrollToPage(targetPage)
                    }
                }
                onDispose {
                    wallpaperController.stepWallpaper = null
                }
            }
            val wallpaperBitmapCache = remember(folderUriStr, wallpaperNamesKey) {
                mutableStateMapOf<String, ImageBitmap>()
            }
            val wallpaperLoading = remember(folderUriStr, wallpaperNamesKey) {
                mutableStateMapOf<String, Boolean>()
            }
            val prefetchMutex = remember(folderUriStr, wallpaperNamesKey) { Mutex() }
            var prefetchGeneration by remember(folderUriStr, wallpaperNamesKey) { mutableIntStateOf(0) }
            LaunchedEffect(themeActivating) {
                if (themeActivating) {
                    prefetchGeneration += 1
                    wallpaperBitmapCache.clear()
                    wallpaperLoading.clear()
                }
            }
            LaunchedEffect(folderUriStr, wallpaperNamesKey, theme) {
                val wantPage = mainScreenWallpaperPagerPageForLogicalIndex(targetIdx, wallpaperCount)
                if (pagerState.currentPage != wantPage) {
                    pagerState.scrollToPage(wantPage)
                }
            }
            LaunchedEffect(targetIdx, sortedNames, uriByFileName, decodeTargetWidthPx, decodeTargetHeightPx, themeActivating) {
                if (themeActivating) return@LaunchedEffect
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
                    isThemeActivating = { settingsViewModel.themeActivationInProgress.value },
                )
            }
            LaunchedEffect(pagerState, sortedNames, wallpaperCount, decodeTargetWidthPx, decodeTargetHeightPx, themeActivating) {
                if (themeActivating) return@LaunchedEffect
                var previousTarget = pagerState.targetPage
                var previousSettled = pagerState.settledPage
                snapshotFlow { Triple(pagerState.targetPage, pagerState.currentPage, pagerState.settledPage) }
                    .collectLatest { (targetPage, currentPage, settledPage) ->
                        if (settingsViewModel.themeActivationInProgress.value) return@collectLatest
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
                            isThemeActivating = { settingsViewModel.themeActivationInProgress.value },
                        )
                    }
            }
            val currentMainScreenPageState by rememberUpdatedState(currentMainScreenPage)
            LaunchedEffect(pagerState, sortedNames, theme, wallpaperCount) {
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
                        displayedFileName = name
                        settingsViewModel.scheduleSaveMainScreenWallpaperSelection(
                            forLightTheme = forLightTheme,
                            fileName = name,
                            page = currentMainScreenPageState,
                        )
                    }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                userScrollEnabled = userScrollEnabled,
            ) { pagerPage ->
                val logicalIndex = logicalIndexFromMainScreenWallpaperPagerPage(pagerPage, wallpaperCount)
                if (logicalIndex != null) {
                    MainScreenWallpaperPagerPage(
                        wallpaperIndex = logicalIndex,
                        sortedNames = sortedNames,
                        bitmapCache = wallpaperBitmapCache,
                        wallpaperCrop = wallpaperCrop,
                        suppressBitmapDraw = themeActivating,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScreenWallpaperPagerPage(
    wallpaperIndex: Int,
    sortedNames: List<String>,
    bitmapCache: SnapshotStateMap<String, ImageBitmap>,
    wallpaperCrop: Boolean,
    suppressBitmapDraw: Boolean,
) {
    val nameKey = sortedNames[wallpaperIndex]
    val slideBitmap = if (suppressBitmapDraw) null else bitmapCache[nameKey]
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
    isThemeActivating: suspend () -> Boolean,
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
            if (generation != currentGeneration()) return@withLock
            if (!coroutineContext.isActive || isThemeActivating()) return@withLock
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
                if (!coroutineContext.isActive || isThemeActivating()) return@withLock
                if (
                    decoded != null &&
                    name in keepNames &&
                    generation == currentGeneration()
                ) {
                    bitmapCache[name] = decoded
                }
            } finally {
                loadingState.remove(name)
            }
        }
        if (generation != currentGeneration()) return
        if (isThemeActivating()) return
        delay(150)
        if (generation != currentGeneration()) return
        if (isThemeActivating()) return
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
    onHorizontalSwipe: ((direction: Int) -> Unit)? = null,
) {
    val savedState by rememberUpdatedState(Pair(normalizedX, normalizedY))

    val density = LocalDensity.current
    val btnPx = with(density) { iconSize.toPx() }
    val swipeThresholdPx = with(density) { 32.dp.toPx() }

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
                .clickableWithSound(onClick = onClick)
                .then(
                    if (onHorizontalSwipe != null) {
                        Modifier.pointerInput(swipeThresholdPx) {
                            var totalDx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDx = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDx += dragAmount },
                                onDragEnd = {
                                    if (abs(totalDx) >= swipeThresholdPx) {
                                        onHorizontalSwipe(if (totalDx < 0f) -1 else 1)
                                    }
                                },
                                onDragCancel = { totalDx = 0f },
                            )
                        }
                    } else {
                        Modifier
                    }
                )
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
