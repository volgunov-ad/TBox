package vad.dashing.tbox.utils

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log

object MockLocationUtils {

    fun checkMockLocationCapabilities(context: Context): MockLocationStatus {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        return MockLocationStatus(
            hasLocationPermissions = context.hasLocationPermission(),
            isMockLocationEnabled = context.isMockLocationEnabled(),
            canAddTestProvider = canAddTestProvider(locationManager),
            apiLevel = Build.VERSION.SDK_INT
        )
    }

    private fun canAddTestProvider(locationManager: LocationManager): Boolean {
        return try {
            val testProviderName = "temp_test_provider_${System.currentTimeMillis()}"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Для Android 12+ используем новый API
                canAddTestProviderNew(locationManager, testProviderName)
            } else {
                // Для Android 9-11 используем старый API
                canAddTestProviderLegacy(locationManager, testProviderName)
            }
        } catch (e: Exception) {
            Log.e("Check mock-location", "Can not check", e)
            false
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun canAddTestProviderNew(locationManager: LocationManager, providerName: String): Boolean {
        return try {
            val properties = android.location.provider.ProviderProperties.Builder()
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(android.location.provider.ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(android.location.provider.ProviderProperties.ACCURACY_FINE)
                .build()

            locationManager.addTestProvider(providerName, properties)
            locationManager.removeTestProvider(providerName)
            true
        } catch (e: Exception) {
            Log.e("Check mock-location", "Can not add TestProvider", e)
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun canAddTestProviderLegacy(locationManager: LocationManager, providerName: String): Boolean {
        return try {
            // Для старых версий используем старый API
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
            locationManager.removeTestProvider(providerName)
            true
        } catch (e: Exception) {
            Log.e("Check mock-location", "Can not add TestProviderLegacy", e)
            false
        }
    }

    data class MockLocationStatus(
        val hasLocationPermissions: Boolean,
        val isMockLocationEnabled: Boolean,
        val canAddTestProvider: Boolean,
        val apiLevel: Int
    ) {
        val canUseMockLocation: Boolean
            get() = hasLocationPermissions &&
                    isMockLocationEnabled &&
                    canAddTestProvider
    }
}

// Extension функции для Context
fun Context.hasLocationPermission(): Boolean {
    return (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
}

fun Context.isMockLocationEnabled(): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1
        } else {
            Settings.Secure.getInt(contentResolver, "development_settings_enabled") == 1
        }
    } catch (e: Exception) {
        // Альтернативная проверка через ALLOW_MOCK_LOCATION
        try {
            Settings.Secure.getInt(contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION) != 0
        } catch (e2: Exception) {
            false
        }
    }
}

fun Context.isAppSelectedAsMockProvider(): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mockLocationApp = Settings.Secure.getString(contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION)
            // На некоторых устройствах может быть "1" вместо имени пакета
            Log.d("isAppSelectedAsMockProvider", mockLocationApp)
            mockLocationApp == packageName || mockLocationApp == "1"
        } else {
            // Для Android 5.0 и ниже достаточно, что mock-локация включена
            isMockLocationEnabled()
        }
    } catch (e: Exception) {
        false
    }
}

fun Context.canUseMockLocation(): Boolean {
    return hasLocationPermission() && isMockLocationEnabled()
}

fun Context.hasLocationProvider(providerName: String): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return try {
        locationManager.allProviders.contains(providerName)
    } catch (e: Exception) {
        false
    }
}

// Дополнительные extension функции
fun Context.isAppInMockLocationList(): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Для Android 6.0+ проверяем, есть ли наше приложение в списке
            val mockLocationApps = getMockLocationApps()
            mockLocationApps.contains(packageName)
        } else {
            // Для старых версий считаем, что приложение в списке
            true
        }
    } catch (e: Exception) {
        false
    }
}

fun Context.getMockLocationApps(): List<String> {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // Этот метод может быть не всегда доступен, но попробуем
            val mockLocationApp = Settings.Secure.getString(contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION)
            if (mockLocationApp.isNullOrEmpty()) {
                emptyList()
            } else {
                listOf(mockLocationApp)
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}