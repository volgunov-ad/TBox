package vad.dashing.mqtt.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import vad.dashing.mqtt.service.MqttBridgeService
import vad.dashing.mqtt.settings.MqttSettingsStore

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runCatching { MqttSettingsStore.get(context).load().normalized() }.getOrNull() ?: return
        if (!settings.autostart || !settings.active) return
        MqttBridgeService.start(context)
    }
}
