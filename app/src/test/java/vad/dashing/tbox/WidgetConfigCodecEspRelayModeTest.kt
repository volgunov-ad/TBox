package vad.dashing.tbox

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecEspRelayModeTest {

    @Test
    fun serializesButtonModeOnlyForRelayTiles() {
        val relay = FloatingDashboardWidgetConfig(
            dataKey = "espRelay0",
            espRelayMode = EspRelayWidgetMode.BUTTON,
        )
        val other = FloatingDashboardWidgetConfig(
            dataKey = "espConnected",
            espRelayMode = EspRelayWidgetMode.BUTTON,
        )
        val json = serializeWidgetConfigs(listOf(relay, other))
        assertEquals(true, json.contains("\"espRelayMode\":\"button\""))
        val arr = JSONArray(json)
        assertFalse(arr.getJSONObject(1).has("espRelayMode"))
    }

    @Test
    fun roundTripsButtonModeAndDefaultsRelay() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = "espRelay1",
                espRelayMode = EspRelayWidgetMode.BUTTON,
            ),
            FloatingDashboardWidgetConfig(dataKey = "espRelay0"),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals(EspRelayWidgetMode.BUTTON, parsed[0].espRelayMode)
        assertEquals(EspRelayWidgetMode.RELAY, parsed[1].espRelayMode)
    }

    @Test
    fun ignoresUnknownModeKey() {
        val raw = """[{"dataKey":"espRelay0","espRelayMode":"nope"}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(EspRelayWidgetMode.DEFAULT, parsed[0].espRelayMode)
    }
}
