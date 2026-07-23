package vad.dashing.tbox.utils

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import vad.dashing.tbox.HeadUnitDayNightRepository

/**
 * Observes head-unit day/night Settings and delivers normalized theme (`1` light, `2` dark).
 * Keys: A9 `night_mode_auto` + `DAY_NIGHT_STATUS`; A10+ `adayo_skin` + `auto_skin`
 * (see [vad.dashing.tbox.HeadUnitDayNightMapping]).
 */
class ThemeObserver(
    private val context: Context,
    private val callback: (themeMode: Int) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val contentResolver = context.contentResolver
    private val debounceHandler = Handler(Looper.getMainLooper())

    private val observedUris = HeadUnitDayNightRepository.observedSettingUris()

    private var isObserving = false
    private var pendingThemeDelivery: Runnable? = null
    private var lastDeliveredTheme: Int? = null

    fun startObserving() {
        try {
            observedUris.forEach { uri ->
                contentResolver.registerContentObserver(uri, false, this)
            }
            isObserving = true
            Log.d("ThemeObserver", "Started observing theme changes (${observedUris.size} uris)")

            deliverCurrentTheme(immediate = true)
        } catch (e: SecurityException) {
            Log.e("ThemeObserver", "SecurityException: Missing READ_SETTINGS permission", e)
            deliverThemeMode(1)
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Failed to start observing theme changes", e)
            deliverThemeMode(1)
        }
    }

    fun stopObserving() {
        try {
            cancelPendingDelivery()
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
        deliverCurrentTheme(immediate = false)
    }

    private fun deliverCurrentTheme(immediate: Boolean) {
        cancelPendingDelivery()
        val delivery = Runnable {
            pendingThemeDelivery = null
            try {
                deliverThemeMode(getNormalizedThemeMode())
            } catch (e: Exception) {
                Log.e("ThemeObserver", "Error in onChange callback", e)
                deliverThemeMode(1)
            }
        }
        pendingThemeDelivery = delivery
        if (immediate) {
            debounceHandler.post(delivery)
        } else {
            debounceHandler.postDelayed(delivery, THEME_CHANGE_DEBOUNCE_MS)
        }
    }

    private fun cancelPendingDelivery() {
        pendingThemeDelivery?.let { debounceHandler.removeCallbacks(it) }
        pendingThemeDelivery = null
    }

    private fun deliverThemeMode(themeMode: Int) {
        if (lastDeliveredTheme == themeMode) return
        lastDeliveredTheme = themeMode
        callback(themeMode)
    }

    private fun getNormalizedThemeMode(): Int {
        return try {
            HeadUnitDayNightRepository.readEffectiveTheme(context)
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Error getting normalized theme mode", e)
            1
        }
    }

    fun isObserving(): Boolean = isObserving

    companion object {
        const val THEME_CHANGE_DEBOUNCE_MS = 1_000L
    }
}
