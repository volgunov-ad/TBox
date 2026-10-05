package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationHuScreenBrightnessCodecTest {
    @Test
    fun roundTrip_preservesHuScreenBrightnessBuiltinAndSignals() {
        val definition = AutomationDefinition(
            id = "hu-brightness",
            name = "Яркость ГУ",
            enabled = false,
            triggers = listOf(
                AutomationTrigger.NumericThreshold(
                    id = "1",
                    signal = AutomationSignalId.HU_SCREEN_BRIGHTNESS,
                    source = AutomationSignalSource.APP,
                    direction = AutomationThresholdDirection.BELOW,
                    threshold = 3.0,
                    holdMillis = 0L,
                    startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
                ),
                AutomationTrigger.StateEquals(
                    id = "2",
                    signal = AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS,
                    source = AutomationSignalSource.APP,
                    expectedState = "off",
                    holdMillis = 0L,
                    startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
                ),
            ),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS,
                    boolValue = false,
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS,
                    intValue = 7,
                ),
            ),
        )
        val encoded = AutomationCodec.encode(AutomationDocument(automations = listOf(definition)))
        assertTrue(encoded.contains("hu_screen_brightness"))
        assertTrue(encoded.contains("hu_screen_auto_brightness"))
        assertTrue(encoded.contains("set_hu_screen_brightness"))
        assertTrue(encoded.contains("set_hu_screen_auto_brightness"))

        val decoded = AutomationCodec.decode(encoded).getOrThrow().automations.single()
        assertEquals(definition, decoded)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }
}
