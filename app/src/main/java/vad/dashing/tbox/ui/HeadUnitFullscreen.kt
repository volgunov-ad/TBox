package vad.dashing.tbox.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Automotive-style layout: draw behind status/navigation bars and hide them (transient swipe to show).
 * Does not remove launcher overlays drawn as privileged system windows; matches patterns used by
 * many full-screen head-unit activities.
 */
fun ComponentActivity.applyHeadUnitFullscreenLayout() {
    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
