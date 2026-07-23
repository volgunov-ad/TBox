package vad.dashing.tbox.freeform

import vad.dashing.tbox.R

/**
 * Which edge of the display the companion app occupies in freeform side-by-side launch.
 * [percent] on the widget is the share of that axis given to the companion app.
 */
enum class FreeformLaunchSide(val storageKey: String, val labelRes: Int) {
    LEFT("left", R.string.widget_app_launcher_freeform_side_left),
    RIGHT("right", R.string.widget_app_launcher_freeform_side_right),
    TOP("top", R.string.widget_app_launcher_freeform_side_top),
    BOTTOM("bottom", R.string.widget_app_launcher_freeform_side_bottom);

    companion object {
        val DEFAULT: FreeformLaunchSide = RIGHT

        fun fromStorageKey(key: String?): FreeformLaunchSide {
            val normalized = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT
        }
    }
}
