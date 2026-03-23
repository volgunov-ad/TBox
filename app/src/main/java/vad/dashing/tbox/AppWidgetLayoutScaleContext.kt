package vad.dashing.tbox

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Context whose [Resources] use scaled density and font scale so RemoteViews / AppWidgetHostView
 * lay out text and dimensions proportionally (instead of stretching the host view with scaleX/scaleY).
 */
fun Context.withAppWidgetLayoutScale(scale: Float): Context {
    val s = scale.coerceIn(0.1f, 2f)
    if (abs(s - 1f) < 0.001f) return this
    val baseRes = resources
    val dm = DisplayMetrics()
    dm.setTo(baseRes.displayMetrics)
    dm.density *= s
    dm.scaledDensity *= s
    dm.xdpi *= s
    dm.ydpi *= s
    dm.densityDpi = (160f * dm.density).roundToInt().coerceIn(10, 640)
    val cfg = Configuration(baseRes.configuration)
    cfg.fontScale = (cfg.fontScale * s).coerceIn(0.01f, 5f)
    @Suppress("DEPRECATION")
    val newRes = Resources(assets, dm, cfg)
    val scaled = object : ContextWrapper(this) {
        override fun getResources(): Resources = newRes
    }
    return ContextThemeWrapper(scaled, theme)
}
