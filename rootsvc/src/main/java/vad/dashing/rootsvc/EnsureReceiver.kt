package vad.dashing.rootsvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Explicit ENSURE broadcast for clients when the socket is down. */
class EnsureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != vad.dashing.rootsvc.client.RootSvcProtocol.ENSURE_ACTION) return
        val pending = goAsync()
        Thread({
            try {
                EnsureDaemon.ensure(context, "ENSURE")
            } finally {
                pending.finish()
            }
        }, "rootsvc-ensure").start()
    }
}
