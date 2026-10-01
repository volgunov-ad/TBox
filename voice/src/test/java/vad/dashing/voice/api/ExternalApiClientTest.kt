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

    @Test
    fun parseCatalog_readsSignalsAndAliases() {
        val catalog = ExternalApiClient.parseCatalog(
            """
            {
              "catalogVersion": 2,
              "signals": [
                {
                  "id": "outside_temperature",
                  "label": "Температура снаружи",
                  "unit": "°C",
                  "valueType": "number",
                  "sources": ["head_unit", "tbox"],
                  "voiceAliasesRu": ["температура на улице", "на улице"],
                  "namedValues": []
                }
              ],
              "actionTypes": [
                {
                  "type": "builtin",
                  "actionType": "show_toast",
                  "safety": "safe",
                  "voiceAliasesRu": ["тост"]
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, catalog.catalogVersion)
        assertEquals(1, catalog.signals.size)
        assertEquals("outside_temperature", catalog.signals[0].id)
        assertTrue(catalog.signals[0].voiceAliasesRu.contains("на улице"))
        assertEquals("show_toast", catalog.actionTypes[0].actionType)
    }

    @Test
    fun parseSignals_readsAvailableValue() {
        val snapshot = ExternalApiClient.parseSignals(
            """
            {
              "signals": [
                {
                  "id": "car_speed",
                  "valueType": "number",
                  "available": true,
                  "value": 12
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, snapshot.signals.size)
        assertTrue(snapshot.signals[0].available)
        assertEquals(12, snapshot.signals[0].value)
    }
}
