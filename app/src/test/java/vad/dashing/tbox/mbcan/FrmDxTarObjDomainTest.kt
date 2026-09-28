package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrmDxTarObjDomainTest {
    @Test
    fun decode_emitsForObjValidOneOrTwo() {
        assertEquals(42, FrmDxTarObjDomain.decode(42, 1))
        assertEquals(0, FrmDxTarObjDomain.decode(0, 1))
        assertEquals(3, FrmDxTarObjDomain.decode(3, 2))
        assertEquals(1, FrmDxTarObjDomain.decode(1, 2))
        assertNull(FrmDxTarObjDomain.decode(42, 0))
        assertNull(FrmDxTarObjDomain.decode(42, 3))
        assertNull(FrmDxTarObjDomain.decode(42, null))
        assertNull(FrmDxTarObjDomain.decode(null, 1))
        assertNull(FrmDxTarObjDomain.decode(null, 2))
    }

    @Test
    fun isObjValidForUi_matchesDecodeGate() {
        assertTrue(FrmDxTarObjDomain.isObjValidForUi(1))
        assertTrue(FrmDxTarObjDomain.isObjValidForUi(2))
        assertFalse(FrmDxTarObjDomain.isObjValidForUi(0))
        assertFalse(FrmDxTarObjDomain.isObjValidForUi(null))
    }
}
