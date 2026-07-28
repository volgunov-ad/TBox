package vad.dashing.tbox.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.TboxRepository

class LocationMockManager(context: Context) {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** True after a successful provider setup until [stopMockLocation] removes it. */
    @Volatile
    private var providerActive: Boolean = false

    private var lastErrorLogAtMs: Long = 0L
    private var lastValueLogAtMs: Long = 0L

    fun setupMockLocationProvider(mockProviderName: String) {
        try {
            removeMockProviderIfExists(mockProviderName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setupMockProviderNew(mockProviderName)
            } else {
                setupMockProviderLegacy(mockProviderName)
            }

            val wasActive = providerActive
            providerActive = true
            if (!wasActive) {
                Log.d(TAG, "Mock provider started")
                TboxRepository.addLog("INFO", TAG, "Mock provider started")
            }
        } catch (e: SecurityException) {
            providerActive = false
            logErrorThrottled("Security exception setting up mock provider", e)
        } catch (e: IllegalArgumentException) {
            providerActive = false
            logErrorThrottled("Illegal argument setting up mock provider", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupMockProviderNew(providerName: String) {
        val properties = ProviderProperties.Builder()
            .setHasAltitudeSupport(true)
            .setHasSpeedSupport(true)
            .setHasBearingSupport(true)
            .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
            .setAccuracy(ProviderProperties.ACCURACY_FINE)
            .build()

        locationManager.addTestProvider(providerName, properties)
        locationManager.setTestProviderEnabled(providerName, true)
    }

    @Suppress("DEPRECATION")
    private fun setupMockProviderLegacy(providerName: String) {
        locationManager.addTestProvider(
            providerName,
            false, // requiresNetwork
            false, // requiresSatellite
            false, // requiresCell
            false, // hasMonetaryCost
            true,  // supportsAltitude
            true,  // supportsSpeed
            true,  // supportsBearing
            1,     // powerRequirement: 1 = POWER_LOW
            1,     // accuracy: 1 = ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(providerName, true)
    }

    private fun removeMockProviderIfExists(providerName: String) {
        try {
            locationManager.removeTestProvider(providerName)
        } catch (_: Exception) {
            // Provider may not exist.
        }
    }

    private fun isTestProviderEnabled(providerName: String): Boolean {
        return try {
            locationManager.isProviderEnabled(providerName)
        } catch (_: Exception) {
            false
        }
    }

    fun setMockLocation(locValues: LocValues) {
        try {
            val mockProviderName = "gps"

            if (!providerActive || !isTestProviderEnabled(mockProviderName)) {
                setupMockLocationProvider(mockProviderName)
            }

            if (locValues.latitude != 0.0 && locValues.longitude != 0.0) {
                val mockLocation = createMockLocation(mockProviderName, locValues)
                locationManager.setTestProviderLocation(mockProviderName, mockLocation)
                logValueThrottled(locValues)
            }
        } catch (e: SecurityException) {
            logErrorThrottled("Security exception setting mock location", e)
        } catch (e: IllegalArgumentException) {
            logErrorThrottled("Illegal argument setting mock location", e)
        }
    }

    private fun createMockLocation(providerName: String, locValues: LocValues): Location {
        return Location(providerName).apply {
            latitude = locValues.latitude
            longitude = locValues.longitude
            altitude = locValues.altitude
            time = System.currentTimeMillis()
            speed = locValues.speed / 3.6f
            bearing = locValues.trueDirection
            accuracy = 5.0f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 1.0f
                speedAccuracyMetersPerSecond = 0.5f
                verticalAccuracyMeters = 1.0f
            }
        }
    }

    fun stopMockLocation() {
        if (!providerActive) return
        try {
            removeMockProviderIfExists("gps")
            providerActive = false
            Log.d(TAG, "Mock provider stopped")
            TboxRepository.addLog("INFO", TAG, "Mock provider stopped")
        } catch (e: Exception) {
            providerActive = false
            logErrorThrottled("Error stopping mock location", e)
        }
    }

    private fun logValueThrottled(locValues: LocValues) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastValueLogAtMs < VALUE_LOG_MIN_INTERVAL_MS) return
        lastValueLogAtMs = now
        val msg = "Mock location: ${locValues.latitude}, ${locValues.longitude}"
        Log.d(TAG, msg)
        TboxRepository.addLog("DEBUG", TAG, msg)
    }

    private fun logErrorThrottled(message: String, e: Exception) {
        Log.e(TAG, message, e)
        val now = SystemClock.elapsedRealtime()
        if (now - lastErrorLogAtMs < ERROR_LOG_MIN_INTERVAL_MS) return
        lastErrorLogAtMs = now
        TboxRepository.addLog("ERROR", TAG, message)
    }

    companion object {
        private const val TAG = "LocationMockManager"
        private const val ERROR_LOG_MIN_INTERVAL_MS = 30_000L
        private const val VALUE_LOG_MIN_INTERVAL_MS = 5_000L
    }
}
