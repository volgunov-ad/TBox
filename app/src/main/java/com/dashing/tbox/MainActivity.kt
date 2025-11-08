package com.dashing.tbox

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import java.io.File
import java.io.FileWriter
import androidx.core.net.toUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var numberPin: EditText
    private lateinit var numberPuk: EditText
    private lateinit var textATCmd: EditText

    companion object {
        private const val TAG = "MainActivity"
    }

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
                    onTboxRestart = { rebootTBox() },
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
            startServiceSafely(intent)
        }
    }

    private fun modemCheck() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_MODEM_CHECK
        }
        startServiceSafely(intent)
    }

    private fun locSubscribe() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_LOC_SUBSCRIBE
        }
        startServiceSafely(intent)
    }

    private fun getCanFrame() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_GET_CAN_FRAME
        }
        startServiceSafely(intent)
    }

    private fun locUnsubscribe() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_LOC_UNSUBSCRIBE
        }
        startServiceSafely(intent)
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
        startServiceSafely(intent)
    }

    private fun rebootTBox() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_TBOX_REBOOT
        }
        startServiceSafely(intent)
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
        startServiceSafely(intent)
    }

    private fun updateVersions() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_GET_VERSIONS
        }
        startServiceSafely(intent)
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
        startServiceSafely(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        //stopNetUpdaterIfNoClients()
    }

    private fun saveDataToFile(dataList: List<String>) {
        try {
            // Проверяем разрешения перед сохранением
            if (!hasStoragePermissions()) {
                Toast.makeText(this, "Нет разрешений для сохранения файла", Toast.LENGTH_LONG).show()
                requestPermissions()
                return
            }

            val savePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Для Android 11+ используем Downloads directory через MediaStore
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            } else {
                // Для старых версий
                Environment.getExternalStorageDirectory().absolutePath + "/Download"
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()).format(Date())
            val dataFile = File(savePath, "tbox_data_$timestamp.txt")

            FileWriter(dataFile).use { writer ->
                dataList.forEach { value ->
                    writer.append("$value\n")
                }
            }

            Toast.makeText(this, "Сохранено в: ${dataFile.absolutePath}", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Файл сохранен: ${dataFile.absolutePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения файла", e)
            Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Контракт для стандартных разрешений (Android 10-)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                onPermissionsGranted()
            } else {
                onPermissionsDenied()
            }
        }
    }

    // Контракт для MANAGE_EXTERNAL_STORAGE (Android 11+)
    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Проверяем результат после возврата из системных настроек
        checkStoragePermissions()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - используем MANAGE_EXTERNAL_STORAGE
            checkStoragePermissions()
        } else {
            // Android 10 и ниже - запрашиваем стандартные разрешения
            requestLegacyStoragePermissions()
        }
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                onPermissionsGranted()
            } else {
                requestManageExternalStoragePermission()
            }
        } else {
            // Для Android 10- проверяем стандартные разрешения
            if (hasLegacyStoragePermissions()) {
                onPermissionsGranted()
            } else {
                requestLegacyStoragePermissions()
            }
        }
    }

    private fun requestLegacyStoragePermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        requestPermissionLauncher.launch(permissions)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestManageExternalStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:$packageName".toUri()
            manageExternalStorageLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageExternalStorageLauncher.launch(intent)
        }
    }

    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasLegacyStoragePermissions()
        }
    }

    private fun hasLegacyStoragePermissions(): Boolean {
        return (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    private fun onPermissionsGranted() {
        // Разрешения получены - запускаем основную логику
        Log.d("Permissions", "Storage permissions granted")
    }

    private fun onPermissionsDenied() {
        // Разрешения отклонены - ограничиваем функциональность
        Log.w("Permissions", "Storage permissions denied")
        // Можно показать диалог с объяснением
        Toast.makeText(this,
            "Без разрешений некоторые функции могут не работать",
            Toast.LENGTH_LONG).show()
    }

    private fun startServiceSafely(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // Логируем ошибку
            e.printStackTrace()
        }
    }
}


