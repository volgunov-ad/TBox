package vad.dashing.mqtt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import vad.dashing.mqtt.service.MqttBridgeService
import vad.dashing.mqtt.settings.MqttSettingsStore
import vad.dashing.mqtt.ui.MqttHomeScreen
import vad.dashing.mqtt.ui.theme.TboxMqttTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (MqttSettingsStore(this).load().normalized().ready) {
            MqttBridgeService.start(this)
        }
        setContent {
            TboxMqttTheme(dark = isSystemInDarkTheme()) {
                MqttHomeScreen(
                    onSettingsSaved = {
                        if (MqttSettingsStore(this).load().normalized().ready) {
                            MqttBridgeService.start(this)
                        }
                    },
                )
            }
        }
    }
}
