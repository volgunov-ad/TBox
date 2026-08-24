package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.MainScreenPagePanelMountPlan
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.isVisibleOnMainScreenPage

@Composable
internal fun MainScreenPagePanels(
    pageNumber: Int,
    pageCount: Int,
    mainPanels: List<MainScreenPanelConfig>,
    maxWpx: Float,
    maxHpx: Float,
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
    onRebootTbox: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    modifier: Modifier = Modifier,
    windowMode: Boolean = false,
) {
    val pagePanels = remember(mainPanels, pageCount, pageNumber) {
        mainPanels.filter { it.isVisibleOnMainScreenPage(pageCount, pageNumber) }
    }
    val pagePanelIds = remember(pagePanels) { pagePanels.map { it.id } }
    val headUnitCanMode by settingsViewModel.headUnitCanMode.collectAsStateWithLifecycle()
    val mountStepSize = MainScreenPagePanelMountPlan.stagedMountStepSize(headUnitCanMode)
    var mountedCount by remember(pageNumber, pagePanelIds, mountStepSize) {
        mutableIntStateOf(
            if (MainScreenPagePanelMountPlan.shouldUseStagedMount(pagePanels.size)) 0
            else pagePanels.size,
        )
    }
    LaunchedEffect(pageNumber, pagePanelIds, mountStepSize) {
        val total = pagePanels.size
        if (!MainScreenPagePanelMountPlan.shouldUseStagedMount(total)) {
            mountedCount = total
            return@LaunchedEffect
        }
        mountedCount = 0
        while (mountedCount < total) {
            mountedCount = MainScreenPagePanelMountPlan.nextMountedCount(
                currentMounted = mountedCount,
                panelCount = total,
                stepSize = mountStepSize,
            )
            if (mountedCount < total) {
                delay(MainScreenPagePanelMountPlan.STAGED_MOUNT_DELAY_MS)
            }
        }
    }
    val visibleCount = MainScreenPagePanelMountPlan.visiblePrefixCount(
        panelCount = pagePanels.size,
        mountedCount = mountedCount,
    )
    Box(modifier = modifier.fillMaxSize()) {
        pagePanels.take(visibleCount).forEach { panel ->
            key(panel.id) {
                MainScreenDashboardPanel(
                    panel = panel,
                    containerWidthPx = maxWpx,
                    containerHeightPx = maxHpx,
                    tboxViewModel = tboxViewModel,
                    canViewModel = canViewModel,
                    appDataViewModel = appDataViewModel,
                    settingsViewModel = settingsViewModel,
                    onRebootTbox = onRebootTbox,
                    onTripFinishAndStart = onTripFinishAndStart,
                    windowMode = windowMode,
                )
            }
        }
    }
}
