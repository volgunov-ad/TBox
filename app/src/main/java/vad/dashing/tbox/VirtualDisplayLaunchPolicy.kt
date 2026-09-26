package vad.dashing.tbox

import androidx.annotation.StringRes

/**
 * How a [AppLauncherLaunchMode.VIRTUAL_DISPLAY] shortcut starts an app that may already
 * be running on another HU display.
 *
 * [RELOCATE] (default): `am force-stop` then `am start --display` so the app moves.
 * [NEW_INSTANCE]: `am start --display … --activity-multiple-task --activity-new-task`
 * (same flags freeform uses) so a second task can appear without killing the first.
 */
enum class VirtualDisplayLaunchPolicy(val storageKey: String, @StringRes val labelRes: Int) {
    RELOCATE("relocate", R.string.widget_app_launcher_vd_policy_relocate),
    NEW_INSTANCE("new_instance", R.string.widget_app_launcher_vd_policy_new_instance);

    companion object {
        val DEFAULT: VirtualDisplayLaunchPolicy = RELOCATE

        fun fromStorageKey(key: String?): VirtualDisplayLaunchPolicy {
            val normalized = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT
        }
    }
}
