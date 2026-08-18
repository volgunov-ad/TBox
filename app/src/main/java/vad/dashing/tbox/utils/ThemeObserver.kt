package vad.dashing.tbox.utils

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import vad.dashing.tbox.HeadUnitDayNightMapping
import vad.dashing.tbox.HeadUnitDayNightRepository

/**
 * Observes head-unit day/night Settings and delivers normalized theme (`1` light, `2` dark).
 * Keys: A9 `night_mode_auto` + `DAY_NIGHT_STATUS`; A10+ `adayo_skin` + `auto_skin`
 * (see [vad.dashing.tbox.HeadUnitDayNightMapping]).
 *
 * When [HeadUnitDayNightRepository.isFollowSystem] is false, system URIs are not watched and
 * [setManualTheme] / [applyFollowMode] deliver the app-local theme instead.
 */
class ThemeObserver(
    private val context: Context,
    private val callback: (themeMode: Int) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val contentResolver = context.contentResolver
    private val debounceHandler = Handler(Looper.getMainLooper())

    private val observedUris = HeadUnitDayNightRepository.observedSettingUris()

    private var isObserving = false
    private var followSystem = true
    private var manualTheme: Int = HeadUnitDayNightMapping.THEME_LIGHT
    private var pendingThemeDelivery: Runnable? = null
    private var lastDeliveredTheme: Int? = null

    fun startObserving() {
        applyFollowMode(
            follow = HeadUnitDayNightRepository.isFollowSystem(),
            manualTheme = HeadUnitDayNightRepository.appLocalTheme(),
        )
    }

    /**
     * Switch between system Settings observation and app-local theme delivery.
     * When [follow] is false, unregisters ContentObserver and delivers [manualTheme].
     */
    fun applyFollowMode(follow: Boolean, manualTheme: Int) {
        this.manualTheme = HeadUnitDayNightMapping.normalizeTheme(manualTheme)
        this.followSystem = follow
        cancelPendingDelivery()
        if (follow) {
            ensureRegistered()
            deliverCurrentTheme(immediate = true)
        } else {
            unregisterIfNeeded()
            deliverThemeMode(this.manualTheme)
        }
    }

    /** App-local day/night toggle while not following the head unit. */
    fun setManualTheme(themeMode: Int) {
        manualTheme = HeadUnitDayNightMapping.normalizeTheme(themeMode)
        if (!followSystem) {
            deliverThemeMode(manualTheme)
        }
    }

    fun stopObserving() {
        try {
            cancelPendingDelivery()
            unregisterIfNeeded()
            Log.d("ThemeObserver", "Stopped observing theme changes")
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Failed to stop observing theme changes", e)
        }
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        if (!followSystem) return
        deliverCurrentTheme(immediate = false)
    }

    private fun ensureRegistered() {
        if (isObserving) return
        try {
            observedUris.forEach { uri ->
                contentResolver.registerContentObserver(uri, false, this)
            }
            isObserving = true
            Log.d("ThemeObserver", "Started observing theme changes (${observedUris.size} uris)")
        } catch (e: SecurityException) {
            Log.e("ThemeObserver", "SecurityException: Missing READ_SETTINGS permission", e)
            deliverThemeMode(HeadUnitDayNightMapping.THEME_LIGHT)
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Failed to start observing theme changes", e)
            deliverThemeMode(HeadUnitDayNightMapping.THEME_LIGHT)
        }
    }

    private fun unregisterIfNeeded() {
        if (!isObserving) return
        try {
            contentResolver.unregisterContentObserver(this)
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Failed to unregister theme observer", e)
        }
        isObserving = false
    }

    private fun deliverCurrentTheme(immediate: Boolean) {
        cancelPendingDelivery()
        val delivery = Runnable {
            pendingThemeDelivery = null
            try {
                deliverThemeMode(getNormalizedThemeMode())
            } catch (e: Exception) {
                Log.e("ThemeObserver", "Error in onChange callback", e)
                deliverThemeMode(HeadUnitDayNightMapping.THEME_LIGHT)
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
        val normalized = HeadUnitDayNightMapping.normalizeTheme(themeMode)
        if (lastDeliveredTheme == normalized) return
        lastDeliveredTheme = normalized
        callback(normalized)
    }

    private fun getNormalizedThemeMode(): Int {
        return try {
            HeadUnitDayNightRepository.readEffectiveTheme(context)
        } catch (e: Exception) {
            Log.e("ThemeObserver", "Error getting normalized theme mode", e)
            HeadUnitDayNightMapping.THEME_LIGHT
        }
    }

    fun isObserving(): Boolean = isObserving

    companion object {
        const val THEME_CHANGE_DEBOUNCE_MS = 1_000L
    }
}
