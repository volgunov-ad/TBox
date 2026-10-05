package vad.dashing.tbox

import android.app.Activity
import android.os.Bundle

/**
 * Trampoline for [android.app.PendingIntent] / widget taps that used to target [MainActivity]
 * directly. Honors [LaunchMainInStockAppWindowSetting] at click time (not at PendingIntent build).
 */
class MainActivityLaunchRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent
        val panelId = source?.getStringExtra(MainActivityIntentHelper.EXTRA_FLOATING_DASHBOARD_PANEL_ID)
        val widgetIndex = source?.getIntExtra(
            MainActivityIntentHelper.EXTRA_FLOATING_DASHBOARD_WIDGET_INDEX,
            -1,
        ) ?: -1
        if (!panelId.isNullOrBlank() && widgetIndex >= 0) {
            MainActivityIntentHelper.openForFloatingDashboardTileEdit(
                this,
                panelId,
                widgetIndex,
            )
        } else {
            MainActivityIntentHelper.bringToFront(this)
        }
        finish()
    }
}
