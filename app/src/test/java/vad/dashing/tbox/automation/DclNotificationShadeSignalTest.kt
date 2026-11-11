package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.dcl.DclBounds
import vad.dashing.tbox.dcl.DclLauncherApiRepository
import vad.dashing.tbox.dcl.DclUiState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DclNotificationShadeSignalTest {

    @Test
    fun catalog_exposesBinaryAppSignal() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED)
        assertEquals(AutomationSignalValueType.STATE, descriptor.id.valueType)
        assertEquals(setOf(AutomationSignalSource.APP), descriptor.sources)
        assertEquals(listOf("off", "on"), descriptor.stateOptions)
        assertEquals("DCL: шторка уведомлений", descriptor.label)
        assertEquals(
            "DCL: шторка уведомлений",
            AutomationParameterLabels.signalLabel(AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED),
        )
        assertEquals(
            "dcl_notification_shade_expanded",
            AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED.storageKey,
        )
    }

    @Test
    fun signalFlow_mapsUiStateBoolean() = runBlocking {
        val key = AutomationSignalKey(
            AutomationSignalId.DCL_NOTIFICATION_SHADE_EXPANDED,
            AutomationSignalSource.APP,
        )
        val flow = AutomationSignalReads.flowFor(key)
        assertTrue(flow != null)
        try {
            DclLauncherApiRepository.publishUiStateForTests(null)
            assertEquals(AutomationSignalValue.Unavailable, flow!!.first())

            DclLauncherApiRepository.publishUiStateForTests(
                uiState(notificationShadeExpanded = true),
            )
            assertEquals(AutomationSignalValue.State("on"), flow.first())

            DclLauncherApiRepository.publishUiStateForTests(
                uiState(notificationShadeExpanded = false),
            )
            assertEquals(AutomationSignalValue.State("off"), flow.first())

            // Present snapshot without the optional field → Unavailable again.
            DclLauncherApiRepository.publishUiStateForTests(
                uiState(notificationShadeExpanded = null),
            )
            assertEquals(AutomationSignalValue.Unavailable, flow.first())
        } finally {
            DclLauncherApiRepository.publishUiStateForTests(null)
        }
    }

    private fun uiState(notificationShadeExpanded: Boolean?): DclUiState =
        DclUiState(
            screenMode = "home",
            foregroundPackage = "",
            leftPackage = "",
            rightPackage = "",
            topBarHeightPx = 51,
            bottomBarHeightPx = 82,
            adasPanelBounds = DclBounds.DEFAULT_ADAS.toCsv(),
            appAreaBounds = DclBounds.DEFAULT_APP_AREA.toCsv(),
            displayWidthPx = 1920,
            displayHeightPx = 1080,
            overlayOpen = false,
            revision = 1L,
            updatedAtEpochMs = null,
            adasPanelVisible = true,
            topBarVisible = true,
            bottomBarVisible = true,
            immersive = false,
            notificationShadeExpanded = notificationShadeExpanded,
        )
}
