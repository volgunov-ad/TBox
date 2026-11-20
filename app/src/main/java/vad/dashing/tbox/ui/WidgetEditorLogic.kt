package vad.dashing.tbox.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT
import vad.dashing.tbox.MIN_FLOATING_PANEL_SIZE_PX
import vad.dashing.tbox.MIN_MAIN_SCREEN_PANEL_REL_FRACTION
import vad.dashing.tbox.MIN_MAIN_SCREEN_PANEL_REL_PERCENT
import vad.dashing.tbox.TileBackgroundImageStorage
import vad.dashing.tbox.normalizePanelCollapseStripLengthPercent
import vad.dashing.tbox.normalizePanelCollapseStripPositionPercent
import kotlin.math.roundToInt

/** Top-level switch of the full-screen tile / panel editor. */
internal enum class WidgetEditorLevel { TILE, PANEL }

internal enum class TileEditorTab { TYPE, VIEW, COLORS, BEHAVIOR }

internal enum class PanelEditorTab { GENERAL, PLACEMENT, GRID, BACKGROUND, COLLAPSE }

/** Grid geometry of the edited panel (rows × cols, spacing, panel chrome) for the preview. */
internal data class WidgetEditorGrid(
    val rows: Int,
    val cols: Int,
    val gridSpacingDp: Int,
    val panelBackgroundColorLight: Int?,
    val panelBackgroundColorDark: Int?,
    val panelShape: Int,
) {
    val tileCount: Int
        get() = (rows * cols).coerceAtLeast(1)
}

/** Pads [configs] with empty tiles up to [count]; never truncates (shrinking happens on Save). */
internal fun padWidgetConfigs(
    configs: List<FloatingDashboardWidgetConfig>,
    count: Int,
): List<FloatingDashboardWidgetConfig> {
    if (configs.size >= count) return configs
    return configs + List(count - configs.size) { FloatingDashboardWidgetConfig(dataKey = "") }
}

/** Number of configured tiles that fall outside a [rows]×[cols] grid and are dropped on Save. */
internal fun countConfiguredTilesOutsideGrid(
    configs: List<FloatingDashboardWidgetConfig>,
    rows: Int,
    cols: Int,
): Int {
    val count = (rows * cols).coerceAtLeast(1)
    return configs.drop(count).count { it.dataKey.isNotEmpty() }
}

/**
 * HTTP-request tiles keep their custom icon under a position-based key
 * ([vad.dashing.tbox.HttpRequestIconPaths.iconKey]); moving them would detach the icon.
 */
internal fun canSwapTiles(
    configs: List<FloatingDashboardWidgetConfig>,
    from: Int,
    to: Int,
): Boolean {
    if (from == to) return false
    if (from !in configs.indices || to !in configs.indices) return false
    return configs[from].dataKey != HTTP_REQUEST_WIDGET_DATA_KEY &&
        configs[to].dataKey != HTTP_REQUEST_WIDGET_DATA_KEY
}

internal fun swapTileConfigs(
    configs: List<FloatingDashboardWidgetConfig>,
    from: Int,
    to: Int,
): List<FloatingDashboardWidgetConfig> {
    if (!canSwapTiles(configs, from, to)) return configs
    val result = configs.toMutableList()
    val tmp = result[from]
    result[from] = result[to]
    result[to] = tmp
    return result
}

/**
 * Copies visual appearance (scales, typography, shape, paddings, tile and control colors)
 * from [source] to [target], keeping the target's type, title text and type-specific options.
 * Tile background images are not copied: their files are stored per tile slot.
 */
internal fun applyTileAppearance(
    target: FloatingDashboardWidgetConfig,
    source: FloatingDashboardWidgetConfig,
): FloatingDashboardWidgetConfig {
    if (target.dataKey.isEmpty()) return target
    return target.copy(
        showTitle = source.showTitle,
        titlePosition = source.titlePosition,
        titleScale = source.titleScale,
        iconScale = source.iconScale,
        textScale = source.textScale,
        textAlign = source.textAlign,
        fontWeight = source.fontWeight,
        shape = source.shape,
        paddingTopPercent = source.paddingTopPercent,
        paddingBottomPercent = source.paddingBottomPercent,
        paddingStartPercent = source.paddingStartPercent,
        paddingEndPercent = source.paddingEndPercent,
        textColorLight = source.textColorLight,
        textColorDark = source.textColorDark,
        backgroundColorLight = source.backgroundColorLight,
        backgroundColorDark = source.backgroundColorDark,
        controlInactiveColorLight = source.controlInactiveColorLight,
        controlInactiveColorDark = source.controlInactiveColorDark,
        controlActiveColorLight = source.controlActiveColorLight,
        controlActiveColorDark = source.controlActiveColorDark,
        controlInactiveBackgroundColorLight = source.controlInactiveBackgroundColorLight,
        controlInactiveBackgroundColorDark = source.controlInactiveBackgroundColorDark,
        controlActiveBackgroundColorLight = source.controlActiveBackgroundColorLight,
        controlActiveBackgroundColorDark = source.controlActiveBackgroundColorDark,
        controlShape = source.controlShape,
        controlPadding = source.controlPadding,
    )
}

