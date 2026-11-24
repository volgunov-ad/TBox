package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

/**
 * Pure diagnostics for Wi-Fi toggle failures (used by [WifiStaController]).
 */
internal object WifiStaToggleDiagnostics {
    fun failureMessage(
        airplane: Boolean,
        softApEnabled: Boolean,
        sdkInt: Int,
        api29: Int = 29,
    ): String {
        if (airplane) {
            return ruEn("Не удалось переключить Wi-Fi: включён режим полёта", "Could not toggle Wi-Fi: airplane mode is on")
        }
        if (softApEnabled) {
            return ruEn("Не удалось переключить Wi-Fi: активна точка доступа ГУ (SoftAP). ", "Could not toggle Wi-Fi: the HU hotspot (SoftAP) is on. ") +
                ruEn("Выключите раздачу Wi‑Fi в настройках ГУ и повторите.", "Turn off the Wi‑Fi hotspot in HU settings and try again.")
        }
        if (sdkInt >= api29) {
            return ruEn("Не удалось переключить Wi-Fi на API $sdkInt: ", "Could not toggle Wi-Fi on API $sdkInt: ") +
                ruEn("системе запрещено setWifiEnabled для этого приложения. ", "the system forbids setWifiEnabled for this app. ") +
                ruEn("Выдайте WRITE_SECURE_SETTINGS (adb pm grant) или переключайте Wi‑Fi вручную.", "Grant WRITE_SECURE_SETTINGS (adb pm grant) or toggle Wi‑Fi manually.")
        }
        return ruEn("Не удалось переключить Wi-Fi", "Could not toggle Wi-Fi")
    }
}
