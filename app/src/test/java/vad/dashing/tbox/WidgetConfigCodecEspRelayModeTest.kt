package vad.dashing.tbox

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun roundTripsBleRelayIdAndLeavesOtherTilesBlank() {
        val original = listOf(
            FloatingDashboardWidgetConfig(dataKey = "espBleRelay0", espBleRelayId = "AABBCCDD"),
            FloatingDashboardWidgetConfig(dataKey = "espRelay0", espBleRelayId = "AABBCCDD"),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(original))
        assertEquals("aabbccdd", parsed[0].espBleRelayId)
        assertEquals("", parsed[1].espBleRelayId)
    }

    @Test
    fun ignoresUnknownModeKey() {
        val raw = """[{"dataKey":"espRelay0","espRelayMode":"nope"}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(EspRelayWidgetMode.DEFAULT, parsed[0].espRelayMode)
    }

    @Test
    fun roundTripsBleRelayBoardAndChannel() {
        val original = listOf(
            FloatingDashboardWidgetConfig(
                dataKey = "espBleRelay0",
                espBleRelayId = "AABBCCDD",
                espBleRelayChannel = 3,
            ),
            FloatingDashboardWidgetConfig(
                dataKey = "espConnected",
                espBleRelayId = "aabbccdd",
                espBleRelayChannel = 3,
            ),
        )
        val json = serializeWidgetConfigs(original)
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals("aabbccdd", parsed[0].espBleRelayId)
        assertEquals(3, parsed[0].espBleRelayChannel)
        assertEquals("", parsed[1].espBleRelayId)
        assertEquals(0, parsed[1].espBleRelayChannel)
        assertFalse(JSONArray(json).getJSONObject(1).has("espBleRelayId"))
        assertFalse(JSONArray(json).getJSONObject(1).has("espBleRelayChannel"))
    }

    @Test
    fun missingOrOutOfRangeBleChannelBecomesZero() {
        val missing = parseWidgetConfigsFromString("""[{"dataKey":"espBleRelay1","espBleRelayId":"11223344"}]""")
        assertEquals("11223344", missing[0].espBleRelayId)
        assertEquals(0, missing[0].espBleRelayChannel)
        val high = parseWidgetConfigsFromString(
            """[{"dataKey":"espBleRelay1","espBleRelayId":"11223344","espBleRelayChannel":99}]""",
        )
        assertEquals(0, high[0].espBleRelayChannel)
        val saved = serializeWidgetConfigs(
            listOf(FloatingDashboardWidgetConfig(dataKey = "espBleRelay0", espBleRelayChannel = 0)),
        )
        assertTrue(saved.contains("\"espBleRelayChannel\":0"))
    }
}
