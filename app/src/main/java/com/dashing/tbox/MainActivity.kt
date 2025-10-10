package com.dashing.tbox

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private lateinit var numberPin: EditText
    private lateinit var numberPuk: EditText
    private lateinit var textATCmd: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = SettingsManager(this)
        setContent {
            TboxAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TboxApp(
                        settingsManager = settingsManager,
                        onTboxRestart = { rebootTbox() },
                        onModemCheck = { modemCheck() },
                        onModemOn = { setModemMode("on") },
                        onModemFly = { setModemMode("fly") },
                        onModemOff = { setModemMode("off") },
                        onLocSubscribeClick = { locSubscribe() },
                        onLocUnsubscribeClick = { locUnsubscribe() }
                    )
                }
            }
        }
        startBackgroundService()
    }

    override fun onRestart() {
        super.onRestart()
        startBackgroundService()
    }

    private fun startBackgroundService() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_NET_UPD_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun modemCheck() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_MODEM_CHECK
        }
        startService(intent)
    }

    private fun locSubscribe() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_LOC_SUBSCRIBE
        }
        startService(intent)
    }

    private fun locUnsubscribe() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_LOC_UNSUBSCRIBE
        }
        startService(intent)
    }

    private fun setModemMode(mode: String = "on") {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = when (mode) {
                "off" -> {
                    BackgroundService.ACTION_MODEM_OFF
                }
                "fly" -> {
                    BackgroundService.ACTION_MODEM_FLY
                }
                else -> {
                    BackgroundService.ACTION_MODEM_ON
                }
            }
        }
        startService(intent)
    }

    private fun rebootTbox() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_TBOX_REBOOT
        }
        startService(intent)
    }

    private fun apnManage(number: Int, cmd: String) {
        val intent = Intent(this, BackgroundService::class.java).apply {
            when (cmd) {
                "restart" -> {
                    action = if (number == 1) {
                        BackgroundService.ACTION_APN1_RESTART
                    }
                    else {
                        BackgroundService.ACTION_APN2_RESTART
                    }
                }
                "fly" -> {
                    action = if (number == 1) {
                        BackgroundService.ACTION_APN1_FLY
                    }
                    else {
                        BackgroundService.ACTION_APN2_FLY
                    }
                }
                "reconnect" -> {
                    action = if (number == 1) {
                        BackgroundService.ACTION_APN1_RECONNECT
                    }
                    else {
                        BackgroundService.ACTION_APN2_RECONNECT
                    }
                }
            }
        }
        startService(intent)
    }

    private fun test() {
        val atCmd = textATCmd.text.toString()
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_TEST1
            putExtra(BackgroundService.EXTRA_AT_CMD, atCmd + "\r\n")
        }
        startService(intent)
    }

    private fun pin(cmd: Int) {
        val pinText = numberPin.text.toString()
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = if (cmd == 1) {
                BackgroundService.ACTION_PUK
            }
            else {
                BackgroundService.ACTION_PIN
            }
            if (pinText.isNotEmpty()) {
                if (cmd == 0) {
                    putExtra(BackgroundService.EXTRA_PIN, pinText)
                }
                else {
                    val pukText = numberPuk.text.toString()
                    if (pukText.isNotEmpty()) {
                        putExtra(BackgroundService.EXTRA_PIN, pinText)
                        putExtra(BackgroundService.EXTRA_PUK, pukText)
                    }
                    else {
                        Toast.makeText(this@MainActivity, "Не введен PUK", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
            else {
                Toast.makeText(this@MainActivity, "Не введен PIN", Toast.LENGTH_SHORT).show()
                return
            }
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        //stopNetUpdaterIfNoClients()
    }

    private fun stopNetUpdaterIfNoClients() {
        if (!WidgetUtils.isWidgetActive(this)) {
            val intent = Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_NET_UPD_STOP
            }
            startService(intent)
        }
    }


}


