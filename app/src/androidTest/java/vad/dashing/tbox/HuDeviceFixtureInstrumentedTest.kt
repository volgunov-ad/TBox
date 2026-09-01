package vad.dashing.tbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCodec
import vad.dashing.tbox.automation.AutomationRunMode
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationTrigger
import vad.dashing.tbox.automation.AutomationValidator

/**
 * Device-side contract for scripts/hu-device-test fixtures.
 * `hu_test_automations.json` is copied into androidTest assets from that folder.
 */
@RunWith(AndroidJUnit4::class)
class HuDeviceFixtureInstrumentedTest {
    @Test
    fun targetPackageIsTboxMonitor() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("vad.dashing.tbox", appContext.packageName)
    }

    @Test
    fun huAutomationsAsset_decodesOnDevice() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val raw = testContext.assets.open("hu_test_automations.json").bufferedReader().use { it.readText() }
        val document = AutomationCodec.decode(raw).getOrThrow()
        assertTrue(AutomationValidator.validate(document).isEmpty())
        assertEquals(4, document.automations.size)
        document.automations.forEach { definition ->
            assertTrue(definition.name, AutomationValidator.isRunnable(definition))
        }
        val hide = document.automations.first { it.id == "hu-test-hide-on-settings" }
        val trigger = hide.triggers.single() as AutomationTrigger.StateEquals
        assertEquals(AutomationSignalId.FOREGROUND_APP, trigger.signal)
        assertEquals(AutomationSignalSource.APP, trigger.source)
        assertEquals("com.android.settings", trigger.expectedState)
        assertEquals(AutomationRunMode.RESTART, hide.runMode)
        assertTrue(
            hide.actions.filterIsInstance<AutomationAction.Builtin>().any {
                it.type == AutomationBuiltinActionType.SHOW_TOAST &&
                    it.stringValue.contains("HUTEST")
            },
        )
    }

    @Test
    fun huThemeWidgetsAreOfferedOnDevice() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val keys = WidgetsRepository.getAvailableDataKeysWidgets(noTboxConnect = false)
        assertTrue(keys.contains(DRIVE_MODE_WIDGET_DATA_KEY))
        assertTrue(keys.contains(DRIVE_MODE_CYCLE_WIDGET_DATA_KEY))
        assertTrue(keys.contains(HIDE_FLOATING_PANELS_WIDGET_DATA_KEY))
        assertTrue(keys.contains(TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY))
        val driveTitle = WidgetsRepository.getTitleForDataKey(appContext, DRIVE_MODE_WIDGET_DATA_KEY)
        assertTrue(driveTitle.isNotBlank())
    }
}
