package com.dashing.tbox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import java.io.File
import java.io.FileWriter
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private lateinit var numberPin: EditText
    private lateinit var numberPuk: EditText
    private lateinit var textATCmd: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = SettingsManager(this)

        requestPermissions()

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize()
            ) {
                TboxApp(
                    settingsManager = settingsManager,
                    onTboxRestart = { rebootTbox() },
                    onModemCheck = { modemCheck() },
                    onModemOn = { setModemMode("on") },
                    onModemFly = { setModemMode("fly") },
                    onModemOff = { setModemMode("off") },
                    onLocSubscribeClick = { locSubscribe() },
                    onLocUnsubscribeClick = { locUnsubscribe() },
                    onUpdateVersions = { updateVersions() },
                    onSaveToFile = { ipList -> saveDataToFile(ipList) }
                )
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
            action = BackgroundService.ACTION_START
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

    private fun getCanFrame() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_GET_CAN_FRAME
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

    private fun updateVersions() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_GET_VERSIONS
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

    private fun saveDataToFile(dataList: List<String>) {
        try {
            val savePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            val csvFile = File(savePath, "data_${System.currentTimeMillis()}.txt")

            FileWriter(csvFile).use { writer ->
                dataList.forEach { value ->
                    writer.append("$value\n")
                }
            }

            Toast.makeText(this, "Сохранено в: ${csvFile.absolutePath}", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // Контракт для разрешений
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, "Не все разрешения предоставлены", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = "package:$packageName".toUri()
                startActivity(intent)
            }
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }
}


