package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.DAY_NIGHT_THEME_WIDGET_DATA_KEY
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.PanelCollapseEdge

class WidgetSelectionDialogSharedTest {

    private fun dialogState(
        dataKey: String,
        config: FloatingDashboardWidgetConfig = FloatingDashboardWidgetConfig(dataKey = dataKey),
    ): WidgetSelectionDialogState =
        WidgetSelectionDialogState(
            initialDataKey = dataKey,
            initialConfig = config,
            panelDefaultBackgroundLight = 0x01000000,
            panelDefaultBackgroundDark = 0x02000000,
        )

    @Test
    fun resolveStoredMediaSelectedPlayer_returnsCurrentWhenStillSelected() {
        val result = resolveStoredMediaSelectedPlayer(
            selectedPlayers = setOf("ru.yandex.music", "com.maxmpz.audioplayer"),
            currentSelectedPlayer = "com.maxmpz.audioplayer"
        )

        assertEquals("com.maxmpz.audioplayer", result)
    }

    @Test
    fun resolveStoredMediaSelectedPlayer_returnsOnlyPlayerWhenCurrentMissing() {
        val result = resolveStoredMediaSelectedPlayer(
            selectedPlayers = setOf("ru.yandex.music"),
            currentSelectedPlayer = "missing.package"
        )

        assertEquals("ru.yandex.music", result)
    }

    @Test
    fun resolveStoredMediaSelectedPlayer_returnsEmptyForEmptySelection() {
        val result = resolveStoredMediaSelectedPlayer(
            selectedPlayers = emptySet(),
            currentSelectedPlayer = "missing.package"
        )

        assertEquals("", result)
    }

    @Test
    fun descriptionResources_areHiddenForUnselectedItem() {
        val result = resolveWidgetSelectionDescriptionResources(
            dataKey = DAY_NIGHT_THEME_WIDGET_DATA_KEY,
            selectedDataKey = "voltage",
        )

        assertNull(result)
    }

    @Test
    fun descriptionResources_hideActionsForNonInteractiveItem() {
        val result = resolveWidgetSelectionDescriptionResources(
            dataKey = "voltage",
            selectedDataKey = "voltage",
        )

        assertNotNull(result)
        assertNull(result?.actionsRes)
    }

    @Test
    fun descriptionResources_showActionsForInteractiveItem() {
        val result = resolveWidgetSelectionDescriptionResources(
            dataKey = DAY_NIGHT_THEME_WIDGET_DATA_KEY,
            selectedDataKey = DAY_NIGHT_THEME_WIDGET_DATA_KEY,
        )

        assertNotNull(result?.descriptionRes)
        assertNotNull(result?.actionsRes)
    }

    @Test
    fun applyDraft_preserveDataKey_keepsCurrentTypeAndAppliesAppearance() {
        val source = dialogState(
            dataKey = "voltage",
            config = FloatingDashboardWidgetConfig(
                dataKey = "voltage",
                showTitle = true,
                customTitle = "FromClip",
                scale = 1.3f,
            ),
        )
        val snapshot = source.toTileClipboardSnapshot()

        val target = dialogState(
            dataKey = "speed",
            config = FloatingDashboardWidgetConfig(
                dataKey = "speed",
                showTitle = false,
                customTitle = "KeepType",
                scale = 1.0f,
            ),
        )
        target.applyTileClipboardSnapshot(snapshot, preserveDataKey = true)

        assertEquals("speed", target.selectedDataKey)
        assertTrue(target.showTitle)
        assertEquals("FromClip", target.customTitle)
        assertEquals(1.3f, target.scale, 0.001f)
    }

    @Test
    fun applyDraft_preserveDataKey_noopWhenTypeNotSelected() {
        val source = dialogState(
            dataKey = "voltage",
            config = FloatingDashboardWidgetConfig(
                dataKey = "voltage",
                showTitle = true,
                customTitle = "ShouldNotApply",
            ),
        )
        val snapshot = source.toTileClipboardSnapshot()

        val target = dialogState(dataKey = "")
        target.customTitle = "Original"
        target.applyTileClipboardSnapshot(snapshot, preserveDataKey = true)

        assertEquals("", target.selectedDataKey)
        assertEquals("Original", target.customTitle)
        assertFalse(target.showTitle)
    }

