package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRequestWidgetTest {

    @Test
    fun parseHttpRequestWidgetYaml_appliesDefaults() {
        val cfg = parseHttpRequestWidgetYaml("url: 'http://example.com'").getOrThrow()
        assertEquals("http://example.com", cfg.url)
        assertEquals("get", cfg.method)
        assertEquals(DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS, cfg.timeoutSeconds)
        assertTrue(cfg.verifySsl)
    }

    @Test
    fun parseHttpRequestWidgetYaml_parsesHeadersPayloadAndOptions() {
        val cfg = parseHttpRequestWidgetYaml(
            """
            url: 'https://example.com/api'
            method: POST
            headers:
                authorization: 'Bearer token'
                content-type: 'application/json'
            payload: '{"entity_id":"timer.test"}'
            timeout: 3
            verify_ssl: false
            insecure_cipher: true
            skip_url_encoding: true
            """.trimIndent()
        ).getOrThrow()

        assertEquals("post", cfg.method)
        assertEquals("Bearer token", cfg.headers["authorization"])
        assertEquals("application/json", cfg.headers["content-type"])
        assertEquals("""{"entity_id":"timer.test"}""", cfg.payload)
        assertEquals(3, cfg.timeoutSeconds)
        assertFalse(cfg.verifySsl)
        assertTrue(cfg.insecureCipher)
        assertTrue(cfg.skipUrlEncoding)
    }

    @Test
    fun parseHttpRequestWidgetYaml_rejectsDefaultUrl() {
        val result = parseHttpRequestWidgetYaml(DEFAULT_HTTP_REQUEST_WIDGET_YAML)
        assertTrue(result.isFailure)
    }

    @Test
    fun httpRequestWidgetIsSuccess_onlyAcceptsSuccessResult() {
        assertTrue(httpRequestWidgetIsSuccess(HttpRequestWidgetResult.Success(204)))
        assertFalse(httpRequestWidgetIsSuccess(HttpRequestWidgetResult.Failure("HTTP 500", 500)))
    }
}
