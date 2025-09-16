package com.dashing.tbox

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

class ThemeObserver(
    private val context: Context,
    private val callback: (themeMode: Int) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val contentResolver = context.contentResolver

    private val dayNightUri = Settings.System.getUriFor("DAY_NIGHT_STATUS")
    private val autoModeUri = Settings.Global.getUriFor("com.mb.provider.night_mode_auto")

    private var isObserving = false

    fun startObserving() {
        try {
            contentResolver.registerContentObserver(
                dayNightUri,
                false,
                this
            )
            contentResolver.registerContentObserver(
                autoModeUri,
                false,
                this
            )
            isObserving = true
            Log.d("ThemeObserver", "Started observing theme changes")

            // Первоначальное чтение текущего значения
            onChange(false)
        } catch (e: SecurityException) {
            Log.e("ThemeObserver", "SecurityException: Missing READ_SETTINGS permission", e)
            callback(1) // Возвращаем светлую тему по умолчанию
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Failed to start observing theme changes", e)
            callback(1) // Возвращаем светлую тему по умолчанию
        }
    }

    fun stopObserving() {
        try {
            if (isObserving) {
                contentResolver.unregisterContentObserver(this)
                isObserving = false
                Log.d("ThemeObserver", "Stopped observing theme changes")
            }
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Failed to stop observing theme changes", e)
        }
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        try {
            val normalizedTheme = getNormalizedThemeMode()
            callback(normalizedTheme)
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Error in onChange callback", e)
            callback(1) // Возвращаем светлую тему по умолчанию при ошибке
        }
    }

    private fun getNormalizedThemeMode(): Int {
        return try {
            val autoMode = getAutoMode()
            when (autoMode) {
                0 -> 1 // Light
                2 -> 2 // Dark
                else -> getDayNightMode()
            }
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Error getting normalized theme mode", e)
            1 // Возвращаем светлую тему по умолчанию при ошибке
        }
    }

    private fun getAutoMode(): Int {
        return try {
            Settings.Global.getInt(contentResolver, "com.mb.provider.night_mode_auto", 1)
        } catch (e: SecurityException) {
            Log.w("ThemeObserver", "SecurityException: No permission to read auto mode", e)
            1 // Auto mode по умолчанию
        } catch (e: Settings.SettingNotFoundException) {
            Log.w("ThemeObserver", "Auto mode setting not found, using default", e)
            1 // Auto mode по умолчанию
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Unexpected error reading auto mode", e)
            1 // Auto mode по умолчанию
        }
    }

    private fun getDayNightMode(): Int {
        return try {
            val dayNight = Settings.System.getInt(contentResolver, "DAY_NIGHT_STATUS", 1)
            if (dayNight == 2) 2 else 1
        } catch (e: SecurityException) {
            Log.w("ThemeObserver", "SecurityException: No permission to read day/night mode", e)
            1 // Light theme по умолчанию
        } catch (e: Settings.SettingNotFoundException) {
            Log.w("ThemeObserver", "Day/Night setting not found, using default", e)
            1 // Light theme по умолчанию
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Unexpected error reading day/night mode", e)
            1 // Light theme по умолчанию
        }
    }

    fun isObserving(): Boolean {
        return isObserving
    }
}