package vad.dashing.tbox.location.roadmatch

import org.json.JSONArray
import org.json.JSONObject

/**
 * Offline road-map catalog (bundled [assets/road_maps/catalog.json] or remote later).
 * Packs are `.tboxroads`; Phase A may use `asset://…` stubs or empty URL (not yet published).
 */
data class RoadMapRegion(
    val id: String,
    val country: String,
    val titleRu: String,
    val titleEn: String,
    /** west, south, east, north */
    val bbox: DoubleArray,
    val url: String,
    val bytes: Long,
    val graphVersion: Int,
) {
    val hasDownloadUrl: Boolean
        get() = url.isNotBlank()

    fun title(isRussian: Boolean): String = if (isRussian) titleRu else titleEn

    fun contains(lat: Double, lon: Double): Boolean {
        if (bbox.size < 4) return false
        val west = bbox[0]
        val south = bbox[1]
        val east = bbox[2]
        val north = bbox[3]
        return lon in west..east && lat in south..north
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoadMapRegion) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class RoadMapCatalog(
    val version: Int,
    val regions: List<RoadMapRegion>,
) {
    fun regionsByCountry(): Map<String, List<RoadMapRegion>> =
        regions.groupBy { it.country }.mapValues { (_, list) -> list.sortedBy { it.id } }

    fun findById(id: String): RoadMapRegion? = regions.firstOrNull { it.id == id }

    fun covering(lat: Double, lon: Double): List<RoadMapRegion> =
        regions.filter { it.contains(lat, lon) }

    companion object {
        /** Display order for Geoposition download UI. */
        val COUNTRY_ORDER: List<String> = listOf("RU", "BY")

        fun parse(json: String): RoadMapCatalog {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            val arr = root.optJSONArray("regions") ?: JSONArray()
            val regions = ArrayList<RoadMapRegion>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isEmpty()) continue
                val bboxArr = o.optJSONArray("bbox")
                val bbox = DoubleArray(4)
                if (bboxArr != null && bboxArr.length() >= 4) {
                    for (j in 0 until 4) bbox[j] = bboxArr.optDouble(j, 0.0)
                }
                regions.add(
                    RoadMapRegion(
                        id = id,
                        country = o.optString("country", "RU").trim().uppercase(),
                        titleRu = o.optString("title_ru", id),
                        titleEn = o.optString("title_en", id),
                        bbox = bbox,
                        url = o.optString("url", "").trim(),
                        bytes = o.optLong("bytes", 0L).coerceAtLeast(0L),
                        graphVersion = o.optInt("graphVersion", 1).coerceAtLeast(1),
                    ),
                )
            }
            return RoadMapCatalog(version = version, regions = regions)
        }
    }
}

data class RoadMapInstallEntry(
    val id: String,
    val graphVersion: Int,
    val fileName: String,
    val bytesOnDisk: Long,
    val installedAtEpochMs: Long,
)

object RoadMapInstallManifest {
    fun parse(json: String?): Map<String, RoadMapInstallEntry> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            val arr = root.optJSONArray("installed") ?: return emptyMap()
            val out = LinkedHashMap<String, RoadMapInstallEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isEmpty()) continue
                out[id] = RoadMapInstallEntry(
                    id = id,
                    graphVersion = o.optInt("graphVersion", 1),
                    fileName = o.optString("fileName", "$id.tboxroads"),
                    bytesOnDisk = o.optLong("bytesOnDisk", 0L),
                    installedAtEpochMs = o.optLong("installedAtEpochMs", 0L),
                )
            }
            out
        }.getOrDefault(emptyMap())
    }

    fun toJson(entries: Map<String, RoadMapInstallEntry>): String {
        val arr = JSONArray()
        for (e in entries.values.sortedBy { it.id }) {
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("graphVersion", e.graphVersion)
                    .put("fileName", e.fileName)
                    .put("bytesOnDisk", e.bytesOnDisk)
                    .put("installedAtEpochMs", e.installedAtEpochMs),
            )
        }
        return JSONObject().put("version", 1).put("installed", arr).toString()
    }
}
