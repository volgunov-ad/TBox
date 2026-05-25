package vad.dashing.tbox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks [MainActivity] visibility and foreground state.
 * Registered from [TboxApplication].
 */
object MainActivityForegroundTracker {

    private val _isMainActivityInForeground = MutableStateFlow(false)
    val isMainActivityInForeground: StateFlow<Boolean> = _isMainActivityInForeground.asStateFlow()
    private val _isMainActivityVisible = MutableStateFlow(false)
    val isMainActivityVisible: StateFlow<Boolean> = _isMainActivityVisible.asStateFlow()

    private var startedCount: Int = 0
    private var resumedCount: Int = 0

    private fun publishStateLocked() {
        _isMainActivityVisible.value = startedCount > 0
        _isMainActivityInForeground.value = resumedCount > 0
    }

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}

            override fun onActivityStarted(activity: Activity) {
                if (activity is MainActivity) {
                    startedCount += 1
                    publishStateLocked()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (activity is MainActivity) {
                    startedCount = (startedCount - 1).coerceAtLeast(0)
                    publishStateLocked()
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) {
                    resumedCount += 1
                    publishStateLocked()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) {
                    resumedCount = (resumedCount - 1).coerceAtLeast(0)
                    publishStateLocked()
                }
            }
        })
    }
}
