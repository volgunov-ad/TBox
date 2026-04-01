package vad.dashing.tbox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether [MainActivity] is in the foreground (between [Activity.onResume] and [Activity.onPause]).
 * Registered from [TboxApplication].
 */
object MainActivityForegroundTracker {

    private val _isMainActivityInForeground = MutableStateFlow(false)
    val isMainActivityInForeground: StateFlow<Boolean> = _isMainActivityInForeground.asStateFlow()

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}

            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) {
                    _isMainActivityInForeground.value = true
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) {
                    _isMainActivityInForeground.value = false
                }
            }
        })
    }
}
