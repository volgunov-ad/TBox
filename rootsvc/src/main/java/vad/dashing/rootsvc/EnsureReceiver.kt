package vad.dashing.rootsvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Explicit ENSURE broadcast for clients when the socket is down. */
class EnsureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != vad.dashing.rootsvc.client.RootSvcProtocol.ENSURE_ACTION) return
        val force = intent.getBooleanExtra(
            vad.dashing.rootsvc.client.RootSvcProtocol.ENSURE_EXTRA_FORCE,
            false,
        )
        val pending = goAsync()
        Thread({
            try {
                EnsureDaemon.ensure(context, if (force) "ENSURE_FORCE" else "ENSURE", force)
            } finally {
                pending.finish()
            }
        }, "rootsvc-ensure").start()
    }
}
