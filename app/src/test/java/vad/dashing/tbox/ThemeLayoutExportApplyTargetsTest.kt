package vad.dashing.tbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeLayoutExportApplyTargetsTest {

    @Test
    fun exportJson_wallpapersOnly_omitsPanelsFromMainScreenSection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        seedLocalState(settingsManager)

        val json = ThemeLayoutExport.exportJson(
            context = context,
            settingsManager = settingsManager,
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS),
        )
        val mainScreen = JSONObject(json).getJSONObject(ThemeSection.MAIN_SCREEN.jsonKey)

        assertTrue(mainScreen.has(MainScreenWallpaperSelectionsByPage.JSON_KEY))
        assertFalse(mainScreen.has("panels"))
    }

    @Test
    fun exportJson_panelsOnly_omitsWallpaperSelectionFromMainScreenSection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        seedLocalState(settingsManager)

        val json = ThemeLayoutExport.exportJson(
            context = context,
            settingsManager = settingsManager,
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        )
        val mainScreen = JSONObject(json).getJSONObject(ThemeSection.MAIN_SCREEN.jsonKey)

        assertTrue(mainScreen.has("panels"))
        assertFalse(mainScreen.has(MainScreenWallpaperSelectionsByPage.JSON_KEY))
    }

    @Test
    fun importJson_panelsOnly_updatesPanelsButLeavesWallpapersUntouched() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        val existingWallpapers = MainScreenWallpaperSelectionsByPage.empty()
            .withFileName(page = 1, forLightTheme = true, fileName = "existing.jpg")
        settingsManager.saveMainScreenWallpaperSelectionsByPage(existingWallpapers)
        settingsManager.saveMainScreenDashboards(listOf(localPanel("local-panel")))

        ThemeLayoutExport.importJson(
            context = context,
            settingsManager = settingsManager,
            json = incomingThemeJson(),
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        ).getOrThrow()

        val panels = settingsManager.mainScreenDashboardsFlow.first()
        assertEquals("incoming-panel", panels.single().id)
        assertEquals(
            "existing.jpg",
            settingsManager.mainScreenWallpaperSelectionsSnapshot().fileNameFor(1, forLightTheme = true),
        )
    }

    @Test
    fun exportImport_panels_roundTripsWindowModeButtonPositions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        seedLocalState(settingsManager)
        settingsManager.saveMainScreenWindowModeExitButton(
            MainScreenWindowModeExitButtonPosition(0.21f, 0.31f),
        )
        settingsManager.saveMainScreenWindowModeRestoreButton(
            MainScreenWindowModeExitButtonPosition(0.41f, 0.51f),
        )

        val json = ThemeLayoutExport.exportJson(
            context = context,
            settingsManager = settingsManager,
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        )
        val mainScreen = JSONObject(json).getJSONObject(ThemeSection.MAIN_SCREEN.jsonKey)
        assertTrue(mainScreen.has("exitWindowModeButton"))
        assertTrue(mainScreen.has("restoreWindowModeButton"))
        assertEquals(0.21, mainScreen.getJSONObject("exitWindowModeButton").getDouble("x"), 1e-6)
        assertEquals(0.41, mainScreen.getJSONObject("restoreWindowModeButton").getDouble("x"), 1e-6)

        settingsManager.saveMainScreenWindowModeExitButton(
            MainScreenWindowModeExitButtonPosition.Default,
        )
        settingsManager.saveMainScreenWindowModeRestoreButton(
            MainScreenWindowModeExitButtonPosition.RestoreFullscreenDefault,
        )

        ThemeLayoutExport.importJson(
            context = context,
            settingsManager = settingsManager,
            json = json,
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        ).getOrThrow()

        val exit = settingsManager.mainScreenWindowModeExitButtonFlow.first()
        val restore = settingsManager.mainScreenWindowModeRestoreButtonFlow.first()
        assertEquals(0.21f, exit.x, 1e-5f)
        assertEquals(0.31f, exit.y, 1e-5f)
        assertEquals(0.41f, restore.x, 1e-5f)
        assertEquals(0.51f, restore.y, 1e-5f)
    }

    @Test
    fun importJson_panelsOnly_restoresCurrentPage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        settingsManager.saveMainScreenPageCount(3)
        settingsManager.saveMainScreenCurrentPage(1)
        settingsManager.saveMainScreenDashboards(listOf(localPanel("local-panel")))

        ThemeLayoutExport.importJson(
            context = context,
            settingsManager = settingsManager,
            json = incomingThemeJson(currentPage = 2, pageCount = 3),
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        ).getOrThrow()

        assertEquals(2, settingsManager.mainScreenCurrentPageFlow.first())
        assertEquals(3, settingsManager.mainScreenPageCountFlow.first())
    }

    @Test
    fun exportImport_panels_roundTripsCurrentPage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(context)
        seedLocalState(settingsManager)
        settingsManager.saveMainScreenPageCount(3)
        settingsManager.saveMainScreenCurrentPage(2)

        val json = ThemeLayoutExport.exportJson(
            context = context,
            settingsManager = settingsManager,
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        )
        val mainScreen = JSONObject(json).getJSONObject(ThemeSection.MAIN_SCREEN.jsonKey)
        assertEquals(2, mainScreen.getInt("currentPage"))
        assertEquals(3, mainScreen.getInt("pageCount"))

        settingsManager.saveMainScreenCurrentPage(1)
        ThemeLayoutExport.importJson(
            context = context,
            settingsManager = settingsManager,
            json = json,
            applyTargets = setOf(ThemeApplyTarget.MAIN_SCREEN_PANELS),
        ).getOrThrow()

        assertEquals(2, settingsManager.mainScreenCurrentPageFlow.first())
    }

    private suspend fun seedLocalState(settingsManager: SettingsManager) {
        settingsManager.saveMainScreenDashboards(listOf(localPanel("local-panel")))
        settingsManager.saveMainScreenWallpaperSelectionsByPage(
            MainScreenWallpaperSelectionsByPage.empty()
                .withFileName(page = 1, forLightTheme = true, fileName = "local.jpg"),
        )
    }

    private fun localPanel(id: String) = MainScreenPanelConfig(
        id = id,
        name = id,
        enabled = true,
        widgetsConfig = emptyList(),
        rows = 1,
        cols = 1,
        relX = 0.05f,
        relY = 0.1f,
        relWidth = 0.4f,
        relHeight = 0.3f,
        background = false,
        clickAction = false,
    )

    private fun incomingThemeJson(
        currentPage: Int? = null,
        pageCount: Int? = null,
    ): String {
        val pageFields = buildString {
            if (pageCount != null) append("\"pageCount\": $pageCount,\n            ")
            if (currentPage != null) append("\"currentPage\": $currentPage,\n            ")
        }
        return """
        {
          "formatVersion": 1,
          "type": "tbox_theme",
          "sections": ["mainScreen"],
          "mainScreen": {
            $pageFields"panels": [{
              "id": "incoming-panel",
              "name": "Incoming",
              "enabled": true,
              "grid": { "rows": 1, "cols": 1 },
              "position": { "x": 0.1, "y": 0.1 },
              "size": { "width": 0.4, "height": 0.3 },
              "widgets": []
            }],
            "wallpaperSelectionByPage": {
              "light": { "1": "incoming.jpg" },
              "dark": {}
            }
          }
        }
    """.trimIndent()
    }
}
