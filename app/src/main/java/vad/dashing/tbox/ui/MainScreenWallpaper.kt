package vad.dashing.tbox.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.decodeImageBitmapFromUri
import vad.dashing.tbox.effectiveWallpaperFileName
import vad.dashing.tbox.listSortedWallpaperImagesInFolder
import vad.dashing.tbox.logicalIndexFromMainScreenWallpaperPagerPage
import vad.dashing.tbox.mainScreenWallpaperPagerPageCount
import vad.dashing.tbox.mainScreenWallpaperPagerPageForLogicalIndex

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
    val selectedLight by settingsViewModel.mainScreenWallpaperLightSelectedFile.collectAsStateWithLifecycle()
    val selectedDark by settingsViewModel.mainScreenWallpaperDarkSelectedFile.collectAsStateWithLifecycle()
    val epoch by settingsViewModel.mainScreenWallpaperEpoch.collectAsStateWithLifecycle()
    val wallpaperCrop by settingsViewModel.isMainScreenWallpaperCrop.collectAsStateWithLifecycle()
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val folderUriStr = if (theme == 2) folderDark else folderLight
    val savedSelectedName = if (theme == 2) selectedDark else selectedLight
    val folderUri = remember(folderUriStr) {
        if (folderUriStr.isBlank()) null else Uri.parse(folderUriStr)
    }
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
            LaunchedEffect(targetIdx, folderUriStr, sortedNames) {
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
                )
            }
            LaunchedEffect(pagerState, sortedNames, wallpaperCount, decodeTargetWidthPx, decodeTargetHeightPx, themeActivating) {
                if (themeActivating) return@LaunchedEffect
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
