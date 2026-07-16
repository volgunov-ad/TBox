package vad.dashing.tbox

import org.json.JSONArray

/**
 * Fine-grained theme application scope. Stored in DataStore and manifest on activation.
 * [ThemeSection] remains the export format in [theme.json]; apply targets control what is
 * imported from cache and which asset lookups are active at runtime.
 */
enum class ThemeApplyTarget(val jsonKey: String) {
    MAIN_SCREEN_WALLPAPERS("mainScreenWallpapers"),
    TILE_BACKGROUNDS("tileBackgrounds"),
    APP_ICONS("appIcons"),
    MAIN_SCREEN_PANELS("mainScreenPanels"),
    FLOATING_PANELS("floatingPanels"),
    ;

    companion object {
        fun fromJsonKey(key: String): ThemeApplyTarget? =
            entries.firstOrNull { it.jsonKey == key }

        fun parseJsonArray(arr: JSONArray?): Set<ThemeApplyTarget> {
            if (arr == null) return emptySet()
            val out = linkedSetOf<ThemeApplyTarget>()
            for (i in 0 until arr.length()) {
                fromJsonKey(arr.optString(i))?.let { out.add(it) }
            }
            return out
        }

        fun toJsonArray(targets: Set<ThemeApplyTarget>): JSONArray {
            val arr = JSONArray()
            entries.forEach { target ->
                if (target in targets) arr.put(target.jsonKey)
            }
            return arr
        }

        /** Legacy themes that only store [ThemeSection] in manifest/DataStore. */
        fun fromLegacySections(sections: Set<ThemeSection>): Set<ThemeApplyTarget> = buildSet {
            if (ThemeSection.MAIN_SCREEN in sections) {
                add(MAIN_SCREEN_PANELS)
                add(MAIN_SCREEN_WALLPAPERS)
                add(TILE_BACKGROUNDS)
            }
            if (ThemeSection.FLOATING_PANELS in sections) {
                add(FLOATING_PANELS)
                add(TILE_BACKGROUNDS)
            }
            if (ThemeSection.APP_ICONS in sections) {
                add(APP_ICONS)
            }
        }

        fun resolveActive(
            applyTargets: Set<ThemeApplyTarget>,
            legacySections: Set<ThemeSection>,
        ): Set<ThemeApplyTarget> =
            applyTargets.ifEmpty { fromLegacySections(legacySections) }

        fun exportSectionsFromTargets(targets: Set<ThemeApplyTarget>): Set<ThemeSection> = buildSet {
            if (MAIN_SCREEN_PANELS in targets || MAIN_SCREEN_WALLPAPERS in targets) {
                add(ThemeSection.MAIN_SCREEN)
            }
            if (FLOATING_PANELS in targets) add(ThemeSection.FLOATING_PANELS)
            if (APP_ICONS in targets) add(ThemeSection.APP_ICONS)
        }
    }
}
