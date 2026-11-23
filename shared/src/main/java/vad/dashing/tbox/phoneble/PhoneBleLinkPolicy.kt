package vad.dashing.tbox.phoneble

/**
 * Central-side BLE recovery timers for [vad.dashing.tbox.phone.PhoneRadio].
 *
 * Android can freeze the process in doze with a hung LE scan, a connectGatt that
 * never reaches link-up, a zombie GATT that stays "connected", or a write that
 * never gets a callback. Kill+relaunch used to clear those; these thresholds
 * drive the same recovery without touching pairing material.
 */
object PhoneBleLinkPolicy {
    /** OS sometimes stops LE scan without [android.bluetooth.le.ScanCallback.onScanFailed]. */
    const val SCAN_STALE_MS: Long = 12_000L

    /** connectGatt can hang with a non-null handle and never reach link-up. */
    const val CONNECT_STALE_MS: Long = 15_000L

    /**
     * Linked but no sealed snapshot for this long: GATT is dead, USB/HU is down,
     * or a write pump is wedged. Phone refreshes about every 2 s, so this is
     * several missed cycles — not a short USB blip.
     */
    const val LINK_STALE_MS: Long = 12_000L

    /** writeCharacteristic accepted but onCharacteristicWrite never arrived. */
    const val WRITE_STALE_MS: Long = 8_000L

    fun hungScan(scanning: Boolean, hasGatt: Boolean, attemptAgeMs: Long, force: Boolean): Boolean =
        scanning && !hasGatt && (force || attemptAgeMs >= SCAN_STALE_MS)

    fun hungConnect(linkUp: Boolean, hasGatt: Boolean, attemptAgeMs: Long, force: Boolean): Boolean =
        !linkUp && hasGatt && (force || attemptAgeMs >= CONNECT_STALE_MS)

    /**
     * @param snapAgeMs elapsed since the last accepted sealed snap, or since link-up
     *   when no snap has arrived yet (pass link age in that case).
     */
    fun staleLinked(linkUp: Boolean, snapAgeMs: Long, force: Boolean): Boolean =
        linkUp && (force || snapAgeMs >= LINK_STALE_MS)

    fun hungWrite(writing: Boolean, writeAgeMs: Long, force: Boolean): Boolean =
        writing && (force || writeAgeMs >= WRITE_STALE_MS)

    /** Direct RFCOMM search pause while the phone UI is visible. */
    const val DIRECT_RETRY_MS: Long = 5_000L

    /** Upper bound of the background direct-search back-off. */
    const val DIRECT_BACKGROUND_RETRY_MAX_MS: Long = 60_000L

    /**
     * Pause after a direct search pass that found no head unit. Each pass may SDP-probe every
     * connected HFP/A2DP device (earbuds, watch), so in the background the wait doubles per
     * empty pass: 5, 10, 20, 40, 60, 60… s.
     */
    fun directSearchRetryMs(foreground: Boolean, passesWithoutLink: Int): Long {
        if (foreground || passesWithoutLink <= 1) return DIRECT_RETRY_MS
        val shift = (passesWithoutLink - 1).coerceAtMost(4)
        return (DIRECT_RETRY_MS shl shift).coerceAtMost(DIRECT_BACKGROUND_RETRY_MAX_MS)
    }

    /** Resume kick: skip only a fresh in-flight scan/connect; always reopen a stale link. */
    fun resumeShouldKick(
        linkUp: Boolean,
        scanningOrConnecting: Boolean,
        attemptAgeMs: Long,
        snapAgeMs: Long,
    ): Boolean {
        if (linkUp) return staleLinked(linkUp = true, snapAgeMs = snapAgeMs, force = false)
        if (scanningOrConnecting && attemptAgeMs < CONNECT_STALE_MS) return false
        return true
    }

    /** Pause after a direct session that actually delivered a sealed answer. */
    const val DIRECT_RECONNECT_MS: Long = 1_000L

    /**
     * A connect the head unit accepts and then closes before any sealed snapshot or
     * [PhoneBleCodec.TYPE_TELEMETRY_ACK] is not a success. Foreground waits at least this long.
     */
    const val DIRECT_CLOSED_BEFORE_ANSWER_FG_MS: Long = 10_000L

