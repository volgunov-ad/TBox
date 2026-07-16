package vad.dashing.tbox

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Head-unit day/night theme.
 *
 * - Android 9: `com.mb.provider.night_mode_auto` + `DAY_NIGHT_STATUS`
 * - Android 10+: Adayo `auto_skin` + `adayo_skin` (+ Launcher `SET_THEME` / `com.adayo.auto.theme`)
 *
 * Same effective theme contract as [vad.dashing.tbox.utils.ThemeObserver] (`1` light, `2` dark).
 */
object HeadUnitDayNightRepository {
    /** @deprecated Prefer [HeadUnitDayNightMapping.A9_NIGHT_MODE_AUTO_KEY] */
    const val NIGHT_MODE_AUTO_KEY = HeadUnitDayNightMapping.A9_NIGHT_MODE_AUTO_KEY
    /** @deprecated Prefer [HeadUnitDayNightMapping.A9_DAY_NIGHT_STATUS_KEY] */
    const val DAY_NIGHT_STATUS_KEY = HeadUnitDayNightMapping.A9_DAY_NIGHT_STATUS_KEY

    const val NIGHT_MODE_LIGHT_MANUAL = HeadUnitDayNightMapping.A9_NIGHT_MODE_LIGHT_MANUAL
    const val NIGHT_MODE_AUTO = HeadUnitDayNightMapping.A9_NIGHT_MODE_AUTO
    const val NIGHT_MODE_DARK_MANUAL = HeadUnitDayNightMapping.A9_NIGHT_MODE_DARK_MANUAL

    const val DAY_NIGHT_LIGHT = HeadUnitDayNightMapping.THEME_LIGHT
    const val DAY_NIGHT_DARK = HeadUnitDayNightMapping.THEME_DARK

    private const val TAG = "HeadUnitDayNight"

    enum class Mode {
        LightManual,
        LightAuto,
        DarkManual,
        DarkAuto,
    }

    private val _modeState = MutableStateFlow<Mode?>(null)
    val modeState: StateFlow<Mode?> = _modeState.asStateFlow()

    private var observer: ContentObserver? = null
    private var observedUris: Set<Uri> = emptySet()
    private var observeRefCount = 0

