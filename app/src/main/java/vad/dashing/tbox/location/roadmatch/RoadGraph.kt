package vad.dashing.tbox.location.roadmatch

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline road graph loaded from a `.tboxroads` v1 pack.
 * See [docs/TBOXROADS_FORMAT_RU.md].
 */
data class RoadEdge(
    val id: Long,
    val highwayClass: String,
    val lengthM: Double,
    val fromNode: Int,
    val toNode: Int,
    /** Interleaved lon, lat (WGS84). Size = pointCount * 2. */
    val coords: DoubleArray,
) {
    val pointCount: Int get() = coords.size / 2

    fun lonAt(i: Int): Double = coords[i * 2]
    fun latAt(i: Int): Double = coords[i * 2 + 1]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoadEdge) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class RoadGraph(
    val regionId: String,
    val graphVersion: Int,
    /** west, south, east, north */
    val bbox: DoubleArray,
    val edges: List<RoadEdge>,
) {
    /** Edge id → edge. */
    val edgeById: Map<Long, RoadEdge> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        edges.associateBy { it.id }
    }

    /**
     * Undirected adjacency: edges that share an endpoint (pack `from`/`to` and/or
     * spatially clustered endpoints ≈1 m). Built once per loaded graph.
     */
    private val adjacency: Map<Long, Set<Long>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildAdjacency()
    }

    fun contains(lat: Double, lon: Double): Boolean {
        if (bbox.size < 4) return false
        return lon in bbox[0]..bbox[2] && lat in bbox[1]..bbox[3]
    }

    fun neighbors(edgeId: Long): Set<Long> = adjacency[edgeId].orEmpty()

    fun isConnected(edgeIdA: Long, edgeIdB: Long): Boolean {
        if (edgeIdA == edgeIdB) return true
        return adjacency[edgeIdA]?.contains(edgeIdB) == true
    }

    /**
     * Edges whose polyline comes within [radiusM] of (lat, lon).
     * Brute-force distance-to-segment; fine for regional packs with match throttle.
     */
    fun edgesNear(lat: Double, lon: Double, radiusM: Double): List<RoadEdge> {
        if (radiusM < 0.0 || edges.isEmpty()) return emptyList()
        // Cheap reject: expand bbox by ~radius in degrees (coarse).
        val padDeg = radiusM / 111_000.0 * 1.2
        val west = bbox[0] - padDeg
        val south = bbox[1] - padDeg
        val east = bbox[2] + padDeg
        val north = bbox[3] + padDeg
        if (lon !in west..east || lat !in south..north) return emptyList()

        val out = ArrayList<RoadEdge>()
        for (edge in edges) {
            if (minDistanceM(lat, lon, edge) <= radiusM) {
                out.add(edge)
            }
        }
        return out
    }

    private fun buildAdjacency(): Map<Long, Set<Long>> {
        if (edges.isEmpty()) return emptyMap()
        val buckets = HashMap<String, ArrayList<Long>>()
        fun add(key: String, edgeId: Long) {
            buckets.getOrPut(key) { ArrayList(4) }.add(edgeId)
        }
        for (edge in edges) {
            if (edge.pointCount < 2) continue
            // Pack-declared nodes (shared when tool assigned them correctly).
            add("n:${edge.fromNode}", edge.id)
            add("n:${edge.toNode}", edge.id)
            // Spatial endpoints — repairs packs that used unique per-edge node ids.
            add(coordKey(edge.latAt(0), edge.lonAt(0)), edge.id)
            val last = edge.pointCount - 1
            add(coordKey(edge.latAt(last), edge.lonAt(last)), edge.id)
        }
        val adj = HashMap<Long, HashSet<Long>>(edges.size)
        for (edge in edges) {
            adj[edge.id] = HashSet()
        }
        for (group in buckets.values) {
            if (group.size < 2) continue
            val unique = group.distinct()
            if (unique.size < 2) continue
            for (i in unique.indices) {
                for (j in i + 1 until unique.size) {
                    val a = unique[i]
                    val b = unique[j]
                    adj[a]?.add(b)
                    adj[b]?.add(a)
                }
            }
        }
        return adj
    }

    companion object {
        const val MAGIC = "TBOXRDS1"
        const val FORMAT_V1 = 1
        /** ~1.1 m grid for endpoint clustering. */
        private const val COORD_QUANT = 100_000.0

        private fun coordKey(lat: Double, lon: Double): String {
            val ilat = Math.round(lat * COORD_QUANT)
            val ilon = Math.round(lon * COORD_QUANT)
            return "c:$ilat:$ilon"
        }

        fun load(file: File): RoadGraph = file.inputStream().use { load(it) }

        fun load(bytes: ByteArray): RoadGraph =
            load(ByteArrayInputStream(bytes))

        fun load(input: InputStream): RoadGraph {
            val all = input.readBytes()
            require(all.size >= 8) { "tboxroads too short" }
            val magic = all.copyOfRange(0, 8).toString(Charsets.US_ASCII)
            require(magic == MAGIC) { "bad magic: $magic" }
            val jsonText = GZIPInputStream(ByteArrayInputStream(all, 8, all.size - 8))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            return parseJson(jsonText)
        }

        fun parseJson(json: String): RoadGraph {
            val root = JSONObject(json)
            val format = root.optInt("format", 0)
            require(format == FORMAT_V1) { "unsupported format: $format" }
            val regionId = root.optString("regionId").trim()
            require(regionId.isNotEmpty()) { "missing regionId" }
            val graphVersion = root.optInt("graphVersion", 1).coerceAtLeast(1)
            val bboxArr = root.optJSONArray("bbox") ?: JSONArray()
            require(bboxArr.length() >= 4) { "bbox requires 4 numbers" }
            val bbox = DoubleArray(4) { i -> bboxArr.optDouble(i, 0.0) }
            val edgesArr = root.optJSONArray("edges") ?: JSONArray()
            val edges = ArrayList<RoadEdge>(edgesArr.length())
            for (i in 0 until edgesArr.length()) {
                val o = edgesArr.optJSONObject(i) ?: continue
                val coordsArr = o.optJSONArray("coords") ?: continue
                if (coordsArr.length() < 2) continue
                val coords = DoubleArray(coordsArr.length() * 2)
                var ci = 0
                for (p in 0 until coordsArr.length()) {
                    val pt = coordsArr.optJSONArray(p) ?: continue
                    if (pt.length() < 2) continue
                    coords[ci++] = pt.optDouble(0, 0.0)
                    coords[ci++] = pt.optDouble(1, 0.0)
                }
                if (ci < 4) continue
                val trimmed = if (ci == coords.size) coords else coords.copyOf(ci)
                edges.add(
                    RoadEdge(
                        id = o.optLong("id", (i + 1).toLong()),
                        highwayClass = o.optString("class", "residential"),
                        lengthM = o.optDouble("lengthM", 0.0).coerceAtLeast(0.0),
                        fromNode = o.optInt("from", 0),
                        toNode = o.optInt("to", 0),
                        coords = trimmed,
                    ),
                )
            }
            return RoadGraph(
                regionId = regionId,
                graphVersion = graphVersion,
                bbox = bbox,
                edges = edges,
            )
        }

        /** Great-circle distance metres (haversine). */
        fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dPhi = Math.toRadians(lat2 - lat1)
            val dLmb = Math.toRadians(lon2 - lon1)
            val a = sin(dPhi / 2) * sin(dPhi / 2) +
                cos(p1) * cos(p2) * sin(dLmb / 2) * sin(dLmb / 2)
            return 2 * r * asin(min(1.0, sqrt(a)))
        }

        fun minDistanceM(lat: Double, lon: Double, edge: RoadEdge): Double {
            var best = Double.POSITIVE_INFINITY
            val n = edge.pointCount
            if (n < 2) return best
            for (i in 0 until n - 1) {
                val d = distanceToSegmentM(
                    lat, lon,
                    edge.latAt(i), edge.lonAt(i),
                    edge.latAt(i + 1), edge.lonAt(i + 1),
                )
                best = min(best, d)
            }
            return best
        }

        /**
         * Approximate distance from point to segment using local equirectangular projection
         * around the point (good for tens of metres…few km).
         */
        fun distanceToSegmentM(
            lat: Double,
            lon: Double,
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val meanLat = Math.toRadians((lat + lat1 + lat2) / 3.0)
            val mPerDegLat = 111_320.0
            val mPerDegLon = 111_320.0 * cos(meanLat)
            val x = (lon - lon1) * mPerDegLon
            val y = (lat - lat1) * mPerDegLat
            val dx = (lon2 - lon1) * mPerDegLon
            val dy = (lat2 - lat1) * mPerDegLat
            val len2 = dx * dx + dy * dy
            if (len2 < 1e-6) {
                return sqrt(x * x + y * y)
            }
            val t = ((x * dx + y * dy) / len2).coerceIn(0.0, 1.0)
            val px = t * dx
            val py = t * dy
            return sqrt((x - px) * (x - px) + (y - py) * (y - py))
        }
    }
}

/** Cache of loaded graphs for installed packs (process-wide). */
object RoadGraphStore {
    @Volatile
    private var cache: Map<String, RoadGraph> = emptyMap()

    fun peek(regionId: String): RoadGraph? = cache[regionId]

    fun put(regionId: String, graph: RoadGraph) {
        cache = cache + (regionId to graph)
    }

    fun remove(regionId: String) {
        cache = cache - regionId
    }

    fun clear() {
        cache = emptyMap()
    }

    fun loadOrGet(regionId: String, file: File): RoadGraph {
        peek(regionId)?.let { return it }
        val g = RoadGraph.load(file)
        put(regionId, g)
        return g
    }

    fun coveringInstalled(
        lat: Double,
        lon: Double,
        installedIds: Collection<String>,
        fileFor: (String) -> File,
    ): List<RoadGraph> {
        val out = ArrayList<RoadGraph>()
        for (id in installedIds) {
            val file = fileFor(id)
            if (!file.isFile) continue
            val g = runCatching { loadOrGet(id, file) }.getOrNull() ?: continue
            if (g.contains(lat, lon) && g.edges.isNotEmpty()) {
                out.add(g)
            }
        }
        return out
    }
}