    /**
     * Header values older than this are stale: dim them and stop sending commands.
     * A lost REFRESH is retried after 3 s, so 6 s flipped the status to «no answer»
     * for a couple of seconds on every lost packet. 10 s covers that gap.
     */
    const val SNAP_STALE_MS: Long = 10_000L

    /** Re-open the Connect page once per link when no snapshot arrives. */
    const val NO_SNAP_REOPEN_MS: Long = 10_000L

    /** The head unit's pair window. After this, the phone says the request was not confirmed. */
    const val PAIR_CONFIRM_TIMEOUT_MS: Long = 90_000L

    /** Android drops a 6th startScan inside 30 s with no callback. Stay under that. */
    const val SCAN_STARTS_PER_WINDOW: Int = 4
    const val SCAN_WINDOW_MS: Long = 30_000L
    const val BLE_RETRY_BASE_MS: Long = 800L
    const val BLE_RETRY_MAX_MS: Long = 30_000L
    const val STRANGER_RESCAN_MS: Long = 10_000L

    /** [android.bluetooth.BluetoothGatt.GATT_ERROR], the status-133 connect failure. */
    const val GATT_ERROR: Int = 133

    /** Stop the telemetry foreground service after this long without a confirmed direct session. */
    const val FGS_IDLE_MS: Long = 10 * 60 * 1000L

    /**
     * After this many fast rejects, the remembered head unit is tried after the live
     * candidates. The stored address is kept: clearing it also disables ACL autostart.
     * An accepted socket that closes before an answer does not count.
     */
    const val FORGET_REMEMBERED_AFTER: Int = 5

    /** Rank of a remembered head unit that has been demoted. Live candidates are 1..4. */
    const val DEMOTED_REMEMBERED_RANK: Int = 5

    /** Connected over HFP/A2DP, not headphones or a wearable, service UUID not cached yet. */
    const val CONNECTED_UNKNOWN_RANK: Int = 4

    /** A connect that fails faster than this was rejected, not a radio timeout. */
    const val DIRECT_FAST_FAIL_MS: Long = 2_000L

    /** Ask SDP again for a connected device after this, or sooner on a new ACL. */
    const val SDP_REFETCH_MS: Long = 2 * 60 * 1000L

    /** Fast rejects of a device that is not the remembered head unit. */
    const val NEGATIVE_CACHE_MS: Long = 10 * 60 * 1000L

    data class AcceptWindow(val acceptFrom: Long, val waiting: Long)

    /**
     * Counters the phone still shows. A command sets [AcceptWindow.acceptFrom] to its own
     * counter so an older REFRESH answer cannot overwrite the optimistic value. The next
     * REFRESH then moves [AcceptWindow.waiting] forward.
     */
    fun acceptWindowAfterSend(
        previous: AcceptWindow,
        counter: Long,
        resetAcceptRange: Boolean,
        updateWaiting: Boolean,
    ): AcceptWindow = AcceptWindow(
        acceptFrom = if (resetAcceptRange) counter else previous.acceptFrom,
        waiting = if (updateWaiting) counter else previous.waiting,
    )

    fun counterInAcceptWindow(counter: Long, window: AcceptWindow): Boolean =
        counter in window.acceptFrom..window.waiting

    data class DirectWait(val passesWithoutLink: Int, val waitMs: Long)

    /**
     * [answered] means a sealed snapshot or telemetry ack arrived before the socket closed.
     * Anything else, including an immediate close when the head unit is full, is a failed pass.
     */
    fun directWaitAfterClose(
        answered: Boolean,
        foreground: Boolean,
        passesWithoutLink: Int,
    ): DirectWait {
        if (answered) return DirectWait(passesWithoutLink = 0, waitMs = DIRECT_RECONNECT_MS)
        val passes = passesWithoutLink + 1
        val backoff = directSearchRetryMs(foreground, passes)
        val wait = if (foreground) maxOf(DIRECT_CLOSED_BEFORE_ANSWER_FG_MS, backoff) else backoff
        return DirectWait(passes, wait)
    }

