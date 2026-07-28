package vad.dashing.tbox.esp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import vad.dashing.tbox.LocValues
import java.util.Date

/**
 * Reads system GNSS / network location into [LocValues].
 */
class AndroidLocationSource(
    context: Context,
    private val onLocation: (LocValues) -> Unit,
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listening = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocation(toLocValues(location))
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (listening) return
        listening = true
        mainHandler.post {
            try {
                val providers = buildList {
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        add(LocationManager.GPS_PROVIDER)
                    }
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        add(LocationManager.NETWORK_PROVIDER)
                    }
                    if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                        add(LocationManager.PASSIVE_PROVIDER)
                    }
                }
                for (provider in providers) {
                    locationManager.requestLocationUpdates(
                        provider,
                        1000L,
                        0f,
                        listener,
                        Looper.getMainLooper(),
                    )
                    locationManager.getLastKnownLocation(provider)?.let { onLocation(toLocValues(it)) }
                }
                if (providers.isEmpty()) {
                    onLocation(LocValues(updateTime = Date()))
                }
            } catch (_: SecurityException) {
                onLocation(LocValues(updateTime = Date()))
            }
        }
    }

    fun stop() {
        if (!listening) return
        listening = false
        mainHandler.post {
            try {
                locationManager.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
    }

    private fun toLocValues(location: Location): LocValues {
        val hasFix = location.latitude != 0.0 || location.longitude != 0.0
        return LocValues(
            rawValue = "android:${location.provider}",
            locateStatus = hasFix,
            utcTime = null,
            longitude = location.longitude,
            latitude = location.latitude,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            visibleSatellites = 0,
            usingSatellites = 0,
            speed = if (location.hasSpeed()) location.speed * 3.6f else 0f,
            trueDirection = if (location.hasBearing()) location.bearing else 0f,
            magneticDirection = if (location.hasBearing()) location.bearing else 0f,
            updateTime = Date(location.time.takeIf { it > 0 } ?: System.currentTimeMillis()),
        )
    }
}
