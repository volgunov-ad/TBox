package vad.dashing.tbox.location.roadmatch

import org.json.JSONObject
import vad.dashing.tbox.ThemeBundleExport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JSON file export/import for [RoadMatchTuning] presets (separate from full app backup).
 *
 * Export writes a **full snapshot** of every tuning key (format v2). Import accepts v2 full
 * files, v1 sparse wrapped exports, and raw sparse JSON from DataStore.
 */
object RoadMatchTuningExport {

    const val FORMAT_VERSION = 2
    const val LEGACY_FORMAT_VERSION = 1
    const val FILE_EXTENSION = "json"
    const val KIND = "road_match_tuning"

    private const val KEY_FORMAT = "formatVersion"
    private const val KEY_KIND = "kind"
    private const val KEY_PACKAGE = "packageName"
    private const val KEY_EXPORTED_AT = "exportedAtMillis"
    private const val KEY_TUNING = "tuning"

    fun exportJson(packageName: String, tuning: RoadMatchTuning): String {
        val root = JSONObject()
        root.put(KEY_FORMAT, FORMAT_VERSION)
        root.put(KEY_KIND, KIND)
        root.put(KEY_PACKAGE, packageName)
        root.put(KEY_EXPORTED_AT, System.currentTimeMillis())
        root.put(KEY_TUNING, JSONObject(tuning.toFullJson()))
        return root.toString(2)
    }

    fun importJson(json: String): Result<RoadMatchTuning> {
        return runCatching {
            val root = JSONObject(json)
            if (root.has(KEY_KIND)) {
                val formatVersion = root.optInt(KEY_FORMAT, 0)
                if (formatVersion != FORMAT_VERSION && formatVersion != LEGACY_FORMAT_VERSION) {
                    throw IllegalArgumentException("unsupported_format")
                }
                if (root.optString(KEY_KIND) != KIND) {
                    throw IllegalArgumentException("unsupported_kind")
                }
                val tuningObj = root.optJSONObject(KEY_TUNING)
                    ?: throw IllegalArgumentException("missing_tuning")
                val tuningRaw = tuningObj.toString()
                when (formatVersion) {
                    LEGACY_FORMAT_VERSION -> RoadMatchTuning.fromJson(tuningRaw)
                    else -> RoadMatchTuning.fromExportJson(tuningRaw)
                }
            } else {
                // Raw tuning JSON: full snapshot or sparse DataStore / manual edit.
                RoadMatchTuning.fromExportJson(json)
            }
        }
    }

    fun downloadsFile(timestampMs: Long = System.currentTimeMillis()): File {
        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(timestampMs))
        return File(ThemeBundleExport.downloadsDir(), "tbox_road_match_tuning_$ts.$FILE_EXTENSION")
    }

    fun exportToDownloads(packageName: String, tuning: RoadMatchTuning): File {
        val dest = downloadsFile()
        dest.writeText(exportJson(packageName, tuning))
        return dest
    }
}
