package vad.dashing.tbox.usbgnss

import android.util.Log
import vad.dashing.tbox.TboxRepository

/**
 * Sequential version/model probes on an open [UsbNmeaGnssSession].
 * Order: Unicore VERSIONA → u-blox MON-VER → MediaTek PMTK605.
 */
object GnssModuleProbe {
    private const val TAG = "GnssModuleProbe"
    const val STEP_TIMEOUT_MS = 2_000L

    /**
     * Whether auto-probe should run for [deviceId] given the persisted map.
     * Missing key → probe; existing (even UNKNOWN after manual) → skip auto.
     * Plan: auto only when there is **no** saved entry.
     */
    fun shouldAutoProbe(
        deviceId: String,
        map: Map<String, GnssModuleIdentity>,
    ): Boolean {
        val id = deviceId.trim()
        if (id.isEmpty()) return false
        return !map.containsKey(id)
    }

    fun probe(session: UsbNmeaGnssSession): GnssModuleIdentity {
        // 1) Unicore
        runCatching {
            val lines = session.execAsciiCommand(GnssModuleCommands.unicoreVersionAscii(), STEP_TIMEOUT_MS)
            GnssModuleCommands.parseProbeReplies(lines)?.let { return it }
        }.onFailure { Log.w(TAG, "VERSIONA probe failed", it) }

        // 2) u-blox binary MON-VER
        runCatching {
            val (_, raw) = session.execRawCommand(GnssModuleCommands.ubloxMonVerPollBytes(), STEP_TIMEOUT_MS)
            GnssModuleCommands.parseUbloxMonVerFromRaw(raw)?.let { return it }
        }.onFailure { Log.w(TAG, "UBX MON-VER probe failed", it) }

        // 3) MediaTek
        runCatching {
            val lines = session.execAsciiCommand(GnssModuleCommands.mtkQueryVersionAscii(), STEP_TIMEOUT_MS)
            GnssModuleCommands.parseProbeReplies(lines)?.let { return it }
        }.onFailure { Log.w(TAG, "PMTK605 probe failed", it) }

        TboxRepository.addLog("INFO", "USB GNSS", "module probe: unknown")
        return GnssModuleIdentity.unknown()
    }
}
