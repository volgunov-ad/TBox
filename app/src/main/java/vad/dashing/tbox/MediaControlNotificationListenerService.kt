package vad.dashing.tbox

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MediaControlNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        SharedMediaControlService.onMediaNotificationsMayHaveChanged(this)
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        SharedMediaControlService.onMediaNotificationsMayHaveChanged(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)
        SharedMediaControlService.onMediaNotificationsMayHaveChanged(this)
    }

    companion object {
        @Volatile
        var instance: MediaControlNotificationListenerService? = null
            private set
    }
}
