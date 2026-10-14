package vad.dashing.dataresize

import android.content.Context

/**
 * Set before an operation that ends in a reboot, so the app reopens itself after boot.
 * Only a reopen hint: the outcome is read from /cache, not from here.
 */
enum class PendingOp { Move, Rollback }

class PendingStore(context: Context) {
    private val prefs = context.getSharedPreferences("dataresize", Context.MODE_PRIVATE)

    fun get(): PendingOp? = prefs.getString(KEY, null)?.let { name -> PendingOp.entries.firstOrNull { it.name == name } }

    /** Written synchronously: the framework stops right after the operation starts. */
    fun set(op: PendingOp?) {
        prefs.edit().apply { if (op == null) remove(KEY) else putString(KEY, op.name) }.commit()
    }

    private companion object {
        const val KEY = "pending"
    }
}
