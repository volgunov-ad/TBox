package vad.dashing.tbox

import org.json.JSONObject
import java.io.File
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption

/**
 * Writes UTF-8 text to [target] via a sibling `.tmp` file, fsync, rename, then verifies
 * the on-disk bytes. Used for settings backup export so a toasted "saved" path cannot
 * leave a truncated or empty Downloads file after a power loss mid-write.
 */
object AtomicUtf8FileWriter {

    fun writeText(target: File, text: String): Result<File> = runCatching {
        require(text.isNotEmpty()) { "empty_payload" }
        val parent = target.parentFile
            ?: throw IllegalArgumentException("missing_parent")
        if (!parent.isDirectory && !parent.mkdirs()) {
            throw IllegalStateException("cannot_create_parent")
        }
        val tmp = File(parent, "${target.name}.tmp")
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        try {
            tmp.outputStream().use { output ->
                output.write(bytes)
                output.flush()
            }
            FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            if (target.exists() && !target.delete()) {
                // Best-effort: rename may still overwrite on some filesystems.
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
                FileChannel.open(target.toPath(), StandardOpenOption.WRITE).use { it.force(true) }
            }
            verifyWritten(target, bytes)
            target
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    fun writeJsonObjectText(target: File, jsonText: String): Result<File> = runCatching {
        val trimmed = jsonText.trim()
        require(trimmed.startsWith("{")) { "not_json_object" }
        // Fail fast if payload is not parseable before touching disk.
        JSONObject(trimmed)
        writeText(target, trimmed).getOrThrow()
    }

    private fun verifyWritten(target: File, expected: ByteArray) {
        if (!target.isFile) throw IllegalStateException("missing_after_write")
        val actual = target.readBytes()
        if (!actual.contentEquals(expected)) {
            target.delete()
            throw IllegalStateException("verify_mismatch")
        }
    }
}
