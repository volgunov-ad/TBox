package vad.dashing.store.install

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApkVerifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var debugCert: X509Certificate

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val keystore = sequenceOf(
            File("keystore/debug.keystore"),
            File("../keystore/debug.keystore"),
            File("../../keystore/debug.keystore"),
        ).map { it.canonicalFile }.firstOrNull { it.isFile }
            ?: error("repo keystore/debug.keystore not found from ${File(".").canonicalPath}")
        debugCert = loadCert(keystore, "androiddebugkey")
    }

    @Test
    fun sha256MatchesFileContents() {
        val file = tmp.newFile("plain.bin")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val digest = ApkVerifier.sha256Hex(file)
        assertEquals(64, digest.length)
        assertTrue(ApkVerifier.verifySha256(file, digest))
        assertTrue(ApkVerifier.verifySha256(file, " ${digest.uppercase()} "))
        assertFalse(ApkVerifier.verifySha256(file, "00".repeat(32)))
    }

    @Test
    fun archiveQueryAsksForGetSignaturesSoApi28CollectsCertificates() {
        @Suppress("DEPRECATION")
        assertNotEquals(0, ApkVerifier.SIGNING_QUERY_FLAGS and PackageManager.GET_SIGNATURES)
        assertNotEquals(0, ApkVerifier.SIGNING_QUERY_FLAGS and PackageManager.GET_SIGNING_CERTIFICATES)
    }

    @Test
    fun legacySignaturesGiveTheRepoDebugCertificate() {
        val info = PackageInfo().apply {
            @Suppress("DEPRECATION")
            signatures = arrayOf(Signature(debugCert.encoded))
        }
        val signer = ApkVerifier.signerOf(info) ?: error("signer expected")
        // `apksigner verify --print-certs` digest of keystore/debug.keystore (catalog pin format).
        assertEquals("50b797117600a81686afe549e01131eb236f60b7d693422b2423805a18a8b2ad", signer.current)
        assertEquals(setOf(signer.current), signer.lineage)
    }

    @Test
    fun rotatedSignerReportsCurrentCertificateNotTheOldest() {
        val oldCert = generateCert("old")
        val info = PackageInfo().apply {
            signingInfo = rotatedSigningInfo(current = debugCert, history = listOf(oldCert, debugCert))
        }
        val signer = ApkVerifier.signerOf(info) ?: error("signer expected")
        assertEquals(sha256(debugCert), signer.current)
        assertEquals(setOf(sha256(oldCert), sha256(debugCert)), signer.lineage)
    }

    @Test
    fun noSigningDataGivesNoSigner() {
        assertNull(ApkVerifier.signerOf(PackageInfo()))
    }

    @Test
    fun lineageDecidesWhetherAnUpdateIsAccepted() {
        val old = ApkSigner("aa", setOf("aa"))
        val rotated = ApkSigner("bb", setOf("aa", "bb"))
        val other = ApkSigner("cc", setOf("cc"))
        assertTrue(old.sharesLineageWith(ApkSigner("aa", setOf("aa"))))
        assertTrue(rotated.sharesLineageWith(old))
        assertTrue(old.sharesLineageWith(rotated))
        assertFalse(other.sharesLineageWith(old))
        assertFalse(old.sharesLineageWith(other))
    }

    @Test
    fun installedSignerReadsTheInstalledPackage() {
        val packageName = "vad.dashing.test.signed"
        val info = PackageInfo().apply {
            this.packageName = packageName
            applicationInfo = ApplicationInfo().also { it.packageName = packageName }
            @Suppress("DEPRECATION")
            signatures = arrayOf(Signature(debugCert.encoded))
        }
        shadowOf(context.packageManager).installPackage(info)
        assertEquals(sha256(debugCert), ApkVerifier.installedSigner(context, packageName)?.current)
        assertNull(ApkVerifier.installedSigner(context, "vad.dashing.test.absent"))
        assertNull(ApkVerifier.installedSigner(context, ""))
    }

    private fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    private fun loadCert(keystore: File, alias: String): X509Certificate {
        val store = KeyStore.getInstance("PKCS12")
        keystore.inputStream().use { store.load(it, "android".toCharArray()) }
        return store.getCertificate(alias) as X509Certificate
    }

    private fun generateCert(alias: String): X509Certificate {
        val keystore = File(tmp.root, "$alias.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool")
            .takeIf { it.isFile }?.absolutePath ?: "keytool"
        val proc = ProcessBuilder(
            keytool, "-genkeypair",
            "-keystore", keystore.absolutePath, "-storetype", "PKCS12",
            "-storepass", "android", "-keypass", "android",
            "-alias", alias, "-keyalg", "EC", "-validity", "1", "-dname", "CN=$alias",
        ).redirectErrorStream(true).start()
        assertTrue("keytool timed out", proc.waitFor(60, TimeUnit.SECONDS))
        assertEquals(
            "keytool failed: ${proc.inputStream.bufferedReader().readText()}",
            0,
            proc.exitValue(),
        )
        return loadCert(keystore, alias)
    }

    /** History is oldest first, like `signingCertificateHistory` under v3 key rotation. */
    private fun rotatedSigningInfo(current: X509Certificate, history: List<X509Certificate>): SigningInfo {
        val signingInfo = Shadow.newInstanceOf(SigningInfo::class.java)
        Shadow.extract<ShadowSigningInfo>(signingInfo).apply {
            setSignatures(arrayOf(Signature(current.encoded)))
            setPastSigningCertificates(history.map { Signature(it.encoded) }.toTypedArray())
        }
        return signingInfo
    }
}
