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
 * Head-unit day/night theme, plus optional app-local day/night when not following the HU.
 *
 * - Android 9: `com.mb.provider.night_mode_auto` + `DAY_NIGHT_STATUS`
 * - Android 10+: Adayo `auto_skin` + `adayo_skin` (+ Launcher `SET_THEME` / `com.adayo.auto.theme`)
 *
 * Same effective theme contract as [vad.dashing.tbox.utils.ThemeObserver] (`1` light, `2` dark).
 *
 * When [isFollowSystem] is false, [toggleManualTheme] / [readMode] use the persisted app-local
 * theme (no Settings writes; no ADB). Double-tap [enableAutoMode] re-enables follow-system.
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

    private val _followSystem = MutableStateFlow(true)
    val followSystemState: StateFlow<Boolean> = _followSystem.asStateFlow()

    private val _appLocalTheme = MutableStateFlow(HeadUnitDayNightMapping.THEME_LIGHT)

    private var observer: ContentObserver? = null
    private var observedUris: Set<Uri> = emptySet()
    private var observeRefCount = 0

    /**
     * Persist follow-system flag (DataStore). Set by [vad.dashing.tbox.BackgroundService].
     */
    @Volatile
    var persistFollowSystem: ((Boolean) -> Unit)? = null

    /**
     * Persist app-local theme `1`/`2`. Set by [vad.dashing.tbox.BackgroundService].
     */
    @Volatile
    var persistAppLocalTheme: ((Int) -> Unit)? = null

    /**
     * Apply follow/manual mode on [vad.dashing.tbox.utils.ThemeObserver].
     * Args: followSystem, manualTheme.
     */
    @Volatile
    var applyThemeObserverFollowMode: ((follow: Boolean, manualTheme: Int) -> Unit)? = null

    /**
     * Deliver app-local theme through ThemeObserver while detached.
     */
    @Volatile
    var applyThemeObserverManualTheme: ((Int) -> Unit)? = null

    fun isFollowSystem(): Boolean = _followSystem.value

    fun appLocalTheme(): Int = _appLocalTheme.value

    /**
     * Sync in-memory follow / local theme from DataStore (or tests).
     * Does not write Settings on the head unit.
     */
    fun syncFromPersisted(followSystem: Boolean, appLocalTheme: Int) {
        val theme = HeadUnitDayNightMapping.normalizeTheme(appLocalTheme)
        _appLocalTheme.value = theme
        _followSystem.value = followSystem
        if (!followSystem) {
            _modeState.value = modeForLocalTheme(theme)
        }
    }

    /**
     * Re-enable following the head-unit day/night Settings (widget double-tap while detached,
     * or settings toggle). Persists the flag and asks ThemeObserver to attach again.
     */
    fun enableFollowSystem(context: Context): Boolean {
        if (_followSystem.value) {
            applyThemeObserverFollowMode?.invoke(true, _appLocalTheme.value)
            publishFromContext(context)
            return true
        }
        _followSystem.value = true
        persistFollowSystem?.invoke(true)
        applyThemeObserverFollowMode?.invoke(true, _appLocalTheme.value)
        publishFromContext(context)
        return true
    }

    /** Refresh [modeState] from HU Settings when following (e.g. after DataStore sync). */
    fun publishSystemModeIfFollowing(context: Context) {
        if (_followSystem.value) {
            publishFromContext(context)
        }
    }

    fun readMode(context: Context): Mode {
        if (!_followSystem.value) {
            return modeForLocalTheme(_appLocalTheme.value)
        }
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
        if (!_followSystem.value) {
            return _appLocalTheme.value
        }
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
        if (!_followSystem.value) {
            return toggleAppLocalTheme()
        }
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

    /**
     * Double tap: enable stock auto day/night on the HU when following;
     * when detached, re-enable follow-system (same as the settings toggle).
     */
    fun enableAutoMode(context: Context): Boolean {
        if (!_followSystem.value) {
            return enableFollowSystem(context)
        }
        return if (HeadUnitDayNightMapping.usesAdayoKeys()) {
            enableAutoModeA10(context)
        } else {
            writeA9AutoMode(context, NIGHT_MODE_AUTO).also { success ->
                if (success) publishFromContext(context)
            }
        }
    }

    fun writeAutoMode(context: Context, value: Int): Boolean {
        if (!_followSystem.value) {
            return when (value) {
                NIGHT_MODE_AUTO -> enableFollowSystem(context)
                NIGHT_MODE_LIGHT_MANUAL -> setAppLocalTheme(HeadUnitDayNightMapping.THEME_LIGHT)
                NIGHT_MODE_DARK_MANUAL -> setAppLocalTheme(HeadUnitDayNightMapping.THEME_DARK)
                else -> false
            }
        }
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
            publishCurrent(context)
            return
        }
        val appContext = context.applicationContext
        val uris = observedSettingUris()
        observedUris = uris
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                if (!_followSystem.value) return
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
        publishCurrent(appContext)
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

    /** Visible for unit tests. */
    internal fun resetAppDayNightControlForTests() {
        _followSystem.value = true
        _appLocalTheme.value = HeadUnitDayNightMapping.THEME_LIGHT
        _modeState.value = null
        persistFollowSystem = null
        persistAppLocalTheme = null
        applyThemeObserverFollowMode = null
        applyThemeObserverManualTheme = null
        observeRefCount = 0
        observer = null
        observedUris = emptySet()
    }

    private fun toggleAppLocalTheme(): Boolean {
        val next = if (_appLocalTheme.value == HeadUnitDayNightMapping.THEME_DARK) {
            HeadUnitDayNightMapping.THEME_LIGHT
        } else {
            HeadUnitDayNightMapping.THEME_DARK
        }
        return setAppLocalTheme(next)
    }

    private fun setAppLocalTheme(theme: Int): Boolean {
        val normalized = HeadUnitDayNightMapping.normalizeTheme(theme)
        _appLocalTheme.value = normalized
        _modeState.value = modeForLocalTheme(normalized)
        persistAppLocalTheme?.invoke(normalized)
        applyThemeObserverManualTheme?.invoke(normalized)
        return true
    }

    private fun modeForLocalTheme(theme: Int): Mode {
        return if (theme == HeadUnitDayNightMapping.THEME_DARK) {
            Mode.DarkManual
        } else {
            Mode.LightManual
        }
    }

    private fun publishCurrent(context: Context) {
        if (!_followSystem.value) {
            _modeState.value = modeForLocalTheme(_appLocalTheme.value)
        } else {
            publishFromContext(context)
        }
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
