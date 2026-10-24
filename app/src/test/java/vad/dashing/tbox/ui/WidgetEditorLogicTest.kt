package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.TileBackgroundImageStorage

class WidgetEditorLogicTest {

    private fun tile(key: String, textScale: Float = 1f, shape: Int = 0) =
        FloatingDashboardWidgetConfig(dataKey = key, textScale = textScale, shape = shape)

    private fun editorState(
        configs: List<FloatingDashboardWidgetConfig>,
        index: Int = 0,
    ) = WidgetSelectionDialogState(
        initialDataKey = configs.getOrNull(index)?.dataKey.orEmpty(),
        initialConfig = configs.getOrNull(index) ?: FloatingDashboardWidgetConfig(dataKey = ""),
        panelDefaultBackgroundLight = 0x01000000,
        panelDefaultBackgroundDark = 0x02000000,
        initialEditingIndex = index,
    )

    @Test
    fun padWidgetConfigs_padsButNeverTruncates() {
        val configs = listOf(tile("a"), tile("b"), tile("c"))
        assertEquals(5, padWidgetConfigs(configs, 5).size)
        assertEquals("", padWidgetConfigs(configs, 5)[4].dataKey)
        assertSame(configs, padWidgetConfigs(configs, 2))
    }

    @Test
    fun countConfiguredTilesOutsideGrid_ignoresEmptySlots() {
        val configs = listOf(tile("a"), tile(""), tile("c"), tile(""), tile("e"))
        assertEquals(2, countConfiguredTilesOutsideGrid(configs, rows = 1, cols = 2))
        assertEquals(0, countConfiguredTilesOutsideGrid(configs, rows = 2, cols = 3))
    }

    @Test
    fun swapTileConfigs_swapsRegularTiles() {
        val swapped = swapTileConfigs(listOf(tile("a"), tile("b"), tile("c")), 0, 2)
        assertEquals(listOf("c", "b", "a"), swapped.map { it.dataKey })
    }

    @Test
    fun swapTileConfigs_refusesHttpTilesAndInvalidIndices() {
        val configs = listOf(tile("a"), tile(HTTP_REQUEST_WIDGET_DATA_KEY), tile("c"))
        assertFalse(canSwapTiles(configs, 0, 1))
        assertFalse(canSwapTiles(configs, 1, 2))
        assertFalse(canSwapTiles(configs, 0, 0))
        assertFalse(canSwapTiles(configs, 0, 3))
        assertSame(configs, swapTileConfigs(configs, 0, 1))
    }

    @Test
    fun applyTileAppearance_copiesLookButKeepsTypeAndTitle() {
        val source = tile("speed", textScale = 1.4f, shape = 12).copy(
            textColorLight = 0x11111111,
            backgroundColorDark = 0x22222222,
            customTitle = "Source",
        )
        val target = tile("rpm").copy(customTitle = "Target")
        val result = applyTileAppearance(target, source)
        assertEquals("rpm", result.dataKey)
        assertEquals("Target", result.customTitle)
        assertEquals(1.4f, result.textScale, 0.0001f)
        assertEquals(12, result.shape)
        assertEquals(0x11111111, result.textColorLight)
        assertEquals(0x22222222, result.backgroundColorDark)
    }

    @Test
    fun applyTileAppearance_skipsEmptyTiles() {
        val empty = tile("")
        assertSame(empty, applyTileAppearance(empty, tile("speed", textScale = 1.5f)))
    }

    @Test
    fun resolveTileBackgroundStorageSlot_prefersPositionWhenFree() {
        val configs = listOf(tile("a"), tile("b"))
        assertEquals(1, resolveTileBackgroundStorageSlot("p", configs, 1, null, null))
    }

    @Test
    fun resolveTileBackgroundStorageSlot_avoidsSlotOwnedByAnotherTile() {
        val slot1Light = TileBackgroundImageStorage.relativePathFor("p", 1, darkTheme = false)
        val configs = listOf(
            tile("a").copy(tileBackgroundImageRelPathLight = slot1Light),
            tile("b"),
        )
        val slot = resolveTileBackgroundStorageSlot("p", configs, 1, null, null)
        assertTrue(slot != 1)
        assertTrue(slot >= configs.size)
    }

    @Test
    fun resolveTileBackgroundStorageSlot_keepsOwnSlotAfterMove() {
        val slot0Dark = TileBackgroundImageStorage.relativePathFor("p", 0, darkTheme = true)
        val configs = listOf(tile("b"), tile("a").copy(tileBackgroundImageRelPathDark = slot0Dark))
        assertEquals(0, resolveTileBackgroundStorageSlot("p", configs, 1, null, slot0Dark))
    }

    @Test
    fun collapseStripSpan_fullLengthCoversWholeEdge() {
        assertEquals(CollapseStripSpan(0f, 1f), collapseStripSpan(100, 30))
    }

