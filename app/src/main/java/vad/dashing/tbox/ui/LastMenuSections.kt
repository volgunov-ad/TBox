package vad.dashing.tbox.ui

import org.json.JSONObject

/**
 * Persisted horizontal section keys per left-menu tab
 * ([LeftMenuTabField.id] → section enum [Enum.name]).
 *
 * Missing / unknown keys resolve to the first section (index 0).
 */
object LastMenuSections {
    fun parse(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val tab = keys.next()
                    val section = root.optString(tab, "").trim()
                    if (tab.isNotBlank() && section.isNotEmpty()) {
                        put(tab, section)
                    }
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun serialize(map: Map<String, String>): String {
        val root = JSONObject()
        map.entries.sortedBy { it.key }.forEach { (tab, section) ->
            if (tab.isNotBlank() && section.isNotBlank()) {
                root.put(tab, section)
            }
        }
        return root.toString()
    }

    /** Index of [savedSectionKey] in [sectionKeys], or 0 if missing/unknown. */
    fun resolveIndex(savedSectionKey: String?, sectionKeys: List<String>): Int {
        if (sectionKeys.isEmpty()) return 0
        if (savedSectionKey.isNullOrBlank()) return 0
        val idx = sectionKeys.indexOf(savedSectionKey)
        return if (idx >= 0) idx else 0
    }
}
