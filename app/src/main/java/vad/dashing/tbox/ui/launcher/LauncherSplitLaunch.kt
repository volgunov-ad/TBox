package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.widget.Toast
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.LaunchableAppEntry

private const val TAG = "LauncherSplit"

internal fun launchSplitPreset(
    context: Context,
    preset: LauncherSplitPreset,
    apps: List<LaunchableAppEntry>,
) {
    val left = apps.firstOrNull { it.packageName == preset.leftPackage }
    val right = apps.firstOrNull { it.packageName == preset.rightPackage }
    if (left == null || right == null) {
        Toast.makeText(context, context.getString(R.string.launcher_split_preset_missing), Toast.LENGTH_SHORT).show()
        return
    }
    launchSplitApps(context, left, right, preset.leftRatio)
}

internal fun launchSplitApps(
    context: Context,
    left: LaunchableAppEntry,
    right: LaunchableAppEntry,
    leftRatio: Float = 0.5f,
) {
    if (!isFreeformEnabled(context)) {
        Toast.makeText(context, context.getString(R.string.launcher_split_freeform_required), Toast.LENGTH_LONG).show()
        return
    }
    val base = LauncherEmbeddedBoundsState.splitBounds() ?: run {
        Toast.makeText(context, context.getString(R.string.launcher_split_bounds_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val ratio = leftRatio.coerceIn(0.2f, 0.8f)
    val panes = LauncherEmbeddedBoundsState.splitPaneBounds(ratio) ?: run {
        Toast.makeText(context, context.getString(R.string.launcher_split_bounds_unavailable), Toast.LENGTH_SHORT).show()
        return
    }
    val (leftBounds, rightBounds) = panes

    val leftIntent = resolveLaunchIntent(context, left.packageName)
        ?: left.activityName?.let { activity ->
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setClassName(left.packageName, activity)
            }
        }
    val rightIntent = resolveLaunchIntent(context, right.packageName)
        ?: right.activityName?.let { activity ->
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                setClassName(right.packageName, activity)
            }
        }
    if (leftIntent == null || rightIntent == null) {
        Toast.makeText(context, context.getString(R.string.launcher_split_resolve_failed), Toast.LENGTH_SHORT).show()
        return
    }

    Log.w(TAG, "split bounds base=$base left=$leftBounds right=$rightBounds ratio=$ratio")
    val leftOk = tryLaunchIntentInBounds(context, left.packageName, leftIntent, leftBounds)
    if (!leftOk) {
        Toast.makeText(context, context.getString(R.string.launcher_split_left_failed), Toast.LENGTH_SHORT).show()
        return
    }
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        val rightOk = tryLaunchIntentInBounds(context, right.packageName, rightIntent, rightBounds)
        if (!rightOk) {
            Toast.makeText(context, context.getString(R.string.launcher_split_right_failed), Toast.LENGTH_SHORT).show()
        }
    }, 450L)
}
