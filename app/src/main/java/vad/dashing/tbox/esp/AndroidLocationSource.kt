package vad.dashing.tbox.esp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import vad.dashing.tbox.LocValues
import java.util.Date

/**
 * Reads system GNSS / network location into [LocValues].
 * Owned by [vad.dashing.tbox.BackgroundService] when [LocationSource.ANDROID] is selected
 * (does not require the ESP companion USB session).
 */
class AndroidLocationSource(
    context: Context,
    private val onLocation: (LocValues) -> Unit,
) {
    companion object {
        private const val TAG = "AndroidLocationSource"
        private val KNOWN_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }

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
                // Prefer enabled providers; if none report enabled (some HUs lie),
                // still attempt the standard set — requestLocationUpdates will throw if unavailable.
                val providers = KNOWN_PROVIDERS.filter { name ->
                    try {
                        locationManager.isProviderEnabled(name)
                    } catch (_: Exception) {
                        false
                    }
                }.ifEmpty { KNOWN_PROVIDERS }

                var subscribed = 0
                for (provider in providers) {
                    try {
                        locationManager.requestLocationUpdates(
                            provider,
                            1000L,
                            0f,
                            listener,
                            Looper.getMainLooper(),
                        )
                        subscribed++
                        locationManager.getLastKnownLocation(provider)?.let {
                            onLocation(toLocValues(it))
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "provider unavailable: $provider (${e.message})")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "missing location permission for $provider", e)
                        onLocation(LocValues(updateTime = Date()))
                        return@post
                    }
                }
                if (subscribed == 0) {
                    Log.w(TAG, "no location providers subscribed")
                    onLocation(LocValues(updateTime = Date()))
                } else {
                    Log.i(TAG, "listening on $subscribed provider(s): $providers")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "missing location permission", e)
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
