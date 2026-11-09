package vad.dashing.tbox.dcl

import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationDocument
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

/**
 * Pure routing / remap helpers for DCL launcher environment mode (v1).
 * Runtime launch side-effects live in [DclAppLaunchHelper] / navigation callers.
 */
object DclLauncherEnvironmentRouting {

    /** Modes that bulk-convert remaps to «Обычный» ([AppLauncherLaunchMode.FULLSCREEN]). */
    val BULK_REMAP_SOURCE_MODES: Set<AppLauncherLaunchMode> = setOf(
        AppLauncherLaunchMode.FREEFORM,
        AppLauncherLaunchMode.VIRTUAL_DISPLAY,
        AppLauncherLaunchMode.STOCK_WINDOW,
    )

    /**
     * When env is ON, «Обычный» and «На весь экран (DCL)» route through DCL.
     * Freeform / VD / stock keep their existing backends (no silent remap).
     */
    fun dclLaunchModeFor(
        envEnabled: Boolean,
        launchMode: AppLauncherLaunchMode,
    ): String? {
        if (!envEnabled) return null
        return when (launchMode) {
            AppLauncherLaunchMode.FULLSCREEN -> DclLauncherApiContract.MODE_APP_AREA
            AppLauncherLaunchMode.DCL_FULLSCREEN -> DclLauncherApiContract.MODE_FULLSCREEN
            AppLauncherLaunchMode.FREEFORM,
            AppLauncherLaunchMode.STOCK_WINDOW,
            AppLauncherLaunchMode.VIRTUAL_DISPLAY,
            -> null
        }
    }

    /** App List / music / paths without a mode picker: always DCL app_area when env ON. */
    fun shouldRouteDefaultLaunchViaDcl(envEnabled: Boolean): Boolean = envEnabled

    fun selectableLaunchModes(
        envEnabled: Boolean,
        currentMode: AppLauncherLaunchMode,
        showStockWindowMode: Boolean,
    ): List<AppLauncherLaunchMode> {
        if (!envEnabled) {
            return buildList {
                add(AppLauncherLaunchMode.FULLSCREEN)
                if (showStockWindowMode) {
                    add(AppLauncherLaunchMode.STOCK_WINDOW)
                }
                add(AppLauncherLaunchMode.FREEFORM)
                add(AppLauncherLaunchMode.VIRTUAL_DISPLAY)
                // Keep a previously saved DCL fullscreen choice visible so the user can change it.
                if (currentMode == AppLauncherLaunchMode.DCL_FULLSCREEN) {
                    add(AppLauncherLaunchMode.DCL_FULLSCREEN)
                }
            }
        }
        return buildList {
            add(AppLauncherLaunchMode.FULLSCREEN)
            add(AppLauncherLaunchMode.DCL_FULLSCREEN)
            if (currentMode in BULK_REMAP_SOURCE_MODES) {
                add(currentMode)
            }
        }
    }

    fun isBulkRemappable(mode: AppLauncherLaunchMode): Boolean =
        mode in BULK_REMAP_SOURCE_MODES

    fun remapWidgetToOrdinary(
        config: FloatingDashboardWidgetConfig,
    ): FloatingDashboardWidgetConfig? {
        if (config.dataKey != APP_LAUNCHER_WIDGET_DATA_KEY) return null
        val mode = AppLauncherLaunchMode.fromStored(
            config.launcherLaunchMode.storageKey,
            config.launcherFreeformEnabled,
        )
        if (!isBulkRemappable(mode)) return null
        return config.copy(
            launcherLaunchMode = AppLauncherLaunchMode.FULLSCREEN,
            launcherFreeformEnabled = false,
            launcherFreeformSide = FreeformLaunchSide.DEFAULT,
            launcherFreeformPercent = FreeformLaunchBounds.DEFAULT_PERCENT,
            launcherFreeformOverlayPage = null,
            launcherFreeformOverlayCrop = false,
            launcherVirtualDisplayId = null,
            launcherVirtualDisplayWidthPx = null,
            launcherVirtualDisplayHeightPx = null,
        )
    }

    fun remapWidgetListToOrdinary(
        widgets: List<FloatingDashboardWidgetConfig>,
    ): Pair<List<FloatingDashboardWidgetConfig>, Int> {
        var count = 0
        val next = widgets.map { widget ->
            val remapped = remapWidgetToOrdinary(widget)
            if (remapped != null) {
                count += 1
                remapped
            } else {
                widget
            }
        }
        return next to count
    }

    fun remapMainScreenPanelsToOrdinary(
        panels: List<MainScreenPanelConfig>,
    ): Pair<List<MainScreenPanelConfig>, Int> {
        var total = 0
        val next = panels.map { panel ->
            val (widgets, count) = remapWidgetListToOrdinary(panel.widgetsConfig)
            total += count
            if (count == 0) panel else panel.copy(widgetsConfig = widgets)
        }
        return next to total
    }

    fun remapFloatingPanelsToOrdinary(
        panels: List<FloatingDashboardConfig>,
    ): Pair<List<FloatingDashboardConfig>, Int> {
        var total = 0
        val next = panels.map { panel ->
            val (widgets, count) = remapWidgetListToOrdinary(panel.widgetsConfig)
            total += count
            if (count == 0) panel else panel.copy(widgetsConfig = widgets)
        }
        return next to total
    }

