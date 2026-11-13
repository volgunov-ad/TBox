package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecCamdWindowTest {

    @Test
    fun serializeAndParseKeepsCameraAndGeometry() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = CAMD_WINDOW_WIDGET_DATA_KEY,
                    camdWindowCameraId = 8,
                    camdWindowX = 10,
                    camdWindowY = 20,
                    camdWindowW = 30,
                    camdWindowH = 40,
                )
            )
        )
        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertEquals(8, parsed.camdWindowCameraId)
        assertEquals(10, parsed.camdWindowX)
        assertEquals(20, parsed.camdWindowY)
        assertEquals(30, parsed.camdWindowW)
        assertEquals(40, parsed.camdWindowH)
    }

    @Test
    fun serializeClampsAWindowThatWouldLeaveTheScreen() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = CAMD_WINDOW_WIDGET_DATA_KEY,
                    camdWindowCameraId = 99,
                    camdWindowX = 90,
                    camdWindowY = 0,
                    camdWindowW = 80,
                    camdWindowH = 40,
                )
            )
        )
        assertFalse(serialized.contains("\"camdWindowCameraId\":99"))
        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertEquals(8, parsed.camdWindowCameraId)
        assertEquals(20, parsed.camdWindowX)
        assertEquals(80, parsed.camdWindowW)
    }

    @Test
    fun serializeAndParseKeepsFlipFlags() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = CAMD_WINDOW_WIDGET_DATA_KEY,
                    camdWindowCameraId = 4,
                    camdWindowFlipH = true,
                    camdWindowFlipV = true,
                    camdWindowRotation = 180,
                )
            )
        )
        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertEquals(true, parsed.camdWindowFlipH)
        assertEquals(true, parsed.camdWindowFlipV)
        assertEquals(180, parsed.camdWindowRotation)
        assertFalse(
            serializeWidgetConfigs(
                listOf(FloatingDashboardWidgetConfig(dataKey = CAMD_WINDOW_WIDGET_DATA_KEY)),
            ).contains("camdWindowFlipH"),
        )
    }
}
