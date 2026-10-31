package vad.dashing.tbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.adb.AdbAuthKeys
import java.math.BigInteger
import java.nio.file.Files
import java.util.Base64

class AdbPubkeyFormatTest {

    private val keyPair = AdbAuthKeys.generateKeyPair()
    private val modulus: BigInteger
    private val exponent: BigInteger
    private val two32 = BigInteger.ONE.shiftLeft(32)

    init {
        val pair = AdbAuthKeys.modulusAndExponent(keyPair.public)
        modulus = pair.first
        exponent = pair.second
    }

    @Test
    fun androidPublicKey_layout() {
        val out = AdbAuthKeys.androidPublicKey(modulus, exponent)
        assertEquals(AdbAuthKeys.ENCODED_SIZE, out.size)
        assertEquals(524, out.size)

        val lenField = (out[0].toInt() and 0xFF) or
            ((out[1].toInt() and 0xFF) shl 8) or
            ((out[2].toInt() and 0xFF) shl 16) or
            ((out[3].toInt() and 0xFF) shl 24)
        assertEquals(64, lenField)

        assertEquals(2048, modulus.bitLength())

        val n0inv = BigInteger(1, out.copyOfRange(4, 8).reversed().toByteArray())
        val n0 = modulus.mod(two32)
        assertEquals(two32.subtract(BigInteger.ONE), n0.multiply(n0inv).mod(two32))

        assertEquals(modulus, BigInteger(1, out.copyOfRange(8, 264)))
        assertEquals(BigInteger.valueOf(2).pow(4096).mod(modulus), BigInteger(1, out.copyOfRange(264, 520)))

        assertArrayEquals(byteArrayOf(0, 1, 0, 1), out.copyOfRange(520, 524))
    }

    @Test
    fun androidPublicKeyPayload_format() {
        val payload = AdbAuthKeys.androidPublicKeyPayload(keyPair.public, "tbox@test")
        val text = payload.toString(Charsets.UTF_8)
        assertEquals(0, payload.last().toInt())
        assertFalse(text.dropLast(1).contains('\u0000'))
        val expectedBody = Base64.getEncoder().encodeToString(AdbAuthKeys.androidPublicKey(modulus, exponent))
        assertTrue(text.startsWith("$expectedBody tbox@test\u0000"))
        assertEquals(expectedBody.length + 1 + "tbox@test".length + 1, payload.size)

        val decoded = Base64.getDecoder().decode(text.substringBefore(" "))
        assertArrayEquals(AdbAuthKeys.androidPublicKey(modulus, exponent), decoded)
    }

    @Test
    fun signToken_sha1Rsa() {
        val token = ByteArray(20) { it.toByte() }
        val signature = AdbAuthKeys.signToken(keyPair.private, token)
        assertTrue(AdbAuthKeys.verifyTokenSignature(keyPair.public, token, signature))
        assertFalse(AdbAuthKeys.verifyTokenSignature(keyPair.public, ByteArray(20) { (it + 1).toByte() }, signature))
    }

    @Test
    fun loadOrCreate_generatesPersistsAndReloads() {
        val dir = Files.createTempDirectory("adbkeys").toFile()
        val first = AdbAuthKeys.loadOrCreate(dir)
        val second = AdbAuthKeys.loadOrCreate(dir)
        assertEquals(
            AdbAuthKeys.modulusAndExponent(first.public).first,
            AdbAuthKeys.modulusAndExponent(second.public).first,
        )
        assertTrue(dir.resolve("adb_key").isFile)
        assertTrue(dir.resolve("adb_key.pub").isFile)
    }

    @Test
    fun loadOrCreate_regeneratesOnCorruptFiles() {
        val dir = Files.createTempDirectory("adbkeys_bad").toFile()
        val first = AdbAuthKeys.loadOrCreate(dir)
        dir.resolve("adb_key").writeText("not base64 !!!")
        val second = AdbAuthKeys.loadOrCreate(dir)
        assertNotEquals(
            AdbAuthKeys.modulusAndExponent(first.public).first,
            AdbAuthKeys.modulusAndExponent(second.public).first,
        )
        val third = AdbAuthKeys.loadOrCreate(dir)
        assertTrue(third.private.encoded.isNotEmpty())
    }
}
