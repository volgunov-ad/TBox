package vad.dashing.mqtt.api

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MonitorApiTest {
    @Test
    fun healthReadsJsonAndRejectsAForeignBody() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"ok":true,"catalogVersion":4}"""))
        server.enqueue(MockResponse().setBody("<html>nope</html>"))
        server.start()
        try {
            val api = MonitorApi()
            val health = api.health(server.port)
            assertEquals(true, health.ok)
            assertEquals(4, health.catalogVersion)
            try {
                api.health(server.port)
                fail("non-json body must fail")
            } catch (error: ApiCallException) {
                assertEquals("bad_response", error.code)
            }
        } finally {
            server.shutdown()
        }
    }
}
