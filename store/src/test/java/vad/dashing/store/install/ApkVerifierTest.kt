package vad.dashing.store.install

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApkVerifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var signedApk: File
    private lateinit var certSha256: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val keystore = sequenceOf(
            File("keystore/debug.keystore"),
            File("../keystore/debug.keystore"),
            File("../../keystore/debug.keystore"),
        ).map { it.canonicalFile }.firstOrNull { it.isFile }
            ?: error("repo keystore/debug.keystore not found from ${File(".").canonicalPath}")
        signedApk = buildSignedApk(keystore)
        certSha256 = ApkVerifier.signingCertSha256(context, signedApk)
            ?: error("expected readable signing cert")
    }

    @Test
    fun sha256MatchesFileContents() {
        val file = tmp.newFile("plain.bin")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val digest = ApkVerifier.sha256Hex(file)
        assertEquals(64, digest.length)
        assertTrue(ApkVerifier.verifySha256(file, digest))
        assertTrue(ApkVerifier.verifySha256(file, digest.uppercase()))
        assertFalse(ApkVerifier.verifySha256(file, "00".repeat(32)))
    }

    @Test
    fun signingCertSha256IsLowercaseHex64() {
        assertEquals(64, certSha256.length)
        assertTrue(certSha256.all { it in '0'..'9' || it in 'a'..'f' })
        // Repo debug.keystore androiddebugkey SHA-256 (no colons).
        assertEquals(
            "50b797117600a81686afe549e01131eb236f60b7d693422b2423805a18a8b2ad",
            certSha256,
        )
    }

    @Test
    fun signingCertReadableWhenPackageManagerPathUsed() {
        // Robolectric + signed jar: either PM or Jar fallback must return the debug cert.
        assertNotNull(ApkVerifier.signingCertSha256(context, signedApk))
    }

    private fun buildSignedApk(keystore: File): File {
        val unsigned = tmp.newFile("unsigned.apk")
        // Minimal ZIP (APK) with an empty classes.dex placeholder entry name.
        java.util.zip.ZipOutputStream(unsigned.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            zip.write(byteArrayOf(0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"))
            zip.write(ByteArray(32) { 0 })
            zip.closeEntry()
        }
        val signed = File(tmp.root, "signed.apk")
        unsigned.copyTo(signed, overwrite = true)
        val jarsigner = findJdkBinary("jarsigner")
        val proc = ProcessBuilder(
            jarsigner,
            "-keystore", keystore.absolutePath,
            "-storepass", "android",
            "-keypass", "android",
            signed.absolutePath,
            "androiddebugkey",
        ).redirectErrorStream(true).start()
        val finished = proc.waitFor(60, TimeUnit.SECONDS)
        assertTrue("jarsigner timed out", finished)
        assertEquals(
            "jarsigner failed: ${proc.inputStream.bufferedReader().readText()}",
            0,
            proc.exitValue(),
        )
        return signed
    }

    private fun findJdkBinary(name: String): String {
        val javaHome = System.getProperty("java.home")
        val candidates = listOf(
            File(javaHome, "bin/$name"),
            File(javaHome, "../bin/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.absolutePath
            ?: name
    }
}
