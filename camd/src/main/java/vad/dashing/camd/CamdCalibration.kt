package vad.dashing.camd

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CamdStockView(
    val sourceId: Int,
    val viewId: Int,
    val inputId: Int,
    val cameraIndex: Int,
    val mirror: Boolean,
    val triangleCount: Int,
    val positions: FloatArray,
    val uvs: FloatArray,
)

class CamdCalibration(
    val views: List<CamdStockView>,
) {
    fun bySource(sourceId: Int): CamdStockView? = views.firstOrNull { it.sourceId == sourceId }

    companion object {
        const val DIR = "/vendor/etc/mbhal/avm/original_ds"
        const val FIRST_SOURCE_ID = 40
        val VIEW_INPUTS = intArrayOf(8, 5, 9, 4, 8, 9)
        val VIEW_CAMERAS = intArrayOf(0, 1, 2, 3, 0, 2)
        private const val CONFIG_LINE = 88
        private const val CONFIG_FIELDS = 13
        private const val VALUE_LIMIT = 4f

        fun parse(cameraPara: String, readMesh: (String) -> ByteArray?): CamdCalibration? {
            val lines = cameraPara.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < CONFIG_LINE + 6) return null
            val views = ArrayList<CamdStockView>(6)
            for (i in 0 until 6) {
                val fields = lines[CONFIG_LINE + i].split(Regex("\\s+"))
                if (fields.size < CONFIG_FIELDS) return null
                val camera = fields[0].toIntOrNull() ?: return null
                if (camera != VIEW_CAMERAS[i]) return null
                val mirror = when (fields[12]) {
                    "0" -> false
                    "1" -> true
                    else -> return null
                }
                val positions = decode(readMesh("camarray_vert${i + 1}"), 9) ?: return null
                val uvs = decode(readMesh("camarray_text${i + 1}"), 6) ?: return null
                val triangleCount = positions.size / 9
                if (triangleCount == 0 || triangleCount != uvs.size / 6) return null
                if (!finiteWithin(positions, VALUE_LIMIT) || !finiteWithin(uvs, VALUE_LIMIT)) return null
                views.add(
                    CamdStockView(
                        sourceId = FIRST_SOURCE_ID + i,
                        viewId = i + 1,
                        inputId = VIEW_INPUTS[i],
                        cameraIndex = camera,
                        mirror = mirror,
                        triangleCount = triangleCount,
                        positions = positions,
                        uvs = uvs,
                    )
                )
            }
            return CamdCalibration(views)
        }

        fun load(dir: File): CamdCalibration? = try {
            parse(dir.resolve("camera_para").readText()) { name ->
                val file = dir.resolve(name)
                if (file.isFile) file.readBytes() else null
            }
        } catch (_: Exception) {
            null
        }

        private fun decode(bytes: ByteArray?, floatsPerTriangle: Int): FloatArray? {
            if (bytes == null || bytes.isEmpty() || bytes.size % 4 != 0) return null
            val floats = FloatArray(bytes.size / 4)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
            if (floats.size % floatsPerTriangle != 0) return null
            return floats
        }

        private fun finiteWithin(values: FloatArray, limit: Float): Boolean {
            for (value in values) {
                if (!value.isFinite() || value < -limit || value > limit) return false
            }
            return true
        }
    }
}
