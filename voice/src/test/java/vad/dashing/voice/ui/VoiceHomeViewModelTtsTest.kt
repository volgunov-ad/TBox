package vad.dashing.voice.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import vad.dashing.voice.api.ExternalApiClient
import vad.dashing.voice.nlu.AliasNluMatcher
import vad.dashing.voice.settings.VoiceSettingsRepository
import vad.dashing.voice.tts.NoOpVoiceTts

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceHomeViewModelTtsTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var server: MockWebServer
    private lateinit var tts: NoOpVoiceTts

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        tts = NoOpVoiceTts()
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    @Test
    fun runPhrase_speaksFormattedSignalAnswer() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "catalogVersion": 2,
                  "signals": [
                    {
                      "id": "outside_temperature",
                      "label": "Температура снаружи",
                      "unit": "°C",
                      "valueType": "number",
                      "sources": ["head_unit"],
                      "voiceAliasesRu": ["сколько градусов на улице"],
                      "namedValues": []
                    }
                  ],
                  "actionTypes": []
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(MockResponse().setBody("""{"automations":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "source": "head_unit",
                  "signals": [
                    {
                      "id": "outside_temperature",
                      "available": true,
                      "valueType": "number",
                      "value": 5
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = VoiceHomeViewModel(
            application = app,
            settingsRepository = VoiceSettingsRepository(app),
            apiClient = ExternalApiClient(),
            nlu = AliasNluMatcher(),
            tts = tts,
        )
        vm.onHostChange(server.hostName)
        vm.onPortChange(server.port.toString())
        vm.onTokenChange("test-token")
        vm.onPhraseChange("сколько градусов на улице")
        vm.runPhrase()

        var waited = 0
        while (tts.speakCount == 0 && waited < 100) {
            Thread.sleep(20)
            waited++
        }
        assertEquals(3, server.requestCount)
        assertEquals(1, tts.speakCount)
        assertEquals("Температура снаружи: 5 °C", tts.lastSpoken)
    }
}
