package vad.dashing.tbox.automation

/**
 * Live UI and service state for [AutomationCondition.UiState] evaluation.
 */
object AutomationUiSnapshot {
    @Volatile
    private var serviceRunning: Boolean = false

    fun setServiceRunning(running: Boolean) {
        serviceRunning = running
    }

    fun isServiceRunning(): Boolean = serviceRunning

    fun isMainScreenOpen(): Boolean =
        AutomationUiEventReporter.isForeground() &&
            AutomationUiEventReporter.currentScreen() == AutomationVisibleScreen.MAIN

    fun isMenuOpen(): Boolean =
        AutomationUiEventReporter.isForeground() &&
            AutomationUiEventReporter.currentScreen() == AutomationVisibleScreen.MENU

    fun matches(state: AutomationUiState): Boolean = when (state) {
        AutomationUiState.SERVICE_RUNNING -> isServiceRunning()
        AutomationUiState.MAIN_SCREEN_OPEN -> isMainScreenOpen()
        AutomationUiState.MENU_OPEN -> isMenuOpen()
    }

    internal fun resetForTests() {
        serviceRunning = false
    }
}