    @Test
    fun controlColors_custom_roundTrip_viaClipboardSnapshot() {
        val source = dialogState(dataKey = "voltage")
        source.controlColorsUseDefaults = false
        source.controlInactiveColorLight = 0xFF112233.toInt()
        source.controlActiveColorLight = 0xFF445566.toInt()
        source.controlInactiveBackgroundColorLight = 0xFF778899.toInt()
        source.controlActiveBackgroundColorDark = 0xFFAABBCC.toInt()
        source.controlShape = 12
        source.controlPadding = 8

        val snapshot = source.toTileClipboardSnapshot()
        assertFalse(snapshot.controlColorsUseDefaults)
        assertEquals(0xFF112233.toInt(), snapshot.config.controlInactiveColorLight)
        assertEquals(0xFF445566.toInt(), snapshot.config.controlActiveColorLight)
        assertEquals(12, snapshot.config.controlShape)
        assertEquals(8, snapshot.config.controlPadding)

        val target = dialogState(dataKey = "speed")
        assertTrue(target.controlColorsUseDefaults)
        target.applyTileClipboardSnapshot(snapshot, preserveDataKey = false)

        assertFalse(target.controlColorsUseDefaults)
        assertEquals(0xFF112233.toInt(), target.controlInactiveColorLight)
        assertEquals(0xFF445566.toInt(), target.controlActiveColorLight)
        assertEquals(0xFF778899.toInt(), target.controlInactiveBackgroundColorLight)
        assertEquals(0xFFAABBCC.toInt(), target.controlActiveBackgroundColorDark)
        assertEquals(12, target.controlShape)
        assertEquals(8, target.controlPadding)
    }

    @Test
    fun controlColors_defaultsFlag_roundTrip() {
        val source = dialogState(dataKey = "voltage")
        source.clearControlColorsToDefaults()
        val snapshot = source.toTileClipboardSnapshot()
        assertTrue(snapshot.controlColorsUseDefaults)

        val target = dialogState(dataKey = "speed")
        target.controlColorsUseDefaults = false
        target.controlInactiveColorLight = 0xFF111111.toInt()
        target.applyTileClipboardSnapshot(snapshot, preserveDataKey = true)

        assertTrue(target.controlColorsUseDefaults)
    }

    @Test
    fun toDraft_applyDraft_roundTrip_restoresDataKeyAndAppearance() {
        val source = dialogState(
            dataKey = "voltage",
            config = FloatingDashboardWidgetConfig(
                dataKey = "voltage",
                showTitle = true,
                showUnit = false,
                customTitle = "Batt",
                scale = 1.5f,
                textColorLight = 0xFF111111.toInt(),
                textColorDark = 0xFF222222.toInt(),
            ),
        )
        source.showTitle = true
        source.customTitle = "Batt"
        source.scale = 1.5f
        source.textColorLight = 0xFF111111.toInt()

        val snapshot = source.toTileClipboardSnapshot()
        val target = dialogState(dataKey = "speed")
        target.applyTileClipboardSnapshot(snapshot, preserveDataKey = false)

        assertEquals("voltage", target.selectedDataKey)
        assertTrue(target.showTitle)
        assertFalse(target.showUnit)
        assertEquals("Batt", target.customTitle)
        assertEquals(1.5f, target.scale, 0.001f)
        assertEquals(0xFF111111.toInt(), target.textColorLight)
    }

    @Test
    fun wholePanelClipboard_roundTrip() {
        val state = dialogState(dataKey = "voltage")
        state.wholePanelNameDraft = "Panel A"
        state.wholePanelRows = 3
        state.wholePanelCols = 4
        state.wholePanelGridSpacingDp = 8
        state.wholePanelPageNumber = 2
        state.wholePanelClickAction = true
        state.wholePanelShowTboxDisconnect = true
        state.wholePanelCollapseEdge = PanelCollapseEdge.LEFT.storageValue
        state.wholePanelCollapseOnTileTap = true
        state.wholePanelCollapseOnTileTapDelaySec = 3

        val currentConfigs = List(12) { i ->
            FloatingDashboardWidgetConfig(dataKey = if (i == 0) "voltage" else "speed$i")
        }
        // Align current tile draft with index 0 config used for merge on copy.
        state.selectedDataKey = "voltage"
        val snapshot = state.toWholePanelClipboardSnapshot(currentConfigs, widgetIndex = 0)
        assertEquals(12, snapshot.widgetsConfig.size)
        assertEquals("voltage", snapshot.widgetsConfig[0].dataKey)
        assertEquals("speed1", snapshot.widgetsConfig[1].dataKey)

        val other = dialogState(dataKey = "speed")
        other.applyWholePanelFromClipboard(snapshot, widgetIndex = 0)

        assertEquals("Panel A", other.wholePanelNameDraft)
        assertEquals(3, other.wholePanelRows)
        assertEquals(4, other.wholePanelCols)
        assertEquals(8, other.wholePanelGridSpacingDp)
        assertEquals(2, other.wholePanelPageNumber)
        assertTrue(other.wholePanelClickAction)
        assertTrue(other.wholePanelShowTboxDisconnect)
        assertEquals(PanelCollapseEdge.LEFT.storageValue, other.wholePanelCollapseEdge)
        assertTrue(other.wholePanelCollapseOnTileTap)
        assertEquals(3, other.wholePanelCollapseOnTileTapDelaySec)
        assertTrue(other.wholePanelDraftSeeded)
        assertEquals(12, other.wholePanelWidgetsDraft?.size)
        assertEquals("voltage", other.selectedDataKey)
        assertEquals("voltage", other.wholePanelWidgetsDraft?.get(0)?.dataKey)
        assertEquals("speed1", other.wholePanelWidgetsDraft?.get(1)?.dataKey)
    }

