package vad.dashing.camd

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import android.util.Log
import vad.dashing.rootsvc.client.RootSvcProtocol

/**
 * Backup for DCL builds that do not unlock USB sticks at boot. With SystemUI hidden, vold
 * stays in secure-keyguard and does not scan USB disks until someone calls `usb.unlock`.
 */
object BootUsbUnlock {
    private const val TAG = "CamdUsbUnlock"
    private const val SYSTEMUI = "com.android.systemui"
    const val DELAY_MS = 10_000L
    const val BOOT_WINDOW_MS = 180_000L

    fun due(uptimeMs: Long, systemUiHidden: Boolean, usbMounted: Boolean): Boolean =
        uptimeMs < BOOT_WINDOW_MS && systemUiHidden && !usbMounted

    /** Blocking; call from a worker thread right after the runtime starts. */
    fun runAfterDelay(context: Context) {
        val started = SystemClock.elapsedRealtime()
        if (started >= BOOT_WINDOW_MS) return
        Thread.sleep(DELAY_MS)
        val hidden = systemUiHidden(context)
        val mounted = usbMounted(context)
        if (!due(started, hidden, mounted)) {
            Log.i(TAG, "не нужно: SystemUI скрыт=$hidden, флешка смонтирована=$mounted")
            return
        }
        val result = CamdRootChannel.usbUnlock()
        if (result.isOk()) {
            Log.i(TAG, "usb.unlock через rootsvc выполнен")
        } else {
            Log.w(TAG, "usb.unlock: ${CamdRootBridge.failMessage(result, RootSvcProtocol.CMD_USB_UNLOCK)}")
        }
    }

    private fun systemUiHidden(context: Context): Boolean = try {
        !context.packageManager.getApplicationInfo(SYSTEMUI, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        true
    }

    private fun usbMounted(context: Context): Boolean {
        val storage = context.getSystemService(StorageManager::class.java) ?: return false
        return storage.storageVolumes.any {
            it.isRemovable && it.state == Environment.MEDIA_MOUNTED
        }
    }
}
