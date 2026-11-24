package vad.dashing.mqtt.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleSettingsUiTest {
    @Test
    fun reloadOnlyAfterTheErrorStoreRecovers() {
        assertFalse(staleUiNeedsReload(loadedWithError = false, storageError = null))
        assertFalse(staleUiNeedsReload(loadedWithError = false, storageError = "Хранилище ключей недоступно"))
        assertFalse(staleUiNeedsReload(loadedWithError = true, storageError = "Хранилище ключей недоступно"))
        assertTrue(staleUiNeedsReload(loadedWithError = true, storageError = null))
    }
}
