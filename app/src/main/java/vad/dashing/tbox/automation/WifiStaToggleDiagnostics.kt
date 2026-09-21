package vad.dashing.tbox.automation

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
            return "Не удалось переключить Wi-Fi: включён режим полёта"
        }
        if (softApEnabled) {
            return "Не удалось переключить Wi-Fi: активна точка доступа ГУ (SoftAP). " +
                "Выключите раздачу Wi‑Fi в настройках ГУ и повторите."
        }
        if (sdkInt >= api29) {
            return "Не удалось переключить Wi-Fi на API $sdkInt: " +
                "системе запрещено setWifiEnabled для этого приложения. " +
                "Выдайте WRITE_SECURE_SETTINGS (adb pm grant) или переключайте Wi‑Fi вручную."
        }
        return "Не удалось переключить Wi-Fi"
    }
}
