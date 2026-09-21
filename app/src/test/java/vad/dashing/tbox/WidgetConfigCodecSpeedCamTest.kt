package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.speedcam.DEFAULT_SPEED_CAM_OVERAGE_KMH
import vad.dashing.tbox.speedcam.DEFAULT_SPEED_CAM_RADIUS_M
import vad.dashing.tbox.speedcam.SPEED_CAM_WIDGET_DATA_KEY

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecSpeedCamTest {
    @Test
    fun defaultsOmitSpeedCamFieldsFromJson() {
        val json = serializeWidgetConfigs(
            listOf(FloatingDashboardWidgetConfig(dataKey = SPEED_CAM_WIDGET_DATA_KEY)),
        )
        assertFalse(json.contains("speedCamOverageKmh"))
        assertFalse(json.contains("speedCamRadiusM"))
        assertFalse(json.contains("speedCamShowOnMap"))
    }

    @Test
    fun roundTripsNonDefaultSettings() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = SPEED_CAM_WIDGET_DATA_KEY,
                    speedCamOverageKmh = 10,
                    speedCamRadiusM = 800,
                    speedCamShowOnMap = true,
                ),
            ),
        )
        assertTrue(json.contains("\"speedCamOverageKmh\":10"))
        assertTrue(json.contains("\"speedCamRadiusM\":800"))
        assertTrue(json.contains("\"speedCamShowOnMap\":true"))
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(10, parsed[0].speedCamOverageKmh)
        assertEquals(800, parsed[0].speedCamRadiusM)
        assertTrue(parsed[0].speedCamShowOnMap)
    }

    @Test
    fun nonSpeedCamKeyIgnoresPersistedSpeedCamFields() {
        val other = FloatingDashboardWidgetConfig(
            dataKey = "espConnected",
            speedCamOverageKmh = 5,
            speedCamRadiusM = 900,
            speedCamShowOnMap = true,
        )
        val json = serializeWidgetConfigs(listOf(other))
        assertFalse(json.contains("speedCamOverageKmh"))
        assertFalse(json.contains("speedCamShowOnMap"))
        val parsed = parseWidgetConfigsFromString(json)
        assertEquals(DEFAULT_SPEED_CAM_OVERAGE_KMH, parsed[0].speedCamOverageKmh)
        assertEquals(DEFAULT_SPEED_CAM_RADIUS_M, parsed[0].speedCamRadiusM)
        assertFalse(parsed[0].speedCamShowOnMap)
    }

    @Test
    fun clampsOutOfRangeOnParse() {
        val raw = """[{"dataKey":"speedCamWidget","speedCamOverageKmh":99,"speedCamRadiusM":50}]"""
        val parsed = parseWidgetConfigsFromString(raw)
        assertEquals(40, parsed[0].speedCamOverageKmh)
        assertEquals(300, parsed[0].speedCamRadiusM)
    }
}
