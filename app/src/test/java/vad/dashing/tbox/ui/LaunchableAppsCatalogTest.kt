package vad.dashing.tbox.ui

import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.LauncherAppIconPaths

class LaunchableAppsCatalogTest {

    private val lookup = LauncherAppIconPaths.Lookup.None

    @Before
    fun setUp() {
        LaunchableAppsCatalog.resetForTests()
    }

    @After
    fun tearDown() {
        LaunchableAppsCatalog.resetForTests()
    }

    @Test
    fun packageCatalogChangeActions_coverInstallUninstallUpdate() {
        assertTrue(LaunchableAppsCatalog.isPackageCatalogChangeAction(Intent.ACTION_PACKAGE_ADDED))
        assertTrue(LaunchableAppsCatalog.isPackageCatalogChangeAction(Intent.ACTION_PACKAGE_REMOVED))
        assertTrue(LaunchableAppsCatalog.isPackageCatalogChangeAction(Intent.ACTION_PACKAGE_CHANGED))
        assertTrue(LaunchableAppsCatalog.isPackageCatalogChangeAction(Intent.ACTION_PACKAGE_REPLACED))
        assertFalse(LaunchableAppsCatalog.isPackageCatalogChangeAction(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(LaunchableAppsCatalog.isPackageCatalogChangeAction(null))
    }

    @Test
    fun getOrLoad_reusesEntriesUntilPackagesRevisionChanges() {
        var loads = 0
        val firstBatch = listOf(
            LaunchableAppEntry("com.a", "A", null),
            LaunchableAppEntry("com.b", "B", null),
        )
        val secondBatch = firstBatch + LaunchableAppEntry("com.new", "New", null)

        val first = LaunchableAppsCatalog.getOrLoad(
            iconSizePx = 48,
            iconRevision = 0,
            packagesRevision = 0,
            lookup = lookup,
        ) {
            loads++
            firstBatch
        }
        val second = LaunchableAppsCatalog.getOrLoad(
            iconSizePx = 48,
            iconRevision = 0,
            packagesRevision = 0,
            lookup = lookup,
        ) {
            loads++
            secondBatch
        }

        assertEquals(1, loads)
        assertSame(first, second)
        assertEquals(2, first.size)

        LaunchableAppsCatalog.invalidateForPackageCatalogChange()
        assertEquals(1, LaunchableAppsCatalog.packagesRevision.value)

        val third = LaunchableAppsCatalog.getOrLoad(
            iconSizePx = 48,
            iconRevision = 0,
            packagesRevision = LaunchableAppsCatalog.packagesRevision.value,
            lookup = lookup,
        ) {
            loads++
            secondBatch
        }

        assertEquals(2, loads)
        assertEquals(3, third.size)
        assertTrue(third.any { it.packageName == "com.new" })
    }

    @Test
    fun clearIcons_doesNotBumpPackagesRevision() {
        LaunchableAppsCatalog.getOrLoad(
            iconSizePx = 48,
            iconRevision = 0,
            packagesRevision = 0,
            lookup = lookup,
        ) {
            listOf(LaunchableAppEntry("com.a", "A", null))
        }
        LaunchableAppsCatalog.clearIcons()
        assertEquals(0, LaunchableAppsCatalog.packagesRevision.value)

        var loads = 0
        LaunchableAppsCatalog.getOrLoad(
            iconSizePx = 48,
            iconRevision = 0,
            packagesRevision = 0,
            lookup = lookup,
        ) {
            loads++
            listOf(LaunchableAppEntry("com.a", "A", null))
        }
        assertEquals(1, loads)
    }
}
