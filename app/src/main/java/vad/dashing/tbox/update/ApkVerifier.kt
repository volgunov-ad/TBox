package vad.dashing.tbox.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

object ApkVerifier {
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun verifySha256(file: File, expectedSha256: String): Boolean =
        sha256Hex(file).equals(expectedSha256.lowercase(), ignoreCase = false)

    fun verifyPackageName(context: Context, apkFile: File, expectedPackageName: String): Boolean {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        } ?: return false
        return archiveInfo.packageName == expectedPackageName
    }
}
