package vad.dashing.tbox.phoneble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneSessionPolicyTest {

    @Test
    fun commandResetsAcceptRange_soOlderRefreshCannotOverwrite() {
        val before = PhoneBleLinkPolicy.AcceptWindow(acceptFrom = 1L, waiting = 8L)
        val afterCommand = PhoneBleLinkPolicy.acceptWindowAfterSend(
            previous = before,
            counter = 9L,
            resetAcceptRange = true,
            updateWaiting = false,
        )
        assertEquals(9L, afterCommand.acceptFrom)
        assertEquals(8L, afterCommand.waiting)
        assertFalse(PhoneBleLinkPolicy.counterInAcceptWindow(8L, afterCommand))
        assertFalse(PhoneBleLinkPolicy.counterInAcceptWindow(9L, afterCommand))

        val afterRefresh = PhoneBleLinkPolicy.acceptWindowAfterSend(
            previous = afterCommand,
            counter = 10L,
            resetAcceptRange = true,
            updateWaiting = true,
        )
        assertTrue(PhoneBleLinkPolicy.counterInAcceptWindow(10L, afterRefresh))
        assertFalse(PhoneBleLinkPolicy.counterInAcceptWindow(9L, afterRefresh))
    }

    @Test
    fun refreshWithoutReset_keepsEarlierCommands() {
        val window = PhoneBleLinkPolicy.acceptWindowAfterSend(
            previous = PhoneBleLinkPolicy.AcceptWindow(4L, 4L),
            counter = 6L,
            resetAcceptRange = false,
            updateWaiting = true,
        )
        assertEquals(4L, window.acceptFrom)
        assertTrue(PhoneBleLinkPolicy.counterInAcceptWindow(5L, window))
        assertFalse(PhoneBleLinkPolicy.counterInAcceptWindow(3L, window))
    }

    @Test
    fun closedBeforeAnswer_backsOff_andDoesNotResetPasses() {
        val foreground = PhoneBleLinkPolicy.directWaitAfterClose(
            answered = false,
            foreground = true,
            passesWithoutLink = 0,
        )
        assertEquals(1, foreground.passesWithoutLink)
        assertTrue(foreground.waitMs >= PhoneBleLinkPolicy.DIRECT_CLOSED_BEFORE_ANSWER_FG_MS)

        val again = PhoneBleLinkPolicy.directWaitAfterClose(
            answered = false,
            foreground = true,
            passesWithoutLink = foreground.passesWithoutLink,
        )
        assertEquals(2, again.passesWithoutLink)
        assertTrue(again.waitMs >= PhoneBleLinkPolicy.DIRECT_CLOSED_BEFORE_ANSWER_FG_MS)

        val background = PhoneBleLinkPolicy.directWaitAfterClose(
            answered = false,
            foreground = false,
            passesWithoutLink = 3,
        )
        assertEquals(4, background.passesWithoutLink)
        assertEquals(
            PhoneBleLinkPolicy.directSearchRetryMs(foreground = false, passesWithoutLink = 4),
            background.waitMs,
        )
        assertTrue(background.waitMs > PhoneBleLinkPolicy.DIRECT_RETRY_MS)
    }

    @Test
    fun answeredLink_reconnectsQuicklyAndClearsPasses() {
        val wait = PhoneBleLinkPolicy.directWaitAfterClose(
            answered = true,
            foreground = false,
            passesWithoutLink = 6,
        )
        assertEquals(0, wait.passesWithoutLink)
        assertEquals(PhoneBleLinkPolicy.DIRECT_RECONNECT_MS, wait.waitMs)
    }

    @Test
    fun status_staleConfirmedLink_isNoAnswer_andControlsStop() {
        assertEquals(
            PhoneBleLinkPolicy.PhoneUiStatus.WAITING,
            PhoneBleLinkPolicy.phoneUiStatus(
                permissionsReady = true,
                bluetoothOn = true,
                linkUp = true,
                confirmed = false,
                snapAgeMs = 1_000L,
            ),
        )
        assertEquals(
            PhoneBleLinkPolicy.PhoneUiStatus.NO_ANSWER,
            PhoneBleLinkPolicy.phoneUiStatus(
                permissionsReady = true,
                bluetoothOn = true,
                linkUp = true,
                confirmed = true,
                snapAgeMs = PhoneBleLinkPolicy.SNAP_STALE_MS + 1,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.controlsLive(
                confirmed = true,
                snapAgeMs = PhoneBleLinkPolicy.SNAP_STALE_MS + 1,
            ),
        )
        assertTrue(PhoneBleLinkPolicy.controlsLive(confirmed = true, snapAgeMs = 1_000L))
        assertFalse(PhoneBleLinkPolicy.controlsLive(confirmed = false, snapAgeMs = 100L))
    }

    @Test
    fun reopenConnect_isOncePerLink_notOncePerProcess() {
        assertFalse(
            PhoneBleLinkPolicy.shouldReopenConnectPage(
                linkUp = true,
                lastSnapAtMs = 5_000L,
                linkedAtMs = 1_000L,
                nowMs = 20_000L,
                alreadyReopened = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.shouldReopenConnectPage(
                linkUp = true,
                lastSnapAtMs = 500L,
                linkedAtMs = 1_000L,
                nowMs = 1_000L + PhoneBleLinkPolicy.NO_SNAP_REOPEN_MS,
                alreadyReopened = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.shouldReopenConnectPage(
                linkUp = true,
                lastSnapAtMs = 500L,
                linkedAtMs = 1_000L,
                nowMs = 20_000L,
                alreadyReopened = true,
            ),
        )
    }

    @Test
    fun scanStart_staysUnderFourPerThirtySeconds() {
        val starts = listOf(0L, 1_000L, 2_000L, 3_000L)
        assertEquals(0L, PhoneBleLinkPolicy.scanStartDelayMs(nowMs = 31_000L, startTimesMs = starts))
        val delay = PhoneBleLinkPolicy.scanStartDelayMs(nowMs = 10_000L, startTimesMs = starts)
        assertTrue(delay > 0L)
        assertTrue(delay <= PhoneBleLinkPolicy.SCAN_WINDOW_MS)
    }

    @Test
    fun gatt133_backsOff_strangerUsesFixedPause() {
        assertEquals(800L, PhoneBleLinkPolicy.bleConnectRetryMs(consecutive133 = 0, stranger = false))
        assertEquals(800L, PhoneBleLinkPolicy.bleConnectRetryMs(consecutive133 = 1, stranger = false))
        assertEquals(1_600L, PhoneBleLinkPolicy.bleConnectRetryMs(consecutive133 = 2, stranger = false))
        assertEquals(
            PhoneBleLinkPolicy.BLE_RETRY_MAX_MS,
            PhoneBleLinkPolicy.bleConnectRetryMs(consecutive133 = 20, stranger = false),
        )
        assertEquals(
            PhoneBleLinkPolicy.STRANGER_RESCAN_MS,
            PhoneBleLinkPolicy.bleConnectRetryMs(consecutive133 = 4, stranger = true),
        )
    }

    @Test
    fun candidates_skipHeadphonesUnlessTheServiceIsCached() {
        assertNull(
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = PhoneBleLinkPolicy.BT_MAJOR_AUDIO_VIDEO,
                deviceClass = PhoneBleLinkPolicy.BT_DEVICE_HEADPHONES,
                hasServiceUuid = false,
                remembered = false,
            ),
        )
        assertEquals(
            3,
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = PhoneBleLinkPolicy.BT_MAJOR_AUDIO_VIDEO,
                deviceClass = PhoneBleLinkPolicy.BT_DEVICE_HEADPHONES,
                hasServiceUuid = true,
                remembered = false,
            ),
        )
        assertEquals(
            1,
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = PhoneBleLinkPolicy.BT_MAJOR_AUDIO_VIDEO,
                deviceClass = PhoneBleLinkPolicy.BT_DEVICE_CAR_AUDIO,
                hasServiceUuid = false,
                remembered = false,
            ),
        )
        assertEquals(
            0,
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = PhoneBleLinkPolicy.BT_MAJOR_WEARABLE,
                deviceClass = 0x0704,
                hasServiceUuid = false,
                remembered = true,
            ),
        )
        assertNull(
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = PhoneBleLinkPolicy.BT_MAJOR_WEARABLE,
                deviceClass = 0x0704,
                hasServiceUuid = false,
                remembered = false,
            ),
        )
        assertEquals(
            PhoneBleLinkPolicy.CONNECTED_UNKNOWN_RANK,
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = 0x0200,
                deviceClass = 0x0204,
                hasServiceUuid = false,
                remembered = false,
                connectedProfile = true,
            ),
        )
        assertNull(
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = 0x0200,
                deviceClass = 0x0204,
                hasServiceUuid = false,
                remembered = false,
                connectedProfile = false,
            ),
        )
        assertNull(
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = PhoneBleLinkPolicy.BT_MAJOR_AUDIO_VIDEO,
                deviceClass = PhoneBleLinkPolicy.BT_DEVICE_HEADPHONES,
                hasServiceUuid = false,
                remembered = false,
                connectedProfile = true,
            ),
        )
        assertEquals(
            PhoneBleLinkPolicy.DEMOTED_REMEMBERED_RANK,
            PhoneBleLinkPolicy.directCandidateRank(
                majorClass = 0x0200,
                deviceClass = 0x0204,
                hasServiceUuid = false,
                remembered = true,
                connectedProfile = true,
                rememberedDemoted = true,
            ),
        )
    }

    @Test
    fun rememberedRejects_demoteRank_andDoNotCountAClosedLink() {
        var rejects = 0
        repeat(PhoneBleLinkPolicy.FORGET_REMEMBERED_AFTER - 1) {
            rejects = PhoneBleLinkPolicy.nextRememberedFastRejects(
                current = rejects,
                fastFailOnRemembered = true,
                answered = false,
            )
        }
        assertFalse(PhoneBleLinkPolicy.rememberedDemoted(rejects))
        rejects = PhoneBleLinkPolicy.nextRememberedFastRejects(
            current = rejects,
            fastFailOnRemembered = false,
            answered = false,
        )
        assertFalse(PhoneBleLinkPolicy.rememberedDemoted(rejects))
        rejects = PhoneBleLinkPolicy.nextRememberedFastRejects(
            current = rejects,
            fastFailOnRemembered = true,
            answered = false,
        )
        assertTrue(PhoneBleLinkPolicy.rememberedDemoted(rejects))
        assertEquals(
            0,
            PhoneBleLinkPolicy.nextRememberedFastRejects(
                current = rejects,
                fastFailOnRemembered = true,
                answered = true,
            ),
        )
    }

    @Test
    fun sdpFetch_isLimitedToRememberedAndConnectedNonAudio() {
        assertTrue(
            PhoneBleLinkPolicy.shouldFetchServiceUuid(
                remembered = true,
                connectedProfile = false,
                wearableOrHeadphones = false,
                alreadyListed = false,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.shouldFetchServiceUuid(
                remembered = false,
                connectedProfile = true,
                wearableOrHeadphones = false,
                alreadyListed = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.shouldFetchServiceUuid(
                remembered = false,
                connectedProfile = true,
                wearableOrHeadphones = true,
                alreadyListed = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.shouldFetchServiceUuid(
                remembered = false,
                connectedProfile = false,
                wearableOrHeadphones = false,
                alreadyListed = false,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.shouldFetchServiceUuid(
                remembered = true,
                connectedProfile = true,
                wearableOrHeadphones = false,
                alreadyListed = true,
            ),
        )
        assertTrue(PhoneBleLinkPolicy.sdpFetchDue(lastAskedAtMs = 0L, nowMs = 1_000L, aclConnectedAtMs = 0L))
        assertFalse(
            PhoneBleLinkPolicy.sdpFetchDue(
                lastAskedAtMs = 1_000L,
                nowMs = 1_000L + PhoneBleLinkPolicy.SDP_REFETCH_MS - 1,
                aclConnectedAtMs = 500L,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.sdpFetchDue(
                lastAskedAtMs = 1_000L,
                nowMs = 1_000L + PhoneBleLinkPolicy.SDP_REFETCH_MS,
                aclConnectedAtMs = 500L,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.sdpFetchDue(
                lastAskedAtMs = 1_000L,
                nowMs = 2_000L,
                aclConnectedAtMs = 1_500L,
            ),
        )
    }

    @Test
    fun negativeCache_neverBlocksRemembered_andExpires() {
        assertFalse(PhoneBleLinkPolicy.negativeCacheBlocks(remembered = true, expiresAtMs = 5_000L, nowMs = 1_000L))
        assertTrue(PhoneBleLinkPolicy.negativeCacheBlocks(remembered = false, expiresAtMs = 5_000L, nowMs = 1_000L))
        assertFalse(PhoneBleLinkPolicy.negativeCacheBlocks(remembered = false, expiresAtMs = 5_000L, nowMs = 5_000L))
    }

    @Test
    fun foregroundPause_isCutOnlyWhenTheUiOpensDuringTheWait() {
        assertFalse(
            PhoneBleLinkPolicy.directPauseCutShort(
                wasForeground = true,
                foregroundNow = true,
                elapsedMs = PhoneBleLinkPolicy.DIRECT_CLOSED_BEFORE_ANSWER_FG_MS,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.directPauseCutShort(
                wasForeground = false,
                foregroundNow = true,
                elapsedMs = PhoneBleLinkPolicy.DIRECT_RETRY_MS - 1,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.directPauseCutShort(
                wasForeground = false,
                foregroundNow = true,
                elapsedMs = PhoneBleLinkPolicy.DIRECT_RETRY_MS,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.directPauseCutShort(
                wasForeground = false,
                foregroundNow = false,
                elapsedMs = 60_000L,
            ),
        )
    }

    @Test
    fun foregroundServiceStopsAfterIdleWithoutDirectLink() {
        assertFalse(
            PhoneBleLinkPolicy.foregroundServiceIdle(
                uiVisible = true,
                telemetryEnabled = true,
                directLinkUp = false,
                idleMs = PhoneBleLinkPolicy.FGS_IDLE_MS,
            ),
        )
        assertFalse(
            PhoneBleLinkPolicy.foregroundServiceIdle(
                uiVisible = false,
                telemetryEnabled = true,
                directLinkUp = true,
                idleMs = PhoneBleLinkPolicy.FGS_IDLE_MS,
            ),
        )
        assertTrue(
            PhoneBleLinkPolicy.foregroundServiceIdle(
                uiVisible = false,
                telemetryEnabled = true,
                directLinkUp = false,
                idleMs = PhoneBleLinkPolicy.FGS_IDLE_MS,
            ),
        )
    }

    @Test
    fun pairTimesOutOnlyWhenNoSnapshotArrivedAfterTheRequest() {
        assertFalse(PhoneBleLinkPolicy.pairTimedOut(sentAtMs = 1_000L, nowMs = 2_000L, snapAtMs = 500L))
        assertFalse(PhoneBleLinkPolicy.pairTimedOut(sentAtMs = 1_000L, nowMs = 80_000L, snapAtMs = 2_000L))
        assertTrue(
            PhoneBleLinkPolicy.pairTimedOut(
                sentAtMs = 1_000L,
                nowMs = 1_000L + PhoneBleLinkPolicy.PAIR_CONFIRM_TIMEOUT_MS,
                snapAtMs = 500L,
            ),
        )
    }

    @Test
    fun olderGenerationCannotReplaceTheLiveSocket() {
        val slot = GenerationSlot<String>()
        val closed = ArrayList<String>()
        assertTrue(slot.publish(1, { 1 }, "first") { closed += it })
        assertEquals("first", slot.current(1))
        assertFalse(slot.publish(1, { 2 }, "stale") { closed += it })
        assertEquals("first", slot.current(1))
        assertTrue(closed.contains("stale"))
        assertTrue(slot.publish(2, { 2 }, "live") { closed += it })
        assertNull(slot.current(1))
        assertEquals("live", slot.current(2))
        assertTrue(closed.contains("first"))
    }
}
