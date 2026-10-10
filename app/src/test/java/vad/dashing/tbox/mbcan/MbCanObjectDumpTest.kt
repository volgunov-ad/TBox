package vad.dashing.tbox.mbcan

import com.mengbo.mbCan.entity.MBCanLightStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MbCanObjectDumpTest {

    private class Outer(
        val nMode: Byte,
        val stLightSts: MBCanLightStatus,
        val raw: IntArray,
        val name: String?,
    )

    private fun light() = MBCanLightStatus(2, 1, 0, 1, 1, 2, 2, 0)

    @Test
    fun `flatten lists every MBCanLightStatus field`() {
        val fields = MbCanObjectDump.flatten(light()).toMap()
        assertEquals("2", fields["nHighBeamSts"])
        assertEquals("1", fields["nLowBeamSts"])
        assertEquals("0", fields["nHazardLightSts"])
        assertEquals("1", fields["nRearFogLightSts"])
        assertEquals("1", fields["nFrontFogLightSts"])
        assertEquals("2", fields["nParkTailLightSts"])
        assertEquals("2", fields["nDRLSts"])
        assertEquals("0", fields["nRev"])
        assertEquals(8, fields.size)
    }

    @Test
    fun `nested structs use dotted paths, primitive arrays one value`() {
        val fields = MbCanObjectDump.flatten(Outer(3, light(), intArrayOf(1, 2, 3), null)).toMap()
        assertEquals("3", fields["nMode"])
        assertEquals("2", fields["stLightSts.nHighBeamSts"])
        assertEquals("2", fields["stLightSts.nDRLSts"])
        assertEquals("[1,2,3]", fields["raw"])
        assertEquals("null", fields["name"])
    }

    @Test
    fun `flattenArgs inlines a single struct and numbers primitive args`() {
        val single = MbCanObjectDump.flattenArgs(arrayOf(light())).toMap()
        assertEquals("2", single["nHighBeamSts"])

        val prims = MbCanObjectDump.flattenArgs(arrayOf(12.5, 3.toByte()))
        assertEquals(listOf("arg0" to "12.5", "arg1" to "3"), prims)

        assertTrue(MbCanObjectDump.flattenArgs(null).isEmpty())
    }

    @Test
    fun `leaf object is reported as value`() {
        assertEquals(listOf("value" to "42"), MbCanObjectDump.flatten(42))
    }
}
