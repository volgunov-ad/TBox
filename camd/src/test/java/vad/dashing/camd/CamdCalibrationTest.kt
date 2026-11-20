package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CamdCalibrationTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun parsesDeviceCameraPara() {
        val cal = CamdCalibration.parse(DEVICE_CAMERA_PARA) { name -> meshBytes(name) }
        assertNotNull(cal)
        cal!!
        assertEquals(listOf(40, 41, 42, 43, 44, 45), cal.views.map { it.sourceId })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), cal.views.map { it.viewId })
        assertEquals(listOf(8, 5, 9, 4, 8, 9), cal.views.map { it.inputId })
        assertEquals(listOf(0, 1, 2, 3, 0, 2), cal.views.map { it.cameraIndex })
        assertEquals(listOf(false, false, true, false, false, true), cal.views.map { it.mirror })
        cal.views.forEach { view ->
            assertEquals(2, view.triangleCount)
            assertEquals(18, view.positions.size)
            assertEquals(12, view.uvs.size)
        }
        assertEquals(8, cal.bySource(40)?.inputId)
        assertEquals(true, cal.bySource(42)?.mirror)
        assertEquals(true, cal.bySource(45)?.mirror)
        assertNull(cal.bySource(8))
        assertNull(cal.bySource(39))
        assertNull(cal.bySource(46))
    }

    @Test
    fun toleratesTrailingSpacesAndBlankLines() {
        val text = "\n" + DEVICE_CAMERA_PARA.lines().joinToString("\r\n") { "  $it  " } + "\n\n"
        val cal = CamdCalibration.parse(text) { name -> meshBytes(name) }
        assertNotNull(cal)
        assertEquals(6, cal!!.views.size)
    }

    @Test
    fun rejectsWrongCameraInConfigLine() {
        val lines = DEVICE_CAMERA_PARA.lines().toMutableList()
        lines[89] = "4 2 0 0 0 175 228 915 514 1280 800 1 0 4 1 "
        assertNull(CamdCalibration.parse(lines.joinToString("\n")) { name -> meshBytes(name) })
    }

    @Test
    fun rejectsBadMirrorFlag() {
        val lines = DEVICE_CAMERA_PARA.lines().toMutableList()
        lines[90] = "2 2 0 0.02 0 255 130 775 584 1280 800 1 2 4 1 "
        assertNull(CamdCalibration.parse(lines.joinToString("\n")) { name -> meshBytes(name) })
    }

    @Test
    fun rejectsShortConfigLine() {
        val lines = DEVICE_CAMERA_PARA.lines().toMutableList()
        lines[92] = "0 2 0.05 -0.15 0 148 0 934 720 1280 800 1 "
        assertNull(CamdCalibration.parse(lines.joinToString("\n")) { name -> meshBytes(name) })
    }

    @Test
    fun rejectsMissingMesh() {
        assertNull(
            CamdCalibration.parse(DEVICE_CAMERA_PARA) { name ->
                if (name == "camarray_text3") null else meshBytes(name)
            }
        )
    }

    @Test
    fun rejectsTriangleCountMismatch() {
        val cal = CamdCalibration.parse(DEVICE_CAMERA_PARA) { name ->
            if (name == "camarray_text1") meshBytes(*QUAD_UVS, *QUAD_UVS.take(6).toFloatArray()) else meshBytes(name)
        }
        assertNull(cal)
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeValues() {
        val nan = CamdCalibration.parse(DEVICE_CAMERA_PARA) { name ->
            if (name == "camarray_vert2") meshBytes(*FloatArray(18) { Float.NaN }) else meshBytes(name)
        }
        assertNull(nan)
        val huge = CamdCalibration.parse(DEVICE_CAMERA_PARA) { name ->
            if (name == "camarray_text6") meshBytes(*FloatArray(12) { 100f }) else meshBytes(name)
        }
        assertNull(huge)
    }

    @Test
    fun rejectsOddByteCounts() {
        val odd = CamdCalibration.parse(DEVICE_CAMERA_PARA) { name ->
            if (name == "camarray_vert1") ByteArray(37) else meshBytes(name)
        }
        assertNull(odd)
    }

    @Test
    fun loadsFromDirectory() {
        val dir = temp.newFolder("original_ds")
        dir.resolve("camera_para").writeText(DEVICE_CAMERA_PARA)
        for (i in 1..6) {
            dir.resolve("camarray_vert$i").writeBytes(meshBytes("camarray_vert$i")!!)
            dir.resolve("camarray_text$i").writeBytes(meshBytes("camarray_text$i")!!)
        }
        val cal = CamdCalibration.load(dir)
        assertNotNull(cal)
        assertEquals(6, cal!!.views.size)
        assertTrue(cal.views[2].mirror)

        dir.resolve("camarray_vert4").writeBytes(ByteArray(35))
        assertNull(CamdCalibration.load(dir))
        assertNull(CamdCalibration.load(temp.newFolder("empty")))
    }

    private fun meshBytes(name: String): ByteArray = when {
        name.startsWith("camarray_vert") -> meshBytes(*QUAD_POSITIONS)
        else -> meshBytes(*QUAD_UVS)
    }

    private fun meshBytes(vararg floats: Float): ByteArray {
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    companion object {
        private val QUAD_POSITIONS = floatArrayOf(
            -1f, -1f, 0f,
            1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, -1f, 0f,
            1f, 1f, 0f,
            -1f, 1f, 0f,
        )
        private val QUAD_UVS = floatArrayOf(
            0f, 1f,
            1f, 1f,
            1f, 0f,
            0f, 1f,
            1f, 0f,
            0f, 0f,
        )

        private val DEVICE_CAMERA_PARA = """
            -0.018759
            0.446153
            -0.487079
            -1.09988
            -0.0123818
            -0.0174583
            0
            0
            0
            0
            128 0 640
            0 128 400
            0 0 1
            -0.018759
            0.446153
            -0.487079
            -1.09988
            -0.0123818
            -0.0174583
            128
            3800 6960 6960 -1900 3480
            0 0
            0.12545
            0.447544
            -0.0173719
            -0.80647
            -0.0165762
            -0.0237657
            0
            0
            0
            0
            128 0 640
            0 128 400
            0 0 1
            0.12545
            0.447544
            -0.0173719
            -0.80647
            -0.0165762
            -0.0237657
            128
            6960 3800 6960 1900 3480
            0 0
            -0.00350195
            0.601676
            -0.408388
            -0.993711
            0.00333805
            0.00828108
            0
            0
            0
            0
            128 0 640
            0 128 400
            0 0 1
            -0.00350195
            0.601676
            -0.408388
            -0.993711
            0.00333805
            0.00828108
            128
            3800 6960 6960 1900 -3480
            0 0
            -0.138615
            0.423747
            0.00609236
            -0.845744
            0.0196433
            0.000524056
            0
            0
            0
            0
            128 0 640
            0 128 400
            0 0 1
            -0.138615
            0.423747
            0.00609236
            -0.845744
            0.0196433
            0.000524056
            128
            6960 3800 6960 -1900 -3480
            0 0
            0 2 0 -0.01 -0.02 250 100 820 580 1280 800 1 0 4 1
            1 2 0 0 0 175 228 915 514 1280 800 1 0 4 1
            2 2 0 0.02 0 255 130 775 584 1280 800 1 1 4 1
            3 2 0.05 0 0 161 248 915 514 1280 800 1 0 4 1
            0 2 0.05 -0.15 0 148 0 934 720 1280 800 1 0 4 1
            2 2 0.05 -0.05 0 206 88 928 604 1280 800 1 1 4 1
        """.trimIndent()
    }
}
