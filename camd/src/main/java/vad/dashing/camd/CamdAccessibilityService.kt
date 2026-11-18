package vad.dashing.camd

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class CamdAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.i(TAG, "спецвозможности подключены — запускаю службу")
        start()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun start() {
        val service = Intent(this, CamdService::class.java)
        try {
            startForegroundService(service)
        } catch (error: Exception) {
            Log.e(TAG, "startForegroundService", error)
            try {
                startService(service)
            } catch (fallback: Exception) {
                Log.e(TAG, "startService", fallback)
            }
        }
    }

    private companion object {
        const val TAG = "CamdBoot"
    }
}
