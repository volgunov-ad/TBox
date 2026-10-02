package vad.dashing.tbox.uda

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdaProtocolTest {
    @Test
    fun buildDiagReq_readDtc_layout() {
        val payload = byteArrayOf(0x12, 0x34)
        val buf = UdaProtocol.buildDiagReq(
            type = UdaProtocol.DiagType.ReadDtc,
            payload = payload,
            ecuParam = 0x01020304,
        )
        assertEquals(UdaProtocol.DIAG_HEADER_SIZE + 2, buf.size)
        assertEquals(0, buf[UdaProtocol.DIAG_OFF_TYPE].toInt())
        assertEquals(4, buf[UdaProtocol.DIAG_OFF_FLAGS].toInt() and 0xFF)
        assertEquals(0x04, buf[UdaProtocol.DIAG_OFF_ECU_PARAM].toInt() and 0xFF)
        assertEquals(0x03, buf[UdaProtocol.DIAG_OFF_ECU_PARAM + 1].toInt() and 0xFF)
        assertEquals(2, buf[UdaProtocol.DIAG_OFF_DATA_LEN].toInt() and 0xFF)
        assertArrayEquals(payload, buf.copyOfRange(UdaProtocol.DIAG_OFF_PAYLOAD, buf.size))
        val path = buf.copyOfRange(0, UdaProtocol.DEFAULT_CFG_SO_PATH.length)
            .toString(Charsets.UTF_8)
        assertEquals(UdaProtocol.DEFAULT_CFG_SO_PATH, path)
    }

    @Test
    fun buildClearDtc_typeAndLen() {
        val buf = UdaProtocol.buildClearDtcProbe()
        assertEquals(1, buf[UdaProtocol.DIAG_OFF_TYPE].toInt())
        assertEquals(3, buf[UdaProtocol.DIAG_OFF_DATA_LEN].toInt() and 0xFF)
        assertEquals(UdaProtocol.DIAG_HEADER_SIZE + 3, buf.size)
    }

    @Test
    fun crtVctrlFrame_sizeAndOpcode() {
        val frame = CrtVctrlProtocol.buildFrame(0x1B, byteArrayOf(1, 2, 3))
        assertEquals(CrtVctrlProtocol.FRAME_SIZE, frame.size)
        assertEquals(0x1B, frame[0].toInt() and 0xFF)
        assertEquals(1, frame[1].toInt())
        assertEquals(2, frame[2].toInt())
        assertEquals(3, frame[3].toInt())
    }

    @Test
    fun crtVctrl_frontLight_aliasesHistoricalOpenClose() {
        val off = CrtVctrlProtocol.buildLockClose()
        val on = CrtVctrlProtocol.buildLockOpen()
        assertEquals(CrtVctrlProtocol.FRONT_LIGHT, off[0].toInt() and 0xFF)
        assertEquals(0, off[CrtVctrlProtocol.OFF_PARAM0].toInt())
        assertEquals(CrtVctrlProtocol.FRONT_LIGHT, on[0].toInt() and 0xFF)
        assertEquals(1, on[CrtVctrlProtocol.OFF_PARAM0].toInt())
        assertEquals("FRONT_LIGHT", CrtVctrlProtocol.nameOf(CrtVctrlProtocol.FRONT_LIGHT))
        assertEquals("LOCK", CrtVctrlProtocol.nameOf(CrtVctrlProtocol.LOCK))
        assertEquals("ENGINE", CrtVctrlProtocol.nameOf(CrtVctrlProtocol.ENGINE))
    }

    @Test
    fun crtVctrl_appPackedContainsFrontLightNotLock() {
        assertTrue(CrtVctrlProtocol.FRONT_LIGHT in CrtVctrlProtocol.APP_PACKED_OPCODES)
        assertTrue(CrtVctrlProtocol.LOCK !in CrtVctrlProtocol.APP_PACKED_OPCODES)
        assertTrue(CrtVctrlProtocol.ENGINE !in CrtVctrlProtocol.APP_PACKED_OPCODES)
        assertTrue(CrtVctrlProtocol.ENGINE in CrtVctrlProtocol.ISVALID_REJECTS_NAMED)
        assertEquals(48, CrtVctrlProtocol.NAMES.size)
        assertTrue(CrtVctrlProtocol.FRONT_LIGHT in CrtVctrlProtocol.MCU_ACTIVE_OPCODES)
        assertTrue(CrtVctrlProtocol.LOCK !in CrtVctrlProtocol.MCU_ACTIVE_OPCODES)
        assertEquals(0x183, CrtVctrlProtocol.MCU_FRONT_LIGHT_COM_ID)
        assertEquals(0x39, CrtVctrlProtocol.MCU_FRONT_LIGHT_COM_IPDU)
        assertEquals(0x315, CrtVctrlProtocol.MCU_FRONT_LIGHT_CAN_ID)
        assertEquals(10, CrtVctrlProtocol.MCU_FRONT_LIGHT_BIT_START)
        assertEquals(2, CrtVctrlProtocol.MCU_FRONT_LIGHT_BIT_LEN)
        assertEquals(1, CrtVctrlProtocol.MCU_FRONT_LIGHT_VAL_ON)
        assertEquals(2, CrtVctrlProtocol.MCU_FRONT_LIGHT_VAL_OFF)
        assertEquals("FRONT_LIGHT", CrtVctrlProtocol.MCU_FRONT_LIGHT_SIGNAL_NAME)
    }
}
