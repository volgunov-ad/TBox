package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import vad.dashing.tbox.MainActivity

internal fun openMainActivityFromWidget(context: Context) {
    try {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
