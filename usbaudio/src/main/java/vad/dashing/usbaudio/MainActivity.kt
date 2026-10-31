package vad.dashing.usbaudio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.usbaudio.ui.UsbAudioScreen
import vad.dashing.usbaudio.ui.theme.UsbAudioTheme

class MainActivity : ComponentActivity() {
    private val viewModel: UsbAudioViewModel by viewModels()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.onUsbDeviceEvent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            UsbAudioTheme(dark = isSystemInDarkTheme()) {
                UsbAudioScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onApply = viewModel::apply,
                    onRestore = viewModel::restore,
                    onRefreshVolume = viewModel::refreshVolume,
                    onApplyVolume = viewModel::applyVolume,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startDeviceWatch()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(usbReceiver, filter)
        }
    }

    override fun onStop() {
        runCatching { unregisterReceiver(usbReceiver) }
        viewModel.stopDeviceWatch()
        super.onStop()
    }
}
