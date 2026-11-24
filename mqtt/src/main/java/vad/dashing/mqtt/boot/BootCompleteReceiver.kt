package vad.dashing.mqtt.boot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import vad.dashing.mqtt.service.MqttBridgeService
import vad.dashing.mqtt.settings.MqttSettingsStore

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (!isMqttBootAction(action)) return
        val pending = goAsync()
        val app = context.applicationContext
        Thread({
            try {
                startIfReady(app)
            } finally {
                pending.finish()
            }
        }, "mqtt-boot").start()
    }

    private fun startIfReady(context: Context) {
        val store = MqttSettingsStore.get(context)
        if (store.storageError != null) {
            scheduleRetry(context)
            return
        }
        val settings = runCatching { store.load().normalized() }.getOrNull() ?: return
        if (!settings.autostart || !settings.active) return
        MqttBridgeService.start(context)
    }

    private fun scheduleRetry(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, BootCompleteReceiver::class.java).setAction(ACTION_RETRY_STORAGE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + RETRY_DELAY_MS, pending)
    }

    private companion object {
        const val RETRY_DELAY_MS = 15_000L
    }
}

internal const val ACTION_RETRY_STORAGE = "vad.dashing.mqtt.RETRY_STORAGE"

internal fun isMqttBootAction(action: String?): Boolean = action in setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    QUICKBOOT_POWERON,
    ACTION_RETRY_STORAGE,
)

private const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
