package vad.dashing.tbox

enum class ThemeSection(val jsonKey: String) {
    MAIN_SCREEN("mainScreen"),
    FLOATING_PANELS("floatingPanels"),
    APP_ICONS("appIcons"),
    ;

    companion object {
        fun fromJsonKey(key: String): ThemeSection? =
            entries.firstOrNull { it.jsonKey == key }

        fun parseJsonArray(arr: org.json.JSONArray?): Set<ThemeSection> {
            if (arr == null) return emptySet()
            val out = linkedSetOf<ThemeSection>()
            for (i in 0 until arr.length()) {
                fromJsonKey(arr.optString(i))?.let { out.add(it) }
            }
            return out
        }

        fun toJsonArray(sections: Set<ThemeSection>): org.json.JSONArray {
            val arr = org.json.JSONArray()
            sections.forEach { arr.put(it.jsonKey) }
            return arr
        }
    }
}
