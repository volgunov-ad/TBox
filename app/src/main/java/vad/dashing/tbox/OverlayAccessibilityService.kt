package vad.dashing.tbox

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Event-less accessibility service whose only purpose is a window token for
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]: that layer is above SystemUI
 * status / navigation bars, so floating panels moved over the stock HU bars stay touchable.
 */
class OverlayAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedWindowManager.value = getSystemService(WindowManager::class.java)
        TboxRepository.addLog("INFO", TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        connectedWindowManager.value = null
        TboxRepository.addLog("INFO", TAG, "disconnected")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connectedWindowManager.value = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OverlayA11y"

        private val connectedWindowManager = MutableStateFlow<WindowManager?>(null)

        /** WindowManager bound to the service token while connected, else null. */
        val windowManager: StateFlow<WindowManager?> = connectedWindowManager.asStateFlow()
    }
}
