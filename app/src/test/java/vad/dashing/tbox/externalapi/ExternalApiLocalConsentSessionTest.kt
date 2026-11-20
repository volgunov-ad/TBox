package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.localaccess.LocalAccessContract

class ExternalApiLocalConsentSessionTest {
    private val mqtt = ExternalApiLocalConsent(
        packageName = "vad.dashing.mqtt",
        clientName = "TBox MQTT",
        certSha256 = "aa11",
    )
    private val dcl = ExternalApiLocalConsent(
        packageName = "ras.dashing.tbox.launcher",
        clientName = "Dashing Club Launcher",
        certSha256 = "bb22",
    )

    @Test
    fun dismissWithoutAnswer_keepsPending() {
        val session = ExternalApiLocalConsentSession()
        assertEquals(LocalAccessContract.STATUS_PENDING, session.requestInteractive(mqtt))
        assertEquals(mqtt, session.pending.value)
        session.dismissWithoutAnswer()
        assertEquals(mqtt, session.pending.value)
        assertEquals(LocalAccessContract.STATUS_PENDING, session.pollStatus(mqtt))
    }

    @Test
    fun takeForApprove_clearsPendingAndCanRegisterTrustedClient() {
        val session = ExternalApiLocalConsentSession()
        session.requestInteractive(dcl)
        val approved = session.takeForApprove()
        assertNotNull(approved)
        assertNull(session.pending.value)
        assertEquals(LocalAccessContract.STATUS_CONSENT_REQUIRED, session.pollStatus(dcl))

        // Same path Settings → API Approve uses after takeForApprove: register trusted local client.
        val clients = ExternalApiAuth.registerApprovedClient(
            clients = emptyList(),
            clientId = ExternalApiLocalClients.clientId(approved!!.packageName),
            clientName = approved.clientName,
            accessToken = "settings-approve-token",
            createdAtEpochMs = 1L,
            packageName = approved.packageName,
            certSha256 = approved.certSha256,
        )
        assertTrue(
            ExternalApiAuth.isLocalClientTrusted(
                clients,
                ExternalApiLocalClients.clientId(approved.packageName),
                approved.packageName,
                approved.certSha256,
            ),
        )
    }

    @Test
    fun takeForDeny_marksDeniedUntilNextInteractive() {
        val session = ExternalApiLocalConsentSession()
        session.requestInteractive(mqtt)
        assertEquals(mqtt, session.takeForDeny())
        assertNull(session.pending.value)
        assertEquals(LocalAccessContract.STATUS_DENIED, session.pollStatus(mqtt))
        session.requestInteractive(mqtt)
        assertEquals(LocalAccessContract.STATUS_PENDING, session.pollStatus(mqtt))
    }

    @Test
    fun pollStatus_consentRequiredWhenNeverAsked() {
        val session = ExternalApiLocalConsentSession()
        assertEquals(LocalAccessContract.STATUS_CONSENT_REQUIRED, session.pollStatus(mqtt))
    }
}
