package vad.dashing.tbox

import android.app.Application
import androidx.datastore.preferences.core.emptyPreferences
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
        assertEquals(5, snap.canDataSaveCount)
        assertEquals(57, snap.fuelTankLiters)
        assertEquals("", snap.fuelCalibrationJson)
        assertEquals(5, snap.fuelCalibrationZoneCount)
        assertEquals(80, snap.fuelCalibrationMaturityThreshold)
        assertEquals(FuelTypes.DEFAULT_FUEL_ID, snap.fuelPriceFuelId)
        assertEquals(5, snap.splitTripTimeMinutes)
        assertTrue(snap.floatingDashboards.isEmpty())
    }
}
