package vad.dashing.tbox.externalapi

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import vad.dashing.tbox.localaccess.LocalAccessContract
import java.security.MessageDigest

/**
 * Token handout for companion apps on the same head unit. Only [call] is served; the caller
 * package comes from Binder and cannot be claimed by the client, unlike `clientName` over HTTP.
 */
class ExternalApiLocalAccessProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val caller = verifiedCaller()
            ?: return ExternalApiLocalAnswer(LocalAccessContract.STATUS_UNKNOWN_CLIENT).toBundle()
        val controller = ExternalApiControllerHolder.get()
            ?: return ExternalApiLocalAnswer(LocalAccessContract.STATUS_UNAVAILABLE).toBundle()
        val answer = when (method) {
            LocalAccessContract.METHOD_GET_ACCESS -> controller.localAccess(
                packageName = caller.packageName,
                certSha256 = caller.certSha256,
                interactive = extras?.getBoolean(LocalAccessContract.EXTRA_INTERACTIVE) == true,
            )
            LocalAccessContract.METHOD_GET_STATUS -> controller.localStatus(caller.packageName)
            else -> ExternalApiLocalAnswer(LocalAccessContract.STATUS_UNKNOWN_CLIENT)
        }
        return answer.toBundle()
    }

    private fun verifiedCaller(): Caller? {
        val packageManager = context?.packageManager ?: return null
        val packageName = callingPackage ?: return null
        val uidPackages = packageManager.getPackagesForUid(Binder.getCallingUid()) ?: return null
        if (packageName !in uidPackages) return null
        val certSha256 = signingCertSha256(packageManager, packageName) ?: return null
        return Caller(packageName, certSha256)
    }

    private fun signingCertSha256(packageManager: PackageManager, packageName: String): String? {
        val info = runCatching {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return null
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(signer.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class Caller(val packageName: String, val certSha256: String)

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
