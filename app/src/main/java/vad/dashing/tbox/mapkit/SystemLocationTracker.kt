package vad.dashing.tbox.mapkit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class SystemLocationState(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val hasFix: Boolean = false,
    val hasPermission: Boolean = false,
    val timestampMs: Long = 0L,
)

fun Context.hasFineLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
@Composable
fun rememberSystemLocationState(): SystemLocationState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(context.hasFineLocationPermission())
    }
    var state by remember {
        mutableStateOf(SystemLocationState(hasPermission = permissionGranted))
    }

    DisposableEffect(lifecycleOwner) {
        val permissionObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = context.hasFineLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(permissionObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(permissionObserver)
        }
    }

    DisposableEffect(lifecycleOwner, permissionGranted) {
        if (!permissionGranted) {
            state = SystemLocationState(hasPermission = false)
            return@DisposableEffect onDispose {}
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
        ).filter { locationManager.isProviderEnabled(it) }

        fun applyLocation(location: Location?) {
            if (location == null) return
            val current = state
            if (current.hasFix && location.time + 500L < current.timestampMs) {
                return
            }
            state = SystemLocationState(
                latitude = location.latitude,
                longitude = location.longitude,
                bearing = if (location.hasBearing()) location.bearing else 0f,
                hasFix = true,
                hasPermission = true,
                timestampMs = location.time,
            )
        }

        providers.forEach { provider ->
            applyLocation(locationManager.getLastKnownLocation(provider))
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                applyLocation(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

        fun registerUpdates() {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider,
                    1000L,
                    1f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        }

        var registered = false
        if (providers.isNotEmpty()) {
            registerUpdates()
            registered = true
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && providers.isNotEmpty() && !registered) {
                registerUpdates()
                registered = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            if (registered) {
                locationManager.removeUpdates(listener)
            }
        }
    }

    return state.copy(hasPermission = permissionGranted)
}
