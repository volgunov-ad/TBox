package vad.dashing.voice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import vad.dashing.voice.api.CatalogWriteOption
import vad.dashing.voice.api.CatalogWriteSchema

class RelativeAdjustSolverTest {
    private val hvacWrite = CatalogWriteSchema(
        kind = CatalogWriteSchema.KIND_NUMBER,
        options = listOf(
            CatalogWriteOption("21.5", 215),
            CatalogWriteOption("22.0", 220),
            CatalogWriteOption("22.5", 225),
            CatalogWriteOption("23.0", 230),
        ),
        min = 15.5,
        max = 32.5,
        step = 0.5,
        unit = "°C",
    )

    private val volumeWrite = CatalogWriteSchema(
        kind = CatalogWriteSchema.KIND_NUMBER,
        options = emptyList(),
        min = 0.0,
        max = 31.0,
        step = 1.0,
    )

    @Test
    fun warmerPicksNextHalfDegree() {
        val solved = RelativeAdjustSolver.relative(current = 22.0, write = hvacWrite, steps = +1)
        assertNotNull(solved)
        assertEquals("22.5", solved!!.state)
        assertEquals(225, solved.invokeValue)
    }

    @Test
    fun coolerPicksPrevious() {
        val solved = RelativeAdjustSolver.relative(current = 22.5, write = hvacWrite, steps = -1)
        assertNotNull(solved)
        assertEquals("22.0", solved!!.state)
        assertEquals(220, solved.invokeValue)
    }

    @Test
    fun volumeQuieter() {
        val solved = RelativeAdjustSolver.relative(current = 15.0, write = volumeWrite, steps = -1)
        assertNotNull(solved)
        assertEquals(14, solved!!.invokeValue)
    }

    @Test
    fun volumeLouderClampsAtMax() {
        val solved = RelativeAdjustSolver.relative(current = 31.0, write = volumeWrite, steps = +1)
        assertNotNull(solved)
        assertEquals(31, solved!!.invokeValue)
    }

    @Test
    fun absoluteNearestOption() {
        val solved = RelativeAdjustSolver.absolute(22.4, hvacWrite)
        assertNotNull(solved)
        assertEquals("22.5", solved!!.state)
        assertEquals(225, solved.invokeValue)
    }

    @Test
    fun binaryOnOff() {
        val write = CatalogWriteSchema(
            kind = CatalogWriteSchema.KIND_BINARY,
            options = listOf(
                CatalogWriteOption("off", "off"),
                CatalogWriteOption("on", "on"),
            ),
        )
        assertEquals("on", RelativeAdjustSolver.binary(true, write)!!.invokeValue)
        assertEquals("off", RelativeAdjustSolver.binary(false, write)!!.invokeValue)
    }
}
