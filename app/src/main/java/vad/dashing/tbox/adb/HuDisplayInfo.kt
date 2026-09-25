package vad.dashing.tbox.adb

import org.json.JSONArray
import org.json.JSONObject

/**
 * One display entry discovered on the head unit (physical or virtual).
 *
 * Cached globally for app-launcher «virtual display» mode; [displayId] is what
 * `am start --display` expects.
 */
data class HuDisplayInfo(
    val displayId: Int,
    val widthPx: Int,
    val heightPx: Int,
) {
    fun label(): String = "$displayId: ${widthPx}×${heightPx}"

    fun toJson(): JSONObject = JSONObject()
        .put(JSON_ID, displayId)
        .put(JSON_W, widthPx)
        .put(JSON_H, heightPx)

    companion object {
        private const val JSON_ID = "id"
        private const val JSON_W = "w"
        private const val JSON_H = "h"

        fun fromJson(obj: JSONObject): HuDisplayInfo? {
            val id = obj.optInt(JSON_ID, Int.MIN_VALUE)
            val w = obj.optInt(JSON_W, 0)
            val h = obj.optInt(JSON_H, 0)
            if (id == Int.MIN_VALUE || w < 1 || h < 1) return null
            return HuDisplayInfo(id, w, h)
        }

        fun listToJson(list: List<HuDisplayInfo>): String {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(raw: String?): List<HuDisplayInfo> {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return emptyList()
            return runCatching {
                val array = JSONArray(text)
                buildList {
                    for (i in 0 until array.length()) {
                        fromJson(array.getJSONObject(i))?.let { add(it) }
                    }
                }
            }.getOrDefault(emptyList())
        }
    }
}

/**
 * Parses `dumpsys display` / `dumpsys activity displays` text for display id + size.
 * Keeps all displays (including id 0).
 */
object HuDisplayDumpParser {
    private val displayIdEquals = Regex("""\bmDisplayId\s*=\s*(\d+)""")
    private val displayIdSpace = Regex("""\bdisplayId\s+(\d+)\b""")
    private val displayIdEqualsAlt = Regex("""\bdisplayId\s*=\s*(\d+)""")
    private val sizePattern = Regex("""\b(\d{3,5})\s*[x×]\s*(\d{3,5})\b""")
    /** e.g. `Display #5` sections in activity dumps. */
    private val displayHash = Regex("""Display\s+#(\d+)\b""")

    fun parse(dumpText: String): List<HuDisplayInfo> {
        if (dumpText.isBlank()) return emptyList()
        val byId = linkedMapOf<Int, HuDisplayInfo>()
        var pendingId: Int? = null

        for (rawLine in dumpText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            extractDisplayId(line)?.let { pendingId = it }

            val sizeMatch = sizePattern.find(line) ?: continue
            val id = pendingId ?: continue
            val w = sizeMatch.groupValues[1].toIntOrNull() ?: continue
            val h = sizeMatch.groupValues[2].toIntOrNull() ?: continue
            if (w < 100 || h < 100) continue
            byId.putIfAbsent(id, HuDisplayInfo(displayId = id, widthPx = w, heightPx = h))
        }

        // Fallback: scan full text for mDisplayId then nearest WxH token order
        if (byId.isEmpty()) {
            var lastId: Int? = null
            val token = Regex("""mDisplayId\s*=\s*(\d+)|(\d{3,5})\s*[x×]\s*(\d{3,5})""")
            for (m in token.findAll(dumpText)) {
                val idGroup = m.groupValues[1]
                if (idGroup.isNotEmpty()) {
                    lastId = idGroup.toIntOrNull()
                    continue
                }
                val w = m.groupValues[2].toIntOrNull() ?: continue
                val h = m.groupValues[3].toIntOrNull() ?: continue
                val id = lastId ?: continue
                if (w >= 100 && h >= 100) {
                    byId.putIfAbsent(id, HuDisplayInfo(id, w, h))
                }
            }
        }

        return byId.values.sortedBy { it.displayId }
    }

    private fun extractDisplayId(line: String): Int? {
        displayIdEquals.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        displayIdEqualsAlt.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        displayIdSpace.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        displayHash.find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return null
    }
}
