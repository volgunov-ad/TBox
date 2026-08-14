package vad.dashing.tbox.location.roadmatch

import android.util.JsonReader
import android.util.JsonToken
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.util.zip.GZIPInputStream
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.round
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
    /**
     * Travel restriction along [coords] order:
     * `0` both ways, `+1` only along coords, `-1` only against coords.
     * Missing field in old packs → `0`.
     */
    val oneway: Int = 0,
    /**
     * Posted numeric speed limit km/h for general traffic, both directions.
     * Missing / non-numeric OSM values → `null`.
     */
    val maxspeed: Int? = null,
    /** Limit along [coords] order (`maxspeed:forward`). */
    val maxspeedForward: Int? = null,
    /** Limit against [coords] order (`maxspeed:backward`). */
    val maxspeedBackward: Int? = null,
    /** OSM `ref` (road number), omitted when blank. */
    val ref: String? = null,
    /** Source OSM way id; several pack edges may share one way after junction splits. */
    val wayId: Long? = null,
) {
    val pointCount: Int get() = coords.size / 2

    fun lonAt(i: Int): Double = coords[i * 2]
    fun latAt(i: Int): Double = coords[i * 2 + 1]

    /** Effective posted limit for travel along or against [coords]. */
    fun speedLimitKmh(travelAgainstCoords: Boolean): Int? {
        val directed = if (travelAgainstCoords) maxspeedBackward else maxspeedForward
        return directed ?: maxspeed
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RoadEdge) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/** Lightweight header for coverage checks without loading all edges. */
data class RoadPackHeader(
    val regionId: String,
    val graphVersion: Int,
    /** west, south, east, north */
    val bbox: DoubleArray,
) {
    fun contains(lat: Double, lon: Double): Boolean = RoadGraph.bboxContains(bbox, lat, lon)
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

    fun contains(lat: Double, lon: Double): Boolean = bboxContains(bbox, lat, lon)

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

        fun load(file: File): RoadGraph =
            BufferedInputStream(file.inputStream(), 64 * 1024).use { load(it) }

        fun load(bytes: ByteArray): RoadGraph =
            load(ByteArrayInputStream(bytes))

        fun bboxContains(bbox: DoubleArray, lat: Double, lon: Double): Boolean {
            if (bbox.size < 4) return false
            return lon in bbox[0]..bbox[2] && lat in bbox[1]..bbox[3]
        }

        /**
         * Read magic + gzip JSON only until `regionId`/`bbox`/`format` are known, then stop.
         * Avoids building the full edge list just to decide coverage.
         */
        fun peekHeader(file: File): RoadPackHeader =
            BufferedInputStream(file.inputStream(), 64 * 1024).use { peekHeader(it) }

        fun peekHeader(input: InputStream): RoadPackHeader {
            val magicBuf = ByteArray(8)
            var off = 0
            while (off < 8) {
                val n = input.read(magicBuf, off, 8 - off)
                if (n <= 0) throw IllegalArgumentException("tboxroads too short")
                off += n
            }
            val magic = magicBuf.toString(Charsets.US_ASCII)
            require(magic == MAGIC) { "bad magic: $magic" }
            return GZIPInputStream(input).use { gz ->
                InputStreamReader(gz, Charsets.UTF_8).use { reader ->
                    peekHeaderReader(reader)
                }
            }
        }

        private fun peekHeaderReader(reader: Reader): RoadPackHeader {
            JsonReader(reader).use { json ->
                json.beginObject()
                var format = 0
                var regionId = ""
                var graphVersion = 1
                var bbox = DoubleArray(0)
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "format" -> format = json.nextInt()
                        "regionId" -> regionId = json.nextString().trim()
                        "graphVersion" -> graphVersion = json.nextInt().coerceAtLeast(1)
                        "bbox" -> bbox = readDoubleArray(json)
                        else -> json.skipValue()
                    }
                    if (format == FORMAT_V1 && regionId.isNotEmpty() && bbox.size >= 4) {
                        return RoadPackHeader(
                            regionId = regionId,
                            graphVersion = graphVersion,
                            bbox = DoubleArray(4) { i -> bbox[i] },
                        )
                    }
                }
                json.endObject()
                require(format == FORMAT_V1) { "unsupported format: $format" }
                require(regionId.isNotEmpty()) { "missing regionId" }
                require(bbox.size >= 4) { "bbox requires 4 numbers" }
                return RoadPackHeader(
                    regionId = regionId,
                    graphVersion = graphVersion,
                    bbox = DoubleArray(4) { i -> bbox[i] },
                )
            }
        }

        /**
         * Stream-parse a `.tboxroads` pack. Does **not** buffer the whole gzip payload or JSON
         * as one String/`JSONObject` (those OOMed on large oblast packs on the HU heap).
         */
        fun load(input: InputStream): RoadGraph {
            val magicBuf = ByteArray(8)
            var off = 0
            while (off < 8) {
                val n = input.read(magicBuf, off, 8 - off)
                if (n <= 0) throw IllegalArgumentException("tboxroads too short")
                off += n
            }
            val magic = magicBuf.toString(Charsets.US_ASCII)
            require(magic == MAGIC) { "bad magic: $magic" }
            // GZIPInputStream owns/wraps [input]; do not close [input] separately mid-parse.
            return GZIPInputStream(input).use { gz ->
                InputStreamReader(gz, Charsets.UTF_8).use { reader ->
                    parseJsonReader(reader)
                }
            }
        }

        fun parseJson(json: String): RoadGraph = parseJsonReader(StringReader(json))

        private fun parseJsonReader(reader: Reader): RoadGraph {
            JsonReader(reader).use { json ->
                json.beginObject()
                var format = 0
                var regionId = ""
                var graphVersion = 1
                var bbox = DoubleArray(0)
                var edges: ArrayList<RoadEdge> = ArrayList(256)
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "format" -> format = json.nextInt()
                        "regionId" -> regionId = json.nextString().trim()
                        "graphVersion" -> graphVersion = json.nextInt().coerceAtLeast(1)
                        "bbox" -> bbox = readDoubleArray(json)
                        "edges" -> edges = readEdges(json)
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                require(format == FORMAT_V1) { "unsupported format: $format" }
                require(regionId.isNotEmpty()) { "missing regionId" }
                require(bbox.size >= 4) { "bbox requires 4 numbers" }
                return RoadGraph(
                    regionId = regionId,
                    graphVersion = graphVersion,
                    bbox = DoubleArray(4) { i -> bbox[i] },
                    edges = edges,
                )
            }
        }

        private fun readDoubleArray(json: JsonReader): DoubleArray {
            val tmp = ArrayList<Double>(4)
            json.beginArray()
            while (json.hasNext()) {
                tmp.add(json.nextDouble())
            }
            json.endArray()
            return DoubleArray(tmp.size) { tmp[it] }
        }

        private fun readEdges(json: JsonReader): ArrayList<RoadEdge> {
            val edges = ArrayList<RoadEdge>(4096)
            var index = 0
            json.beginArray()
            while (json.hasNext()) {
                if (json.peek() == JsonToken.NULL) {
                    json.nextNull()
                    index++
                    continue
                }
                val edge = readEdge(json, fallbackId = (index + 1).toLong())
                if (edge != null) edges.add(edge)
                index++
            }
            json.endArray()
            return edges
        }

        private fun readEdge(json: JsonReader, fallbackId: Long): RoadEdge? {
            var id = fallbackId
            var highwayClass = "residential"
            var lengthM = 0.0
            var fromNode = 0
            var toNode = 0
            var oneway = 0
            var maxspeed: Int? = null
            var maxspeedForward: Int? = null
            var maxspeedBackward: Int? = null
            var ref: String? = null
            var wayId: Long? = null
            var coords: DoubleArray? = null
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "id" -> id = json.nextLong()
                    "class" -> highwayClass = json.nextString()
                    "lengthM" -> lengthM = json.nextDouble().coerceAtLeast(0.0)
                    "from" -> fromNode = json.nextInt()
                    "to" -> toNode = json.nextInt()
                    "oneway" -> oneway = json.nextInt().coerceIn(-1, 1)
                    "maxspeed" -> maxspeed = readOptionalSpeedKmh(json)
                    "maxspeedForward" -> maxspeedForward = readOptionalSpeedKmh(json)
                    "maxspeedBackward" -> maxspeedBackward = readOptionalSpeedKmh(json)
                    "ref" -> ref = readOptionalString(json)
                    "wayId" -> wayId = readOptionalLong(json)
                    "coords" -> coords = readCoords(json)
                    else -> json.skipValue()
                }
            }
            json.endObject()
            val c = coords ?: return null
            if (c.size < 4) return null
            return RoadEdge(
                id = id,
                highwayClass = highwayClass,
                lengthM = lengthM,
                fromNode = fromNode,
                toNode = toNode,
                coords = c,
                oneway = oneway,
                maxspeed = maxspeed,
                maxspeedForward = maxspeedForward,
                maxspeedBackward = maxspeedBackward,
                ref = ref,
                wayId = wayId,
            )
        }

        private fun readOptionalSpeedKmh(json: JsonReader): Int? {
            val raw = when (json.peek()) {
                JsonToken.NULL -> {
                    json.nextNull()
                    return null
                }
                JsonToken.NUMBER -> json.nextDouble()
                JsonToken.STRING -> {
                    val s = json.nextString().trim()
                    s.toDoubleOrNull() ?: return null
                }
                else -> {
                    json.skipValue()
                    return null
                }
            }
            if (!raw.isFinite()) return null
            val kmh = round(raw).toInt()
            return kmh.takeIf { it in 1..200 }
        }

        private fun readOptionalLong(json: JsonReader): Long? {
            return when (json.peek()) {
                JsonToken.NULL -> {
                    json.nextNull()
                    null
                }
                JsonToken.NUMBER -> json.nextLong()
                JsonToken.STRING -> json.nextString().trim().toLongOrNull()
                else -> {
                    json.skipValue()
                    null
                }
            }?.takeIf { it > 0L }
        }

        private fun readOptionalString(json: JsonReader): String? {
            return when (json.peek()) {
                JsonToken.NULL -> {
                    json.nextNull()
                    null
                }
                JsonToken.STRING -> json.nextString().trim().ifEmpty { null }
                else -> {
                    json.skipValue()
                    null
                }
            }
        }

        private fun readCoords(json: JsonReader): DoubleArray {
            // Growable buffer of interleaved lon, lat.
            var buf = DoubleArray(32)
            var ci = 0
            json.beginArray()
            while (json.hasNext()) {
                if (json.peek() == JsonToken.NULL) {
                    json.nextNull()
                    continue
                }
                json.beginArray()
                val lon = if (json.hasNext()) json.nextDouble() else 0.0
                val lat = if (json.hasNext()) json.nextDouble() else 0.0
                while (json.hasNext()) json.skipValue()
                json.endArray()
                if (ci + 2 > buf.size) {
                    buf = buf.copyOf(buf.size * 2)
                }
                buf[ci++] = lon
                buf[ci++] = lat
            }
            json.endArray()
            return if (ci == buf.size) buf else buf.copyOf(ci)
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

    /** Reject matched-edge display when the resolved polyline is this far from the pose. */
    const val MATCHED_EDGE_MAX_CROSS_M = 80.0

    fun peek(regionId: String): RoadGraph? = cache[regionId]

    /** Snapshot of currently cached tile/pack graphs (for overlay / debug). */
    fun cachedGraphs(): List<RoadGraph> = cache.values.toList()

    /**
     * Find an edge by pack [regionId] + [edgeId] across cached tiles
     * (`cache` keys look like `regionId/tileId`).
     *
     * Prefer the copy nearest to [nearLat]/[nearLon]: tiles share stable edge ids from the
     * monolith, but a stale id after tile eviction must not resolve to another region's
     * sequential id (that painted a blue road far from the green shadow).
     */
    fun findEdge(
        regionId: String,
        edgeId: Long,
        nearLat: Double? = null,
        nearLon: Double? = null,
        maxCrossTrackM: Double = MATCHED_EDGE_MAX_CROSS_M,
    ): RoadEdge? {
        var best: RoadEdge? = null
        var bestCross = Double.POSITIVE_INFINITY
        var any: RoadEdge? = null
        for ((key, g) in cache) {
            if (g.regionId != regionId && key != regionId && !key.startsWith("$regionId/")) {
                continue
            }
            val edge = g.edgeById[edgeId] ?: continue
            if (nearLat == null || nearLon == null) {
                return edge
            }
            any = edge
            val proj = RoadMapMatcher.projectOntoEdge(nearLat, nearLon, edge) ?: continue
            if (proj.crossTrackM < bestCross) {
                bestCross = proj.crossTrackM
                best = edge
            }
        }
        if (best != null && bestCross <= maxCrossTrackM) return best
        // No near hit — do not return a far duplicate / wrong geometry.
        if (nearLat != null && nearLon != null) return null
        return any
    }

    fun put(regionId: String, graph: RoadGraph) {
        cache = cache + (regionId to graph)
    }

    fun remove(regionId: String) {
        cache = cache.filterKeys { it != regionId && !it.startsWith("$regionId/") }
    }

    /** Keep only graphs selected around the current pose (tile cache eviction). */
    fun retainOnly(keys: Set<String>) {
        cache = cache.filterKeys { it in keys }
    }

    fun clear() {
        cache = emptyMap()
    }

    fun loadOrGet(regionId: String, file: File): RoadGraph {
        peek(regionId)?.let { return it }
        val g = try {
            RoadGraph.load(file)
        } catch (oom: OutOfMemoryError) {
            // Drop cached packs so the next attempt may succeed with a smaller set.
            clear()
            throw oom
        }
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
            val headerOk = runCatching { RoadGraph.peekHeader(file).contains(lat, lon) }
                .getOrDefault(true)
            if (!headerOk) continue
            val g = runCatching { loadOrGet(id, file) }.getOrNull() ?: continue
            if (g.contains(lat, lon) && g.edges.isNotEmpty()) {
                out.add(g)
            }
        }
        return out
    }
}
