package vad.dashing.tbox

/** Dashboard tile: embedded Yandex MapKit map (requires MAPKIT_API_KEY in local.properties). */
const val MAP_KIT_WIDGET_DATA_KEY = "mapKitWidget"

const val DEFAULT_MAP_KIT_ZOOM = 14f
const val MIN_MAP_KIT_ZOOM = 5f
const val MAX_MAP_KIT_ZOOM = 19f

fun normalizeMapKitZoom(rawZoom: Float): Float {
    if (!rawZoom.isFinite()) return DEFAULT_MAP_KIT_ZOOM
    return rawZoom.coerceIn(MIN_MAP_KIT_ZOOM, MAX_MAP_KIT_ZOOM)
}
