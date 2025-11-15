package com.dashing.tbox

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
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
import com.dashing.tbox.ui.TboxApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    // Контракт для стандартных разрешений (Android 10-)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onStoragePermissionsGranted()
        } else {
            onStoragePermissionsDenied()
        }
    }

    // Контракт для MANAGE_EXTERNAL_STORAGE (Android 11+)
    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasStoragePermissions()) {
            onStoragePermissionsGranted()
        } else {
            onStoragePermissionsDenied()
        }
    }

    // Переменная для хранения данных, которые нужно сохранить после получения разрешений
    private var pendingDataToSave: List<String>? = null

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)

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
                    onUpdateInfoClick = { updateInfo() },
                    onSaveToFile = { ipList ->
                        saveDataToFile(ipList)
                    }
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
        startServiceSafely(intent)
    }

    private fun modemCheck() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_MODEM_CHECK
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

    private fun updateInfo() {
        val intent = Intent(this, BackgroundService::class.java).apply {
            action = BackgroundService.ACTION_GET_INFO
        }
        startServiceSafely(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun saveDataToFile(dataList: List<String>) {
        // Проверяем есть ли уже разрешения
        if (hasStoragePermissions()) {
            // Если разрешения есть - сразу сохраняем
            performFileSave(dataList)
        } else {
            // Если разрешений нет - сохраняем данные и запрашиваем разрешения
            pendingDataToSave = dataList
            requestStoragePermissions()
        }
    }

    private fun performFileSave(dataList: List<String>) {
        try {
            val savePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Для Android 11+ используем Downloads directory через MediaStore
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            } else {
                // Для старых версий
                Environment.getExternalStorageDirectory().absolutePath + "/Download"
            }

            // Создаем папку если не существует
            val saveDir = File(savePath)
            if (!saveDir.exists()) {
                saveDir.mkdirs()
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

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - используем MANAGE_EXTERNAL_STORAGE
            requestManageExternalStoragePermission()
        } else {
            // Android 10 и ниже - запрашиваем стандартные разрешения
            requestLegacyStoragePermissions()
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

    private fun onStoragePermissionsGranted() {
        Log.d(TAG, "Storage permissions granted")
        // Если есть ожидающие данные для сохранения - сохраняем их
        pendingDataToSave?.let { data ->
            performFileSave(data)
            pendingDataToSave = null
        }
    }

    private fun onStoragePermissionsDenied() {
        Log.w(TAG, "Storage permissions denied")
        Toast.makeText(this,
            "Не удалось сохранить файл: нет разрешений на запись",
            Toast.LENGTH_LONG).show()
        pendingDataToSave = null
    }

    private fun startServiceSafely(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска сервиса", e)
            Toast.makeText(this, "Ошибка запуска службы", Toast.LENGTH_SHORT).show()
        }
    }
}