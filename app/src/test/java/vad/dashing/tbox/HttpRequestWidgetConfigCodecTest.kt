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
class HttpRequestWidgetConfigCodecTest {

    @Test
    fun serializeAndParseHttpRequestWidget_keepsYamlAndBrowserFlag() {
        val yaml = """
            url: 'https://example.com/api'
            method: post
            payload: '{}'
        """.trimIndent()
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = HTTP_REQUEST_WIDGET_DATA_KEY,
                    showTitle = true,
                    customTitle = "Timer",
                    httpRequestYaml = yaml,
                    httpOpenBrowser = true,
                )
            )
        )

        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertEquals(HTTP_REQUEST_WIDGET_DATA_KEY, parsed.dataKey)
        assertTrue(parsed.showTitle)
        assertEquals("Timer", parsed.customTitle)
        assertEquals(yaml, parsed.httpRequestYaml)
        assertTrue(parsed.httpOpenBrowser)
    }

    @Test
    fun parseHttpRequestFields_ignoresThemForOtherWidgets() {
        val parsed = parseWidgetConfigsFromString(
            """
            [{
              "dataKey":"${APP_LAUNCHER_WIDGET_DATA_KEY}",
              "httpRequestYaml":"url: 'https://example.com'",
              "httpOpenBrowser":true
            }]
            """.trimIndent()
        ).single()

        assertEquals(DEFAULT_HTTP_REQUEST_WIDGET_YAML, parsed.httpRequestYaml)
        assertFalse(parsed.httpOpenBrowser)
    }
}
