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
        val pages = PhoneBleCodec.pairPages(id, key, "Телефон")
        assertEquals(2, pages.size)
        assertTrue(pages.all { it.size <= PhoneBleCodec.PAYLOAD_MAX })
        val material = PhoneBleCodec.assemblePair(pages.map { PhoneBleCodec.parsePairPage(it)!! })
        assertNotNull(material)
        assertTrue(key.contentEquals(material!!.key))
        assertTrue(id.contentEquals(material.id))
        assertEquals("Телефон", material.name)
    }

    @Test
    fun pairLongName_roundTripOutOfOrder() {
        val name = "Я".repeat(13)
        val pages = PhoneBleCodec.pairPages(id, key, name)
        assertEquals(3, pages.size)
        assertTrue(pages.all { it.size <= PhoneBleCodec.PAYLOAD_MAX })
        val parsed = pages.map { PhoneBleCodec.parsePairPage(it)!! }.reversed()
        val material = PhoneBleCodec.assemblePair(parsed)
        assertEquals(name, material!!.name)
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
            """{"signals":[{"id":"hu_media_volume","available":true,"value":11}]}""",
        )
        val snap = PhoneCompanionHost.snapshotFromSignals(hu, app)
        assertEquals(230, snap.leftTenths)
        assertEquals(225, snap.rightTenths)
        assertEquals(2, snap.mode)
        assertEquals(1, snap.auto)
        assertEquals(0, snap.sync)
        assertEquals(2, snap.seats[0])
        assertEquals(11, snap.volume)
        val line = EspCompanionProtocol.encodePhoneSnap(4, snap)
        assertTrue(line.contains("\"t\":\"phoneSnap\""))
        assertTrue(line.contains("\"left\":230"))
        assertFalse(line.contains("key"))
        val action = PhoneCompanionHost.toAction(PhoneBleCodec.OP_VOLUME, 0, 0)
        val builtin = action as AutomationAction.Builtin
        assertEquals(AutomationBuiltinActionType.SET_MEDIA_VOLUME, builtin.type)
        assertEquals(0, builtin.intValue)
    }
}
