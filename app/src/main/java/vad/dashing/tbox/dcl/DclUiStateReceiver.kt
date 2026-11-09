package vad.dashing.tbox.dcl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-exported receiver for DCL UI state push.
 *
 * DCL targets package `vad.dashing.tbox` explicitly. Exported is required so the
 * launcher process can deliver the broadcast; action filter limits what we accept.
 */
class DclUiStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!DclLauncherApiRepository.isUiStateAction(intent.action)) return
        DclLauncherApiRepository.onUiStateBroadcast(intent)
    }
}
