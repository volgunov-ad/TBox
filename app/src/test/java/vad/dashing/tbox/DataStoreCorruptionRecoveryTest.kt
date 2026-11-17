package vad.dashing.tbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreCorruptionRecoveryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val storeName = "vad.dashing.tbox.test_store"

    private fun filesDir(): File = tmpFolder.root

    private fun mainFile(): File = DataStoreFiles.mainFile(filesDir(), storeName)

    private fun backupFile(): File = DataStoreFiles.backupFile(filesDir(), storeName)

    private fun markerFile(): File = DataStoreFiles.markerFile(filesDir())

    private fun corruptDir(): File = DataStoreFiles.corruptDir(filesDir())

    private fun writePreferences(file: File, key: String, value: Int) {
        file.parentFile?.mkdirs()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            runBlocking { store.edit { it[intPreferencesKey(key)] = value } }
        } finally {
            scope.cancel()
        }
    }

    private fun seedBackupFile(key: String, value: Int) {
        val src = File(tmpFolder.newFolder("seed"), "seed.preferences_pb")
        writePreferences(src, key, value)
        src.copyTo(backupFile(), overwrite = true)
    }

    @Test
    fun backupSkippedWhenMainFileMissing() = runBlocking {
        assertFalse(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        assertFalse(backupFile().exists())
    }

    @Test
    fun backupCreatedForValidMainFile() = runBlocking {
        writePreferences(mainFile(), "speed", 42)
        assertTrue(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        assertTrue(backupFile().isFile)
        assertEquals(mainFile().length(), backupFile().length())
    }

    @Test
    fun backupNotRewrittenWhenUnchanged() = runBlocking {
        writePreferences(mainFile(), "speed", 42)
        assertTrue(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        val bytesBefore = backupFile().readBytes()
        val modifiedBefore = backupFile().lastModified()
        assertFalse(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        assertTrue(bytesBefore.contentEquals(backupFile().readBytes()))
        assertEquals(modifiedBefore, backupFile().lastModified())
    }

    @Test
    fun backupRefreshedAfterMainChanges() = runBlocking {
        writePreferences(mainFile(), "speed", 42)
        assertTrue(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        val changed = File(tmpFolder.newFolder("changed_src"), "other.preferences_pb")
        writePreferences(changed, "extra", 7)
        changed.copyTo(mainFile(), overwrite = true)
        assertTrue(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        val restored = DataStoreBackupManager.restorePreferences(filesDir(), storeName)
        assertEquals(7, restored?.get(intPreferencesKey("extra")))
    }

    @Test
    fun backupKeptWhenMainCorrupt() = runBlocking {
        writePreferences(mainFile(), "speed", 42)
        assertTrue(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        val goodBackup = backupFile().readBytes()
        mainFile().writeBytes(byteArrayOf(0, 1, 2, 3, 4))
        assertFalse(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        assertTrue(goodBackup.contentEquals(backupFile().readBytes()))
    }

    @Test
    fun restorePreferencesReturnsValuesFromValidBackup() = runBlocking {
        seedBackupFile("speed", 42)
        val restored = DataStoreBackupManager.restorePreferences(filesDir(), storeName)
        assertEquals(42, restored?.get(intPreferencesKey("speed")))
    }

    @Test
    fun restorePreferencesReturnsNullForCorruptBackup() = runBlocking {
        backupFile().parentFile?.mkdirs()
        backupFile().writeBytes(byteArrayOf(9, 8, 7))
        assertNull(DataStoreBackupManager.restorePreferences(filesDir(), storeName))
    }

    @Test
    fun restorePreferencesReturnsNullForMissingBackup() = runBlocking {
        assertNull(DataStoreBackupManager.restorePreferences(filesDir(), storeName))
    }

    @Test
    fun restorePreferencesReturnsNullForEmptyBackupFile() = runBlocking {
        backupFile().parentFile?.mkdirs()
        backupFile().writeBytes(ByteArray(0))
        assertNull(DataStoreBackupManager.restorePreferences(filesDir(), storeName))
    }

    @Test
    fun handleCorruptionRestoresFromBackupAndRecordsDiagnostics() = runBlocking {
        writePreferences(mainFile(), "speed", 42)
        assertTrue(DataStoreBackupManager.backupIfChanged(filesDir(), storeName))
        mainFile().writeBytes(byteArrayOf(1, 2, 3))
        val prefs = handleDataStoreCorruption(
            filesDir(),
            storeName,
            IllegalStateException("test corruption"),
        )
        assertEquals(42, prefs[intPreferencesKey("speed")])
        assertTrue(markerFile().isFile)
        val marker = markerFile().readText()
        assertTrue(marker.contains("store=$storeName"))
        assertTrue(marker.contains("restored=true"))
        val copies = corruptDir().listFiles { file -> file.name.endsWith(".corrupt") }
        assertTrue(copies != null && copies.size == 1)
    }

    @Test
    fun handleCorruptionResetsWithoutBackup() = runBlocking {
        writePreferences(mainFile(), "speed", 42)
        val prefs = handleDataStoreCorruption(filesDir(), storeName, null)
        assertTrue(prefs.asMap().isEmpty())
        assertTrue(markerFile().isFile)
        val marker = markerFile().readText()
        assertTrue(marker.contains("restored=false"))
        assertTrue(marker.contains("store=$storeName"))
    }

    @Test
    fun corruptCopiesRotatedToLimit() = runBlocking {
        val dir = corruptDir()
        dir.mkdirs()
        for (i in 1..4) {
            val stamp = "20260101_000000_00$i"
            File(dir, "$storeName.$stamp.corrupt").writeBytes(byteArrayOf(i.toByte()))
        }
        seedBackupFile("speed", 42)
        writePreferences(mainFile(), "speed", 42)
        handleDataStoreCorruption(filesDir(), storeName, null)
        val copies =
            dir.listFiles { file -> file.isFile && file.name.startsWith("$storeName.") }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
        assertEquals(3, copies.size)
        assertFalse(copies.contains("$storeName.20260101_000000_001.corrupt"))
    }
}