    fun remapLaunchApplicationToOrdinary(
        action: AutomationAction.LaunchApplication,
    ): AutomationAction.LaunchApplication? {
        if (!isBulkRemappable(action.launchMode)) return null
        return action.copy(
            launchMode = AppLauncherLaunchMode.FULLSCREEN,
            freeformSide = FreeformLaunchSide.DEFAULT,
            freeformPercent = FreeformLaunchBounds.DEFAULT_PERCENT,
            freeformOverlayPage = null,
            freeformOverlayCrop = false,
            virtualDisplayId = null,
            virtualDisplayWidthPx = null,
            virtualDisplayHeightPx = null,
        )
    }

    fun remapActionsToOrdinary(
        actions: List<AutomationAction>,
    ): Pair<List<AutomationAction>, Int> {
        var count = 0
        fun remapOne(action: AutomationAction): AutomationAction =
            when (action) {
                is AutomationAction.LaunchApplication -> {
                    val remapped = remapLaunchApplicationToOrdinary(action)
                    if (remapped != null) {
                        count += 1
                        remapped
                    } else {
                        action
                    }
                }
                is AutomationAction.IfThenElse -> {
                    val (thenActions, thenCount) = remapActionsToOrdinary(action.thenActions)
                    val (elseActions, elseCount) = remapActionsToOrdinary(action.elseActions)
                    count += thenCount + elseCount
                    if (thenCount == 0 && elseCount == 0) {
                        action
                    } else {
                        action.copy(thenActions = thenActions, elseActions = elseActions)
                    }
                }
                is AutomationAction.Delay,
                is AutomationAction.CanCommand,
                is AutomationAction.OpenMainScreen,
                is AutomationAction.HttpRequest,
                is AutomationAction.Builtin,
                -> action
            }
        val next = actions.map(::remapOne)
        return next to count
    }

    fun remapAutomationsToOrdinary(
        document: AutomationDocument,
    ): Pair<AutomationDocument, Int> {
        var total = 0
        val nextDefs = document.automations.map { def ->
            val (actions, count) = remapActionsToOrdinary(def.actions)
            total += count
            if (count == 0) def else def.copy(actions = actions)
        }
        return document.copy(automations = nextDefs) to total
    }

    data class BulkRemapPreview(
        val shortcutCount: Int,
        val automationActionCount: Int,
    ) {
        val total: Int get() = shortcutCount + automationActionCount
    }

    fun previewBulkRemap(
        dashboardWidgets: List<FloatingDashboardWidgetConfig>,
        mainScreenPanels: List<MainScreenPanelConfig>,
        floatingPanels: List<FloatingDashboardConfig>,
        document: AutomationDocument,
    ): BulkRemapPreview {
        val (_, dashCount) = remapWidgetListToOrdinary(dashboardWidgets)
        val (_, mainCount) = remapMainScreenPanelsToOrdinary(mainScreenPanels)
        val (_, floatCount) = remapFloatingPanelsToOrdinary(floatingPanels)
        val (_, autoCount) = remapAutomationsToOrdinary(document)
        return BulkRemapPreview(
            shortcutCount = dashCount + mainCount + floatCount,
            automationActionCount = autoCount,
        )
    }

    data class BulkRemapResult(
        val dashboardWidgets: List<FloatingDashboardWidgetConfig>,
        val mainScreenPanels: List<MainScreenPanelConfig>,
        val floatingPanels: List<FloatingDashboardConfig>,
        val document: AutomationDocument,
        val shortcutCount: Int,
        val automationActionCount: Int,
    )

    fun applyBulkRemap(
        dashboardWidgets: List<FloatingDashboardWidgetConfig>,
        mainScreenPanels: List<MainScreenPanelConfig>,
        floatingPanels: List<FloatingDashboardConfig>,
        document: AutomationDocument,
    ): BulkRemapResult {
        val (nextDash, dashCount) = remapWidgetListToOrdinary(dashboardWidgets)
        val (nextMain, mainCount) = remapMainScreenPanelsToOrdinary(mainScreenPanels)
        val (nextFloat, floatCount) = remapFloatingPanelsToOrdinary(floatingPanels)
        val (nextDoc, autoCount) = remapAutomationsToOrdinary(document)
        return BulkRemapResult(
            dashboardWidgets = nextDash,
            mainScreenPanels = nextMain,
            floatingPanels = nextFloat,
            document = nextDoc,
            shortcutCount = dashCount + mainCount + floatCount,
            automationActionCount = autoCount,
        )
    }
}

/**
 * Whether DCL can be used for `launchApp(mode=app_area)`.
 * Missing capabilities after a successful availability probe still allow an attempt
 * (provider may omit flags on older builds).
 */
fun dclCanLaunchAppArea(
    available: Boolean,
    capabilities: DclCapabilities?,
): Boolean {
    if (!available) return false
    return capabilities?.launchAppArea != false
}

fun dclCanLaunchFullscreen(
    available: Boolean,
    capabilities: DclCapabilities?,
): Boolean {
    if (!available) return false
    return capabilities?.launchFullscreen != false
}