    enum class PhoneUiStatus {
        NEED_PERMISSION,
        BLUETOOTH_OFF,
        LOOKING,
        WAITING,
        NO_ANSWER,
        CONNECTED,
    }

    fun phoneUiStatus(
        permissionsReady: Boolean,
        bluetoothOn: Boolean,
        linkUp: Boolean,
        confirmed: Boolean,
        snapAgeMs: Long,
        staleAfterMs: Long = SNAP_STALE_MS,
    ): PhoneUiStatus = when {
        !permissionsReady -> PhoneUiStatus.NEED_PERMISSION
        !bluetoothOn -> PhoneUiStatus.BLUETOOTH_OFF
        !linkUp -> PhoneUiStatus.LOOKING
        !confirmed -> PhoneUiStatus.WAITING
        snapAgeMs > staleAfterMs -> PhoneUiStatus.NO_ANSWER
        else -> PhoneUiStatus.CONNECTED
    }

    /** Commands and undimmed values require a confirmed link and a fresh snapshot. */
    fun controlsLive(
        confirmed: Boolean,
        snapAgeMs: Long,
        staleAfterMs: Long = SNAP_STALE_MS,
    ): Boolean = confirmed && snapAgeMs >= 0L && snapAgeMs <= staleAfterMs

    fun shouldReopenConnectPage(
        linkUp: Boolean,
        lastSnapAtMs: Long,
        linkedAtMs: Long,
        nowMs: Long,
        alreadyReopened: Boolean,
        reopenAfterMs: Long = NO_SNAP_REOPEN_MS,
    ): Boolean =
        linkUp &&
            lastSnapAtMs < linkedAtMs &&
            !alreadyReopened &&
            nowMs - linkedAtMs >= reopenAfterMs

    fun pairTimedOut(
        sentAtMs: Long,
        nowMs: Long,
        snapAtMs: Long,
        timeoutMs: Long = PAIR_CONFIRM_TIMEOUT_MS,
    ): Boolean = sentAtMs != 0L && snapAtMs < sentAtMs && nowMs - sentAtMs >= timeoutMs

    /** 0 means start now. Otherwise wait until the oldest start falls out of the window. */
    fun scanStartDelayMs(
        nowMs: Long,
        startTimesMs: List<Long>,
        maxStarts: Int = SCAN_STARTS_PER_WINDOW,
        windowMs: Long = SCAN_WINDOW_MS,
    ): Long {
        val recent = startTimesMs.filter { nowMs - it < windowMs }
        if (recent.size < maxStarts) return 0L
        val oldest = recent.minOrNull() ?: return 0L
        return (windowMs - (nowMs - oldest)).coerceAtLeast(1L)
    }

    fun bleConnectRetryMs(
        consecutive133: Int,
        stranger: Boolean,
        strangerMs: Long = STRANGER_RESCAN_MS,
        baseMs: Long = BLE_RETRY_BASE_MS,
    ): Long {
        if (stranger) return strangerMs
        if (consecutive133 <= 0) return baseMs
        val shift = (consecutive133 - 1).coerceAtMost(6)
        return (baseMs shl shift).coerceAtMost(BLE_RETRY_MAX_MS)
    }

    fun foregroundServiceIdle(
        uiVisible: Boolean,
        telemetryEnabled: Boolean,
        directLinkUp: Boolean,
        idleMs: Long,
        limitMs: Long = FGS_IDLE_MS,
    ): Boolean = !uiVisible && telemetryEnabled && !directLinkUp && idleMs >= limitMs

    enum class TelemetryNotice { SEARCHING, SENDING, WAITING_FOR_CAR }

    fun telemetryNotice(directLinkUp: Boolean, activelySending: Boolean): TelemetryNotice = when {
        !directLinkUp -> TelemetryNotice.SEARCHING
        activelySending -> TelemetryNotice.SENDING
        else -> TelemetryNotice.WAITING_FOR_CAR
    }

