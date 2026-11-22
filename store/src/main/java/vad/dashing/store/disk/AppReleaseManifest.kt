package vad.dashing.store.disk

import org.json.JSONArray
import org.json.JSONObject

data class AppReleaseInfo(
    val versionCode: Long,
    val versionName: String,
    val flavor: String,
    val apkFileName: String,
    val sha256: String,
    val apkSizeBytes: Long? = null,
    val minSupportedVersionCode: Long = 0L,
    val changelog: String = "",
    val publishedAt: String = "",
)

data class AppReleaseManifest(
    val schemaVersion: Int,
    val releases: List<AppReleaseInfo>,
) {
    /** Flavors that are language builds (`ru` / `en`), lowercased unique order. */
    fun languageFlavors(): List<String> =
        releases
            .map { it.flavor.lowercase() }
            .filter { it == "ru" || it == "en" }
            .distinct()

    fun hasLanguageChoice(): Boolean = languageFlavors().size >= 2

    /**
     * Prefers [preferredFlavor], then `default`/`all`, then the first release.
     * Companion apps usually publish a single `default` (or `ru`) entry.
     */
    fun preferredRelease(preferredFlavor: String? = null): AppReleaseInfo? {
        if (releases.isEmpty()) return null
        if (!preferredFlavor.isNullOrBlank()) {
            releases.firstOrNull { it.flavor.equals(preferredFlavor, ignoreCase = true) }
                ?.let { return it }
        }
        releases.firstOrNull {
            it.flavor.equals("default", ignoreCase = true) ||
                it.flavor.equals("all", ignoreCase = true)
        }?.let { return it }
        return releases.first()
    }

    companion object {
        fun parse(json: String): AppReleaseManifest {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", 1)
            val releasesArray = root.optJSONArray("releases") ?: JSONArray()
            val releases = buildList {
                for (index in 0 until releasesArray.length()) {
                    val item = releasesArray.optJSONObject(index) ?: continue
                    val versionCode = item.optLong("versionCode", -1L)
                    val versionName = item.optString("versionName")
                    val flavor = item.optString("flavor").ifBlank { "default" }
                    val apkFileName = item.optString("apkFileName")
                    val sha256 = item.optString("sha256").lowercase()
                    val apkSizeBytes = item.optLong("apkSizeBytes", -1L).takeIf { it > 0L }
                    if (versionCode < 0L || versionName.isBlank() ||
                        apkFileName.isBlank() || sha256.isBlank()
                    ) {
                        continue
                    }
                    add(
                        AppReleaseInfo(
                            versionCode = versionCode,
                            versionName = versionName,
                            flavor = flavor,
                            apkFileName = apkFileName,
                            sha256 = sha256,
                            apkSizeBytes = apkSizeBytes,
                            minSupportedVersionCode = item.optLong("minSupportedVersionCode", 0L),
                            changelog = item.optString("changelog"),
                            publishedAt = item.optString("publishedAt"),
                        ),
                    )
                }
            }
            return AppReleaseManifest(schemaVersion = schemaVersion, releases = releases)
        }
    }
}

fun isReleaseNewer(remoteVersionCode: Long, currentVersionCode: Long): Boolean =
    remoteVersionCode > currentVersionCode