/**
 * Picks the file slot for a new tile background image of the tile at [editingIndex].
 * Files are named by slot ([TileBackgroundImageStorage.relativePathFor]); after tiles are
 * reordered or pasted, the slot equal to the tile position may hold another tile's image.
 * Prefers the slot of the tile's own current image, then its position, then the first slot
 * past the grid that no other tile references.
 */
internal fun resolveTileBackgroundStorageSlot(
    panelStorageId: String,
    configs: List<FloatingDashboardWidgetConfig>,
    editingIndex: Int,
    ownPathLight: String?,
    ownPathDark: String?,
): Int {
    val usedByOthers = buildSet {
        configs.forEachIndexed { index, cfg ->
            if (index == editingIndex) return@forEachIndexed
            cfg.tileBackgroundImageRelPathLight?.let { add(it.trim()) }
            cfg.tileBackgroundImageRelPathDark?.let { add(it.trim()) }
        }
    }
    fun isFree(slot: Int): Boolean =
        TileBackgroundImageStorage.relativePathFor(panelStorageId, slot, darkTheme = false) !in usedByOthers &&
            TileBackgroundImageStorage.relativePathFor(panelStorageId, slot, darkTheme = true) !in usedByOthers

    val ownSlots = listOfNotNull(ownPathLight, ownPathDark).mapNotNull { path ->
        (0..maxOf(configs.size, editingIndex + 1) + 64).firstOrNull { slot ->
            TileBackgroundImageStorage.relativePathFor(panelStorageId, slot, darkTheme = false) == path.trim() ||
                TileBackgroundImageStorage.relativePathFor(panelStorageId, slot, darkTheme = true) == path.trim()
        }
    }
    val candidates = ownSlots + editingIndex.coerceAtLeast(0)
    candidates.firstOrNull { isFree(it) }?.let { return it }
    var slot = maxOf(configs.size, editingIndex + 1)
    while (!isFree(slot)) slot++
    return slot
}

/** Start/end of the collapse strip along its edge, as fractions 0..1. */
internal data class CollapseStripSpan(val start: Float, val end: Float)

internal fun collapseStripSpan(lengthPercent: Int, positionPercent: Int): CollapseStripSpan {
    val length = normalizePanelCollapseStripLengthPercent(lengthPercent)
    if (length >= MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT) return CollapseStripSpan(0f, 1f)
    val position = normalizePanelCollapseStripPositionPercent(positionPercent, length)
    return CollapseStripSpan(position / 100f, (position + length) / 100f)
}

/** Strip position (%) that centers a strip of [lengthPercent] at [centerFraction] along the edge. */
internal fun collapseStripPositionForCenter(centerFraction: Float, lengthPercent: Int): Int {
    val length = normalizePanelCollapseStripLengthPercent(lengthPercent)
    val raw = (centerFraction.coerceIn(0f, 1f) * 100f - length / 2f).roundToInt()
    return normalizePanelCollapseStripPositionPercent(raw, length)
}

/**
 * Stored panel frame. Main-screen panels: size in % of the screen, X/Y in % of the free range
 * (screen minus panel, so 0 = flush left/top, 100 = flush right/bottom). Floating panels: px.
 */
internal data class PanelPlacement(val x: Int, val y: Int, val width: Int, val height: Int)

/** How a [PanelPlacement] maps onto the screen. */
internal sealed interface PanelPlacementUnits {
    val keepOnScreen: Boolean
    val minWidthFraction: Float
    val minHeightFraction: Float

    data object MainScreenPercent : PanelPlacementUnits {
        override val keepOnScreen = true
        override val minWidthFraction = MIN_MAIN_SCREEN_PANEL_REL_FRACTION
        override val minHeightFraction = MIN_MAIN_SCREEN_PANEL_REL_FRACTION
    }

    data class Pixels(
        val screenWidth: Int,
        val screenHeight: Int,
        val allowBeyondScreen: Boolean,
    ) : PanelPlacementUnits {
        override val keepOnScreen get() = !allowBeyondScreen
        override val minWidthFraction get() = MIN_FLOATING_PANEL_SIZE_PX / screenWidth.coerceAtLeast(1).toFloat()
        override val minHeightFraction get() = MIN_FLOATING_PANEL_SIZE_PX / screenHeight.coerceAtLeast(1).toFloat()
    }
}

/** Panel frame as fractions of the screen (floating panels may leave 0..1 when allowed). */
internal data class PanelScreenRect(val left: Float, val top: Float, val width: Float, val height: Float)

