package vad.dashing.voice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.api.CatalogSignal
import vad.dashing.voice.api.CatalogWriteOption
import vad.dashing.voice.api.CatalogWriteSchema
import vad.dashing.voice.api.SignalReading

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceInvokePlannerTest {
    private val hvacTemp = CatalogAction(
        type = "can_command",
        bus = "vehicle",
        propertyId = 37,
        label = "Температура климата",
        voiceAliasesRu = listOf("потеплее", "похолоднее"),
        operations = listOf("set"),
        signalId = "hvac_temperature_left",
        write = CatalogWriteSchema(
            kind = CatalogWriteSchema.KIND_NUMBER,
            options = listOf(
                CatalogWriteOption("22.0", 220),
                CatalogWriteOption("22.5", 225),
                CatalogWriteOption("23.0", 230),
            ),
            min = 15.5,
            max = 32.5,
            step = 0.5,
            unit = "°C",
        ),
    )

    private val hvacPower = CatalogAction(
        type = "can_command",
        bus = "vehicle",
        propertyId = 188,
        label = "Климат",
        voiceAliasesRu = listOf("включи климат", "выключи климат"),
        operations = listOf("set", "toggle"),
        signalId = "hvac_power",
        write = CatalogWriteSchema(
            kind = CatalogWriteSchema.KIND_BINARY,
            options = listOf(
                CatalogWriteOption("off", "off"),
                CatalogWriteOption("on", "on"),
            ),
        ),
    )

    private val volume = CatalogAction(
        type = "builtin",
        actionType = "set_media_volume",
        label = "Громкость медиа",
        voiceAliasesRu = listOf("тише", "громче"),
        write = CatalogWriteSchema(
            kind = CatalogWriteSchema.KIND_NUMBER,
            options = emptyList(),
            min = 0.0,
            max = 31.0,
            step = 1.0,
        ),
    )

    private val catalog = ApiCatalog(
        catalogVersion = 4,
        signals = listOf(
            CatalogSignal(
                id = "hvac_temperature_left",
                label = "Температура слева",
                unit = "°C",
                valueType = "number",
                sources = listOf("head_unit"),
                voiceAliasesRu = emptyList(),
            ),
            CatalogSignal(
                id = "hu_media_volume",
                label = "Громкость медиа",
                unit = "",
                valueType = "number",
                sources = listOf("app"),
                voiceAliasesRu = emptyList(),
            ),
        ),
        actionTypes = listOf(hvacTemp, hvacPower, volume),
    )

    @Test
    fun warmerNeedsSignalThenInvokesRaw() {
        val plan = VoiceInvokePlanner.plan("потеплее", hvacTemp, catalog)
        assertTrue(plan is VoiceInvokePlanner.Plan.NeedsSignal)
        plan as VoiceInvokePlanner.Plan.NeedsSignal
        assertEquals("hvac_temperature_left", plan.signalId)
        val payload = plan.apply(
            SignalReading("hvac_temperature_left", true, "number", 22.0),
        ).getOrThrow()
        assertEquals("can_command", payload.getString("type"))
        assertEquals(225, payload.getInt("value"))
        assertEquals("set", payload.getString("operation"))
    }

    @Test
    fun climateOnImmediate() {
        val plan = VoiceInvokePlanner.plan("включи климат", hvacPower, catalog)
        assertTrue(plan is VoiceInvokePlanner.Plan.Immediate)
        plan as VoiceInvokePlanner.Plan.Immediate
        assertEquals("on", plan.payload.getString("value"))
    }

    @Test
    fun quieterNeedsVolumeSignal() {
        val plan = VoiceInvokePlanner.plan("тише", volume, catalog)
        assertTrue(plan is VoiceInvokePlanner.Plan.NeedsSignal)
        plan as VoiceInvokePlanner.Plan.NeedsSignal
        assertEquals("hu_media_volume", plan.signalId)
        val payload = plan.apply(
            SignalReading("hu_media_volume", true, "number", 12),
        ).getOrThrow()
        assertEquals("builtin", payload.getString("type"))
        assertEquals("set_media_volume", payload.getString("actionType"))
        assertEquals(11, payload.getInt("intValue"))
    }

    @Test
    fun absoluteVolumeImmediate() {
        val plan = VoiceInvokePlanner.plan("громкость 18", volume, catalog)
        assertTrue(plan is VoiceInvokePlanner.Plan.Immediate)
        plan as VoiceInvokePlanner.Plan.Immediate
        assertEquals(18, plan.payload.getInt("intValue"))
    }
}
