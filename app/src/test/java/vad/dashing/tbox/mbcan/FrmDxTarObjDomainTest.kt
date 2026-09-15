package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrmDxTarObjDomainTest {
    @Test
    fun decode_requiresObjValidOne() {
        assertEquals(42, FrmDxTarObjDomain.decode(42, 1))
        assertEquals(0, FrmDxTarObjDomain.decode(0, 1))
        assertNull(FrmDxTarObjDomain.decode(42, 0))
        assertNull(FrmDxTarObjDomain.decode(42, 2))
        assertNull(FrmDxTarObjDomain.decode(42, null))
        assertNull(FrmDxTarObjDomain.decode(null, 1))
    }
}
