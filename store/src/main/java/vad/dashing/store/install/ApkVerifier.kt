package vad.dashing.store.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile

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
        sha256Hex(file).equals(expectedSha256.trim().lowercase(), ignoreCase = true)

    fun verifyPackageName(context: Context, apkFile: File, expectedPackageName: String): Boolean {
        val archiveInfo = packageArchiveInfo(context, apkFile, signingFlags = false) ?: return false
        return archiveInfo.packageName == expectedPackageName
    }

    /**
     * SHA-256 (lowercase hex) of the **current** APK signer certificate.
     * Prefers [android.content.pm.SigningInfo.getApkContentsSigners] (same as Monitor OTA);
     * does **not** use history[0] (oldest under key rotation).
     */
    fun signingCertSha256(context: Context, apkFile: File): String? {
        val fromPm = signingCertSha256FromPackageManager(context, apkFile)
        if (fromPm != null) return fromPm
        return signingCertSha256FromJar(apkFile)
    }

    /** Signer of an already-installed package, or null if missing / unreadable. */
    fun installedSigningCertSha256(context: Context, packageName: String): String? {
        if (packageName.isBlank()) return null
        val info = installedPackageInfo(context, packageName) ?: return null
        return certSha256FromPackageInfo(info)
    }

    private fun signingCertSha256FromPackageManager(context: Context, apkFile: File): String? {
        val archiveInfo = packageArchiveInfo(context, apkFile, signingFlags = true) ?: return null
        return certSha256FromPackageInfo(archiveInfo)
    }

    private fun certSha256FromPackageInfo(info: PackageInfo): String? {
        val signatures = currentSignerSignatures(info) ?: return null
        val first = signatures.firstOrNull() ?: return null
        return signatureCertSha256(first)
    }

    /**
     * Current APK contents signers. Avoids [SigningInfo.getSigningCertificateHistory]
     * first element (oldest when key rotation is used).
     */
    private fun currentSignerSignatures(info: PackageInfo): Array<Signature>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo != null) {
                val current = signingInfo.apkContentsSigners
                if (current != null && current.isNotEmpty()) return current
                // Last resort: newest in history (chronological: oldest first).
                val history = signingInfo.signingCertificateHistory
                if (history != null && history.isNotEmpty()) {
                    return arrayOf(history.last())
                }
            }
        }
        @Suppress("DEPRECATION")
        val legacy = info.signatures
        return legacy?.takeIf { it.isNotEmpty() }
    }

    private fun signatureCertSha256(signature: Signature): String? = runCatching {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(signature.toByteArray())) as X509Certificate
        MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /** Fallback when OEM getPackageArchiveInfo omits signingInfo for archive APKs. */
    private fun signingCertSha256FromJar(apkFile: File): String? = runCatching {
        JarFile(apkFile, true).use { jar ->
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!name.startsWith("META-INF/", ignoreCase = true)) continue
                val lower = name.lowercase()
                if (!lower.endsWith(".rsa") && !lower.endsWith(".dsa") && !lower.endsWith(".ec")) {
                    continue
                }
                jar.getInputStream(entry).use { stream ->
                    val certs = CertificateFactory.getInstance("X.509").generateCertificates(stream)
                    val cert = certs.firstOrNull() as? X509Certificate ?: return@use null
                    return@use MessageDigest.getInstance("SHA-256")
                        .digest(cert.encoded)
                        .joinToString("") { "%02x".format(it) }
                }?.let { return it }
            }
        }
        null
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun signingQueryFlags(): Int =
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES

    private fun packageArchiveInfo(
        context: Context,
        apkFile: File,
        signingFlags: Boolean,
    ): PackageInfo? {
        val flags = if (signingFlags) signingQueryFlags() else 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }

    private fun installedPackageInfo(context: Context, packageName: String): PackageInfo? {
        val flags = signingQueryFlags()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, flags)
            }
        }.getOrNull()
    }
}
