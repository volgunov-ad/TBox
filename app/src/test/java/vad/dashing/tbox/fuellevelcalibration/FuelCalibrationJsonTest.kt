package vad.dashing.tbox.fuellevelcalibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FuelCalibrationJsonTest {

    @Test
    fun roundTrip_preservesArrays() {
        val original = CalibrationData(
            doubleArrayOf(1.5, 2.5, 3.0),
            doubleArrayOf(1.0, 2.0, 3.0),
        )
        val json = FuelCalibrationJson.encode(original)
        val decoded = FuelCalibrationJson.decode(json)
        assertNotNull(decoded)
        val d = decoded!!
        assertEquals(original.realLiters.size, d.realLiters.size)
        assertEquals(original.sensorLiters.size, d.sensorLiters.size)
        for (i in original.realLiters.indices) {
            assertEquals(original.realLiters[i], d.realLiters[i], 1e-9)
        }
        for (i in original.sensorLiters.indices) {
            assertEquals(original.sensorLiters[i], d.sensorLiters[i], 1e-9)
        }
    }

    @Test
    fun decode_blank_returnsNull() {
        assertNull(FuelCalibrationJson.decode(""))
        assertNull(FuelCalibrationJson.decode("   "))
    }

    @Test
    fun decode_mismatchedArrayLengths_returnsNull() {
        val bad = """{"realLiters":[1,2],"sensorLiters":[1]}"""
        assertNull(FuelCalibrationJson.decode(bad))
    }
}
