package vad.dashing.tbox.location.roadmatch

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * One-shot USB / SAF catalog: same region model as [RoadMapCatalog], plus a relative
 * [relativeFile] next to the JSON. SHA-256 is optional (published Yandex Disk catalogs
 * have [RoadMapRegion.bytes] but no hash).
 */
data class RoadMapOfflineRegion(
    val region: RoadMapRegion,
    /** Relative path under the catalog folder (sanitized). */
    val relativeFile: String,
    /** Lowercase hex SHA-256 of the pack file; empty if catalog omitted it. */
    val sha256: String,
)

data class RoadMapOfflineCatalog(
    val version: Int,
    val title: String,
    val regions: List<RoadMapOfflineRegion>,
)

object RoadMapOfflineCatalogParser {
    /**
     * Parse USB catalog JSON. Rejects path traversal / absolute / scheme paths.
     * Rows without a resolvable relative `file` / URL basename are skipped
     * (unpublished remote catalog entries). Duplicate [RoadMapRegion.id]: keep the
     * highest [RoadMapRegion.graphVersion]; same version with different file/hash →
     * [IllegalArgumentException].
     */
    fun parse(json: String): RoadMapOfflineCatalog {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        val title = root.optString("title", "").trim()
        val arr = root.optJSONArray("regions") ?: JSONArray()
        val raw = ArrayList<RoadMapOfflineRegion>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            if (id.isEmpty()) continue
            val relative = resolveRelativeFile(o) ?: continue
            val bboxArr = o.optJSONArray("bbox")
            val bbox = DoubleArray(4)
            if (bboxArr != null && bboxArr.length() >= 4) {
                for (j in 0 until 4) bbox[j] = bboxArr.optDouble(j, 0.0)
            }
            val sha = o.optString("sha256", "").trim().lowercase(Locale.US)
            raw.add(
                RoadMapOfflineRegion(
                    region = RoadMapRegion(
                        id = id,
                        country = o.optString("country", "RU").trim().uppercase(Locale.US),
                        titleRu = o.optString("title_ru", id),
                        titleEn = o.optString("title_en", id),
                        bbox = bbox,
                        url = "",
                        bytes = o.optLong("bytes", 0L).coerceAtLeast(0L),
                        graphVersion = o.optInt("graphVersion", 1).coerceAtLeast(1),
                    ),
                    relativeFile = relative,
                    sha256 = sha,
                ),
            )
        }
        return RoadMapOfflineCatalog(
            version = version,
            title = title,
            regions = dedupeRegions(raw),
        )
    }

    /**
     * Prefer `file`; else basename of `url` (no network). Returns null if unsafe/empty.
     */
    fun resolveRelativeFile(o: JSONObject): String? {
        val fromFile = o.optString("file", "").trim()
        if (fromFile.isNotEmpty()) return sanitizeRelativePackPath(fromFile)
        val url = o.optString("url", "").trim()
        if (url.isEmpty()) return null
        val name = runCatching {
            val path = URI(url).path
            path?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: url.substringAfterLast('/').substringAfterLast('\\')
        return sanitizeRelativePackPath(name)
    }

    /**
     * Allow only a relative path under the catalog folder.
     * Rejects absolute paths, `..`, URI schemes, backslashes-as-escape, empty.
     */
    fun sanitizeRelativePackPath(raw: String): String? {
        var s = raw.trim().replace('\\', '/')
        if (s.isEmpty()) return null
        if (s.startsWith("/")) return null
        if (s.contains("://")) return null
        // Drive letter or scheme-like "foo:bar"
        if (s.contains(':')) return null
        while (s.startsWith("./")) s = s.removePrefix("./")
        if (s.isEmpty() || s == "." || s == "..") return null
        val parts = s.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        // Conservative charset for pack names from our tools.
        val allowed = Regex("^[A-Za-z0-9._\\-]+$")
        if (parts.any { !allowed.matches(it) }) return null
        return parts.joinToString("/")
    }

    fun dedupeRegions(regions: List<RoadMapOfflineRegion>): List<RoadMapOfflineRegion> {
        val byId = LinkedHashMap<String, RoadMapOfflineRegion>()
        for (r in regions) {
            val prev = byId[r.region.id]
            if (prev == null) {
                byId[r.region.id] = r
                continue
            }
            when {
                r.region.graphVersion > prev.region.graphVersion -> byId[r.region.id] = r
                r.region.graphVersion < prev.region.graphVersion -> Unit
                r.sha256 != prev.sha256 || r.relativeFile != prev.relativeFile -> {
                    throw IllegalArgumentException(
                        "duplicate region ${r.region.id} graphVersion=${r.region.graphVersion} " +
                            "with conflicting file/hash",
                    )
                }
            }
        }
        return byId.values.toList()
    }
}
