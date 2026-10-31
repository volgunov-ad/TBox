package vad.dashing.tbox.utils

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SizeLimitExceededException(val limitBytes: Long) :
    IOException("size_limit_exceeded:$limitBytes")

/** Reads the whole stream, throwing [SizeLimitExceededException] once more than [maxBytes] arrive. */
fun InputStream.readBytesAtMost(maxBytes: Long): ByteArray {
    val out = ByteArrayOutputStream()
    copyToAtMost(out, maxBytes)
    return out.toByteArray()
}

/** Copies the whole stream, throwing [SizeLimitExceededException] once more than [maxBytes] arrive. */
fun InputStream.copyToAtMost(out: OutputStream, maxBytes: Long): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) return total
        total += n
        if (total > maxBytes) throw SizeLimitExceededException(maxBytes)
        out.write(buffer, 0, n)
    }
}

fun File.readBytesAtMost(maxBytes: Long): ByteArray {
    if (length() > maxBytes) throw SizeLimitExceededException(maxBytes)
    return inputStream().use { it.readBytesAtMost(maxBytes) }
}

fun File.readTextAtMost(maxBytes: Long): String =
    readBytesAtMost(maxBytes).toString(Charsets.UTF_8)
