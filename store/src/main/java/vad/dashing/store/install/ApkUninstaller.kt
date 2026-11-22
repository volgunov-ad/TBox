package vad.dashing.store.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object ApkUninstaller {
    /**
     * Opens the system uninstall UI for [packageName].
     * Returns false when the package is this Store app itself (refuse self-uninstall).
     */
    fun uninstall(context: Context, packageName: String): Boolean {
        val target = packageName.trim()
        if (target.isEmpty() || target == context.packageName) return false
        val uri = Uri.fromParts("package", target, null)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Intent(Intent.ACTION_DELETE, uri)
        } else {
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, uri)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
