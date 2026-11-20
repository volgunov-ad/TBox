package vad.dashing.tbox

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePublicMirrorTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var publicRoot: File

    @Before
    fun setUp() {
        publicRoot = tmpFolder.newFolder("Tbm")
        DataStorePublicMirror.publicRootOverride = publicRoot
    }

    @After
    fun tearDown() {
        DataStorePublicMirror.publicRootOverride = null
    }

    @Test
    fun resolvePublicRootUsesOverride() {
        assertEquals(publicRoot, DataStorePublicMirror.resolvePublicRoot(context = null))
    }

    @Test
    fun resolvePublicRootNullWithoutOverrideOrContext() {
        DataStorePublicMirror.publicRootOverride = null
        assertNull(DataStorePublicMirror.resolvePublicRoot(context = null))
    }

    @Test
    fun mirrorBackupCopiesBytesUnderDatastoreBackup() {
        val source = tmpFolder.newFile("src.bak")
        source.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        DataStorePublicMirror.mirrorBackup(source, "vad.dashing.tbox.settings")
        val mirrored =
            DataStorePublicMirror.publicBackupFile(publicRoot, "vad.dashing.tbox.settings")
        assertTrue(mirrored.isFile)
        assertTrue(source.readBytes().contentEquals(mirrored.readBytes()))
        assertEquals(
            File(publicRoot, "datastore_backup").absolutePath,
            mirrored.parentFile?.absolutePath,
        )
    }

    @Test
    fun mirrorMarkerCopiesCorruptionLog() {
        val source = tmpFolder.newFile("datastore_corruption.log")
        source.writeText("line1\n")
        DataStorePublicMirror.mirrorMarker(source)
        val mirrored = DataStorePublicMirror.publicMarkerFile(publicRoot)
        assertTrue(mirrored.isFile)
        assertEquals("line1\n", mirrored.readText())
    }

    @Test
    fun mirrorCorruptCopyKeepsFileName() {
        val storeName = "vad.dashing.tbox.settings"
        val source =
            File(tmpFolder.newFolder("priv_corrupt"), "$storeName.20260101_000000_001.corrupt")
        source.writeBytes(byteArrayOf(9, 8, 7))
        DataStorePublicMirror.mirrorCorruptCopy(source, storeName)
        val mirrored = File(DataStorePublicMirror.publicCorruptDir(publicRoot), source.name)
        assertTrue(mirrored.isFile)
        assertTrue(source.readBytes().contentEquals(mirrored.readBytes()))
    }

    @Test
    fun mirrorCorruptCopyRotatesToThreePerStore() {
        val storeName = "vad.dashing.tbox.settings"
        val dir = DataStorePublicMirror.publicCorruptDir(publicRoot)
        dir.mkdirs()
        for (i in 1..3) {
            File(dir, "$storeName.20260101_000000_00$i.corrupt").writeBytes(byteArrayOf(i.toByte()))
        }
        val source =
            File(tmpFolder.newFolder("priv_corrupt2"), "$storeName.20260101_000000_004.corrupt")
        source.writeBytes(byteArrayOf(4))
        DataStorePublicMirror.mirrorCorruptCopy(source, storeName)
        val names =
            dir.listFiles { file -> file.isFile && file.name.startsWith("$storeName.") }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
        assertEquals(3, names.size)
        assertFalse(names.contains("$storeName.20260101_000000_001.corrupt"))
        assertTrue(names.contains("$storeName.20260101_000000_004.corrupt"))
    }

    @Test
    fun mirrorBackupNoOpWhenSourceMissing() {
        val missing = File(tmpFolder.root, "missing.bak")
        DataStorePublicMirror.mirrorBackup(missing, "vad.dashing.tbox.settings")
        assertFalse(
            DataStorePublicMirror.publicBackupFile(publicRoot, "vad.dashing.tbox.settings").exists(),
        )
    }
}
