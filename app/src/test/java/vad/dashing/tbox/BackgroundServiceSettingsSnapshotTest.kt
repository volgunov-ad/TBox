package vad.dashing.tbox

import android.app.Application
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.fuel.FuelTypes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackgroundServiceSettingsSnapshotTest {

    @After
    fun resetUsageStatsHideFloatingRules() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Application>()
            SettingsManager(context).saveUsageStatsHideFloatingRules(emptySet(), emptySet())
        }
    }

    @Test
    fun emptyPreferences_matchesDocumentedDefaults() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        val snap = manager.backgroundSnapshotFromPreferences(emptyPreferences())
        assertFalse(snap.autoModemRestart)
        assertTrue(snap.getCanFrame)
        assertFalse(snap.getCycleSignal)
        assertTrue(snap.getLocData)
        assertEquals(5, snap.canDataSaveCount)
        assertEquals(57, snap.fuelTankLiters)
        assertEquals("", snap.fuelCalibrationJson)
        assertEquals(5, snap.fuelCalibrationZoneCount)
        assertEquals(80, snap.fuelCalibrationMaturityThreshold)
        assertEquals(FuelTypes.DEFAULT_FUEL_ID, snap.fuelPriceFuelId)
        assertEquals(5, snap.splitTripTimeMinutes)
        assertFalse(snap.wheelPressurePersistAcrossStops)
        assertTrue(snap.floatingDashboards.isEmpty())
        assertTrue(snap.usageStatsHideFloatingWatchPackages.isEmpty())
        assertTrue(snap.usageStatsHideFloatingPanelIds.isEmpty())
    }

    @Test
    fun usageStatsHideFloatingRules_persistToDataStore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        manager.saveUsageStatsHideFloatingRules(
            watchPackages = setOf("com.persist.test.app"),
            floatingPanelIds = setOf("floating-persist-1", "floating-persist-2")
        )
        val snap = manager.readBackgroundServiceSettingsSnapshot()
        assertEquals(setOf("com.persist.test.app"), snap.usageStatsHideFloatingWatchPackages)
        assertEquals(setOf("floating-persist-1", "floating-persist-2"), snap.usageStatsHideFloatingPanelIds)
    }
}
