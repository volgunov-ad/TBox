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
import vad.dashing.voice.api.CatalogWriteOption
import vad.dashing.voice.api.CatalogWriteSchema
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
            CatalogSignal(
                id = "hvac_power",
                label = "Климат",
                unit = "",
                valueType = "state",
                sources = listOf("head_unit"),
                voiceAliasesRu = listOf(
                    "кондиционер",
                    "климат",
                    "состояние климата",
                ),
            ),
        ),
        actionTypes = listOf(
            CatalogAction(
                type = "builtin",
                actionType = "media_next",
                voiceAliasesRu = listOf("следующий трек", "следующий"),
            ),
            CatalogAction(
                type = "can_command",
                label = "Управление кондиционером",
                voiceAliasesRu = listOf(
                    "кондиционер",
                    "включи климат",
                    "выключи климат",
                    "климат-контроль",
                ),
                // No write → still not voice-invokable (legacy catalog / incomplete pair).
            ),
            CatalogAction(
                type = "can_command",
                bus = "vehicle",
                propertyId = 37,
                label = "Температура климата",
                voiceAliasesRu = listOf("потеплее", "похолоднее"),
                operations = listOf("set"),
                signalId = "hvac_temperature_left",
                write = CatalogWriteSchema(
                    kind = CatalogWriteSchema.KIND_NUMBER,
                    options = listOf(CatalogWriteOption("22.5", 225)),
                    step = 0.5,
                ),
            ),
            CatalogAction(
                type = "can_command",
                bus = "vehicle",
                propertyId = 188,
                label = "Климат питание",
                voiceAliasesRu = listOf("включи климат-контроль полностью"),
                operations = listOf("set"),
                signalId = "hvac_power",
                write = CatalogWriteSchema(
                    kind = CatalogWriteSchema.KIND_BINARY,
                    options = listOf(
                        CatalogWriteOption("off", "off"),
                        CatalogWriteOption("on", "on"),
                    ),
                ),
            ),
            CatalogAction(
                type = "delay",
                voiceAliasesRu = listOf("задержка", "подожди секунд"),
            ),
            CatalogAction(
                type = "builtin",
                actionType = "media_play_pause",
                voiceAliasesRu = listOf("пауза", "стоп", "плей пауза"),
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
    fun prefersAutomationForVkluchiRuleName() {
        val intent = matcher.match("включи климат", catalog, automations)
        assertTrue(intent is VoiceIntent.RunAutomation)
        intent as VoiceIntent.RunAutomation
        assertEquals("климат", intent.automation.name)
    }

    @Test
    fun skipsCanCommandWithoutWrite_fallsBackToClimateSignal() {
        val intent = matcher.match("состояние климата", catalog, automations = emptyList())
        assertTrue(intent is VoiceIntent.QuerySignal)
        intent as VoiceIntent.QuerySignal
        assertEquals("hvac_power", intent.signal.id)
    }

    @Test
    fun matchesRelativeCanCommandWhenWritePresent() {
        val intent = matcher.match("потеплее", catalog, automations = emptyList())
        assertTrue(intent is VoiceIntent.InvokeAction)
        intent as VoiceIntent.InvokeAction
        assertEquals("can_command", intent.action.type)
        assertEquals(37, intent.action.propertyId)
    }

    @Test
    fun matchesBinaryCanCommandWhenWritePresent() {
        val intent = matcher.match(
            "включи климат-контроль полностью",
            catalog,
            automations = emptyList(),
        )
        assertTrue(intent is VoiceIntent.InvokeAction)
        intent as VoiceIntent.InvokeAction
        assertEquals(188, intent.action.propertyId)
        assertEquals(CatalogWriteSchema.KIND_BINARY, intent.action.write?.kind)
    }

    @Test
    fun doesNotMatchDelayOrCanCommandForPause() {
        val intent = matcher.match("пауза", catalog, automations)
        assertTrue(intent is VoiceIntent.InvokeAction)
        intent as VoiceIntent.InvokeAction
        assertEquals("media_play_pause", intent.action.actionType)
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
