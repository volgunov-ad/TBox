package vad.dashing.tbox.automation

interface AutomationServiceActions {
    suspend fun finishAndStartTrip(): AutomationActionResult
    suspend fun restartTbox(): AutomationActionResult
    suspend fun toggleHideFloatingPanels(): AutomationActionResult
    suspend fun toggleFloatingPanelsEnabled(): AutomationActionResult
    suspend fun setEspRelayMask(mask: Int): AutomationActionResult
    suspend fun toggleEspRelay(channel: Int): AutomationActionResult
    suspend fun pulseEspRelay(channel: Int, durationMillis: Long?): AutomationActionResult
    suspend fun rebootGnssModule(): AutomationActionResult
    suspend fun setSimulatedLocationSourceLoss(enabled: Boolean): AutomationActionResult
}