internal fun panelPlacementToScreenRect(placement: PanelPlacement, units: PanelPlacementUnits): PanelScreenRect =
    when (units) {
        PanelPlacementUnits.MainScreenPercent -> {
            val w = (placement.width / 100f).coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f)
            val h = (placement.height / 100f).coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f)
            PanelScreenRect(
                left = placement.x.coerceIn(0, 100) / 100f * (1f - w),
                top = placement.y.coerceIn(0, 100) / 100f * (1f - h),
                width = w,
                height = h,
            )
        }
        is PanelPlacementUnits.Pixels -> {
            val sw = units.screenWidth.coerceAtLeast(1).toFloat()
            val sh = units.screenHeight.coerceAtLeast(1).toFloat()
            PanelScreenRect(placement.x / sw, placement.y / sh, placement.width / sw, placement.height / sh)
        }
    }

internal fun panelPlacementFromScreenRect(rect: PanelScreenRect, units: PanelPlacementUnits): PanelPlacement =
    when (units) {
        PanelPlacementUnits.MainScreenPercent -> {
            val widthPercent = (rect.width * 100f).roundToInt().coerceIn(MIN_MAIN_SCREEN_PANEL_REL_PERCENT, 100)
            val heightPercent = (rect.height * 100f).roundToInt().coerceIn(MIN_MAIN_SCREEN_PANEL_REL_PERCENT, 100)
            fun freeRangePercent(start: Float, sizePercent: Int): Int {
                val range = 1f - sizePercent / 100f
                if (range <= 0.0001f) return 0
                return (start / range * 100f).roundToInt().coerceIn(0, 100)
            }
            PanelPlacement(
                x = freeRangePercent(rect.left, widthPercent),
                y = freeRangePercent(rect.top, heightPercent),
                width = widthPercent,
                height = heightPercent,
            )
        }
        is PanelPlacementUnits.Pixels -> PanelPlacement(
            x = (rect.left * units.screenWidth).roundToInt(),
            y = (rect.top * units.screenHeight).roundToInt(),
            width = (rect.width * units.screenWidth).roundToInt().coerceAtLeast(MIN_FLOATING_PANEL_SIZE_PX),
            height = (rect.height * units.screenHeight).roundToInt().coerceAtLeast(MIN_FLOATING_PANEL_SIZE_PX),
        )
    }

/** Moves the frame by a screen fraction; stays fully on screen when [PanelPlacementUnits.keepOnScreen]. */
internal fun movePanelScreenRect(
    rect: PanelScreenRect,
    dx: Float,
    dy: Float,
    units: PanelPlacementUnits,
): PanelScreenRect {
    val left = rect.left + dx
    val top = rect.top + dy
    if (!units.keepOnScreen) return rect.copy(left = left, top = top)
    return rect.copy(
        left = left.coerceIn(0f, (1f - rect.width).coerceAtLeast(0f)),
        top = top.coerceIn(0f, (1f - rect.height).coerceAtLeast(0f)),
    )
}

/** Resizes the frame from its bottom-right corner by a screen fraction. */
internal fun resizePanelScreenRect(
    rect: PanelScreenRect,
    dw: Float,
    dh: Float,
    units: PanelPlacementUnits,
): PanelScreenRect {
    val maxWidth = if (units.keepOnScreen) 1f - rect.left else Float.MAX_VALUE
    val maxHeight = if (units.keepOnScreen) 1f - rect.top else Float.MAX_VALUE
    return rect.copy(
        width = (rect.width + dw).coerceIn(units.minWidthFraction, maxWidth.coerceAtLeast(units.minWidthFraction)),
        height = (rect.height + dh).coerceIn(units.minHeightFraction, maxHeight.coerceAtLeast(units.minHeightFraction)),
    )
}

/** Centers the frame horizontally ([horizontal]) or vertically, keeping its size. */
internal fun centerPanelPlacement(
    placement: PanelPlacement,
    units: PanelPlacementUnits,
    horizontal: Boolean,
): PanelPlacement = when (units) {
    PanelPlacementUnits.MainScreenPercent ->
        if (horizontal) placement.copy(x = 50) else placement.copy(y = 50)
    is PanelPlacementUnits.Pixels ->
        if (horizontal) {
            placement.copy(x = ((units.screenWidth - placement.width) / 2).coerceAtLeast(0))
        } else {
            placement.copy(y = ((units.screenHeight - placement.height) / 2).coerceAtLeast(0))
        }
}

/** Distinct non-empty tile types of a panel, in grid order. */
internal fun tileTypesOnPanel(configs: List<FloatingDashboardWidgetConfig>): List<String> =
    configs.map { it.dataKey }.filter { it.isNotEmpty() }.distinct()

/** Process-wide list of recently chosen tile types (most recent first); not persisted. */
internal object WidgetEditorRecentTypes {
    private const val MAX_SIZE = 8

    var keys by mutableStateOf<List<String>>(emptyList())
        private set

    fun record(dataKey: String) {
        if (dataKey.isEmpty()) return
        keys = (listOf(dataKey) + keys.filter { it != dataKey }).take(MAX_SIZE)
    }
}
