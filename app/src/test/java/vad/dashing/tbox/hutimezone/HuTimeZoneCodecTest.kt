package vad.dashing.tbox.hutimezone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuTimeZoneCodecTest {
    @Test
    fun parsesSingleLineStringReply() {
        val out = "Result: Parcel(00000000 00000003 00520050 00000043 '........P.R.C...')"
        assertEquals("PRC", HuTimeZoneCodec.parseString(out))
    }

    @Test
    fun parsesMultiLineStringReply() {
        val out = """
            Result: Parcel(
              0x00000000: 00000000 0000000d 00750045 006f0072 '........E.u.r.o.'
              0x00000010: 00650070 004d002f 0073006f 006f0063 'p.e./.M.o.s.c.o.'
              0x00000020: 00000077                            'w...            ')
        """.trimIndent()
        assertEquals("Europe/Moscow", HuTimeZoneCodec.parseString(out))
    }

    @Test
    fun parsesIntReplyAndRejectsGarbage() {
        assertEquals(0, HuTimeZoneCodec.parseInt("Result: Parcel(00000000 00000000   '........')"))
        assertNull(HuTimeZoneCodec.parseString("service: Service adayo.setting.v2.0 does not exist"))
        assertNull(HuTimeZoneCodec.parseInt("Result: Parcel(fffffffc 00000000 '........')"))
    }

    @Test
    fun writeCommandAcceptsOnlyZoneIds() {
        assertEquals(
            "service call adayo.setting.v2.0 15 s16 Europe/Moscow",
            HuTimeZoneCodec.writeCommand("Europe/Moscow"),
        )
        assertTrue(runCatching { HuTimeZoneCodec.writeCommand("Europe/Moscow; reboot") }.isFailure)
    }
}
