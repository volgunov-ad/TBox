package vad.dashing.camdbench

/** Parsers for the camd side logs collected next to the CPU samples. */
object BenchLogs {

    /** `copy <id>: frames= fps= avg= ms max= ms` from camd_grab version 5 and later. */
    class GrabCopy(val inputId: Int, val frames: Int, val fps: Double, val avgMs: Double, val maxMs: Double)

    /** `cam<id> <file>: кадров N, пропущено M, X с` from CamdRecorder (camd 0.9.5+). */
    class RecorderSegment(val inputId: Int, val file: String, val frames: Int, val skipped: Int, val seconds: Double)

    /** Frames and span counted from a finished MP4 segment. */
    class SegmentFps(val inputId: Int, val file: String, val frames: Int, val seconds: Double) {
        val fps: Double get() = if (seconds > 0) (frames - 1).coerceAtLeast(0) / seconds else 0.0
    }

    class CameraFps(val inputId: Int, val segments: Int, val frames: Int, val seconds: Double) {
        val fps: Double get() = if (seconds > 0) frames / seconds else 0.0
    }

    class CopySummary(val inputId: Int, val periods: Int, val fps: Double, val avgMs: Double, val maxMs: Double)

    private val copyRegex = Regex(
        """copy (\d+): frames=(\d+) fps=([\d.]+) avg=([\d.]+) ms max=([\d.]+) ms""",
    )
    private val segmentRegex = Regex(
        """cam(\d+) (\S+): кадров (\d+), пропущено (\d+), ([\d.]+) с""",
    )
    private val segmentNameRegex = Regex("""cam(\d+)_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}(?:_\d+)?\.mp4""")

    fun parseGrabCopy(text: String): List<GrabCopy> = text.lineSequence().mapNotNull { line ->
        val m = copyRegex.find(line) ?: return@mapNotNull null
        GrabCopy(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toDouble(),
            m.groupValues[4].toDouble(),
            m.groupValues[5].toDouble(),
        )
    }.toList()

    /** Per camera: frame-weighted average copy time and the worst single copy. */
    fun summarizeCopy(lines: List<GrabCopy>): List<CopySummary> =
        lines.filter { it.frames > 0 }.groupBy { it.inputId }.toSortedMap().map { (id, rows) ->
            val frames = rows.sumOf { it.frames }
            CopySummary(
                inputId = id,
                periods = rows.size,
                fps = rows.sumOf { it.fps } / rows.size,
                avgMs = rows.sumOf { it.avgMs * it.frames } / frames,
                maxMs = rows.maxOf { it.maxMs },
            )
        }

    fun parseRecorderSegments(text: String): List<RecorderSegment> = text.lineSequence().mapNotNull { line ->
        val m = segmentRegex.find(line) ?: return@mapNotNull null
        RecorderSegment(
            m.groupValues[1].toInt(),
            m.groupValues[2],
            m.groupValues[3].toInt(),
            m.groupValues[4].toInt(),
            m.groupValues[5].toDouble(),
        )
    }.distinctBy { it.inputId to it.file }.toList()

    fun segmentInput(fileName: String): Int? =
        segmentNameRegex.matchEntire(fileName)?.groupValues?.get(1)?.toIntOrNull()

    fun summarizeFps(segments: List<SegmentFps>): List<CameraFps> =
        segments.filter { it.seconds > 0 }.groupBy { it.inputId }.toSortedMap().map { (id, rows) ->
            CameraFps(id, rows.size, rows.sumOf { (it.frames - 1).coerceAtLeast(0) }, rows.sumOf { it.seconds })
        }

    /** `recorder_folder` from camd's shared_prefs/camd.xml. */
    fun recorderFolder(prefsXml: String): String? {
        val m = Regex("""<string name="recorder_folder">([^<]*)</string>""").find(prefsXml) ?: return null
        return unescapeXml(m.groupValues[1]).ifBlank { null }
    }

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

    /** `key=value` tokens of a camd STATUS reply (`OK windows=none avm=stopped rec_active=1 …`). */
    fun statusFields(reply: String): Map<String, String> =
        reply.trim().split(' ').mapNotNull { token ->
            val eq = token.indexOf('=')
            if (eq <= 0) null else token.substring(0, eq) to token.substring(eq + 1)
        }.toMap()
}