    fun readMode(context: Context): Mode {
        return if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            HeadUnitDayNightMapping.modeFromA10(
                autoSkin = readA10AutoSkin(context),
                skin = readA10Skin(context),
            )
        } else {
            HeadUnitDayNightMapping.modeFromA9(
                nightModeAuto = readA9AutoMode(context),
                dayNightStatus = readA9DayNightStatus(context),
            )
        }
    }

    /** Effective theme for Material / wallpapers: `1` light, `2` dark. */
    fun readEffectiveTheme(context: Context): Int {
        return if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            HeadUnitDayNightMapping.effectiveThemeFromA10(readA10Skin(context))
        } else {
            HeadUnitDayNightMapping.effectiveThemeFromA9(
                nightModeAuto = readA9AutoMode(context),
                dayNightStatus = readA9DayNightStatus(context),
            )
        }
    }

    /** Single tap: switch to the opposite manual theme (day ↔ night). */
    fun toggleManualTheme(context: Context): Boolean {
        val mode = readMode(context)
        return if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            toggleManualThemeA10(context, mode)
        } else {
            val nextAutoValue = HeadUnitDayNightMapping.nextA9ManualAutoValue(mode)
            writeA9AutoMode(context, nextAutoValue).also { success ->
                if (success) publishFromContext(context)
            }
        }
    }

    /** Double tap: enable stock auto day/night mode. */
    fun enableAutoMode(context: Context): Boolean {
        return if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            enableAutoModeA10(context)
        } else {
            writeA9AutoMode(context, NIGHT_MODE_AUTO).also { success ->
                if (success) publishFromContext(context)
            }
        }
    }

    fun writeAutoMode(context: Context, value: Int): Boolean {
        return if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            // A10: legacy API — map A9 auto values onto Adayo keys when callers still pass them.
            when (value) {
                NIGHT_MODE_AUTO -> enableAutoModeA10(context)
                NIGHT_MODE_LIGHT_MANUAL -> applyManualSkinA10(context, HeadUnitDayNightMapping.A10_SKIN_DAY)
                NIGHT_MODE_DARK_MANUAL -> applyManualSkinA10(context, HeadUnitDayNightMapping.A10_SKIN_NIGHT)
                else -> false
            }
        } else {
            writeA9AutoMode(context, value)
        }
    }

    fun startObserving(context: Context) {
        observeRefCount++
        if (observer != null) {
            publishFromContext(context)
            return
        }
        val appContext = context.applicationContext
        val uris = observedSettingUris()
        observedUris = uris
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (uri == null || uri in observedUris) {
                    publishFromContext(appContext)
                }
            }
        }
        observer = contentObserver
        val resolver = appContext.contentResolver
        uris.forEach { uri ->
            resolver.registerContentObserver(uri, false, contentObserver)
        }
        publishFromContext(appContext)
    }

    fun stopObserving(context: Context) {
        if (observeRefCount <= 0) return
        observeRefCount--
        if (observeRefCount > 0) return
        observer?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        observer = null
        observedUris = emptySet()
        _modeState.value = null
    }

    fun observedSettingUris(): Set<Uri> {
        // Watch both stacks: HU mode can resolve after ThemeObserver starts, and Adayo
        // firmware may report API 28 while using adayo_skin / auto_skin.
        return setOf(
            Settings.Global.getUriFor(NIGHT_MODE_AUTO_KEY),
            Settings.System.getUriFor(DAY_NIGHT_STATUS_KEY),
            Settings.Global.getUriFor(HeadUnitDayNightMapping.A10_SKIN_KEY),
            Settings.Global.getUriFor(HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY),
        )
    }

    private fun toggleManualThemeA10(context: Context, mode: Mode): Boolean {
        val nextSkin = HeadUnitDayNightMapping.nextManualSkinForToggle(mode)
        return applyManualSkinA10(context, nextSkin)
    }

    private fun enableAutoModeA10(context: Context): Boolean {
        val putOk = runCatching {
            Settings.Global.putInt(
                context.contentResolver,
                HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY,
                HeadUnitDayNightMapping.A10_AUTO_SKIN_ON,
            )
        }.getOrDefault(false)
        if (!putOk) return false
        runCatching {
            context.sendBroadcast(Intent(HeadUnitDayNightMapping.A10_AUTO_THEME_BROADCAST))
        }.onFailure { e ->
            Log.w(TAG, "Failed to broadcast ${HeadUnitDayNightMapping.A10_AUTO_THEME_BROADCAST}", e)
        }
        publishFromContext(context)
        return true
    }

    private fun applyManualSkinA10(context: Context, skin: Int): Boolean {
        val autoOff = runCatching {
            Settings.Global.putInt(
                context.contentResolver,
                HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY,
                HeadUnitDayNightMapping.A10_AUTO_SKIN_OFF,
            )
        }.getOrDefault(false)
        if (!autoOff) return false

        val serviceOk = runCatching {
            val intent = Intent(HeadUnitDayNightMapping.A10_SET_THEME_ACTION).apply {
                setPackage(HeadUnitDayNightMapping.A10_LAUNCHER_PACKAGE)
                putExtra(HeadUnitDayNightMapping.A10_SET_THEME_EXTRA_SKIN, skin)
            }
            context.startService(intent)
            true
        }.getOrElse { e ->
            Log.w(TAG, "SET_THEME service failed; falling back to Settings put", e)
            false
        }

        if (!serviceOk) {
            val putSkin = runCatching {
                Settings.Global.putInt(
                    context.contentResolver,
                    HeadUnitDayNightMapping.A10_SKIN_KEY,
                    skin,
                )
            }.getOrDefault(false)
            if (!putSkin) return false
        }

        publishFromContext(context)
        return true
    }

    private fun writeA9AutoMode(context: Context, value: Int): Boolean {
        return runCatching {
            Settings.Global.putInt(context.contentResolver, NIGHT_MODE_AUTO_KEY, value)
        }.getOrDefault(false)
    }

    private fun readA9AutoMode(context: Context): Int {
        return runCatching {
            Settings.Global.getInt(context.contentResolver, NIGHT_MODE_AUTO_KEY, NIGHT_MODE_AUTO)
        }.getOrDefault(NIGHT_MODE_AUTO)
    }

    private fun readA9DayNightStatus(context: Context): Int {
        return runCatching {
            Settings.System.getInt(context.contentResolver, DAY_NIGHT_STATUS_KEY, DAY_NIGHT_LIGHT)
        }.getOrDefault(DAY_NIGHT_LIGHT)
    }

    private fun readA10Skin(context: Context): Int {
        return runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                HeadUnitDayNightMapping.A10_SKIN_KEY,
                HeadUnitDayNightMapping.A10_SKIN_NIGHT,
            )
        }.getOrDefault(HeadUnitDayNightMapping.A10_SKIN_NIGHT)
    }

    private fun readA10AutoSkin(context: Context): Int {
        return runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY,
                HeadUnitDayNightMapping.A10_AUTO_SKIN_ON,
            )
        }.getOrDefault(HeadUnitDayNightMapping.A10_AUTO_SKIN_ON)
    }

    private fun publishFromContext(context: Context) {
        _modeState.value = readMode(context)
    }
}
