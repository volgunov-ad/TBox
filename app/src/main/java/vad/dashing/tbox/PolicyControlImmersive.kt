package vad.dashing.tbox

import android.content.Context
import android.provider.Settings

private const val IMMERSIVE_FULL_PREFIX = "immersive.full="

/**
 * Builds a new [Settings.Global.POLICY_CONTROL] value: keeps existing policies except
 * `immersive.full=`, then appends merged package list for immersive full-screen.
 */
fun mergeImmersiveFullPolicy(existingPolicy: String?, packages: List<String>): String {
    val normalized = packages
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val kept = existingPolicy
        ?.split(';')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith(IMMERSIVE_FULL_PREFIX) }
        .orEmpty()
    if (normalized.isEmpty()) {
        return kept.joinToString(";")
    }
    val immersiveSegment = IMMERSIVE_FULL_PREFIX + normalized.joinToString(",")
    return (kept + immersiveSegment).joinToString(";")
}

/** Mirrors [Settings.Global.POLICY_CONTROL] for builds where the constant is not exposed. */
private const val GLOBAL_POLICY_CONTROL = "policy_control"

/** @return true if the value was written (requires [android.Manifest.permission.WRITE_SECURE_SETTINGS]). */
fun putGlobalPolicyControl(context: Context, value: String): Boolean =
    try {
        Settings.Global.putString(
            context.applicationContext.contentResolver,
            GLOBAL_POLICY_CONTROL,
            value
        )
        true
    } catch (_: SecurityException) {
        false
    }

fun readGlobalPolicyControl(context: Context): String =
    Settings.Global.getString(
        context.applicationContext.contentResolver,
        GLOBAL_POLICY_CONTROL
    ).orEmpty()
