package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CamdSourcesTest {

    @Test
    fun `stock range is 40 until 46`() {
        assertEquals(40..45, CamdSources.STOCK_IDS)
        assertFalse(CamdSources.isStock(39))
        assertTrue(CamdSources.isStock(40))
        assertTrue(CamdSources.isStock(45))
        assertFalse(CamdSources.isStock(46))
    }

    @Test
    fun `stock ids map to calibrated physical inputs`() {
        assertEquals(8, CamdSources.physicalInput(40))
        assertEquals(5, CamdSources.physicalInput(41))
        assertEquals(9, CamdSources.physicalInput(42))
        assertEquals(4, CamdSources.physicalInput(43))
        assertEquals(8, CamdSources.physicalInput(44))
        assertEquals(9, CamdSources.physicalInput(45))
    }

    @Test
    fun `raw ids keep their own input`() {
        listOf(0, 1, 3, 4, 5, 8, 9).forEach { id ->
            assertEquals(id, CamdSources.physicalInput(id))
        }
    }

    @Test
    fun `unknown ids have no physical input`() {
        assertNull(CamdSources.physicalInput(-1))
        assertNull(CamdSources.physicalInput(2))
        assertNull(CamdSources.physicalInput(39))
        assertNull(CamdSources.physicalInput(46))
        assertNull(CamdSources.physicalInput(99))
        assertFalse(CamdSources.isKnown(99))
        assertTrue(CamdSources.isKnown(42))
    }

    @Test
    fun `heldByAvm follows the physical input`() {
        assertTrue(CamdSources.heldByAvm(8))
        assertTrue(CamdSources.heldByAvm(40))
        assertTrue(CamdSources.heldByAvm(45))
        assertFalse(CamdSources.heldByAvm(0))
        assertFalse(CamdSources.heldByAvm(99))
    }

    @Test
    fun `camera resolves raw inputs unchanged`() {
        assertEquals("8 · перед", CamdSources.camera(8)!!.title)
        assertEquals(1280, CamdSources.camera(8)!!.width)
        assertEquals(800, CamdSources.camera(8)!!.height)
        assertNull(CamdSources.camera(39))
        assertNull(CamdSources.camera(46))
    }

    @Test
    fun `camera resolves stock view descriptors`() {
        assertEquals("40 · сток: перед", CamdSources.camera(40)!!.title)
        assertEquals("41 · сток: право", CamdSources.camera(41)!!.title)
        assertEquals("42 · сток: зад", CamdSources.camera(42)!!.title)
        assertEquals("43 · сток: лево", CamdSources.camera(43)!!.title)
        assertEquals("44 · сток: широкий перед", CamdSources.camera(44)!!.title)
        assertEquals("45 · сток: широкий зад", CamdSources.camera(45)!!.title)
        val front = CamdSources.camera(40)!!
        assertEquals(1280, front.width)
        assertEquals(800, front.height)
        assertTrue(front.heldByAvm)
    }

    @Test
    fun `ui list is raw inputs then stock views`() {
        val all = CamdSources.allSources()
        assertEquals(listOf(0, 1, 3, 4, 5, 8, 9, 40, 41, 42, 43, 44, 45), all.map { it.id })
        assertEquals(13, all.size)
    }
}
