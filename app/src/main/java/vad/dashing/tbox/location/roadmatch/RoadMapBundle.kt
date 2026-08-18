package vad.dashing.tbox.location.roadmatch

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class RoadMapTile(
    val id: String,
    val file: String,
    /** west, south, east, north; includes builder overlap. */
    val bbox: DoubleArray,
    val bytes: Long,
) {
    fun contains(lat: Double, lon: Double): Boolean = RoadGraph.bboxContains(bbox, lat, lon)
}

data class RoadMapBundleIndex(
    val regionId: String,
    val graphVersion: Int,
    val bbox: DoubleArray,
    val tiles: List<RoadMapTile>,
) {
    fun contains(lat: Double, lon: Double): Boolean = RoadGraph.bboxContains(bbox, lat, lon)

    fun covering(lat: Double, lon: Double): List<RoadMapTile> =
        tiles.filter { it.contains(lat, lon) }
}

object RoadMapBundle {
    const val INDEX_FILE = "index.json"
    const val INSTALL_SUFFIX = ".tboxroads.d"
    private const val MAX_ENTRIES = 20_000
    private const val MAX_INDEX_BYTES = 4 * 1024 * 1024L

    fun installDir(mapsDir: File, regionId: String): File =
        File(mapsDir, "$regionId$INSTALL_SUFFIX")

    fun stagingDir(mapsDir: File, regionId: String): File =
        File(mapsDir, "$regionId$INSTALL_SUFFIX.part")

    fun isBundle(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return file.inputStream().buffered().use { input ->
            input.read() == 'P'.code &&
                input.read() == 'K'.code &&
                input.read() == 3 &&
                input.read() == 4
        }
    }

    fun parseIndex(json: String): RoadMapBundleIndex {
        val root = JSONObject(json)
        require(root.optInt("format", 0) == 1) { "unsupported bundle format" }
        val regionId = root.optString("regionId").trim()
        require(regionId.isNotEmpty()) { "missing bundle regionId" }
        val bbox = readBbox(root.optJSONArray("bbox"))
        val arr = root.optJSONArray("tiles") ?: JSONArray()
        require(arr.length() in 1..MAX_ENTRIES) { "bundle has no tiles or too many tiles" }
        val tiles = ArrayList<RoadMapTile>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val file = o.optString("file").trim()
            require(id.isNotEmpty() && file.startsWith("tiles/") && file.endsWith(".tboxroads")) {
                "bad tile entry"
            }
            tiles.add(
                RoadMapTile(
                    id = id,
                    file = file,
                    bbox = readBbox(o.optJSONArray("bbox")),
                    bytes = o.optLong("bytes", 0L).coerceAtLeast(0L),
                ),
            )
        }
        require(tiles.isNotEmpty()) { "bundle has no valid tiles" }
        return RoadMapBundleIndex(
            regionId = regionId,
            graphVersion = root.optInt("graphVersion", 1).coerceAtLeast(1),
            bbox = bbox,
            tiles = tiles,
        )
    }

    fun loadIndex(installDir: File): RoadMapBundleIndex {
        val file = File(installDir, INDEX_FILE)
        require(file.isFile && file.length() in 1..MAX_INDEX_BYTES) { "missing bundle index" }
        return parseIndex(file.readText(Charsets.UTF_8))
    }

    /**
     * Extract and validate into [stagingDir]. Caller atomically swaps it into final location.
     * Tile payloads remain independently gzip-compressed `.tboxroads` files on disk.
     */
    fun extractAndValidate(
        zipFile: File,
        stagingDir: File,
        expectedRegionId: String,
        checkCancelled: () -> Unit = {},
    ): RoadMapBundleIndex {
        stagingDir.deleteRecursively()
        require(stagingDir.mkdirs()) { "cannot create bundle staging directory" }
        val root = stagingDir.canonicalFile
        var entries = 0
        try {
            ZipInputStream(BufferedInputStream(zipFile.inputStream(), 64 * 1024)).use { zip ->
                while (true) {
                    checkCancelled()
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= MAX_ENTRIES + 8) { "bundle has too many entries" }
                    val out = File(stagingDir, entry.name).canonicalFile
                    require(out.path.startsWith(root.path + File.separator)) { "unsafe bundle path" }
                    if (entry.isDirectory) {
                        require(out.mkdirs() || out.isDirectory) { "cannot create bundle directory" }
                    } else {
                        val parent = requireNotNull(out.parentFile)
                        require(parent.mkdirs() || parent.isDirectory) { "cannot create tile directory" }
                        FileOutputStream(out).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                checkCancelled()
                                val read = zip.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val index = loadIndex(stagingDir)
            require(index.regionId == expectedRegionId) {
                "bundle region mismatch: ${index.regionId}"
            }
            for (tile in index.tiles) {
                checkCancelled()
                val file = File(stagingDir, tile.file)
                require(file.isFile) { "missing tile: ${tile.id}" }
                val header = RoadGraph.peekHeader(file)
                require(header.regionId == expectedRegionId) { "tile region mismatch: ${tile.id}" }
                require(header.graphVersion == index.graphVersion) { "tile version mismatch: ${tile.id}" }
            }
            return index
        } catch (t: Throwable) {
            stagingDir.deleteRecursively()
            throw t
        }
    }

    fun directorySize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun readBbox(arr: JSONArray?): DoubleArray {
        require(arr != null && arr.length() >= 4) { "bbox requires 4 numbers" }
        return DoubleArray(4) { i -> arr.optDouble(i, Double.NaN) }.also { bbox ->
            require(bbox.all { it.isFinite() }) { "invalid bbox" }
            require(bbox[0] <= bbox[2] && bbox[1] <= bbox[3]) { "invalid bbox order" }
        }
    }
}
