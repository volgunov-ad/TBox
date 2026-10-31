package vad.dashing.mqtt

import android.app.Application
import vad.dashing.mqtt.wireguard.WgTunnel

class MqttApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WgTunnel.prepare(this)
    }
}
