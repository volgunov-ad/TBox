package vad.dashing.store.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

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
        val archiveInfo = packageArchiveInfo(context, apkFile) ?: return false
        return archiveInfo.packageName == expectedPackageName
    }

    fun signingCertSha256(context: Context, apkFile: File): String? {
        val archiveInfo = packageArchiveInfo(context, apkFile) ?: return null
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = archiveInfo.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.signatures
        } ?: return null
        val first = signatures.firstOrNull() ?: return null
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(first.toByteArray())) as X509Certificate
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(cert.encoded).joinToString("") { "%02x".format(it) }
    }

    private fun packageArchiveInfo(context: Context, apkFile: File) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
        }
}
