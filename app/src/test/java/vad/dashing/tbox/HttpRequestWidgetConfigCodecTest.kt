package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.freeform.FreeformLaunchSide

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
        assertFalse(parsed.httpLaunchAppBeforeRequest)
        assertEquals(DEFAULT_HTTP_LAUNCH_APP_DELAY_SEC, parsed.httpLaunchAppDelaySec)
        assertEquals("", parsed.launcherAppPackage)
    }

    @Test
    fun serializeAndParseHttpRequestWidget_keepsLaunchBeforeRequestSettings() {
        val yaml = "url: 'https://example.com/hook'"
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = HTTP_REQUEST_WIDGET_DATA_KEY,
                    httpRequestYaml = yaml,
                    httpLaunchAppBeforeRequest = true,
                    httpLaunchAppDelaySec = 3,
                    launcherAppPackage = "com.example.app",
                    launcherLaunchMode = AppLauncherLaunchMode.FREEFORM,
                    launcherFreeformEnabled = true,
                    launcherFreeformSide = FreeformLaunchSide.RIGHT,
                    launcherFreeformPercent = 40,
                )
            )
        )

        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertTrue(parsed.httpLaunchAppBeforeRequest)
        assertEquals(3, parsed.httpLaunchAppDelaySec)
        assertEquals("com.example.app", parsed.launcherAppPackage)
        assertEquals(AppLauncherLaunchMode.FREEFORM, parsed.launcherLaunchMode)
        assertTrue(parsed.launcherFreeformEnabled)
        assertEquals(FreeformLaunchSide.RIGHT, parsed.launcherFreeformSide)
        assertEquals(40, parsed.launcherFreeformPercent)
        assertTrue(serialized.contains("httpLaunchAppBeforeRequest"))
    }

    @Test
    fun serializeHttpRequestWidget_omitsLaunchFieldsWhenFeatureOff() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = HTTP_REQUEST_WIDGET_DATA_KEY,
                    httpRequestYaml = "url: 'http://'",
                    httpLaunchAppBeforeRequest = false,
                    launcherAppPackage = "com.should.not.persist",
                    launcherLaunchMode = AppLauncherLaunchMode.VIRTUAL_DISPLAY,
                    httpLaunchAppDelaySec = 9,
                )
            )
        )

        assertFalse(serialized.contains("httpLaunchAppBeforeRequest"))
        assertFalse(serialized.contains("httpLaunchAppDelaySec"))
        assertFalse(serialized.contains("launcherAppPackage"))
        assertFalse(serialized.contains("launcherLaunchMode"))

        val parsed = parseWidgetConfigsFromString(serialized).single()
        assertFalse(parsed.httpLaunchAppBeforeRequest)
        assertEquals(DEFAULT_HTTP_LAUNCH_APP_DELAY_SEC, parsed.httpLaunchAppDelaySec)
        assertEquals("", parsed.launcherAppPackage)
        assertEquals(AppLauncherLaunchMode.DEFAULT, parsed.launcherLaunchMode)
    }

    @Test
    fun parseHttpRequestFields_ignoresThemForOtherWidgets() {
        val parsed = parseWidgetConfigsFromString(
            """
            [{
              "dataKey":"${APP_LAUNCHER_WIDGET_DATA_KEY}",
              "httpRequestYaml":"url: 'https://example.com'",
              "httpOpenBrowser":true,
              "httpLaunchAppBeforeRequest":true,
              "httpLaunchAppDelaySec":5
            }]
            """.trimIndent()
        ).single()

        assertEquals(DEFAULT_HTTP_REQUEST_WIDGET_YAML, parsed.httpRequestYaml)
        assertFalse(parsed.httpOpenBrowser)
        assertFalse(parsed.httpLaunchAppBeforeRequest)
        assertEquals(DEFAULT_HTTP_LAUNCH_APP_DELAY_SEC, parsed.httpLaunchAppDelaySec)
    }

    @Test
    fun normalizeHttpLaunchAppDelaySec_clampsRange() {
        assertEquals(MIN_HTTP_LAUNCH_APP_DELAY_SEC, normalizeHttpLaunchAppDelaySec(-3))
        assertEquals(MAX_HTTP_LAUNCH_APP_DELAY_SEC, normalizeHttpLaunchAppDelaySec(999))
        assertEquals(DEFAULT_HTTP_LAUNCH_APP_DELAY_SEC, normalizeHttpLaunchAppDelaySec(1))
    }

    @Test
    fun parseHttpRequestWidget_clearsLaunchWhenOpenBrowserAlsoSet() {
        val parsed = parseWidgetConfigsFromString(
            """
            [{
              "dataKey":"${HTTP_REQUEST_WIDGET_DATA_KEY}",
              "httpRequestYaml":"url: 'https://example.com'",
              "httpOpenBrowser":true,
              "httpLaunchAppBeforeRequest":true,
              "httpLaunchAppDelaySec":5,
              "launcherAppPackage":"com.example.app",
              "launcherLaunchMode":"freeform"
            }]
            """.trimIndent()
        ).single()

        assertTrue(parsed.httpOpenBrowser)
        assertFalse(parsed.httpLaunchAppBeforeRequest)
        assertEquals(DEFAULT_HTTP_LAUNCH_APP_DELAY_SEC, parsed.httpLaunchAppDelaySec)
        assertEquals("", parsed.launcherAppPackage)
        assertEquals(AppLauncherLaunchMode.DEFAULT, parsed.launcherLaunchMode)
    }

    @Test
    fun serializeHttpRequestWidget_omitsLaunchWhenOpenBrowserOn() {
        val serialized = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = HTTP_REQUEST_WIDGET_DATA_KEY,
                    httpRequestYaml = "url: 'https://example.com'",
                    httpOpenBrowser = true,
                    httpLaunchAppBeforeRequest = true,
                    httpLaunchAppDelaySec = 4,
                    launcherAppPackage = "com.example.app",
                    launcherLaunchMode = AppLauncherLaunchMode.STOCK_WINDOW,
                )
            )
        )

        assertFalse(serialized.contains("httpLaunchAppBeforeRequest"))
        assertFalse(serialized.contains("httpLaunchAppDelaySec"))
        assertFalse(serialized.contains("launcherAppPackage"))
        assertFalse(serialized.contains("launcherLaunchMode"))
        assertTrue(serialized.contains("\"httpOpenBrowser\":true") || serialized.contains("\"httpOpenBrowser\": true"))
    }
}