    @Test
    fun wholePanelClipboard_includesAllTilesAndResizesToGrid() {
        val state = dialogState(dataKey = "a")
        state.wholePanelRows = 2
        state.wholePanelCols = 2
        state.wholePanelDraftSeeded = true
        val currentConfigs = listOf(
            FloatingDashboardWidgetConfig(dataKey = "a"),
            FloatingDashboardWidgetConfig(dataKey = "b"),
            FloatingDashboardWidgetConfig(dataKey = "c"),
            FloatingDashboardWidgetConfig(dataKey = "d"),
            FloatingDashboardWidgetConfig(dataKey = "e"),
        )
        val snapshot = state.toWholePanelClipboardSnapshot(currentConfigs, widgetIndex = 0)
        assertEquals(4, snapshot.widgetsConfig.size)
        assertEquals("a", snapshot.widgetsConfig[0].dataKey)
        assertEquals("d", snapshot.widgetsConfig[3].dataKey)

        val target = dialogState(dataKey = "")
        target.applyWholePanelFromClipboard(
            snapshot.copy(
                rows = 1,
                cols = 3,
                widgetsConfig = listOf(
                    FloatingDashboardWidgetConfig(dataKey = "x"),
                    FloatingDashboardWidgetConfig(dataKey = "y"),
                ),
            ),
            widgetIndex = 0,
        )
        assertEquals(1, target.wholePanelRows)
        assertEquals(3, target.wholePanelCols)
        assertEquals(3, target.wholePanelWidgetsDraft?.size)
        assertEquals("x", target.wholePanelWidgetsDraft?.get(0)?.dataKey)
        assertEquals("y", target.wholePanelWidgetsDraft?.get(1)?.dataKey)
        assertEquals("", target.wholePanelWidgetsDraft?.get(2)?.dataKey)
        assertEquals("x", target.selectedDataKey)
    }

    @Test
    fun widgetDialogClipboard_tileAndPanelSlotsAreIndependent() {
        WidgetDialogClipboard.copyTile(
            TileClipboardSnapshot(
                config = FloatingDashboardWidgetConfig(
                    dataKey = "voltage",
                    showTitle = true,
                    controlInactiveColorLight = 0xFF123456.toInt(),
                ),
                controlColorsUseDefaults = false,
            ),
        )
        WidgetDialogClipboard.copyPanel(
            WholePanelClipboardSnapshot(
                name = "P",
                showTboxDisconnect = false,
                rows = 2,
                cols = 3,
                gridSpacingDp = 4,
                pageNumber = 1,
                clickAction = false,
                collapseEdge = PanelCollapseEdge.NONE.storageValue,
                collapseStripThicknessDp = 24,
                collapseTouchZoneThicknessDp = 24,
                collapseStripColorLight = 1,
                collapseStripColorDark = 2,
                collapseStripExpandedColorLight = 3,
                collapseStripExpandedColorDark = 4,
                collapseOnStripTap = false,
                collapseOnTileTap = false,
                collapseOnTileTapDelaySec = 0,
                widgetsConfig = listOf(
                    FloatingDashboardWidgetConfig(dataKey = "voltage"),
                    FloatingDashboardWidgetConfig(dataKey = "speed"),
                ),
            ),
        )

        assertTrue(WidgetDialogClipboard.hasTile)
        assertTrue(WidgetDialogClipboard.hasPanel)
        assertEquals("voltage", WidgetDialogClipboard.tileSnapshot?.config?.dataKey)
        assertFalse(WidgetDialogClipboard.tileSnapshot!!.controlColorsUseDefaults)
        assertEquals("P", WidgetDialogClipboard.panelSnapshot?.name)
        assertEquals(2, WidgetDialogClipboard.panelSnapshot?.widgetsConfig?.size)
    }
}
