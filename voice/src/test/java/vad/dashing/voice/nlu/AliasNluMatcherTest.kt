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
import vad.dashing.voice.api.SignalReading

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AliasNluMatcherTest {
    private val matcher = AliasNluMatcher()

    private val catalog = ApiCatalog(
        catalogVersion = 2,
        signals = listOf(
            CatalogSignal(
                id = "outside_temperature",
                label = "Температура снаружи",
                unit = "°C",
                valueType = "number",
                sources = listOf("head_unit", "tbox"),
                voiceAliasesRu = listOf(
                    "температура снаружи",
                    "температура на улице",
                    "сколько градусов на улице",
                    "на улице",
                ),
            ),
            CatalogSignal(
                id = "car_speed",
                label = "Скорость автомобиля",
                unit = "км/ч",
                valueType = "number",
                sources = listOf("head_unit"),
                voiceAliasesRu = listOf("скорость", "какая скорость"),
            ),
        ),
        actionTypes = listOf(
            CatalogAction(
                type = "builtin",
                actionType = "media_next",
                voiceAliasesRu = listOf("следующий трек", "следующий"),
            ),
        ),
    )

    private val automations = listOf(
        AutomationSummary(
            id = "f9c025e4-b8c7-4f72-b593-a2ac0e3b4102",
            name = "климат",
            enabled = false,
        ),
    )

    @Test
    fun matchesOutsideTemperaturePhrase() {
        val intent = matcher.match("Сколько градусов на улице?", catalog, automations)
        assertTrue(intent is VoiceIntent.QuerySignal)
        intent as VoiceIntent.QuerySignal
        assertEquals("outside_temperature", intent.signal.id)
    }

    @Test
    fun prefersAutomationWhenRunVerbPresent() {
        val intent = matcher.match("запусти климат", catalog, automations)
        assertTrue(intent is VoiceIntent.RunAutomation)
        intent as VoiceIntent.RunAutomation
        assertEquals("климат", intent.automation.name)
    }

    @Test
    fun matchesMediaNext() {
        val intent = matcher.match("следующий трек", catalog, automations)
        assertTrue(intent is VoiceIntent.InvokeAction)
        intent as VoiceIntent.InvokeAction
        assertEquals("media_next", intent.action.actionType)
    }

    @Test
    fun unknownPhrase() {
        val intent = matcher.match("расскажи анекдот", catalog, automations)
        assertEquals(VoiceIntent.Unknown, intent)
    }

    @Test
    fun formatter_handlesSentinelTemperature() {
        val signal = catalog.signals.first { it.id == "outside_temperature" }
        val text = SignalAnswerFormatter.format(
            signal,
            SignalReading("outside_temperature", available = true, valueType = "number", value = -40),
        )
        assertTrue(text.startsWith("Нет данных"))
    }

    @Test
    fun formatter_formatsSpeed() {
        val signal = catalog.signals.first { it.id == "car_speed" }
        val text = SignalAnswerFormatter.format(
            signal,
            SignalReading("car_speed", available = true, valueType = "number", value = 36),
        )
        assertEquals("Скорость автомобиля: 36 км/ч", text)
    }

    @Test
    fun preferredSource_prefersHeadUnit() {
        val signal = catalog.signals.first { it.id == "outside_temperature" }
        assertEquals("head_unit", SignalAnswerFormatter.preferredSource(signal))
    }
}
