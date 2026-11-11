package vad.dashing.tbox.localaccess

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.DeadObjectException
import android.os.RemoteException

data class LocalAccessResult(
    val status: String,
    val accessToken: String? = null,
    val port: Int? = null,
    val serverEnabled: Boolean? = null,
) {
    val granted: Boolean
        get() = status == LocalAccessContract.STATUS_GRANTED && !accessToken.isNullOrBlank()
}

/**
 * Binder calls into Monitor; all of them block, so keep them off the main thread.
 *
 * Uses [android.content.ContentResolver.acquireUnstableContentProviderClient] so a dying
 * Monitor process (APK update / force-stop) yields [DeadObjectException] instead of AMS killing
 * this client with "depends on provider". On API 28, plain [android.content.ContentResolver.call]
 * still acquires a **stable** provider for the duration of the call — fatal for HOME launchers.
 */
object LocalAccessClient {
    fun getAccess(context: Context, interactive: Boolean): LocalAccessResult {
        val extras = Bundle().apply {
            putBoolean(LocalAccessContract.EXTRA_INTERACTIVE, interactive)
        }
        return call(context, LocalAccessContract.METHOD_GET_ACCESS, extras)
    }

    fun getStatus(context: Context): LocalAccessResult =
        call(context, LocalAccessContract.METHOD_GET_STATUS, null)

    /**
     * Opens the consent dialog in Monitor once, then polls without reopening it until the user
     * answers Allow/Deny (dialog is non-cancelable; pending also appears in Settings → API) or
     * [timeoutMs] runs out.
     */
    fun awaitAccess(
        context: Context,
        timeoutMs: Long = 120_000L,
        pollMs: Long = 1_000L,
        onPending: () -> Unit = {},
    ): LocalAccessResult {
        var result = getAccess(context, interactive = true)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (result.status == LocalAccessContract.STATUS_PENDING) {
            if (System.currentTimeMillis() >= deadline) return result
            onPending()
            Thread.sleep(pollMs)
            result = getAccess(context, interactive = false)
        }
        return result
    }

    private fun call(context: Context, method: String, extras: Bundle?): LocalAccessResult {
        val uri = Uri.parse(LocalAccessContract.URI)
        val client = try {
            context.contentResolver.acquireUnstableContentProviderClient(uri)
        } catch (_: SecurityException) {
            null
        } ?: return LocalAccessResult(LocalAccessContract.STATUS_NOT_SUPPORTED)
        return try {
            val bundle = try {
                client.call(method, null, extras)
            } catch (_: DeadObjectException) {
                null
            } catch (_: RemoteException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: SecurityException) {
                null
            } ?: return LocalAccessResult(LocalAccessContract.STATUS_NOT_SUPPORTED)
            val port = bundle.getInt(LocalAccessContract.KEY_PORT, 0)
            LocalAccessResult(
                status = bundle.getString(LocalAccessContract.KEY_STATUS)
                    ?: LocalAccessContract.STATUS_NOT_SUPPORTED,
                accessToken = bundle.getString(LocalAccessContract.KEY_ACCESS_TOKEN),
                port = port.takeIf { it > 0 },
                serverEnabled = if (bundle.containsKey(LocalAccessContract.KEY_SERVER_ENABLED)) {
                    bundle.getBoolean(LocalAccessContract.KEY_SERVER_ENABLED)
                } else {
                    null
                },
            )
        } finally {
            client.close()
        }
    }
}
