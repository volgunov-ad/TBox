package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.speedcam.DEFAULT_SPEED_CAM_OVERAGE_KMH

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecMapsCamTest {

    @Test
    fun encodeOmitsMapsCamDefaults() {
        val json = serializeWidgetConfigs(
            listOf(FloatingDashboardWidgetConfig(dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY)),
        ).toString()
        assertFalse(json.contains("speedCamOverageKmh"))
        assertFalse(json.contains("mapsCamLookaheadDistanceM"))
        assertFalse(json.contains("mapsCamShowCameras"))
        assertFalse(json.contains("speedCamShowOnMap"))
    }

    @Test
    fun encodeAndDecodeNonDefaults() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = OSM_SPEED_LIMIT_WIDGET_DATA_KEY,
                    speedCamOverageKmh = 12,
                    mapsCamLookaheadDistanceM = 1500,
                    mapsCamRadarHoldDistanceM = 800,
                    mapsCamShowCameras = false,
                    mapsCamShowCurrentLimit = false,
                    mapsCamShowAheadLimit = true,
                    speedCamShowOnMap = true,
                ),
            ),
        ).toString()
        assertTrue(json.contains("\"speedCamOverageKmh\":12"))
        assertTrue(json.contains("\"mapsCamLookaheadDistanceM\":1500"))
        assertTrue(json.contains("\"mapsCamRadarHoldDistanceM\":800"))
        assertTrue(json.contains("\"mapsCamShowCameras\":false"))
        assertTrue(json.contains("\"mapsCamShowCurrentLimit\":false"))
        assertTrue(json.contains("\"speedCamShowOnMap\":true"))
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(1, parsed.size)
        assertEquals(12, parsed[0].speedCamOverageKmh)
        assertEquals(1500, parsed[0].mapsCamLookaheadDistanceM)
        assertEquals(800, parsed[0].mapsCamRadarHoldDistanceM)
        assertFalse(parsed[0].mapsCamShowCameras)
        assertFalse(parsed[0].mapsCamShowCurrentLimit)
        assertTrue(parsed[0].mapsCamShowAheadLimit)
        assertTrue(parsed[0].speedCamShowOnMap)
    }

    @Test
    fun removedSpeedCamWidgetBecomesEmpty() {
        val raw = """[{"dataKey":"speedCamWidget","speedCamOverageKmh":12}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(1, parsed.size)
        assertEquals("", parsed[0].dataKey)
        assertEquals(DEFAULT_SPEED_CAM_OVERAGE_KMH, parsed[0].speedCamOverageKmh)
    }

    @Test
    fun legacyRadiusMapsToLookahead() {
        val raw = """[{"dataKey":"osmSpeedLimitWidget","speedCamRadiusM":800}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(800, parsed[0].mapsCamLookaheadDistanceM)
    }
}
