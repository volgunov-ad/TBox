package vad.dashing.voice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.api.CatalogSignal

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NgramLocalIntentClassifierTest {
    private val classifier = NgramLocalIntentClassifier()

    private val catalog = ApiCatalog(
        catalogVersion = 11,
        signals = listOf(
            CatalogSignal(
                id = "fuel_level_percent",
                label = "Уровень топлива",
                unit = "%",
                valueType = "number",
                sources = listOf("head_unit"),
                voiceAliasesRu = listOf("сколько бензина", "уровень топлива", "бак"),
            ),
            CatalogSignal(
                id = "outside_temperature",
                label = "Температура снаружи",
                unit = "°C",
                valueType = "number",
                sources = listOf("head_unit"),
                voiceAliasesRu = listOf("температура на улице", "на улице"),
            ),
        ),
        actionTypes = listOf(
            CatalogAction(
                type = "builtin",
                actionType = "media_next",
                label = "Следующий трек",
                voiceAliasesRu = listOf("следующий трек", "следующий"),
            ),
            CatalogAction(
                type = "can_command",
                label = "Климат",
                voiceAliasesRu = listOf("включи климат"),
            ),
        ),
    )

    private val automations = listOf(
        AutomationSummary(
            id = "f9c025e4-b8c7-4f72-b593-a2ac0e3b4102",
            name = "климат",
            enabled = true,
        ),
    )

    @Test
    fun classifiesParaphraseFuelQuestion() {
        val intent = classifier.classify(
            "скажи пожалуйста сколько бензина осталось",
            catalog,
            automations,
        )
        assertTrue(intent is VoiceIntent.QuerySignal)
        assertEquals("fuel_level_percent", (intent as VoiceIntent.QuerySignal).signal.id)
    }

    @Test
    fun classifiesMediaNext() {
        val intent = classifier.classify("включи следующий трек", catalog, automations)
        assertTrue(intent is VoiceIntent.InvokeAction)
        assertEquals("media_next", (intent as VoiceIntent.InvokeAction).action.actionType)
    }

    @Test
    fun prefersAutomationWhenRunCue() {
        val intent = classifier.classify("запусти климат", catalog, automations)
        assertTrue(intent is VoiceIntent.RunAutomation)
        assertEquals("климат", (intent as VoiceIntent.RunAutomation).automation.name)
    }

    @Test
    fun skipsCanCommand() {
        val intent = classifier.classify("включи климат контроль полностью", catalog, emptyList())
        // Without automation, should not pick can_command; may be Unknown or weak signal.
        if (intent is VoiceIntent.InvokeAction) {
            assertTrue(intent.action.type != "can_command")
        }
    }

    @Test
    fun unknownNoise() {
        assertEquals(
            VoiceIntent.Unknown,
            classifier.classify("расскажи анекдот про слона", catalog, automations),
        )
    }

    @Test
    fun cosine_identicalIsOne() {
        val v = NgramLocalIntentClassifier.ngramVector("температура")
        assertEquals(1.0, NgramLocalIntentClassifier.cosine(v, v), 1e-6)
    }
}
