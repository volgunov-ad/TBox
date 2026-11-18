package vad.dashing.tbox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AtomicUtf8FileWriterTest {

    @Test
    fun writeJsonObjectText_writesUtf8AndVerifies() {
        val dir = createTempDirectory("tbox-backup-export").toFile()
        try {
            val target = File(dir, "tbox_backup_test.json")
            val json = JSONObject()
                .put("formatVersion", 1)
                .put("packageName", "vad.dashing.tbox")
                .toString(2)

            val result = AtomicUtf8FileWriter.writeJsonObjectText(target, json)
            assertTrue(result.isSuccess)
            assertTrue(target.isFile)
            assertEquals(json.trim(), target.readText(Charsets.UTF_8))
            assertFalse(File(dir, "tbox_backup_test.json.tmp").exists())
            assertEquals('{', target.readBytes().first().toInt().toChar())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeJsonObjectText_rejectsNonObject() {
        val dir = createTempDirectory("tbox-backup-export").toFile()
        try {
            val target = File(dir, "bad.json")
            val result = AtomicUtf8FileWriter.writeJsonObjectText(target, "[1,2]")
            assertTrue(result.isFailure)
            assertFalse(target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writeText_rejectsEmpty() {
        val dir = createTempDirectory("tbox-backup-export").toFile()
        try {
            val target = File(dir, "empty.json")
            val result = AtomicUtf8FileWriter.writeText(target, "")
            assertTrue(result.isFailure)
            assertFalse(target.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
