package vad.dashing.voice.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiClientTest {
    @Test
    fun parseHealth_readsMonitorPayload() {
        val health = ExternalApiClient.parseHealth(
            """
            {
              "ok": true,
              "apiVersion": 1,
              "catalogVersion": 2,
              "serverEnabled": true,
              "pairingActive": false,
              "appVersion": "1.0.0-ru"
            }
            """.trimIndent(),
        )
        assertTrue(health.ok)
        assertEquals(1, health.apiVersion)
        assertEquals(2, health.catalogVersion)
        assertTrue(health.serverEnabled)
        assertEquals("1.0.0-ru", health.appVersion)
    }

    @Test
    fun baseUrl_trimsHost() {
        assertEquals("http://127.0.0.1:8765", ExternalApiClient.baseUrl(" 127.0.0.1 ", 8765))
    }
}
