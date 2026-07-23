package vad.dashing.tbox.freeform

import android.content.Context
import android.widget.Toast
import vad.dashing.tbox.R

/** User-facing guard for actions that need a focusable Activity (dialogs / settings UI). */
object WindowModeUiGuard {
    fun isActive(): Boolean = FreeformCompanionSession.isActive

    fun toastEditingBlocked(context: Context) {
        Toast.makeText(
            context.applicationContext,
            context.getString(R.string.main_screen_window_mode_edit_blocked_toast),
            Toast.LENGTH_LONG,
        ).show()
    }

    /** Returns true if the action must not run (and a toast was shown). */
    fun blockEditingIfActive(context: Context): Boolean {
        if (!isActive()) return false
        toastEditingBlocked(context)
        return true
    }
}
