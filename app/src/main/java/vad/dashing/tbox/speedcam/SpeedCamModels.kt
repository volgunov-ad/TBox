package vad.dashing.tbox.speedcam

/**
 * One SpeedCamOnline / iGO speedcam.txt row.
 *
 * [lon]/[lat] from CSV X,Y. [typeCode] is the raw iGO TYPE (std 1–5 or ext 68+).
 * [dirType]: 0 = all, 1 = one direction ([directionDeg]), 2 = both ways.
 */
data class SpeedCamPoint(
    val id: Int,
    val lon: Double,
    val lat: Double,
    val typeCode: Int,
    val speedKmh: Int,
    val dirType: Int,
    val directionDeg: Int,
) {
    val category: SpeedCamCategory get() = SpeedCamCategory.fromTypeCode(typeCode)
    val hasSpeedLimit: Boolean get() = speedKmh > 0
}

enum class SpeedCamCategory {
    FIXED,
    TRAFFIC_LIGHT,
    SECTION,
    MOBILE,
    POLICE_POST,
    RAILWAY,
    DUMMY,
    OTHER,
    ;

    companion object {
        /**
         * Maps iGO std (1–5) and common extended codes used by SpeedCamOnline igoext.
         */
        fun fromTypeCode(type: Int): SpeedCamCategory = when (type) {
            1, 32, 64, 96, 128, 160, 192, 224 -> FIXED
            2, 34, 66, 98, 130, 162, 194, 226 -> TRAFFIC_LIGHT
            3, 36, 68, 100, 132, 164, 196, 228 -> TRAFFIC_LIGHT // red-light only
            4, 35, 67, 99, 131, 163, 195, 227 -> SECTION
            5, 33, 39, 65, 71, 97, 103, 129, 161, 193, 199, 225, 231 -> MOBILE
            14, 46, 78, 110, 142, 174, 206, 238 -> POLICE_POST
            6, 37, 69, 101, 133, 165, 197, 229 -> RAILWAY
            // SCO «муляж» often exported as low-priority / POI-like codes — treat unknown with speed 0 as OTHER
            else -> OTHER
        }
    }
}

/** Arrow on the widget: camera monitors same travel way vs oncoming. */
enum class SpeedCamRelativeDirection {
    SAME,
    ONCOMING,
}

data class SpeedCamAlert(
    val point: SpeedCamPoint,
    val distanceM: Double,
    val relative: SpeedCamRelativeDirection,
    val overLimit: Boolean,
)

data class SpeedCamUiState(
    val installed: Boolean = false,
    val pointCount: Int = 0,
    val installedAtEpochMs: Long = 0L,
    val sourceLabel: String = "",
    val alert: SpeedCamAlert? = null,
    /** All points in the current radius (for optional map markers). */
    val nearbyForMap: List<SpeedCamMapMarker> = emptyList(),
) {
    companion object {
        val EMPTY = SpeedCamUiState()
    }
}

data class SpeedCamMapMarker(
    val lat: Double,
    val lon: Double,
    val category: SpeedCamCategory,
    val speedKmh: Int,
    val isAlertTarget: Boolean,
)

data class SpeedCamInstallManifest(
    val version: Int = 1,
    val pointCount: Int = 0,
    val bytesOnDisk: Long = 0L,
    val installedAtEpochMs: Long = 0L,
    val source: String = "",
    val sha256Hex: String = "",
    val format: String = "igoext",
)
