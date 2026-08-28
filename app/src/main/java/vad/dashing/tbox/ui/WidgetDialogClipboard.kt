package vad.dashing.tbox.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.DEFAULT_PANEL_SHAPE

/**
 * Process-wide in-memory clipboard for the widget selection dialog.
 * Separate slots for tile settings and whole-panel settings; not persisted to disk.
 * Shared across main-screen panels, floating panels, and the Tiles tab.
 */
internal object WidgetDialogClipboard {
    var tileSnapshot by mutableStateOf<TileClipboardSnapshot?>(null)
        private set

    var panelSnapshot by mutableStateOf<WholePanelClipboardSnapshot?>(null)
        private set

    val hasTile: Boolean
        get() = tileSnapshot != null

    val hasPanel: Boolean
        get() = panelSnapshot != null

    fun copyTile(snapshot: TileClipboardSnapshot) {
        tileSnapshot = snapshot
    }

    fun copyPanel(snapshot: WholePanelClipboardSnapshot) {
        panelSnapshot = snapshot
    }
}

/**
 * Tile draft clipboard payload.
 * [config] holds all tile fields; control colors in [config] are always the raw draft ints
 * (never null). [controlColorsUseDefaults] is stored separately so paste can restore the switch.
 */
internal data class TileClipboardSnapshot(
    val config: FloatingDashboardWidgetConfig,
    val controlColorsUseDefaults: Boolean,
)

/**
 * Whole-panel clipboard: panel chrome settings plus every tile config (types and settings).
 * Position/size on screen are not included (main vs floating use different models).
 */
internal data class WholePanelClipboardSnapshot(
    val name: String,
    val showTboxDisconnect: Boolean,
    val rows: Int,
    val cols: Int,
    val gridSpacingDp: Int,
    val pageNumber: Int,
    val clickAction: Boolean,
    val collapseEdge: String,
    val collapseStripThicknessDp: Int,
    val collapseTouchZoneThicknessDp: Int,
    val collapseStripColorLight: Int,
    val collapseStripColorDark: Int,
    val collapseStripExpandedColorLight: Int,
    val collapseStripExpandedColorDark: Int,
    val collapseOnStripTap: Boolean,
    val collapseOnTileTap: Boolean,
    val collapseOnTileTapDelaySec: Int,
    val panelBackgroundColorLight: Int? = null,
    val panelBackgroundColorDark: Int? = null,
    val panelBackgroundImageRelPathLight: String? = null,
    val panelBackgroundImageRelPathDark: String? = null,
    val panelShape: Int = DEFAULT_PANEL_SHAPE,
    val widgetsConfig: List<FloatingDashboardWidgetConfig>,
)
