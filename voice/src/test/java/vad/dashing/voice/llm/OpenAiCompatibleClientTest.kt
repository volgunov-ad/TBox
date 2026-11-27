package vad.dashing.voice.llm

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.voice.api.ExternalApiHttpException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OpenAiCompatibleClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiCompatibleClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OpenAiCompatibleClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listModels_parsesIdsAndSendsBearer() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "object": "list",
                  "data": [
                    {"id": "gpt-4o-mini", "object": "model"},
                    {"id": "gpt-4o", "object": "model"},
                    {"id": "gpt-4o-mini", "object": "model"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        val base = server.url("v1").toString().trimEnd('/')
        val result = client.listModels(base, "Bearer secret-key").getOrThrow()
        assertEquals(listOf("gpt-4o-mini", "gpt-4o"), result.modelIds)

        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer secret-key", request.getHeader("Authorization"))
    }

    @Test
    fun listModels_mapsHttpError() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key","type":"invalid_request_error"}}"""),
        )
        val base = server.url("v1").toString().trimEnd('/')
        val error = client.listModels(base, "bad").exceptionOrNull()
        assertTrue(error is ExternalApiHttpException)
        assertEquals(401, (error as ExternalApiHttpException).httpStatus)
        assertTrue(error.message!!.contains("Incorrect API key"))
    }

    @Test
    fun parseModels_requiresData() {
        val parsed = OpenAiCompatibleClient.parseModels(
            """{"data":[{"id":"m1"},{"id":""},{"object":"model"}]}""",
        )
        assertEquals(listOf("m1"), parsed.modelIds)
    }

    @Test
    fun chatCompletions_postsToolsAndParsesAssistant() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {"role":"assistant","content":"Уточните сигнал"}
                  }]
                }
                """.trimIndent(),
            ),
        )
        val base = server.url("v1").toString().trimEnd('/')
        val result = client.chatCompletions(
            baseUrl = base,
            apiKey = "k",
            model = "gpt-test",
            messages = listOf(CloudChatMessage(role = "user", content = "привет")),
            tools = VoiceAgentTools.definitions(),
        ).getOrThrow()
        assertEquals("Уточните сигнал", result.message.content)

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"gpt-test\""))
        assertTrue(body.contains("query_signal"))
    }
}
