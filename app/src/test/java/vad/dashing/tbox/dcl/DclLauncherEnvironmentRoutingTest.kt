package vad.dashing.tbox.dcl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationCondition
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationDocument
import vad.dashing.tbox.automation.AutomationUiState
import vad.dashing.tbox.freeform.FreeformLaunchSide

class DclLauncherEnvironmentRoutingTest {

    @Test
    fun dclLaunchMode_envOff_alwaysNull() {
        assertNull(
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = false,
                launchMode = AppLauncherLaunchMode.FULLSCREEN,
            ),
        )
        assertNull(
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = false,
                launchMode = AppLauncherLaunchMode.DCL_FULLSCREEN,
            ),
        )
    }

    @Test
    fun dclLaunchMode_envOn_mapsOrdinaryAndDclFullscreen() {
        assertEquals(
            DclLauncherApiContract.MODE_APP_AREA,
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.FULLSCREEN,
            ),
        )
        assertEquals(
            DclLauncherApiContract.MODE_FULLSCREEN,
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.DCL_FULLSCREEN,
            ),
        )
        assertNull(
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.FREEFORM,
            ),
        )
        assertNull(
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.STOCK_WINDOW,
            ),
        )
        assertNull(
            DclLauncherEnvironmentRouting.dclLaunchModeFor(
                envEnabled = true,
                launchMode = AppLauncherLaunchMode.VIRTUAL_DISPLAY,
            ),
        )
    }

    @Test
    fun selectableModes_envOn_hidesFreeformVdStock_keepsCurrentLegacy() {
        val fresh = DclLauncherEnvironmentRouting.selectableLaunchModes(
            envEnabled = true,
            currentMode = AppLauncherLaunchMode.FULLSCREEN,
            showStockWindowMode = true,
        )
        assertEquals(
            listOf(AppLauncherLaunchMode.FULLSCREEN, AppLauncherLaunchMode.DCL_FULLSCREEN),
            fresh,
        )

        val legacy = DclLauncherEnvironmentRouting.selectableLaunchModes(
            envEnabled = true,
            currentMode = AppLauncherLaunchMode.FREEFORM,
            showStockWindowMode = true,
        )
        assertEquals(
            listOf(
                AppLauncherLaunchMode.FULLSCREEN,
                AppLauncherLaunchMode.DCL_FULLSCREEN,
                AppLauncherLaunchMode.FREEFORM,
            ),
            legacy,
        )
    }

    @Test
    fun remapWidget_onlyRemapsFreeformVdStock() {
        val freeform = FloatingDashboardWidgetConfig(
            dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
            launcherAppPackage = "com.example.app",
            launcherLaunchMode = AppLauncherLaunchMode.FREEFORM,
            launcherFreeformEnabled = true,
            launcherFreeformSide = FreeformLaunchSide.LEFT,
            launcherFreeformPercent = 40,
            launcherFreeformOverlayPage = 2,
            launcherFreeformOverlayCrop = true,
        )
        val remapped = DclLauncherEnvironmentRouting.remapWidgetToOrdinary(freeform)!!
        assertEquals(AppLauncherLaunchMode.FULLSCREEN, remapped.launcherLaunchMode)
        assertFalse(remapped.launcherFreeformEnabled)
        assertNull(remapped.launcherFreeformOverlayPage)
        assertFalse(remapped.launcherFreeformOverlayCrop)

        assertNull(
            DclLauncherEnvironmentRouting.remapWidgetToOrdinary(
                freeform.copy(launcherLaunchMode = AppLauncherLaunchMode.FULLSCREEN),
            ),
        )
        assertNull(
            DclLauncherEnvironmentRouting.remapWidgetToOrdinary(
                freeform.copy(launcherLaunchMode = AppLauncherLaunchMode.DCL_FULLSCREEN),
            ),
        )

        val httpLaunch = FloatingDashboardWidgetConfig(
            dataKey = HTTP_REQUEST_WIDGET_DATA_KEY,
            httpLaunchAppBeforeRequest = true,
            launcherAppPackage = "com.example.app",
            launcherLaunchMode = AppLauncherLaunchMode.STOCK_WINDOW,
        )
        val remappedHttp = DclLauncherEnvironmentRouting.remapWidgetToOrdinary(httpLaunch)!!
        assertEquals(AppLauncherLaunchMode.FULLSCREEN, remappedHttp.launcherLaunchMode)
        assertNull(
            DclLauncherEnvironmentRouting.remapWidgetToOrdinary(
                httpLaunch.copy(httpLaunchAppBeforeRequest = false),
            ),
        )
        assertNull(
            DclLauncherEnvironmentRouting.remapWidgetToOrdinary(
                httpLaunch.copy(httpOpenBrowser = true),
            ),
        )
    }

    @Test
    fun bulkRemap_countsShortcutsAndNestedAutomations() {
        val widget = FloatingDashboardWidgetConfig(
            dataKey = APP_LAUNCHER_WIDGET_DATA_KEY,
            launcherAppPackage = "com.example.app",
            launcherLaunchMode = AppLauncherLaunchMode.VIRTUAL_DISPLAY,
            launcherVirtualDisplayId = 3,
        )
        val main = listOf(
            MainScreenPanelConfig(
                id = "m1",
                name = "Main",
                enabled = true,
                widgetsConfig = listOf(widget),
                rows = 1,
                cols = 1,
                relX = 0f,
                relY = 0f,
                relWidth = 1f,
                relHeight = 1f,
                background = false,
                clickAction = false,
            ),
        )
        val floating = listOf(
            FloatingDashboardConfig(
                id = "f1",
                name = "Float",
                enabled = true,
                widgetsConfig = listOf(
                    widget.copy(launcherLaunchMode = AppLauncherLaunchMode.STOCK_WINDOW),
                    widget.copy(launcherLaunchMode = AppLauncherLaunchMode.FULLSCREEN),
                ),
                rows = 1,
                cols = 2,
                width = 100,
                height = 100,
                startX = 0,
                startY = 0,
                background = false,
                clickAction = false,
            ),
        )
        val document = AutomationDocument(
            automations = listOf(
                AutomationDefinition(
                    name = "launch",
                    triggers = emptyList(),
                    actions = listOf(
                        AutomationAction.LaunchApplication(
                            packageName = "com.example.app",
                            launchMode = AppLauncherLaunchMode.FREEFORM,
                        ),
                        AutomationAction.IfThenElse(
                            condition = AutomationCondition.UiState(
                                state = AutomationUiState.MAIN_SCREEN_OPEN,
                            ),
                            thenActions = listOf(
                                AutomationAction.LaunchApplication(
                                    packageName = "com.example.other",
                                    launchMode = AppLauncherLaunchMode.STOCK_WINDOW,
                                ),
                            ),
                            elseActions = listOf(
                                AutomationAction.LaunchApplication(
                                    packageName = "com.example.keep",
                                    launchMode = AppLauncherLaunchMode.FULLSCREEN,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val preview = DclLauncherEnvironmentRouting.previewBulkRemap(
            dashboardWidgets = emptyList(),
            mainScreenPanels = main,
            floatingPanels = floating,
            document = document,
        )
        assertEquals(2, preview.shortcutCount) // main VD + floating stock; fullscreen skipped
        assertEquals(2, preview.automationActionCount) // freeform + nested stock; fullscreen skipped

        val applied = DclLauncherEnvironmentRouting.applyBulkRemap(
            dashboardWidgets = emptyList(),
            mainScreenPanels = main,
            floatingPanels = floating,
            document = document,
        )
        assertEquals(AppLauncherLaunchMode.FULLSCREEN, applied.mainScreenPanels[0].widgetsConfig[0].launcherLaunchMode)
        assertNull(applied.mainScreenPanels[0].widgetsConfig[0].launcherVirtualDisplayId)
        val nested = applied.document.automations[0].actions[1] as AutomationAction.IfThenElse
        assertEquals(
            AppLauncherLaunchMode.FULLSCREEN,
            (nested.thenActions[0] as AutomationAction.LaunchApplication).launchMode,
        )
        assertEquals(
            AppLauncherLaunchMode.FULLSCREEN,
            (nested.elseActions[0] as AutomationAction.LaunchApplication).launchMode,
        )
    }

    @Test
    fun dclCanLaunchAppArea_requiresExplicitCapability() {
        assertFalse(dclCanLaunchAppArea(available = false, capabilities = null))
        // Null caps must not imply can-launch (P1-4).
        assertFalse(dclCanLaunchAppArea(available = true, capabilities = null))
        assertTrue(
            dclCanLaunchAppArea(
                available = true,
                capabilities = DclCapabilities(
                    apiVersion = 1,
                    launchFullscreen = true,
                    launchAppArea = true,
                    uiStateQuery = true,
                    uiStateEvents = true,
                    adasPanel = true,
                    screenModes = listOf("home"),
                    splitSupported = null,
                ),
            ),
        )
        assertFalse(
            dclCanLaunchAppArea(
                available = true,
                capabilities = DclCapabilities(
                    apiVersion = 1,
                    launchFullscreen = true,
                    launchAppArea = false,
                    uiStateQuery = true,
                    uiStateEvents = true,
                    adasPanel = false,
                    screenModes = emptyList(),
                    splitSupported = null,
                ),
            ),
        )
    }

    @Test
    fun dclModeCapabilityMissing_andRawFallbackCodes() {
        assertTrue(dclModeCapabilityMissing(DclLauncherApiContract.MODE_APP_AREA, null))
        assertTrue(
            dclModeCapabilityMissing(
                DclLauncherApiContract.MODE_APP_AREA,
                DclCapabilities(
                    apiVersion = 1,
                    launchFullscreen = true,
                    launchAppArea = false,
                    uiStateQuery = false,
                    uiStateEvents = false,
                    adasPanel = false,
                    screenModes = emptyList(),
                    splitSupported = null,
                ),
            ),
        )
        assertFalse(
            dclModeCapabilityMissing(
                DclLauncherApiContract.MODE_FULLSCREEN,
                DclCapabilities(
                    apiVersion = 1,
                    launchFullscreen = true,
                    launchAppArea = false,
                    uiStateQuery = false,
                    uiStateEvents = false,
                    adasPanel = false,
                    screenModes = emptyList(),
                    splitSupported = null,
                ),
            ),
        )

        assertTrue(dclLaunchShouldUseRawFallback(DclCallResult.Unavailable))
        assertTrue(
            dclLaunchShouldUseRawFallback(
                DclCallResult.Error(
                    DclApiError(DclLauncherApiContract.ERROR_UNSUPPORTED, "no"),
                ),
            ),
        )
        assertTrue(
            dclLaunchShouldUseRawFallback(
                DclCallResult.Ok(
                    DclLaunchResult(
                        status = DclLauncherApiContract.STATUS_ERROR,
                        packageName = "a.b",
                        mode = DclLauncherApiContract.MODE_APP_AREA,
                        error = DclApiError(DclLauncherApiContract.ERROR_INTERNAL, "x"),
                    ),
                ),
            ),
        )
        assertFalse(
            dclLaunchShouldUseRawFallback(
                DclCallResult.Error(
                    DclApiError(DclLauncherApiContract.ERROR_BUSY, "wait"),
                ),
            ),
        )
        assertFalse(
            dclLaunchShouldUseRawFallback(
                DclCallResult.Ok(
                    DclLaunchResult(
                        status = DclLauncherApiContract.STATUS_ERROR,
                        packageName = "a.b",
                        mode = DclLauncherApiContract.MODE_APP_AREA,
                        error = DclApiError(DclLauncherApiContract.ERROR_NOT_INSTALLED, "gone"),
                    ),
                ),
            ),
        )
        assertFalse(
            dclLaunchShouldUseRawFallback(
                DclCallResult.Ok(
                    DclLaunchResult(
                        status = DclLauncherApiContract.STATUS_OK,
                        packageName = "a.b",
                        mode = DclLauncherApiContract.MODE_APP_AREA,
                        error = null,
                    ),
                ),
            ),
        )
    }
}
