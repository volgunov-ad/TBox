package vad.dashing.tbox

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Own-task hygiene for [MainActivity] (`singleTask`).
 *
 * On A9 + DCL, leaving app_area (HOME) often leaves an **empty freeform task** for this
 * package: DCL's `removeTask` is denied (`REMOVE_TASKS`), while the activity itself is gone.
 * The next DCL `launchApp(mode=app_area)` then reports `REUSED_TASK` + `moveTaskToFront`
 * success on that husk (`sz=0`) and never starts Main — floating-panel / DCL shortcut
 * appear dead after the first successful open.
 */
object MainActivityOwnTasks {
    private const val TAG = "MainActivityOwnTasks"

    /** Sentinel: do not protect any task id when pruning. */
    const val NO_KEEP_TASK_ID: Int = -1

    /**
     * Pure: empty own tasks (no activities) must be removed before DCL reuse, otherwise
     * launcher may "successfully" front a husk.
     */
    fun shouldRemoveEmptyOwnTask(
        numActivities: Int,
        taskId: Int,
        keepTaskId: Int = NO_KEEP_TASK_ID,
    ): Boolean {
        if (keepTaskId != NO_KEEP_TASK_ID && taskId == keepTaskId) return false
        return numActivities <= 0
    }

    /**
     * Removes empty tasks that belong to this app (via [ActivityManager.getAppTasks]).
     * Never touches [keepTaskId] — the in-onCreate Main task can briefly report 0 activities.
     *
     * @return number of tasks where [ActivityManager.AppTask.finishAndRemoveTask] was invoked
     *         without throwing
     */
    fun removeEmptyTasks(
        context: Context,
        keepTaskId: Int = NO_KEEP_TASK_ID,
    ): Int {
        val am = context.applicationContext.getSystemService(ActivityManager::class.java)
            ?: return 0
        var removed = 0
        val tasks = runCatching { am.appTasks }.getOrNull().orEmpty()
        for (task in tasks) {
            val info = runCatching { task.taskInfo }.getOrNull() ?: continue
            @Suppress("DEPRECATION")
            val id = info.id
            if (
                !shouldRemoveEmptyOwnTask(
                    numActivities = info.numActivities,
                    taskId = id,
                    keepTaskId = keepTaskId,
                )
            ) {
                continue
            }
            val ok = runCatching {
                task.finishAndRemoveTask()
                true
            }.getOrDefault(false)
            if (ok) {
                removed++
                Log.i(TAG, "Removed empty own task id=$id base=${info.baseActivity}")
            } else {
                Log.w(TAG, "Failed to remove empty own task id=$id base=${info.baseActivity}")
            }
        }
        if (tasks.isNotEmpty()) {
            Log.i(TAG, "prune scanned=${tasks.size} removed=$removed keepTaskId=$keepTaskId")
        }
        return removed
    }
}
