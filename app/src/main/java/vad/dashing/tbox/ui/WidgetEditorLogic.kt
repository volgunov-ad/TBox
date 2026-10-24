package vad.dashing.tbox.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT
import vad.dashing.tbox.TileBackgroundImageStorage
import vad.dashing.tbox.normalizePanelCollapseStripLengthPercent
import vad.dashing.tbox.normalizePanelCollapseStripPositionPercent
import kotlin.math.roundToInt

/** Top-level switch of the full-screen tile / panel editor. */
internal enum class WidgetEditorLevel { TILE, PANEL }

internal enum class TileEditorTab { TYPE, VIEW, COLORS, BEHAVIOR }

internal enum class PanelEditorTab { GENERAL, GRID, BACKGROUND, COLLAPSE }

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
