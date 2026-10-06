package vad.dashing.tbox.phoneble

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.esp.EspCompanionProtocol

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhoneBleCodecTest {
    private val id = byteArrayOf(0x0A, 0x1B, 0x2C, 0x3D)
    private val key = ByteArray(16) { (it + 1).toByte() }

    @Test
    fun pairRoundTrip_keepsKeyAndRussianName() {
        val packet = PhoneBleCodec.pairPacket(id, key, "Телефон")
        assertTrue(packet.size <= 47)
        val material = PhoneBleCodec.parsePair(packet)
        assertNotNull(material)
        assertTrue(key.contentEquals(material!!.key))
        assertTrue(id.contentEquals(material.id))
        assertEquals("Телефон", material.name)
    }

    @Test
    fun pairLongName_truncatedOnUtf8Boundary() {
        val name = "Я".repeat(20)
        val packet = PhoneBleCodec.pairPacket(id, key, name)
        val material = PhoneBleCodec.parsePair(packet)
        assertEquals("Я".repeat(13), material!!.name)
        assertTrue(key.contentEquals(material.key))
    }

    @Test
    fun sealedCommand_roundTrip_andRejectsWrongKey() {
        val body = PhoneBleCodec.commandBody(PhoneBleCodec.OP_TEMP_LEFT, 0, 230)
        val packet = PhoneBleCodec.seal(key, PhoneBleCodec.TYPE_CMD, id, 7, body)
        assertEquals(PhoneBleCodec.PAYLOAD_MAX, packet.size)
        assertEquals(PhoneBleCodec.SEALED_LEN, packet.size)
        val open = PhoneBleCodec.open(key, packet)
        assertNotNull(open)
        assertEquals(7L, open!!.counter)
        assertEquals(PhoneBleCodec.OP_TEMP_LEFT, PhoneBleCodec.readCommand(open.body)!!.op)
        assertEquals(230, PhoneBleCodec.readCommand(open.body)!!.arg)
        val other = key.copyOf()
        other[0] = 0
        assertNull(PhoneBleCodec.open(other, packet))
        assertTrue(PhoneBleCodec.counterAccepted(0, 7))
        assertFalse(PhoneBleCodec.counterAccepted(7, 7))
    }

    @Test
    fun snapshotPages_roundTrip() {
        val snap = PhoneBleCodec.Snapshot(
            leftTenths = 230,
            rightTenths = 225,
            fan = 3,
            mode = 2,
            auto = 1,
            blow = 1,
            sync = 0,
            seats = listOf(2, 1, 1, 1),
            volume = 10,
            gen = 4,
        )
        var merged = PhoneBleCodec.Snapshot()
        PhoneBleCodec.snapshotBodies(4, snap).forEach { body ->
            val packet = PhoneBleCodec.seal(key, PhoneBleCodec.TYPE_SNAP, id, 9, body)
            merged = PhoneBleCodec.overlaySnapshot(merged, PhoneBleCodec.open(key, packet)!!.body)
        }
        assertEquals(230, merged.leftTenths)
        assertEquals(225, merged.rightTenths)
        assertEquals(3, merged.fan)
        assertEquals(2, merged.mode)
        assertEquals(1, merged.auto)
        assertEquals(0, merged.sync)
        assertEquals(listOf(2, 1, 1, 1), merged.seats)
        assertEquals(10, merged.volume)
        assertEquals(4, merged.gen)
    }

    @Test
    fun newGenerationKeepsFieldsOfPagesNotYetReceived() {
        val old = PhoneBleCodec.Snapshot(
            leftTenths = 230,
            fan = 3,
            mode = 2,
            seats = listOf(2, 1, 1, 1),
            volume = 10,
            gen = 4,
        )
        val next = PhoneBleCodec.Snapshot(leftTenths = 235, fan = 4, gen = 5)
        val page0 = PhoneBleCodec.snapshotBodies(5, next)[0]
        val merged = PhoneBleCodec.overlaySnapshot(old, page0)
        assertEquals(235, merged.leftTenths)
        assertEquals(4, merged.fan)
        assertEquals(2, merged.mode)
        assertEquals(listOf(2, 1, 1, 1), merged.seats)
        assertEquals(10, merged.volume)
        assertEquals(5, merged.gen)
    }

    @Test
    fun textHash_matchesFirmwareVectors() {
        assertEquals(0, PhoneBleCodec.textHash(ByteArray(0)))
        assertEquals(0xCD20, PhoneBleCodec.textHash("a".toByteArray()))
        assertEquals(0x5D1C, PhoneBleCodec.textHash(PhoneBleCodec.textBytes("Песня", "Artist")))
    }

    @Test
    fun nowPlaying_assemblesTextAndSkipsItWhenPhoneHasIt() {
        val title = "Очень длинное название песни, чтобы не влезть"
        val snap = PhoneBleCodec.Snapshot(
            volume = 7,
            playing = 1,
            positionMs = 61_500,
            durationMs = 215_000,
            title = title,
            artist = "Исполнитель",
        )
        val assembler = PhoneBleCodec.SnapshotAssembler()
        val bodies = PhoneBleCodec.snapshotBodies(3, snap)
        assertTrue(bodies.size > 5)
        bodies.forEach { body ->
            val type = PhoneBleCodec.snapType(body[0].toInt())
            val packet = PhoneBleCodec.seal(key, type, id, 9, body)
            val open = PhoneBleCodec.open(key, packet)!!
            assertTrue(PhoneBleCodec.isSnapType(open.type))
            assembler.accept(open.type, open.body)
        }
        val got = assembler.snapshot
        assertEquals(1, got.playing)
        assertEquals(61_000L, got.positionMs)
        assertEquals(215_000L, got.durationMs)
        assertEquals(PhoneBleCodec.clipText(title, PhoneBleCodec.TITLE_MAX), got.title)
        assertEquals("Исполнитель", got.artist)
        assertEquals(7, got.volume)
        assertTrue(got.textHash != 0)

        val again = PhoneBleCodec.snapshotBodies(4, snap.copy(playing = 0), got.textHash)
        assertEquals(PhoneBleCodec.PAGE_MEDIA + 1, again.size)
        again.forEach { assembler.accept(PhoneBleCodec.snapType(it[0].toInt()), it) }
        assertEquals(0, assembler.snapshot.playing)
        assertEquals("Исполнитель", assembler.snapshot.artist)
    }

    @Test
    fun nowPlaying_keepsOldTextUntilAllPagesArrive_andClearsOnEmpty() {
        val assembler = PhoneBleCodec.SnapshotAssembler()
        fun feed(bodies: List<ByteArray>) =
            bodies.forEach { assembler.accept(PhoneBleCodec.snapType(it[0].toInt()), it) }
        feed(PhoneBleCodec.snapshotBodies(1, PhoneBleCodec.Snapshot(title = "Old", artist = "A")))
        assertEquals("Old", assembler.snapshot.title)

        val next = PhoneBleCodec.snapshotBodies(2, PhoneBleCodec.Snapshot(title = "New song title here"))
        feed(next.dropLast(1))
        assertEquals("Old", assembler.snapshot.title)
        feed(next.takeLast(1))
        assertEquals("New song title here", assembler.snapshot.title)
        assertNull(assembler.snapshot.artist)

        feed(PhoneBleCodec.snapshotBodies(3, PhoneBleCodec.Snapshot(playing = 0)))
        assertNull(assembler.snapshot.title)
        assertEquals(0, assembler.snapshot.textHash)
    }

    @Test
    fun nowPlaying_rejectsPageSealedUnderAnotherType() {
        val assembler = PhoneBleCodec.SnapshotAssembler()
        val media = PhoneBleCodec.snapshotBodies(1, PhoneBleCodec.Snapshot(playing = 1))[PhoneBleCodec.PAGE_MEDIA]
        assembler.accept(PhoneBleCodec.TYPE_SNAP, media)
        assertNull(assembler.snapshot.playing)
        assembler.accept(PhoneBleCodec.snapType(PhoneBleCodec.PAGE_MEDIA), media)
        assertEquals(1, assembler.snapshot.playing)
    }

    @Test
    fun clipText_dropsControlCharsAndCutsOnUtf8Boundary() {
        assertEquals("ab", PhoneBleCodec.clipText("a\nb", 10))
        assertEquals("Я".repeat(5), PhoneBleCodec.clipText("Я".repeat(10), 11))
    }

    @Test
    fun hostMapsSignalsAndVolumeCommand() {
        val hu = JSONObject(
            """
            {"signals":[
              {"id":"hvac_temperature_left","available":true,"value":23.0},
              {"id":"hvac_temperature_right","available":true,"value":22.5},
              {"id":"hvac_fan_speed","available":true,"value":3},
              {"id":"hvac_auto","available":true,"value":"on"},
              {"id":"hvac_sync","available":true,"value":"off"},
              {"id":"hvac_fan_direction","available":true,"value":"face"},
              {"id":"hvac_custom_mode","available":true,"value":"comfort"},
              {"id":"front_left_seat_mode","available":true,"value":"heat_1"},
              {"id":"front_right_seat_mode","available":true,"value":"off"},
              {"id":"rear_left_seat_mode","available":true,"value":"off"},
              {"id":"rear_right_seat_mode","available":true,"value":"off"}
            ]}
            """.trimIndent(),
        )
        val app = JSONObject(
            """
            {"signals":[
              {"id":"hu_media_volume","available":true,"value":11},
              {"id":"media_title","available":true,"value":"Song \"1\"/2"},
              {"id":"media_artist","available":true,"value":"Band"},
              {"id":"media_playing","available":true,"value":"on"},
              {"id":"media_position_ms","available":true,"value":12345.0},
              {"id":"media_duration_ms","available":true,"value":200000.0}
            ]}
            """.trimIndent(),
        )
        val snap = PhoneCompanionHost.snapshotFromSignals(hu, app)
        assertEquals(230, snap.leftTenths)
        assertEquals(225, snap.rightTenths)
        assertEquals(2, snap.mode)
        assertEquals(1, snap.auto)
        assertEquals(0, snap.sync)
        assertEquals(2, snap.seats[0])
        assertEquals(11, snap.volume)
        assertEquals(1, snap.playing)
        assertEquals(12_345L, snap.positionMs)
        assertEquals(200_000L, snap.durationMs)
        assertEquals("Song \"1\"/2", snap.title)
        assertEquals("Band", snap.artist)
        val line = EspCompanionProtocol.encodePhoneSnap(4, snap)
        assertTrue(line.contains("\"t\":\"phoneSnap\""))
        assertTrue(line.contains("\"left\":230"))
        assertTrue(line.contains("\"play\":1"))
        assertTrue(line.contains("\"pos\":12345"))
        assertTrue(line.contains("\"dur\":200000"))
        assertEquals("Song \"1\"/2", JSONObject(line).getString("title"))
        assertEquals("Band", JSONObject(line).getString("artist"))
        assertFalse(line.contains("key"))
        val action = PhoneCompanionHost.toAction(PhoneBleCodec.OP_VOLUME, 0, 0)
        val builtin = action as AutomationAction.Builtin
        assertEquals(AutomationBuiltinActionType.SET_MEDIA_VOLUME, builtin.type)
        assertEquals(0, builtin.intValue)
    }

    @Test
    fun hostRejectsOutOfRangeArguments() {
        assertNotNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_TEMP_LEFT, 0, 225))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_TEMP_LEFT, 0, 227))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_TEMP_RIGHT, 0, 310))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_FAN, 0, 8))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_BLOW, 0, 0))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_MODE, 0, 4))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_AUTO, 0, 2))
        assertNotNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_SEAT, 0, 7))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_SEAT, 2, 5))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_SEAT, 4, 1))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_VOLUME, 0, 32))
        assertNull(PhoneCompanionHost.toAction(PhoneBleCodec.OP_VOLUME, 0, -1))
        assertNull(PhoneCompanionHost.toAction(99, 0, 0))
    }
}
