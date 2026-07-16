package vad.dashing.tbox

import android.os.Build
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Pure mapping + Settings key names for head-unit day/night (A9 mbCAN vs A10 Adayo).
 * App contract stays `theme = 1` light, `theme = 2` dark.
 *
 * Naming note: in this project «Android 10» means the Adayo/VHAL head-unit stack.
 * Stock UI may show [Build.VERSION.RELEASE] as `10` while [Build.VERSION.SDK_INT] is still
 * API 28 (Pie) — see [docs/CAN_BACKENDS_RU.md].
 */
object HeadUnitDayNightMapping {
    // --- A9 (mbCAN) ---
    const val A9_NIGHT_MODE_AUTO_KEY = "com.mb.provider.night_mode_auto"
    const val A9_DAY_NIGHT_STATUS_KEY = "DAY_NIGHT_STATUS"

    const val A9_NIGHT_MODE_LIGHT_MANUAL = 0
    const val A9_NIGHT_MODE_AUTO = 1
    const val A9_NIGHT_MODE_DARK_MANUAL = 2

    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    // --- A10 (Adayo) ---
    const val A10_SKIN_KEY = "adayo_skin"
    const val A10_AUTO_SKIN_KEY = "auto_skin"

    /** Day skin (stock ThemeHelper.DAY_SKIN / LightFragment). */
    const val A10_SKIN_DAY = 1
    /** Night skin (stock ThemeHelper.NIGHT_SKIN; Launcher getSkin default). */
    const val A10_SKIN_NIGHT = 2

    const val A10_AUTO_SKIN_OFF = 0
    const val A10_AUTO_SKIN_ON = 1

    const val A10_SET_THEME_ACTION = "com.adayo.launcher.SET_THEME"
    const val A10_LAUNCHER_PACKAGE = "com.adayo.launcher"
    const val A10_AUTO_THEME_BROADCAST = "com.adayo.auto.theme"
    const val A10_SET_THEME_EXTRA_SKIN = "skin"

    /**
     * When non-null, overrides [usesAdayoKeys] (unit tests). Production always leaves this null.
     */
    @Volatile
    internal var usesAdayoKeysOverride: Boolean? = null

    /**
     * Prefer selected [HeadUnitCanMode.Android10Vhal] over [Build.VERSION.SDK_INT]:
     * Adayo HUs often keep API 28 while advertising RELEASE=10 in factory/system UI.
     */
    fun usesAdayoKeys(): Boolean {
        usesAdayoKeysOverride?.let { return it }
        if (UniversalCanRepository.mode.value == HeadUnitCanMode.Android10Vhal) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    fun modeFromA9(nightModeAuto: Int, dayNightStatus: Int): HeadUnitDayNightRepository.Mode {
        return when (nightModeAuto) {
            A9_NIGHT_MODE_LIGHT_MANUAL -> HeadUnitDayNightRepository.Mode.LightManual
            A9_NIGHT_MODE_DARK_MANUAL -> HeadUnitDayNightRepository.Mode.DarkManual
            else -> if (normalizeTheme(dayNightStatus) == THEME_DARK) {
                HeadUnitDayNightRepository.Mode.DarkAuto
            } else {
                HeadUnitDayNightRepository.Mode.LightAuto
            }
        }
    }

    /**
     * @param autoSkin `auto_skin`: 1 = auto, 0 = manual
     * @param skin `adayo_skin`: 1 = day, 2 = night (0 / other → night default)
     */
    fun modeFromA10(autoSkin: Int, skin: Int): HeadUnitDayNightRepository.Mode {
        val dark = normalizeA10Skin(skin) == THEME_DARK
        val auto = autoSkin == A10_AUTO_SKIN_ON
        return when {
            auto && dark -> HeadUnitDayNightRepository.Mode.DarkAuto
            auto && !dark -> HeadUnitDayNightRepository.Mode.LightAuto
            !auto && dark -> HeadUnitDayNightRepository.Mode.DarkManual
            else -> HeadUnitDayNightRepository.Mode.LightManual
        }
    }

    /** Effective Material/wallpaper theme: 1 light, 2 dark. */
    fun effectiveThemeFromA9(nightModeAuto: Int, dayNightStatus: Int): Int {
        return when (nightModeAuto) {
            A9_NIGHT_MODE_LIGHT_MANUAL -> THEME_LIGHT
            A9_NIGHT_MODE_DARK_MANUAL -> THEME_DARK
            else -> normalizeTheme(dayNightStatus)
        }
    }

    fun effectiveThemeFromA10(skin: Int): Int = normalizeA10Skin(skin)

    fun normalizeTheme(raw: Int): Int = if (raw == THEME_DARK) THEME_DARK else THEME_LIGHT

    /** Launcher defaults missing/0 skin to night (2). */
    fun normalizeA10Skin(raw: Int): Int =
        if (raw == A10_SKIN_DAY) THEME_LIGHT else THEME_DARK

    fun nextManualSkinForToggle(mode: HeadUnitDayNightRepository.Mode): Int {
        return when (mode) {
            HeadUnitDayNightRepository.Mode.LightManual,
            HeadUnitDayNightRepository.Mode.LightAuto,
            -> A10_SKIN_NIGHT
            HeadUnitDayNightRepository.Mode.DarkManual,
            HeadUnitDayNightRepository.Mode.DarkAuto,
            -> A10_SKIN_DAY
        }
    }

    fun nextA9ManualAutoValue(mode: HeadUnitDayNightRepository.Mode): Int {
        return when (mode) {
            HeadUnitDayNightRepository.Mode.LightManual,
            HeadUnitDayNightRepository.Mode.LightAuto,
            -> A9_NIGHT_MODE_DARK_MANUAL
            HeadUnitDayNightRepository.Mode.DarkManual,
            HeadUnitDayNightRepository.Mode.DarkAuto,
            -> A9_NIGHT_MODE_LIGHT_MANUAL
        }
    }
}
