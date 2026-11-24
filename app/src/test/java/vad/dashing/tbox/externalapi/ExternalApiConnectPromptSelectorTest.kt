package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiConnectPromptSelectorTest {
    private val mqtt = ExternalApiLocalConsent(
        packageName = "vad.dashing.mqtt",
        clientName = "TBox MQTT",
        certSha256 = "abc",
    )

    private val pair = ExternalApiPairRequest(
        requestId = "req-1",
        clientId = "pc-1",
        clientName = "PC tools",
        clientKind = "tools",
        status = ExternalApiPairRequestStatus.PENDING,
    )

    @Test
    fun disabledApi_neverShowsPrompt() {
        assertNull(
            ExternalApiConnectPromptSelector.select(
                apiEnabled = false,
                localConsent = mqtt,
                pendingPairs = listOf(pair),
            ),
        )
    }

    @Test
    fun localConsent_hasPriorityOverHttpPair() {
        val selected = ExternalApiConnectPromptSelector.select(
            apiEnabled = true,
            localConsent = mqtt,
            pendingPairs = listOf(pair),
        )
        assertTrue(selected is ExternalApiConnectPrompt.Local)
        assertEquals(mqtt, (selected as ExternalApiConnectPrompt.Local).consent)
    }

    @Test
    fun httpPair_shownWhenNoLocalConsent() {
        val selected = ExternalApiConnectPromptSelector.select(
            apiEnabled = true,
            localConsent = null,
            pendingPairs = listOf(pair),
        )
        assertTrue(selected is ExternalApiConnectPrompt.HttpPair)
        assertEquals(pair.requestId, (selected as ExternalApiConnectPrompt.HttpPair).request.requestId)
    }

    @Test
    fun empty_returnsNull() {
        assertNull(
            ExternalApiConnectPromptSelector.select(
                apiEnabled = true,
                localConsent = null,
                pendingPairs = emptyList(),
            ),
        )
    }

    @Test
    fun httpPair_usesOldestPending() {
        val older = pair
        val newer = pair.copy(requestId = "req-2", clientId = "pc-2", clientName = "Other")
        val selected = ExternalApiConnectPromptSelector.select(
            apiEnabled = true,
            localConsent = null,
            pendingPairs = listOf(older, newer),
        )
        assertEquals("req-1", (selected as ExternalApiConnectPrompt.HttpPair).request.requestId)
    }
}
