package vad.dashing.tbox

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class KeyboardAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateKeyboardVisibility()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> updateKeyboardVisibility()
        }
    }

    override fun onInterrupt() {
        updateKeyboardShown(false)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        updateKeyboardShown(false)
        return super.onUnbind(intent)
    }

    private fun updateKeyboardVisibility() {
        val windows = windows ?: run {
            updateKeyboardShown(false)
            return
        }
        val isImeVisible = windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && window.isVisible
        }
        updateKeyboardShown(isImeVisible)
    }

    private fun updateKeyboardShown(isShown: Boolean) {
        if (TboxRepository.isKeyboardShown.value == isShown) return
        TboxRepository.updateIsKeyboardShown(isShown)
    }
}