    @Test
    fun collapseStripPositionForCenter_staysInsideEdge() {
        assertEquals(0, collapseStripPositionForCenter(0f, 40))
        assertEquals(60, collapseStripPositionForCenter(1f, 40))
        assertEquals(30, collapseStripPositionForCenter(0.5f, 40))
    }

    @Test
    fun tileTypesOnPanel_returnsDistinctNonEmptyInOrder() {
        val configs = listOf(tile("b"), tile(""), tile("a"), tile("b"))
        assertEquals(listOf("b", "a"), tileTypesOnPanel(configs))
    }

    @Test
    fun recentTypes_movesRepeatedTypeToFront() {
        WidgetEditorRecentTypes.record("x-test-1")
        WidgetEditorRecentTypes.record("x-test-2")
        WidgetEditorRecentTypes.record("x-test-1")
        assertEquals(listOf("x-test-1", "x-test-2"), WidgetEditorRecentTypes.keys.take(2))
        WidgetEditorRecentTypes.record("")
        assertEquals("x-test-1", WidgetEditorRecentTypes.keys.first())
    }

    @Test
    fun freshState_hasNoUnsavedChanges() {
        val state = editorState(listOf(tile("speed"), tile("rpm")))
        assertFalse(state.isEditingTileDirty)
        assertFalse(state.isPanelDirty)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun editingTile_marksDirty() {
        val state = editorState(listOf(tile("speed"), tile("rpm")))
        state.textScale = 1.5f
        assertTrue(state.isEditingTileDirty)
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun goToTile_keepsEditsOfPreviousTileAndLoadsTarget() {
        val current = listOf(tile("speed"), tile("rpm", textScale = 1.2f))
        val state = editorState(current)
        state.textScale = 1.5f

        state.goToTile(1, current, tileCount = 2)

        assertEquals(1, state.editingIndex)
        assertEquals("rpm", state.selectedDataKey)
        assertEquals(1.2f, state.textScale, 0.0001f)
        assertFalse(state.isEditingTileDirty)
        assertTrue(state.otherTilesDirty)
        val draft = state.wholePanelWidgetsDraft!!
        assertEquals(1.5f, draft[0].textScale, 0.0001f)
        assertEquals("speed", draft[0].dataKey)
    }

    @Test
    fun goToTile_withoutEditsDoesNotMarkOtherTilesDirty() {
        val current = listOf(tile("speed"), tile("rpm"))
        val state = editorState(current)
        state.goToTile(1, current, tileCount = 2)
        state.goToTile(0, current, tileCount = 2)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun goToTile_ignoresOutOfRangeTargets() {
        val current = listOf(tile("speed"), tile("rpm"))
        val state = editorState(current)
        state.goToTile(5, current, tileCount = 2)
        state.goToTile(-1, current, tileCount = 2)
        assertEquals(0, state.editingIndex)
        assertNull(state.wholePanelWidgetsDraft)
    }

    @Test
    fun swapTiles_editorFollowsMovedTile() {
        val current = listOf(tile("speed"), tile("rpm"), tile("fuel"))
        val state = editorState(current)
        state.gridSelection = setOf(2)

        assertTrue(state.swapTiles(0, 2, current, tileCount = 3))

        assertEquals(2, state.editingIndex)
        assertEquals("speed", state.selectedDataKey)
        assertEquals(listOf("fuel", "rpm", "speed"), state.workingTileConfigs(current, 3).map { it.dataKey })
        assertTrue(state.gridSelection.isEmpty())
        assertTrue(state.hasUnsavedChanges)
    }

    @Test
    fun swapTiles_refusesHttpTile() {
        val current = listOf(tile("speed"), tile(HTTP_REQUEST_WIDGET_DATA_KEY))
        val state = editorState(current)
        assertFalse(state.swapTiles(0, 1, current, tileCount = 2))
        assertEquals(0, state.editingIndex)
    }

    @Test
    fun applyAppearanceToSelection_copiesLookToSelectedTilesOnly() {
        val current = listOf(tile("speed"), tile("rpm"), tile("fuel"), tile(""))
        val state = editorState(current)
        state.textScale = 1.5f
        state.shape = 10
        state.gridSelection = setOf(1, 3)

        val changed = state.applyAppearanceToSelection(current, tileCount = 4)

        assertEquals(1, changed)
        val working = state.workingTileConfigs(current, 4)
        assertEquals("rpm", working[1].dataKey)
        assertEquals(1.5f, working[1].textScale, 0.0001f)
        assertEquals(10, working[1].shape)
        assertEquals(1f, working[2].textScale, 0.0001f)
        assertEquals("", working[3].dataKey)
    }

    @Test
    fun workingTileConfigs_reflectsUncommittedEditsOfCurrentTile() {
        val current = listOf(tile("speed"), tile("rpm"))
        val state = editorState(current, index = 1)
        state.selectedDataKey = ""
        val working = state.workingTileConfigs(current, 2)
        assertEquals("", working[1].dataKey)
        assertEquals("speed", working[0].dataKey)
    }
}
