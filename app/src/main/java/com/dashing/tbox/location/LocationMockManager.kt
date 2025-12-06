package com.dashing.tbox.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.dashing.tbox.LocValues
import com.dashing.tbox.TboxRepository

class LocationMockManager(context: Context) {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun setupMockLocationProvider(mockProviderName:String) {
        try {
            removeMockProviderIfExists(mockProviderName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setupMockProviderNew(mockProviderName)
            } else {
                setupMockProviderLegacy(mockProviderName)
            }

            Log.d("LocationMockManager", "Mock provider setup successfully")
            TboxRepository.addLog("DEBUG", "LocationMockManager", "Mock provider setup successfully")

        } catch (e: SecurityException) {
            Log.e("LocationMockManager", "Security exception setting up mock provider", e)
            TboxRepository.addLog("ERROR", "LocationMockManager", "Security exception setting up mock provider")
        } catch (e: IllegalArgumentException) {
            Log.e("LocationMockManager", "Illegal argument setting up mock provider", e)
            TboxRepository.addLog("ERROR", "LocationMockManager", "Illegal argument setting up mock provider")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun setupMockProviderNew(providerName: String) {
        val properties = android.location.provider.ProviderProperties.Builder()
            .setHasAltitudeSupport(true)
            .setHasSpeedSupport(true)
            .setHasBearingSupport(true)
            .setPowerUsage(android.location.provider.ProviderProperties.POWER_USAGE_LOW)
            .setAccuracy(android.location.provider.ProviderProperties.ACCURACY_FINE)
            .build()

        locationManager.addTestProvider(providerName, properties)
        locationManager.setTestProviderEnabled(providerName, true)
    }

    @Suppress("DEPRECATION")
    private fun setupMockProviderLegacy(providerName: String) {
        // Для Android 9-11 используем старый API
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
            1      // accuracy: 1 = ACCURACY_FINE
        )
        locationManager.setTestProviderEnabled(providerName, true)
        //val now = System.currentTimeMillis()
        //locationManager.setTestProviderStatus(providerName, LocationProvider.AVAILABLE, null, now)
    }

    private fun removeMockProviderIfExists(providerName: String) {
        try {
            // Пытаемся удалить провайдер, если он существует
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            // Игнорируем, если провайдера нет
        }
    }

    private fun isTestProviderEnabled(providerName: String): Boolean {
        return try {
            locationManager.isProviderEnabled(providerName)
        } catch (e: Exception) {
            false
        }
    }

    fun setMockLocation(locValues: LocValues) {
        try {
            val mockProviderName = LocationManager.GPS_PROVIDER

            if (!isTestProviderEnabled(mockProviderName)) {
                setupMockLocationProvider(mockProviderName)
            }

            if (locValues.locateStatus && locValues.latitude != 0.0 && locValues.longitude != 0.0) {
                val mockLocation = createMockLocation(mockProviderName, locValues)
                locationManager.setTestProviderLocation(mockProviderName, mockLocation)

                Log.d(
                    "LocationMockManager",
                    "Mock location set: ${locValues.latitude}, ${locValues.longitude}"
                )
                TboxRepository.addLog("DEBUG", "LocationMockManager", "Mock location set: ${locValues.latitude}, ${locValues.longitude}")
            }

        } catch (e: SecurityException) {
            Log.e("LocationMockManager", "Security exception setting mock location", e)
            TboxRepository.addLog("ERROR", "LocationMockManager", "Security exception setting mock location")
        } catch (e: IllegalArgumentException) {
            Log.e("LocationMockManager", "Illegal argument setting mock location", e)
            TboxRepository.addLog("ERROR", "LocationMockManager", "Illegal argument setting mock location")
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
        try {
            val mockProviderName = LocationManager.GPS_PROVIDER
            removeMockProviderIfExists(mockProviderName)
            Log.d("LocationMockManager", "Mock location stopped")
            TboxRepository.addLog("DEBUG", "LocationMockManager", "Mock location stopped")
        } catch (e: Exception) {
            Log.e("LocationMockManager", "Error stopping mock location", e)
            TboxRepository.addLog("ERROR", "LocationMockManager", "Error stopping mock location")
        }
    }
}