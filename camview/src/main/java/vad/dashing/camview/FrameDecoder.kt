package vad.dashing.camview

import android.graphics.Bitmap
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

enum class PixelDecodeMode(val label: String) {
    AUTO("Авто"),
    ARGB_8888("ARGB_8888 (copyPixels)"),
    RGB_565("RGB_565 (copyPixels)"),
    UYVY("UYVY 4:2:2"),
    YUYV("YUYV 4:2:2"),
    NV12("NV12 4:2:0"),
}

object FrameDecoder {

    private var reusableRawBuffer = ByteArray(0)
    private var reusablePixelBuffer = IntArray(0)

    private fun obtainRawBuffer(size: Int): ByteArray {
        if (reusableRawBuffer.size < size) {
            reusableRawBuffer = ByteArray(size)
        }
        return reusableRawBuffer
    }

    private fun obtainPixelBuffer(size: Int): IntArray {
        if (reusablePixelBuffer.size < size) {
            reusablePixelBuffer = IntArray(size)
        }
        return reusablePixelBuffer
    }

    @Synchronized
    fun decodeToBitmap(
        buf: ByteBuffer,
        width: Int,
        height: Int,
        stride: Int,
        imageSize: Int,
        colorFmt: Int,
        mode: PixelDecodeMode,
        reuseBitmap: Bitmap? = null
    ): Bitmap? {
        if (width <= 0 || height <= 0 || width > 4096 || height > 4096) return null
        val dup = buf.duplicate()
        dup.clear()
        val avail = dup.remaining()
        if (avail <= 0) return null

        val effectiveMode = if (mode != PixelDecodeMode.AUTO) {
            mode
        } else {
            val bpp = avail / (width * height)
            when {
                bpp >= 4 -> PixelDecodeMode.ARGB_8888
                colorFmt == 13 -> PixelDecodeMode.RGB_565
                colorFmt == 16 -> PixelDecodeMode.YUYV
                else -> PixelDecodeMode.RGB_565
            }
        }

        return try {
            when (effectiveMode) {
                PixelDecodeMode.ARGB_8888 -> {
                    val needed = width * height * 4
                    if (avail < needed) return decodeRgb565OrUyvyFallback(dup, width, height, reuseBitmap)
                    dup.limit(needed)
                    val bmp = obtainBitmap(reuseBitmap, width, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(dup)
                    bmp
                }
                PixelDecodeMode.RGB_565 -> {
                    val needed = width * height * 2
                    if (avail < needed) return null
                    dup.limit(needed)
                    val bmp = obtainBitmap(reuseBitmap, width, height, Bitmap.Config.RGB_565)
                    bmp.copyPixelsFromBuffer(dup)
                    bmp
                }
                PixelDecodeMode.UYVY -> decodeYuv422(dup, width, height, stride, uyvy = true, reuseBitmap = reuseBitmap)
                PixelDecodeMode.YUYV -> decodeYuv422(dup, width, height, stride, uyvy = false, reuseBitmap = reuseBitmap)
                PixelDecodeMode.NV12 -> decodeNv12(dup, width, height, stride, reuseBitmap = reuseBitmap)
                PixelDecodeMode.AUTO -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun obtainBitmap(
        candidate: Bitmap?,
        width: Int,
        height: Int,
        config: Bitmap.Config
    ): Bitmap {
        if (
            candidate != null &&
            !candidate.isRecycled &&
            candidate.isMutable &&
            candidate.width == width &&
            candidate.height == height &&
            candidate.config == config
        ) {
            return candidate
        }
        return Bitmap.createBitmap(width, height, config)
    }

    private fun decodeRgb565OrUyvyFallback(
        dup: ByteBuffer,
        width: Int,
        height: Int,
        reuseBitmap: Bitmap?
    ): Bitmap? {
        val needed = width * height * 2
        if (dup.remaining() < needed) return null
        dup.position(0)
        dup.limit(needed)
        val bmp = obtainBitmap(reuseBitmap, width, height, Bitmap.Config.RGB_565)
        bmp.copyPixelsFromBuffer(dup)
        return bmp
    }

    private fun decodeYuv422(
        dup: ByteBuffer,
        width: Int,
        height: Int,
        stride: Int,
        uyvy: Boolean,
        reuseBitmap: Bitmap?
    ): Bitmap? {
        val rowBytes = if (stride >= width * 2) stride else width * 2
        val needed = rowBytes * height
        if (dup.remaining() < width * height * 2) return null
        val copyBytes = minOf(dup.remaining(), needed)
        val raw = obtainRawBuffer(copyBytes)
        dup.get(raw, 0, copyBytes)
        val pixels = obtainPixelBuffer(width * height)
        var outIdx = 0
        for (y in 0 until height) {
            var inIdx = y * rowBytes
            var x = 0
            while (x < width && inIdx + 3 < copyBytes) {
                val b0 = raw[inIdx].toInt() and 0xFF
                val b1 = raw[inIdx + 1].toInt() and 0xFF
                val b2 = raw[inIdx + 2].toInt() and 0xFF
                val b3 = raw[inIdx + 3].toInt() and 0xFF
                val u: Int
                val y0: Int
                val v: Int
                val y1: Int
                if (uyvy) {
                    u = b0 - 128
                    y0 = b1
                    v = b2 - 128
                    y1 = b3
                } else {
                    y0 = b0
                    u = b1 - 128
                    y1 = b2
                    v = b3 - 128
                }
                pixels[outIdx++] = yuvToArgb(y0, u, v)
                if (x + 1 < width) {
                    pixels[outIdx++] = yuvToArgb(y1, u, v)
                }
                x += 2
                inIdx += 4
            }
        }
        val bmp = obtainBitmap(reuseBitmap, width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun decodeNv12(
        dup: ByteBuffer,
        width: Int,
        height: Int,
        stride: Int,
        reuseBitmap: Bitmap?
    ): Bitmap? {
        val rowStride = if (stride >= width) stride else width
        val ySize = rowStride * height
        val totalNeeded = ySize + rowStride * (height / 2)
        if (dup.remaining() < width * height * 3 / 2) return null
        val copyBytes = minOf(dup.remaining(), totalNeeded)
        val raw = obtainRawBuffer(copyBytes)
        dup.get(raw, 0, copyBytes)
        val pixels = obtainPixelBuffer(width * height)
        for (y in 0 until height) {
            val yRow = y * rowStride
            val uvRow = ySize + (y / 2) * rowStride
            var x = 0
            while (x < width) {
                val yIdx = yRow + x
                val uIdx = uvRow + (x and 1.inv())
                val vIdx = uIdx + 1
                val y0 = if (yIdx < copyBytes) raw[yIdx].toInt() and 0xFF else 0
                val u = (if (uIdx < copyBytes) raw[uIdx].toInt() and 0xFF else 128) - 128
                val v = (if (vIdx < copyBytes) raw[vIdx].toInt() and 0xFF else 128) - 128
                pixels[y * width + x] = yuvToArgb(y0, u, v)
                x++
            }
        }
        val bmp = obtainBitmap(reuseBitmap, width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    private fun yuvToArgb(y: Int, u: Int, v: Int): Int {
        val c = (y - 16).coerceAtLeast(0)
        val r = ((298 * c + 409 * v + 128) shr 8).coerceIn(0, 255)
        val g = ((298 * c - 100 * u - 208 * v + 128) shr 8).coerceIn(0, 255)
        val b = ((298 * c + 516 * u + 128) shr 8).coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun readShareFileDirect(
        path: String,
        width: Int,
        height: Int,
        headerOffset: Int,
        mode: PixelDecodeMode
    ): Pair<Bitmap?, String> {
        val f = File(path)
        if (!f.exists()) return null to "Файл не найден: $path"
        return try {
            RandomAccessFile(f, "r").use { raf ->
                val len = raf.length().toInt()
                if (len <= headerOffset) return null to "Малый размер: $len"
                val payloadLen = minOf(len - headerOffset, width * height * 4)
                val bytes = ByteArray(payloadLen)
                raf.seek(headerOffset.toLong())
                raf.readFully(bytes)
                val bb = ByteBuffer.wrap(bytes)
                val bmp = decodeToBitmap(
                    buf = bb,
                    width = width,
                    height = height,
                    stride = width * 2,
                    imageSize = payloadLen,
                    colorFmt = 13,
                    mode = if (mode == PixelDecodeMode.AUTO) PixelDecodeMode.UYVY else mode
                )
                bmp to "Прочитано $payloadLen байт из $path (всего $len, смещение $headerOffset)"
            }
        } catch (t: Throwable) {
            null to "Ошибка чтения $path: ${t.message}"
        }
    }
}
