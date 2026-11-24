package vad.dashing.tbox.automation

interface AutomationServiceActions {
    suspend fun finishAndStartTrip(): AutomationActionResult
    suspend fun restartTbox(): AutomationActionResult
    suspend fun applyFloatingPanelVisibility(action: AutomationAction.Builtin): AutomationActionResult
    suspend fun applyFloatingPanelEnabled(action: AutomationAction.Builtin): AutomationActionResult
    suspend fun setAutomationEnabled(automationId: String, enabled: Boolean): AutomationActionResult
    suspend fun setEspRelayMask(mask: Int): AutomationActionResult
    suspend fun toggleEspRelay(channel: Int): AutomationActionResult
    suspend fun pulseEspRelay(channel: Int, durationMillis: Long?): AutomationActionResult
    suspend fun setEspBleRelayState(id: String, channel: Int, on: Boolean): AutomationActionResult
    suspend fun toggleEspBleRelay(id: String, channel: Int): AutomationActionResult
    suspend fun pulseEspBleRelay(id: String, channel: Int, durationMillis: Long?): AutomationActionResult
    suspend fun rebootGnssModule(): AutomationActionResult
    suspend fun setSimulatedLocationSourceLoss(enabled: Boolean): AutomationActionResult
    suspend fun setWifiModemMobileDataEnabled(enabled: Boolean): AutomationActionResult
    suspend fun rebootWifiModem(): AutomationActionResult
}
