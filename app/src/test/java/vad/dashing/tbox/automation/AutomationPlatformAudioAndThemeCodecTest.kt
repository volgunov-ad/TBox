package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationPlatformAudioAndThemeCodecTest {
    @Test
    fun roundTrip_preservesThemeVolumeAndHeadrestBuiltins() {
        val definition = AutomationDefinition(
            id = "hu-audio-theme",
            name = "Тема и микшер ГУ",
            enabled = false,
            triggers = listOf(
                AutomationTrigger.NumericThreshold(
                    id = "1",
                    signal = AutomationSignalId.HU_MEDIA_VOLUME,
                    source = AutomationSignalSource.APP,
                    direction = AutomationThresholdDirection.BELOW,
                    threshold = 5.0,
                    holdMillis = 0L,
                    startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
                ),
                AutomationTrigger.StateEquals(
                    id = "2",
                    signal = AutomationSignalId.HU_HEADREST_SPEAKER,
                    source = AutomationSignalSource.APP,
                    expectedState = "only",
                    holdMillis = 0L,
                    startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
                ),
            ),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME,
                    stringValue = "dark",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_PHONE_VOLUME,
                    intValue = 12,
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_NAVI_VOLUME,
                    intValue = 4,
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_VOICE_VOLUME,
                    intValue = 6,
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SET_HEADREST_SPEAKER,
                    stringValue = "assist",
                ),
            ),
        )
        val encoded = AutomationCodec.encode(AutomationDocument(automations = listOf(definition)))
        assertTrue(encoded.contains("set_hu_day_night_theme"))
        assertTrue(encoded.contains("set_phone_volume"))
        assertTrue(encoded.contains("set_navi_volume"))
        assertTrue(encoded.contains("set_voice_volume"))
        assertTrue(encoded.contains("set_headrest_speaker"))
        assertTrue(encoded.contains("hu_media_volume"))
        assertTrue(encoded.contains("hu_headrest_speaker"))

        val decoded = AutomationCodec.decode(encoded).getOrThrow().automations.single()
        assertEquals(definition, decoded)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }
}
