package vad.dashing.tbox.hotspot

import android.content.Context

/**
 * A9 router bring-up: put the running head-unit AP on 2.4 GHz and remember the password
 * that [setWifiApConfiguration] just generated. The stored band can stay 0 while hostapd
 * is still on 5 GHz, so the decision uses the live frequency.
 */
internal object HuSoftApRouter {
    data class Push(
        val ssid: String,
        val password: String,
    )

    @Volatile
    private var push: Push? = null

    fun current(): Push? = push

    fun clear() {
        push = null
    }

    /** Null when the companion can be told the current head-unit AP. Otherwise a short error code. */
    suspend fun prepare(context: Context): String? {
        var snap = HuSoftApClient.refresh(context)
        val first = snap.read ?: return snap.errorCode ?: "failed"
        snap = if (!HuSoftApCodec.on24Ghz(first.liveMhz)) {
            HuSoftApClient.retune24(context)
        } else if (snap.radioEnabled != true && !first.enabled) {
            HuSoftApClient.setEnabled(context, true)
        } else {
            snap
        }
        val read = snap.read ?: return snap.errorCode ?: "failed"
        if (read.ssid.isBlank()) return "no-ssid"
        push = Push(read.ssid, read.password)
        if (!HuSoftApCodec.on24Ghz(read.liveMhz)) return "band"
        return null
    }
}
