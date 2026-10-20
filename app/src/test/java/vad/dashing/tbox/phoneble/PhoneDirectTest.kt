package vad.dashing.tbox.phoneble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhoneDirectTest {
    private val id = byteArrayOf(0x0A, 0x1B, 0x2C, 0x3D)
    private val key = ByteArray(16) { (it + 1).toByte() }
    private val idHex = PhoneBleCodec.idHex(id)

    private fun pairedHost(counter: Long = 0L): PhoneDirectHost =
        PhoneDirectHost(listOf(PhoneDirectRecord(id, key, "Pixel", counter)))

    private fun refresh(counter: Long, groups: Int = 0): ByteArray =
        PhoneBleCodec.seal(key, PhoneBleCodec.TYPE_REFRESH, id, counter, PhoneBleCodec.refreshBody(0, groups))

    @Test
    fun frameReader_splitsConcatenatedAndPartialFrames() {
        val first = PhoneBleCodec.pairPacket(id, key, "Телефон")
        val second = refresh(5)
        val stream = PhoneDirectWire.frame(first) + PhoneDirectWire.frame(second)
        val reader = PhoneDirectWire.FrameReader()
        val out = mutableListOf<ByteArray>()
        // Odd chunks: a frame boundary falls inside a read.
        stream.toList().chunked(7).forEach { chunk -> out += reader.feed(chunk.toByteArray()) }
        assertEquals(2, out.size)
        assertTrue(first.contentEquals(out[0]))
        assertTrue(second.contentEquals(out[1]))
        assertFalse(reader.broken)
    }

    @Test
    fun frameReader_respectsCount() {
        val payload = refresh(1)
        val buffer = PhoneDirectWire.frame(payload) + ByteArray(10) { 0x55 }
        val reader = PhoneDirectWire.FrameReader()
        val out = reader.feed(buffer, payload.size + 1)
        assertEquals(1, out.size)
        assertFalse(reader.broken)
    }

    @Test
    fun frameReader_foreignStreamIsBroken() {
        val reader = PhoneDirectWire.FrameReader()
        assertTrue(reader.feed("AT+BRSF=1\r".toByteArray()).isEmpty())
        assertTrue(reader.broken)
        assertTrue(reader.feed(PhoneDirectWire.frame(refresh(1))).isEmpty())

        val zero = PhoneDirectWire.FrameReader()
        zero.feed(byteArrayOf(0))
        assertTrue(zero.broken)
    }

    @Test
    fun pairFrameFitsLimit() {
        val packet = PhoneBleCodec.pairPacket(id, key, "Я".repeat(40))
        assertEquals(packet.size + 1, PhoneDirectWire.frame(packet).size)
    }

    @Test
    fun pair_needsOpenWindowAndAllow() {
        val host = PhoneDirectHost(emptyList())
        val pair = PhoneBleCodec.pairPacket(id, key, "Pixel")
        assertEquals(PhoneDirectHost.Result.Ignored, host.onPayload(pair))

        host.beginLearn()
        assertEquals(PhoneDirectHost.Result.PairRequest(idHex, "Pixel"), host.onPayload(pair))
        assertEquals(PhoneDirectHost.Result.Accepted, host.onPayload(pair))
        assertTrue(host.allow(idHex))
        assertFalse(host.learning)
        assertEquals(1, host.records().size)
        assertTrue(host.onPayload(refresh(1)) is PhoneDirectHost.Result.Refresh)
    }

    @Test
    fun pair_deniedIdIgnoredUntilWindowReopens() {
        val host = PhoneDirectHost(emptyList())
        val pair = PhoneBleCodec.pairPacket(id, key, "Pixel")
        host.beginLearn()
        host.onPayload(pair)
        host.deny(idHex)
        assertEquals(PhoneDirectHost.Result.Ignored, host.onPayload(pair))
        assertFalse(host.allow(idHex))

        host.beginLearn()
        assertTrue(host.onPayload(pair) is PhoneDirectHost.Result.PairRequest)
    }

    @Test
    fun repair_sameKeyKeepsCounter_newKeyResetsIt() {
        val host = pairedHost(counter = 40)
        host.beginLearn()
        assertEquals(PhoneDirectHost.Result.Accepted, host.onPayload(PhoneBleCodec.pairPacket(id, key, "Pixel")))

        val newKey = ByteArray(16) { (it + 100).toByte() }
        assertTrue(host.onPayload(PhoneBleCodec.pairPacket(id, newKey, "Pixel")) is PhoneDirectHost.Result.PairRequest)
        assertTrue(host.allow(idHex))
        assertEquals(0L, host.records().single().counter)
        assertTrue(newKey.contentEquals(host.records().single().key))
    }

    @Test
    fun sealed_rejectsReplayUnknownIdAndWrongKey() {
        val host = pairedHost(counter = 10)
        assertEquals(PhoneDirectHost.Result.Ignored, host.onPayload(refresh(10)))
        val ok = host.onPayload(refresh(11, PhoneBleCodec.GROUP_CLIMATE))
        assertEquals(PhoneDirectHost.Result.Refresh(idHex, 11, 0, PhoneBleCodec.GROUP_CLIMATE), ok)
        assertEquals(PhoneDirectHost.Result.Ignored, host.onPayload(refresh(11)))
        assertTrue(host.takeCountersDirty())
        assertFalse(host.takeCountersDirty())

        val stranger = byteArrayOf(1, 2, 3, 4)
        val foreign = PhoneBleCodec.seal(key, PhoneBleCodec.TYPE_REFRESH, stranger, 50, PhoneBleCodec.refreshBody())
        assertEquals(PhoneDirectHost.Result.Ignored, host.onPayload(foreign))

        val wrongKey = ByteArray(16)
        val forged = PhoneBleCodec.seal(wrongKey, PhoneBleCodec.TYPE_REFRESH, id, 60, PhoneBleCodec.refreshBody())
        assertEquals(PhoneDirectHost.Result.Ignored, host.onPayload(forged))
    }

    @Test
    fun command_decoded() {
        val host = pairedHost()
        val packet = PhoneBleCodec.seal(
            key,
            PhoneBleCodec.TYPE_CMD,
            id,
            3,
            PhoneBleCodec.commandBody(PhoneBleCodec.OP_FAN, 0, 4),
        )
        assertEquals(PhoneDirectHost.Result.Command(idHex, PhoneBleCodec.OP_FAN, 0, 4), host.onPayload(packet))
    }

    @Test
    fun sealSnapshot_opensOnPhoneWithRequestCounter() {
        val host = pairedHost()
        val snap = PhoneBleCodec.Snapshot(leftTenths = 215, fan = 3, outsideTenths = -45, title = "Song")
        val pages = host.sealSnapshot(idHex, 7, 9, snap, phoneTextHash = 0, groups = 0)
        assertTrue(pages.isNotEmpty())
        val assembler = PhoneBleCodec.SnapshotAssembler()
        var last: PhoneBleCodec.Snapshot? = null
        for (page in pages) {
            assertTrue(page.size <= PhoneDirectWire.PAYLOAD_MAX)
            val open = PhoneBleCodec.open(key, page)
            assertNotNull(open)
            assertEquals(7L, open!!.counter)
            assertTrue(PhoneBleCodec.isSnapType(open.type))
            last = assembler.accept(open.type, open.body)
        }
        assertEquals(215, last!!.leftTenths)
        assertEquals(3, last.fan)
        assertEquals(-45, last.outsideTenths)
        assertEquals("Song", last.title)
        assertTrue(host.sealSnapshot("ffffffff", 7, 9, snap, 0, 0).isEmpty())
    }

    @Test
    fun json_roundTripSkipsBrokenEntries() {
        val records = listOf(PhoneDirectRecord(id, key, "Pixel «7»", 123))
        val json = PhoneDirectHost.toJson(records)
        val back = PhoneDirectHost.fromJson(json)
        assertEquals(1, back.size)
        assertEquals(idHex, back[0].idHex)
        assertTrue(key.contentEquals(back[0].key))
        assertEquals("Pixel «7»", back[0].name)
        assertEquals(123L, back[0].counter)

        val broken = """[{"id":"zz","key":"AA=="},{"id":"0a1b2c3d","key":"AAAA"}]"""
        assertTrue(PhoneDirectHost.fromJson(broken).isEmpty())
        assertTrue(PhoneDirectHost.fromJson(null).isEmpty())
        assertTrue(PhoneDirectHost.fromJson("not json").isEmpty())
    }
}
