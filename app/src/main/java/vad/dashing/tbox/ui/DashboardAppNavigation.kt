package vad.dashing.tbox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import vad.dashing.tbox.MainActivityIntentHelper

internal fun launchAppFromWidget(context: Context, packageName: String) {
    if (packageName.isBlank()) return
    try {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return
        // Third-party launch intents: bring existing task forward when possible; NEW_TASK only if needed.
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        if (context !is Activity) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(launchIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

internal fun openMainActivityFromWidget(context: Context) {
    try {
        val intent = MainActivityIntentHelper.createBringToFrontIntent(context)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
