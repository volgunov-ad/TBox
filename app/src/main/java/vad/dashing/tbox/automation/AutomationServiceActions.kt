package vad.dashing.tbox.automation

interface AutomationServiceActions {
    suspend fun finishAndStartTrip(): AutomationActionResult
    suspend fun restartTbox(): AutomationActionResult
    suspend fun applyFloatingPanelVisibility(action: AutomationAction.Builtin): AutomationActionResult
    suspend fun applyFloatingPanelEnabled(action: AutomationAction.Builtin): AutomationActionResult
    suspend fun setEspRelayMask(mask: Int): AutomationActionResult
    suspend fun toggleEspRelay(channel: Int): AutomationActionResult
    suspend fun pulseEspRelay(channel: Int, durationMillis: Long?): AutomationActionResult
    suspend fun rebootGnssModule(): AutomationActionResult
    suspend fun setSimulatedLocationSourceLoss(enabled: Boolean): AutomationActionResult
}
