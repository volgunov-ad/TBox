package vad.dashing.tbox

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * Brings an existing [MainActivity] (singleTask) to the front when possible.
 *
 * Uses [Intent.FLAG_ACTIVITY_CLEAR_TOP], [Intent.FLAG_ACTIVITY_SINGLE_TOP], and
 * [Intent.FLAG_ACTIVITY_REORDER_TO_FRONT]. [Intent.FLAG_ACTIVITY_NEW_TASK] is added only when
 * [context] is not an [Activity], because starting an activity from a non-Activity context
 * requires it (e.g. [android.app.Service], [Application], app widget [Context]).
 */
object MainActivityIntentHelper {

    fun applyBringToFrontFlags(intent: Intent, context: Context) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createBringToFrontIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).also { applyBringToFrontFlags(it, context) }

    /**
     * Third-party app [Intent] from [android.content.pm.PackageManager.getLaunchIntentForPackage]:
     * bring an existing task forward; [Intent.FLAG_ACTIVITY_NEW_TASK] only when [context] is not an [Activity].
     */
    fun applyExternalAppLaunchFlags(intent: Intent, context: Context) {
        intent.addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
