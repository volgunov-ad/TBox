package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetConfigCodecAutomationTriggerTest {

    @Test
    fun serializeAndParseAutomationTriggerWidget_keepsTriggerId() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = AUTOMATION_TRIGGER_WIDGET_DATA_KEY,
                    automationTriggerId = "button1",
                )
            )
        )

        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertEquals(AUTOMATION_TRIGGER_WIDGET_DATA_KEY, parsed.dataKey)
        assertEquals("button1", parsed.automationTriggerId)
    }

    @Test
    fun serialize_trimsAndCapsTriggerId() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = AUTOMATION_TRIGGER_WIDGET_DATA_KEY,
                    automationTriggerId = "  " + "x".repeat(40) + "  ",
                )
            )
        )

        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertEquals(AUTOMATION_TRIGGER_ID_MAX_CHARS, parsed.automationTriggerId.length)
        assertTrue(parsed.automationTriggerId.all { it == 
'x'
 })
    }

    @Test
    fun serialize_omitsBlankTriggerId() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = AUTOMATION_TRIGGER_WIDGET_DATA_KEY,
                    automationTriggerId = "   ",
                )
            )
        )

        assertFalse(serialized.contains("automationTriggerId"))
        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertEquals("", parsed.automationTriggerId)
    }

    @Test
    fun parseAutomationTriggerId_ignoresItForOtherWidgets() {
        val parsed = parseWidgetConfigsFromString(
            """
            [{
              "dataKey":"${APP_LAUNCHER_WIDGET_DATA_KEY}",
              "automationTriggerId":"button1"
            }]
            """.trimIndent()
        ).single()

        assertEquals("", parsed.automationTriggerId)
    }
}
