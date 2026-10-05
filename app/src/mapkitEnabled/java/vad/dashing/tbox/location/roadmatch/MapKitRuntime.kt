package vad.dashing.tbox.location.roadmatch

import android.content.Context
import com.yandex.mapkit.MapKitFactory
import vad.dashing.tbox.BuildConfig
import kotlin.math.cos
import kotlin.math.ln

/** Lazy MapKit init and camera helpers for the road-match basemap tile. */
object MapKitRuntime {
    @Volatile
    private var initializedKey: String? = null

    fun effectiveApiKey(userOverride: String): String {
        val override = userOverride.trim()
        if (override.isNotEmpty()) return override
        return BuildConfig.MAPKIT_API_KEY.trim()
    }

    /**
     * Initializes MapKit once per process for [apiKey]. Returns false when the key is blank
     * or MapKit was already initialized with a different key (requires app restart).
     */
    fun ensureInitialized(context: Context, userOverride: String): Boolean {
        val key = effectiveApiKey(userOverride)
        if (key.isEmpty()) return false
        synchronized(this) {
            initializedKey?.let { existing ->
                if (existing == key) return true
                return false
            }
            MapKitFactory.setApiKey(key)
            MapKitFactory.initialize(context.applicationContext)
            initializedKey = key
            return true
        }
    }

    /** Matches F2a follow span to MapKit zoom at [lat] for [viewHeightPx]. */
    fun zoomForHalfHeightM(halfHeightM: Double, lat: Double, viewHeightPx: Int): Float {
        if (!halfHeightM.isFinite() || halfHeightM <= 0.0 || viewHeightPx <= 0) return 16f
        val visibleM = halfHeightM * 2.0
        val metersPerPixel = visibleM / viewHeightPx.toDouble()
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) return 16f
        val latRad = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val scale = 156543.03392 * cos(latRad) / metersPerPixel
        if (!scale.isFinite() || scale <= 0.0) return 16f
        return (ln(scale) / ln(2.0)).toFloat().coerceIn(2f, 21f)
    }
}