    // BluetoothClass.Device / Device.Major public values. Kept here so ranking is testable
    // without the Android framework.
    const val BT_MAJOR_AUDIO_VIDEO: Int = 0x0400
    const val BT_MAJOR_WEARABLE: Int = 0x0700
    const val BT_DEVICE_WEARABLE_HEADSET: Int = 0x0404
    const val BT_DEVICE_HANDSFREE: Int = 0x0408
    const val BT_DEVICE_HEADPHONES: Int = 0x0418
    const val BT_DEVICE_CAR_AUDIO: Int = 0x0420

    fun isWearableOrHeadphones(majorClass: Int, deviceClass: Int): Boolean {
        val wearable = majorClass == BT_MAJOR_WEARABLE || deviceClass == BT_DEVICE_WEARABLE_HEADSET
        val headphones = deviceClass == BT_DEVICE_HEADPHONES
        return wearable || headphones
    }

    /** Fast rejects only. A link the head unit accepts and then closes does not move the count. */
    fun nextRememberedFastRejects(current: Int, fastFailOnRemembered: Boolean, answered: Boolean): Int =
        when {
            answered -> 0
            fastFailOnRemembered -> current + 1
            else -> current
        }

    fun rememberedDemoted(fastRejects: Int, after: Int = FORGET_REMEMBERED_AFTER): Boolean =
        fastRejects >= after

    /**
     * SDP is fetched for the remembered head unit and for a connected HFP/A2DP device
     * that is not headphones or a wearable. Other bonded devices stay on the cache.
     */
    fun shouldFetchServiceUuid(
        remembered: Boolean,
        connectedProfile: Boolean,
        wearableOrHeadphones: Boolean,
        alreadyListed: Boolean,
    ): Boolean = !alreadyListed && (remembered || (connectedProfile && !wearableOrHeadphones))

    /** First lookup, a new ACL after the last lookup, or [refetchAfterMs] since that lookup. */
    fun sdpFetchDue(
        lastAskedAtMs: Long,
        nowMs: Long,
        aclConnectedAtMs: Long,
        refetchAfterMs: Long = SDP_REFETCH_MS,
    ): Boolean {
        if (lastAskedAtMs <= 0L) return true
        if (aclConnectedAtMs > lastAskedAtMs) return true
        return nowMs - lastAskedAtMs >= refetchAfterMs
    }

    /** The remembered address is never blocked. An expired entry does not block. */
    fun negativeCacheBlocks(remembered: Boolean, expiresAtMs: Long, nowMs: Long): Boolean =
        !remembered && expiresAtMs > nowMs

    /**
     * A background wait may be cut once the UI becomes visible. A wait that already
     * started in the foreground runs for the full time, including the 10 s pause
     * after a link that closed before an answer.
     */
    fun directPauseCutShort(
        wasForeground: Boolean,
        foregroundNow: Boolean,
        elapsedMs: Long,
        minElapsedMs: Long = DIRECT_RETRY_MS,
    ): Boolean = !wasForeground && foregroundNow && elapsedMs >= minElapsedMs

    /**
     * Lower rank is tried first. Null means do not connect.
     * The head unit that answered last time is rank 0 until [rememberedDemoted], then
     * [DEMOTED_REMEMBERED_RANK], and it is still tried.
     * Headphones and wearables are skipped unless their cached SDP lists the TBox service.
     * Any other device connected over HFP or A2DP is rank [CONNECTED_UNKNOWN_RANK] even
     * when the class is not car audio or hands-free: the head unit's class is not known.
     */
    fun directCandidateRank(
        majorClass: Int,
        deviceClass: Int,
        hasServiceUuid: Boolean,
        remembered: Boolean,
        connectedProfile: Boolean = false,
        rememberedDemoted: Boolean = false,
    ): Int? {
        if (remembered && !rememberedDemoted) return 0
        val skippedAudio = isWearableOrHeadphones(majorClass, deviceClass)
        if (skippedAudio && !hasServiceUuid && !remembered) return null
        if (remembered && rememberedDemoted) return DEMOTED_REMEMBERED_RANK
        return when (deviceClass) {
            BT_DEVICE_CAR_AUDIO -> 1
            BT_DEVICE_HANDSFREE -> 2
            else -> when {
                hasServiceUuid -> 3
                connectedProfile && !skippedAudio -> CONNECTED_UNKNOWN_RANK
                else -> null
            }
        }
    }
}
