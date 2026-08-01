package vad.dashing.tbox.drsensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UdsSensorParserTest {

    @Test
    fun parsesGyroAndAccelLines() {
        val raw = """
            ${'$'}GYR,123.5,36.1,0.01,-0.02,0.03
            ${'$'}A3D,124.0,1.1,2.2,3.3
        """.trimIndent().replace("\n", "\r\n")
        val r = UdsSensorParser.parse(raw)
        assertEquals(1, r.gyros.size)
        assertEquals(123.5, r.gyros[0].timestamp, 1e-6)
        assertEquals(36.1f, r.gyros[0].temperature, 1e-3f)
        assertEquals(0.01f, r.gyros[0].pitch, 1e-3f)
        assertEquals(-0.02f, r.gyros[0].yaw, 1e-3f)
        assertEquals(0.03f, r.gyros[0].roll, 1e-3f)
        assertEquals(1, r.accels.size)
        assertEquals(1.1f, r.accels[0].pitch, 1e-3f)
        assertEquals(2.2f, r.accels[0].yaw, 1e-3f)
        assertEquals(3.3f, r.accels[0].roll, 1e-3f)
    }

    @Test
    fun ignoresJunk() {
        val r = UdsSensorParser.parse("hello\r\n\$GYR,1\r\n")
        assertTrue(r.gyros.isEmpty())
        assertTrue(r.accels.isEmpty())
    }

    @Test
    fun keepsLastOfMultipleGyros() {
        val raw = "\$GYR,1,0,0,0,0\r\n\$GYR,2,1,2,3,4\r\n"
        val r = UdsSensorParser.parse(raw)
        assertEquals(2, r.gyros.size)
        assertEquals(3f, r.gyros.last().yaw, 0f)
    }
}
