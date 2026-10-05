package vad.dashing.tbox.adb

import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object AdbAuthKeys {

    const val MODULUS_SIZE_BYTES = 256
    const val ENCODED_SIZE = 4 + 4 + MODULUS_SIZE_BYTES + MODULUS_SIZE_BYTES + 4

    private val two32 = BigInteger.ONE.shiftLeft(32)

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    fun modulusAndExponent(publicKey: PublicKey): Pair<BigInteger, BigInteger> {
        val spec = KeyFactory.getInstance("RSA").getKeySpec(publicKey, RSAPublicKeySpec::class.java)
        return spec.modulus to spec.publicExponent
    }

    fun signToken(privateKey: PrivateKey, token: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(privateKey)
        signature.update(token)
        return signature.sign()
    }

    fun verifyTokenSignature(publicKey: PublicKey, token: ByteArray, signatureBytes: ByteArray): Boolean {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initVerify(publicKey)
        signature.update(token)
        return signature.verify(signatureBytes)
    }

    fun androidPublicKey(n: BigInteger, exponent: BigInteger): ByteArray {
        require(n.signum() > 0 && n.bitLength() <= 2048)
        require(exponent.signum() > 0 && exponent.bitLength() <= 32)
        val rr = BigInteger.valueOf(2).pow(4096).mod(n)
        val n0 = n.mod(two32)
        val n0inv = n0.modInverse(two32).negate().mod(two32)
        val buffer = ByteBuffer.allocate(ENCODED_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(64)
        buffer.putInt(n0inv.toInt())
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(toBigEndianBytes(n, MODULUS_SIZE_BYTES))
        buffer.put(toBigEndianBytes(rr, MODULUS_SIZE_BYTES))
        buffer.putInt(exponent.toInt())
        return buffer.array()
    }

    fun androidPublicKeyPayload(publicKey: PublicKey, name: String): ByteArray {
        require(name.isNotEmpty() && !name.contains('\u0000'))
        val encoded = Base64.getEncoder().encodeToString(androidPublicKey(publicKey))
        return (encoded + " " + name + "\u0000").toByteArray(Charsets.UTF_8)
    }

    private fun androidPublicKey(publicKey: PublicKey): ByteArray {
        val (n, e) = modulusAndExponent(publicKey)
        return androidPublicKey(n, e)
    }

    fun encodePrivateKey(privateKey: PrivateKey): String =
        Base64.getEncoder().encodeToString(privateKey.encoded)

    fun encodePublicKey(publicKey: PublicKey): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    fun decodePrivateKey(text: String): PrivateKey =
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(text.trim())))

    fun decodePublicKey(text: String): PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(text.trim())))

    fun loadOrCreate(directory: File): KeyPair {
        val keyFile = File(directory, "adb_key")
        val pubFile = File(directory, "adb_key.pub")
        if (keyFile.isFile && pubFile.isFile) {
            try {
                val publicKey = decodePublicKey(pubFile.readText())
                val privateKey = decodePrivateKey(keyFile.readText())
                return KeyPair(publicKey, privateKey)
            } catch (ignored: Exception) {
            }
        }
        val keyPair = generateKeyPair()
        directory.mkdirs()
        keyFile.writeText(encodePrivateKey(keyPair.private))
        pubFile.writeText(encodePublicKey(keyPair.public))
        return keyPair
    }

    private fun toBigEndianBytes(value: BigInteger, size: Int): ByteArray {
        var bytes = value.toByteArray()
        if (bytes.size > size) {
            check(bytes.size == size + 1 && bytes[0].toInt() == 0)
            bytes = bytes.copyOfRange(1, bytes.size)
        }
        val out = ByteArray(size)
        System.arraycopy(bytes, 0, out, size - bytes.size, bytes.size)
        return out
    }
}
