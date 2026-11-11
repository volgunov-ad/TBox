package vad.dashing.tbox.dcl

import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationDocument
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.widgetStoresAppLauncherLaunchSettings

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
     * When env is ON, «Обычный», «На всю ширину (DCL)» and «На весь экран (DCL)»
     * route through DCL. Freeform / VD / stock keep their existing backends (no silent remap).
     */
    fun dclLaunchModeFor(
        envEnabled: Boolean,
        launchMode: AppLauncherLaunchMode,
    ): String? {
        if (!envEnabled) return null
        return when (launchMode) {
            AppLauncherLaunchMode.FULLSCREEN -> DclLauncherApiContract.MODE_APP_AREA
            AppLauncherLaunchMode.DCL_FULL_WIDTH -> DclLauncherApiContract.MODE_FULL_WIDTH
            AppLauncherLaunchMode.DCL_FULLSCREEN -> DclLauncherApiContract.MODE_FULLSCREEN
            AppLauncherLaunchMode.FREEFORM,
            AppLauncherLaunchMode.STOCK_WINDOW,
            AppLauncherLaunchMode.VIRTUAL_DISPLAY,
            -> null
        }
    }

    /** App List / mode-less paths: always DCL app_area when env ON. Music widgets use a mode picker. */
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
                // Keep previously saved DCL-only choices visible so the user can change them.
                if (currentMode == AppLauncherLaunchMode.DCL_FULL_WIDTH) {
                    add(AppLauncherLaunchMode.DCL_FULL_WIDTH)
                }
                if (currentMode == AppLauncherLaunchMode.DCL_FULLSCREEN) {
                    add(AppLauncherLaunchMode.DCL_FULLSCREEN)
                }
            }
        }
        return buildList {
            add(AppLauncherLaunchMode.FULLSCREEN)
            add(AppLauncherLaunchMode.DCL_FULL_WIDTH)
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
        if (
            !widgetStoresAppLauncherLaunchSettings(
                config.dataKey,
                config.httpLaunchAppBeforeRequest && !config.httpOpenBrowser,
            )
        ) {
            return null
        }
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
 * Whether DCL can be preferred for `launchApp(mode=app_area)`.
 * Requires [available] **and** a successful capabilities snapshot with
 * [DclCapabilities.launchAppArea] == true — null caps must not imply can-launch
 * (caps Error / probe incomplete).
 */
fun dclCanLaunchAppArea(
    available: Boolean,
    capabilities: DclCapabilities?,
): Boolean {
    if (!available) return false
    return capabilities?.launchAppArea == true
}

fun dclCanLaunchFullscreen(
    available: Boolean,
    capabilities: DclCapabilities?,
): Boolean {
    if (!available) return false
    return capabilities?.launchFullscreen == true
}

/**
 * Prefer explicit [DclCapabilities.launchFullWidth] when present.
 * When the key is absent (`null`), treat as tentatively available so callers attempt
 * Binder `launchApp(mode=full_width)` and fall back on unsupported like peers.
 */
fun dclCanLaunchFullWidth(
    available: Boolean,
    capabilities: DclCapabilities?,
): Boolean {
    if (!available) return false
    val caps = capabilities ?: return false
    return caps.launchFullWidth != false
}

/**
 * Explicit capability flag for [mode] is missing or false → raw fallback (design §3.1).
 *
 * For [DclLauncherApiContract.MODE_FULL_WIDTH]: gate only when `launchFullWidth` is
 * present and false; absent key → not missing (attempt launch; unsupported → raw fallback).
 */
fun dclModeCapabilityMissing(
    mode: String,
    capabilities: DclCapabilities?,
): Boolean {
    val caps = capabilities ?: return true
    return when (mode) {
        DclLauncherApiContract.MODE_APP_AREA -> !caps.launchAppArea
        DclLauncherApiContract.MODE_FULLSCREEN -> !caps.launchFullscreen
        DclLauncherApiContract.MODE_FULL_WIDTH -> caps.launchFullWidth == false
        else -> true
    }
}

/**
 * Error codes that should fall back to raw `startActivity` like [DclCallResult.Unavailable]
 * (missing/unsupported API surface). Keep `busy` / `not_installed` as toast-only.
 */
val DCL_LAUNCH_RAW_FALLBACK_ERROR_CODES: Set<String> = setOf(
    DclLauncherApiContract.ERROR_UNSUPPORTED,
    DclLauncherApiContract.ERROR_INTERNAL,
)

fun dclLaunchShouldUseRawFallback(result: DclCallResult<DclLaunchResult>): Boolean =
    when (result) {
        DclCallResult.Unavailable -> true
        is DclCallResult.Error ->
            result.error.errorCode in DCL_LAUNCH_RAW_FALLBACK_ERROR_CODES ||
                result.error.errorCode.isNullOrBlank()
        is DclCallResult.Ok -> {
            if (result.value.success) {
                false
            } else {
                val code = result.value.error?.errorCode
                code in DCL_LAUNCH_RAW_FALLBACK_ERROR_CODES || code.isNullOrBlank()
            }
        }
    }
