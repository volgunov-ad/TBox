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

/** Signing identity of an APK or an installed package (SHA-256 of certificates, lowercase hex). */
data class ApkSigner(
    /** Certificate of the current signer. */
    val current: String,
    /** [current] plus the past certificates its key rotation lineage vouches for. */
    val lineage: Set<String>,
) {
    /**
     * True for the same key, a rotated key whose lineage proves the other one, or a rollback.
     * The system installer refuses an update between unrelated keys.
     */
    fun sharesLineageWith(other: ApkSigner): Boolean = lineage.any { it in other.lineage }
}

object ApkVerifier {
    /**
     * On API 28–29 (Jetour head units) `getPackageArchiveInfo` collects certificates only when
     * GET_SIGNATURES is set; with GET_SIGNING_CERTIFICATES alone `signingInfo` stays null.
     */
    @Suppress("DEPRECATION")
    internal val SIGNING_QUERY_FLAGS: Int =
        PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES

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
        val archiveInfo = packageArchiveInfo(context, apkFile, flags = 0) ?: return false
        return archiveInfo.packageName == expectedPackageName
    }

    /**
     * Signer of [apkFile] after PackageManager verified its signature; null when it cannot.
     * Reads the whole APK: call off the main thread.
     */
    fun apkSigner(context: Context, apkFile: File): ApkSigner? =
        packageArchiveInfo(context, apkFile, SIGNING_QUERY_FLAGS)?.let(::signerOf)

    /** Signer of an installed package, or null when it is not installed or unreadable. */
    fun installedSigner(context: Context, packageName: String): ApkSigner? {
        if (packageName.isBlank()) return null
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(SIGNING_QUERY_FLAGS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, SIGNING_QUERY_FLAGS)
            }
        }.getOrNull() ?: return null
        return signerOf(info)
    }

    /**
     * The current signer is `apkContentsSigners`: the first element of
     * `signingCertificateHistory` is the oldest certificate under key rotation.
     */
    internal fun signerOf(info: PackageInfo): ApkSigner? {
        val signingInfo = info.signingInfo
        if (signingInfo != null) {
            val current = signingInfo.apkContentsSigners.orEmpty().mapNotNull(::certSha256)
            val history = signingInfo.signingCertificateHistory.orEmpty().mapNotNull(::certSha256)
            val head = current.firstOrNull() ?: history.lastOrNull()
            if (head != null) return ApkSigner(head, (current + history).toSet())
        }
        @Suppress("DEPRECATION")
        val legacy = info.signatures.orEmpty().mapNotNull(::certSha256)
        val head = legacy.firstOrNull() ?: return null
        return ApkSigner(head, legacy.toSet())
    }

    private fun certSha256(signature: Signature): String? = runCatching {
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(signature.toByteArray())) as X509Certificate
        MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun packageArchiveInfo(context: Context, apkFile: File, flags: Int): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
}
