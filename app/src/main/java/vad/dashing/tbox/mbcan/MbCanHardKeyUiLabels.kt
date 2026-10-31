package vad.dashing.tbox.mbcan

import android.content.res.Resources
import androidx.annotation.StringRes
import vad.dashing.tbox.R

/**
 * User-facing RU/EN labels for A9 mbCAN hardkey codes.
 *
 * Technical ids for logs/JSON stay in [KeyPressDiagnosticFormat.mbCanKeyName]
 * (`WHEEL_LEFT_BTN_TOP`, etc.) and must not change for automations codecs.
 */
internal object MbCanHardKeyUiLabels {
    @StringRes
    fun labelResId(keyCode: Int): Int? = when (keyCode) {
        29 -> R.string.hard_key_label_wheel_left_joy_up
        30 -> R.string.hard_key_label_wheel_left_joy_down
        31 -> R.string.hard_key_label_wheel_left_joy_left
        32 -> R.string.hard_key_label_wheel_left_joy_right
        33 -> R.string.hard_key_label_wheel_left_joy_press
        216 -> R.string.hard_key_label_wheel_left_btn_top
        316 -> R.string.hard_key_label_wheel_left_btn_bottom
        115 -> R.string.hard_key_label_wheel_right_joy_up
        114 -> R.string.hard_key_label_wheel_right_joy_down
        163 -> R.string.hard_key_label_wheel_right_joy_left
        165 -> R.string.hard_key_label_wheel_right_joy_right
        28 -> R.string.hard_key_label_wheel_right_joy_press
        158 -> R.string.hard_key_label_wheel_right_btn_top
        582 -> R.string.hard_key_label_wheel_right_btn_bottom
        210 -> R.string.hard_key_label_door_front_passenger
        211 -> R.string.hard_key_label_door_rear_right
        212 -> R.string.hard_key_label_door_rear_left
        103 -> R.string.hard_key_label_up
        105 -> R.string.hard_key_label_left
        106 -> R.string.hard_key_label_right
        108 -> R.string.hard_key_label_down
        116 -> R.string.hard_key_label_power
        587 -> R.string.hard_key_label_home
        else -> null
    }

    /** Localized picker / diagnostics label; includes numeric code for power users. */
    fun formatLabel(resources: Resources, keyCode: Int): String {
        val labelRes = labelResId(keyCode)
        return if (labelRes != null) {
            resources.getString(
                R.string.hard_key_label_with_code,
                resources.getString(labelRes),
                keyCode,
            )
        } else {
            resources.getString(R.string.hard_key_code_unknown, keyCode)
        }
    }
}
