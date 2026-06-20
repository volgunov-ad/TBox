package vad.dashing.tbox.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.MainActivityLoadTimings
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.decodeImageBitmapFromUri
import vad.dashing.tbox.effectiveWallpaperFileName
import vad.dashing.tbox.listSortedWallpaperImagesInFolder
import vad.dashing.tbox.logicalIndexFromMainScreenWallpaperPagerPage
import vad.dashing.tbox.mainScreenWallpaperPagerPageCount
import vad.dashing.tbox.mainScreenWallpaperPagerPageForLogicalIndex

internal const val MAIN_SCREEN_WALLPAPER_CROSSFADE_MS = 400

internal class MainScreenWallpaperController {
    var stepWallpaper: ((direction: Int) -> Unit)? = null

    fun step(direction: Int) {
        stepWallpaper?.invoke(direction)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MainScreenWallpaperBackground(
    theme: Int,
    settingsViewModel: SettingsViewModel,
    currentMainScreenPage: Int,
    userScrollEnabled: Boolean,
    wallpaperController: MainScreenWallpaperController,
    onWallpaperCountChanged: (Int) -> Unit,
    folderUriStr: String,
    forLightTheme: Boolean,
    wallpaperWorkEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val canvasBgLight by settingsViewModel.mainScreenCanvasBackgroundLight.collectAsStateWithLifecycle()
    val canvasBgDark by settingsViewModel.mainScreenCanvasBackgroundDark.collectAsStateWithLifecycle()
    val canvasColor = Color(if (theme == 2) canvasBgDark else canvasBgLight)
    val hasWallpaperFolder = folderUriStr.isNotBlank()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(canvasColor)
        )
        if (hasWallpaperFolder && wallpaperWorkEnabled) {
            MainScreenWallpaperFolderContent(
                settingsViewModel = settingsViewModel,
                currentMainScreenPage = currentMainScreenPage,
                userScrollEnabled = userScrollEnabled,
                wallpaperController = wallpaperController,
                onWallpaperCountChanged = onWallpaperCountChanged,
                folderUriStr = folderUriStr,
                forLightTheme = forLightTheme,
                maxWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) },
                maxHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) },
            )
        } else {
            LaunchedEffect(Unit) {
                onWallpaperCountChanged(0)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreenWallpaperFolderContent(
    settingsViewModel: SettingsViewModel,
    currentMainScreenPage: Int,
    userScrollEnabled: Boolean,
    wallpaperController: MainScreenWallpaperController,
    onWallpaperCountChanged: (Int) -> Unit,
    folderUriStr: String,
    forLightTheme: Boolean,
    maxWidthPx: Int,
    maxHeightPx: Int,
) {
    val context = LocalContext.current
    val folderUri = remember(folderUriStr) { Uri.parse(folderUriStr) }
    val wallpaperSelections by settingsViewModel.mainScreenWallpaperSelectionsByPage.collectAsStateWithLifecycle()
    val activeThemeUri by settingsViewModel.activeThemeUri.collectAsStateWithLifecycle()
    val epoch by settingsViewModel.mainScreenWallpaperEpoch.collectAsStateWithLifecycle()
    val wallpaperCrop by settingsViewModel.isMainScreenWallpaperCrop.collectAsStateWithLifecycle()
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    var displayedFileName by remember(folderUriStr, forLightTheme, epoch, activeThemeUri) {
        mutableStateOf<String?>(null)
    }
    var sortedPairs by remember(folderUriStr) { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
    LaunchedEffect(folderUriStr, epoch) {
        MainActivityLoadTimings.mark("main_wallpaper_list_begin")
        sortedPairs = listSortedWallpaperImagesInFolder(context, folderUri)
        MainActivityLoadTimings.mark("main_wallpaper_list_done")
    }
    val sortedNames = remember(sortedPairs) { sortedPairs.map { it.first } }
    val uriByFileName = remember(sortedPairs) { sortedPairs.toMap() }
    LaunchedEffect(sortedNames.size) {
        onWallpaperCountChanged(sortedNames.size)
    }
    val savedForPage = wallpaperSelections.fileNameFor(currentMainScreenPage, forLightTheme)
    val effectiveName = remember(sortedNames, displayedFileName, savedForPage, forLightTheme) {
        if (sortedNames.isEmpty()) {
            null
        } else {
            when {
                savedForPage != null -> effectiveWallpaperFileName(sortedNames, savedForPage)
                displayedFileName != null -> effectiveWallpaperFileName(sortedNames, displayedFileName!!)
                else -> effectiveWallpaperFileName(sortedNames, "")
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val decodeTargetWidthPx = maxWidthPx
        val decodeTargetHeightPx = maxHeightPx
        if (sortedNames.isEmpty() || effectiveName == null || themeActivating) {
            return@Box
        }
        val targetIdx = sortedNames.indexOf(effectiveName)
        if (targetIdx < 0) {
            return@Box
        }
        val wallpaperCount = sortedNames.size
        val pagerPageCount = mainScreenWallpaperPagerPageCount(wallpaperCount)
        val initialPagerPage = mainScreenWallpaperPagerPageForLogicalIndex(targetIdx, wallpaperCount)
        val wallpaperNamesKey = sortedNames.joinToString("\u0000")
        val scope = rememberCoroutineScope()
        key(folderUriStr, wallpaperNamesKey, epoch, activeThemeUri) {
            val wallpaperBitmapCache = remember(folderUriStr, wallpaperNamesKey) {
                mutableStateMapOf<String, ImageBitmap>()
            }
            val wallpaperLoading = remember(folderUriStr, wallpaperNamesKey) {
                mutableStateMapOf<String, Boolean>()
            }
            val prefetchMutex = remember(folderUriStr, wallpaperNamesKey) { Mutex() }
            var prefetchGeneration by remember(folderUriStr, wallpaperNamesKey) { mutableIntStateOf(0) }
            val currentMainScreenPageState by rememberUpdatedState(currentMainScreenPage)
            val effectiveNameState by rememberUpdatedState(effectiveName)
            val targetIdxState by rememberUpdatedState(targetIdx)
            LaunchedEffect(themeActivating) {
                if (themeActivating) {
                    prefetchGeneration += 1
                    wallpaperBitmapCache.clear()
                    wallpaperLoading.clear()
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
            if (!userScrollEnabled) {
                DisposableEffect(wallpaperController, wallpaperCount, forLightTheme, currentMainScreenPage) {
                    wallpaperController.stepWallpaper = { direction ->
                        scope.launch {
                            if (wallpaperCount <= 1) return@launch
                            val currentName = effectiveNameState ?: return@launch
                            val logical = sortedNames.indexOf(currentName)
                            if (logical < 0) return@launch
                            val newLogical = when {
                                direction < 0 && logical <= 0 -> wallpaperCount - 1
                                direction > 0 && logical >= wallpaperCount - 1 -> 0
                                else -> logical + direction
                            }
                            val newName = sortedNames[newLogical]
                            settingsViewModel.scheduleSaveMainScreenWallpaperSelection(
                                forLightTheme = forLightTheme,
                                fileName = newName,
                                page = currentMainScreenPageState,
                            )
                        }
                    }
                    onDispose {
                        wallpaperController.stepWallpaper = null
                    }
                }
                MainScreenWallpaperCrossfade(
                    effectiveName = effectiveName,
                    sortedNames = sortedNames,
                    bitmapCache = wallpaperBitmapCache,
                    wallpaperCrop = wallpaperCrop,
                    suppressBitmapDraw = themeActivating,
                    forLightTheme = forLightTheme,
                    folderUriStr = folderUriStr,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val pagerState = rememberPagerState(
                    initialPage = initialPagerPage,
                    pageCount = { pagerPageCount },
                )
                var suppressWallpaperSave by remember(activeThemeUri, epoch) { mutableStateOf(true) }
                var saveWallpaperOnNextSettle by remember(activeThemeUri, epoch) { mutableStateOf(false) }
                var wallpaperScrollIsProgrammatic by remember { mutableStateOf(false) }
                var userDraggedWallpaper by remember { mutableStateOf(false) }
                LaunchedEffect(themeActivating) {
                    if (themeActivating) {
                        suppressWallpaperSave = true
                        saveWallpaperOnNextSettle = false
                        userDraggedWallpaper = false
                        wallpaperScrollIsProgrammatic = false
                    }
                }
                LaunchedEffect(pagerState, themeActivating) {
                    if (themeActivating) return@LaunchedEffect
                    var wasScrolling = false
                    snapshotFlow { pagerState.isScrollInProgress }.collect { scrolling ->
                        if (wasScrolling && !scrolling && !wallpaperScrollIsProgrammatic) {
                            userDraggedWallpaper = true
                        }
                        wasScrolling = scrolling
                    }
                }
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
                            saveWallpaperOnNextSettle = true
                            pagerState.animateScrollToPage(targetPage)
                        }
                    }
                    onDispose {
                        wallpaperController.stepWallpaper = null
                    }
                }
                LaunchedEffect(targetIdx, currentMainScreenPage, folderUriStr, wallpaperNamesKey, forLightTheme, themeActivating, activeThemeUri) {
                    if (themeActivating || wallpaperCount <= 0) return@LaunchedEffect
                    val wantPage = mainScreenWallpaperPagerPageForLogicalIndex(targetIdx, wallpaperCount)
                    if (wantPage !in 0 until pagerPageCount) return@LaunchedEffect
                    suppressWallpaperSave = true
                    userDraggedWallpaper = false
                    if (pagerState.currentPage != wantPage) {
                        wallpaperScrollIsProgrammatic = true
                        runCatching { pagerState.scrollToPage(wantPage) }
                        wallpaperScrollIsProgrammatic = false
                    }
                    if (logicalIndexFromMainScreenWallpaperPagerPage(pagerState.settledPage, wallpaperCount) == targetIdx) {
                        suppressWallpaperSave = false
                    }
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
                LaunchedEffect(pagerState, sortedNames, theme, wallpaperCount, themeActivating) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collectLatest { page ->
                            if (themeActivating) return@collectLatest
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
                            if (logical !in sortedNames.indices) return@collectLatest
                            val name = sortedNames[logical]
                            displayedFileName = name
                            if (suppressWallpaperSave) {
                                if (logical == targetIdxState) {
                                    suppressWallpaperSave = false
                                }
                                return@collectLatest
                            }
                            val shouldSave = saveWallpaperOnNextSettle || userDraggedWallpaper
                            if (!shouldSave) return@collectLatest
                            saveWallpaperOnNextSettle = false
                            userDraggedWallpaper = false
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
}

private data class MainScreenWallpaperCrossfadeKey(
    val folderUriStr: String,
    val forLightTheme: Boolean,
    val fileName: String,
)

@Composable
private fun MainScreenWallpaperCrossfade(
    effectiveName: String,
    sortedNames: List<String>,
    bitmapCache: SnapshotStateMap<String, ImageBitmap>,
    wallpaperCrop: Boolean,
    suppressBitmapDraw: Boolean,
    forLightTheme: Boolean,
    folderUriStr: String,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = MainScreenWallpaperCrossfadeKey(folderUriStr, forLightTheme, effectiveName),
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(MAIN_SCREEN_WALLPAPER_CROSSFADE_MS)) togetherWith
                fadeOut(tween(MAIN_SCREEN_WALLPAPER_CROSSFADE_MS))
        },
        label = "main_screen_wallpaper_crossfade",
    ) { key ->
        val idx = sortedNames.indexOf(key.fileName)
        if (idx >= 0) {
            MainScreenWallpaperPagerPage(
                wallpaperIndex = idx,
                sortedNames = sortedNames,
                bitmapCache = bitmapCache,
                wallpaperCrop = wallpaperCrop,
                suppressBitmapDraw = suppressBitmapDraw,
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
    suppressBitmapDraw: Boolean,
) {
    if (wallpaperIndex !in sortedNames.indices) return
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
    val primaryName = sortedNames.getOrNull(logicalIndex)
    var primaryDecodeTimed = false
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
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path
                if (path.isNullOrBlank() || !File(path).isFile) continue
            }
            loadingState[name] = true
            try {
                if (name == primaryName && !primaryDecodeTimed) {
                    MainActivityLoadTimings.mark("main_wallpaper_decode_begin")
                }
                val decoded = decodeImageBitmapFromUri(
                    context = context,
                    uri = uri,
                    targetWidthPx = targetWidthPx,
                    targetHeightPx = targetHeightPx,
                )
                if (name == primaryName && decoded != null && !primaryDecodeTimed) {
                    MainActivityLoadTimings.mark("main_wallpaper_decode_done")
                    primaryDecodeTimed = true
                }
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
