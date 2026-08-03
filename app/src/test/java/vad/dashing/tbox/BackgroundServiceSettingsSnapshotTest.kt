package vad.dashing.tbox

import android.app.Application
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
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

    @Test
    fun emptyPreferences_matchesDocumentedDefaults() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        val snap = manager.backgroundSnapshotFromPreferences(emptyPreferences())
        assertFalse(snap.autoModemRestart)
        assertTrue(snap.getCanFrame)
        assertFalse(snap.getCycleSignal)
        assertTrue(snap.getLocData)
        assertEquals(vad.dashing.tbox.esp.LocationSource.TBOX, snap.locationSource)
        assertFalse(snap.espCompanionEnabled)
        assertFalse(snap.noTboxConnect)
        assertEquals("", snap.usbGnssDeviceId)
        assertEquals(115_200, snap.usbGnssBaud)
        assertEquals(vad.dashing.tbox.location.MockCanSpeedMode.NONE, snap.mockCanSpeedMode)
        assertTrue(snap.mockJunkFixFilter)
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
        assertTrue(snap.usageStatsForceShowFloatingWatchPackages.isEmpty())
        assertTrue(snap.usageStatsForceShowFloatingPanelIds.isEmpty())
    }

    @Test
    fun esp32WithoutCompanionEnabled_remapsToTbox() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = SettingsManager(context)
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("vad.dashing.tbox.location_source") to "ESP32",
            booleanPreferencesKey("vad.dashing.tbox.esp_companion_enabled") to false,
        )
        val snap = manager.backgroundSnapshotFromPreferences(prefs)
        assertEquals(vad.dashing.tbox.esp.LocationSource.TBOX, snap.locationSource)
        assertFalse(snap.espCompanionEnabled)
        assertTrue(snap.getLocData)
    }
}
