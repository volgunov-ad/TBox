package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.adb.AdbAuthKeys
import vad.dashing.tbox.adb.AdbConnection
import vad.dashing.tbox.adb.AdbProtocol
import vad.dashing.tbox.adb.AdbShellV2
import vad.dashing.tbox.adb.AdbTransport
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbConnectionAuthTest {

    @Test
    fun connect_withoutAuth_parsesBannerAndFeatures() {
        val transport = FakeTransport(
            packet(
                AdbProtocol.CMD_CNXN,
                AdbProtocol.VERSION,
                4096,
                "device::ro.product.name=x;features=shell_v2,cmd\u0000".toByteArray(),
            ),
        )
        val connection = AdbConnection(transport, AdbAuthKeys.generateKeyPair(), "tbox@test")
        val info = connection.connect()

        assertEquals(4096, info.maxPayload)
        assertTrue("shell_v2" in info.features)
        assertTrue("cmd" in info.features)
        assertEquals(AdbProtocol.CMD_CNXN, writtenHeaders(transport)[0].command)
        assertTrue(writtenPayloads(transport)[0].toString(Charsets.UTF_8).contains("features=shell_v2"))
    }

    @Test
    fun connect_modernPeerUsesZeroChecksumsAfterCnxn() {
        val remoteId = 4
        val transport = FakeTransport(
            packet(
                AdbProtocol.CMD_CNXN,
                AdbProtocol.VERSION,
                4096,
                "device::features=cmd\u0000".toByteArray(),
                useChecksum = false,
            ) +
                packet(AdbProtocol.CMD_OKAY, remoteId, 1, useChecksum = false) +
                packet(AdbProtocol.CMD_WRTE, remoteId, 1, "ok".toByteArray(), useChecksum = false) +
                packet(AdbProtocol.CMD_CLSE, remoteId, 1, useChecksum = false),
        )
        val connection = AdbConnection(transport, AdbAuthKeys.generateKeyPair(), "tbox@test")
        connection.connect()
        assertEquals("ok", connection.execute("id").stdout)

        val headers = writtenHeaders(transport)
        assertTrue(headers.first().dataCheck != 0)
        assertTrue(headers.drop(1).all { it.dataCheck == 0 })
    }

    @Test
    fun connect_authenticatesWithSignatureThenPublicKey() {
        val keyPair = AdbAuthKeys.generateKeyPair()
        val firstToken = ByteArray(20) { it.toByte() }
        val secondToken = ByteArray(20) { (it + 20).toByte() }
        val transport = FakeTransport(
            packet(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TYPE_TOKEN, 0, firstToken) +
                packet(AdbProtocol.CMD_AUTH, AdbProtocol.AUTH_TYPE_TOKEN, 0, secondToken) +
                packet(AdbProtocol.CMD_CNXN, AdbProtocol.VERSION, 4096, "device::features=shell_v2\u0000".toByteArray()),
        )
        val connection = AdbConnection(transport, keyPair, "tbox@test")
        connection.connect()

        val headers = writtenHeaders(transport)
        val payloads = writtenPayloads(transport)
        assertEquals(listOf(AdbProtocol.CMD_CNXN, AdbProtocol.CMD_AUTH, AdbProtocol.CMD_AUTH), headers.map { it.command })
        assertEquals(AdbProtocol.AUTH_TYPE_SIGNATURE, headers[1].arg0)
        assertTrue(AdbAuthKeys.verifyTokenSignature(keyPair.public, firstToken, payloads[1]))
        assertEquals(AdbProtocol.AUTH_TYPE_RSAPUBLICKEY, headers[2].arg0)
        assertTrue(payloads[2].toString(Charsets.UTF_8).endsWith(" tbox@test\u0000"))
    }

    @Test
    fun execute_shellV2_collectsStdoutStderrAndExit() {
        val remoteId = 9
        val v2Payload = shellChunk(AdbShellV2.CHUNK_STDOUT, "hello\n".toByteArray()) +
            shellChunk(AdbShellV2.CHUNK_STDERR, "warning\n".toByteArray()) +
            shellChunk(AdbShellV2.CHUNK_EXIT, byteArrayOf(7))
        val transport = FakeTransport(
            packet(AdbProtocol.CMD_CNXN, AdbProtocol.VERSION, 4096, "device::features=shell_v2\u0000".toByteArray()) +
                packet(AdbProtocol.CMD_OKAY, remoteId, 1) +
                packet(AdbProtocol.CMD_WRTE, remoteId, 1, v2Payload) +
                packet(AdbProtocol.CMD_CLSE, remoteId, 1),
            maxRead = 3,
        )
        val connection = AdbConnection(transport, AdbAuthKeys.generateKeyPair(), "tbox@test")
        connection.connect()
        val result = connection.execute("id")

        assertTrue(result.shellV2)
        assertEquals("hello\n", result.stdout)
        assertEquals("warning\n", result.stderr)
        assertEquals(7, result.exitCode)
        val headers = writtenHeaders(transport)
        val payloads = writtenPayloads(transport)
        assertEquals(AdbProtocol.CMD_OPEN, headers[1].command)
        assertEquals("shell,v2:id\u0000", payloads[1].toString(Charsets.UTF_8))
        assertEquals(AdbProtocol.CMD_OKAY, headers[2].command)
        assertEquals(1, headers[2].arg0)
        assertEquals(remoteId, headers[2].arg1)
        assertEquals(AdbProtocol.CMD_CLSE, headers[3].command)
    }

    @Test
    fun execute_withoutShellV2_usesLegacyService() {
        val transport = FakeTransport(
            packet(AdbProtocol.CMD_CNXN, AdbProtocol.VERSION, 4096, "device::features=cmd\u0000".toByteArray()) +
                packet(AdbProtocol.CMD_OKAY, 4, 1) +
                packet(AdbProtocol.CMD_WRTE, 4, 1, "legacy output".toByteArray()) +
                packet(AdbProtocol.CMD_CLSE, 4, 1),
        )
        val connection = AdbConnection(transport, AdbAuthKeys.generateKeyPair(), "tbox@test")
        connection.connect()
        val result = connection.execute("getprop")

        assertFalse(result.shellV2)
        assertEquals("legacy output", result.stdout)
        assertEquals("", result.stderr)
        assertEquals(null, result.exitCode)
        assertEquals("shell:getprop\u0000", writtenPayloads(transport)[1].toString(Charsets.UTF_8))
    }

    @Test
    fun execute_shellV2Rejected_fallsBackToLegacy() {
        val transport = FakeTransport(
            packet(AdbProtocol.CMD_CNXN, AdbProtocol.VERSION, 4096, "device::features=shell_v2\u0000".toByteArray()) +
                packet(AdbProtocol.CMD_CLSE, 0, 1) +
                packet(AdbProtocol.CMD_OKAY, 5, 2) +
                packet(AdbProtocol.CMD_WRTE, 5, 2, "fallback".toByteArray()) +
                packet(AdbProtocol.CMD_CLSE, 5, 2),
        )
        val connection = AdbConnection(transport, AdbAuthKeys.generateKeyPair(), "tbox@test")
        connection.connect()
        val result = connection.execute("uname")

        assertFalse(result.shellV2)
        assertEquals("fallback", result.stdout)
        val payloads = writtenPayloads(transport)
        assertEquals("shell,v2:uname\u0000", payloads[1].toString(Charsets.UTF_8))
        assertEquals("shell:uname\u0000", payloads[3].toString(Charsets.UTF_8))
    }

    private fun packet(
        command: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray = ByteArray(0),
        useChecksum: Boolean = true,
    ): ByteArray = AdbProtocol.encode(command, arg0, arg1, payload, useChecksum)

    private fun shellChunk(id: Int, data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(id.toByte())
        buffer.putInt(data.size)
        buffer.put(data)
        return buffer.array()
    }

    private fun writtenHeaders(transport: FakeTransport) = transport.writes.map { AdbProtocol.decodeHeader(it) }

    private fun writtenPayloads(transport: FakeTransport) = transport.writes.map {
        val header = AdbProtocol.decodeHeader(it)
        it.copyOfRange(AdbProtocol.HEADER_SIZE, AdbProtocol.HEADER_SIZE + header.dataLength)
    }

    private class FakeTransport(
        private val incoming: ByteArray,
        private val maxRead: Int = Int.MAX_VALUE,
    ) : AdbTransport {
        override val description: String = "fake"
        val writes = ArrayList<ByteArray>()
        private var position = 0
        private var closed = false

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position == incoming.size) return -1
            val count = minOf(length, maxRead, incoming.size - position)
            System.arraycopy(incoming, position, buffer, offset, count)
            position += count
            return count
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            writes.add(buffer.copyOfRange(offset, offset + length))
        }

        override fun close() {
            closed = true
        }
    }
}
