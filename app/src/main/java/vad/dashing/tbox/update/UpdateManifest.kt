package vad.dashing.tbox.update

import org.json.JSONArray
import org.json.JSONObject

data class UpdateReleaseInfo(
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

data class UpdateManifest(
    val schemaVersion: Int,
    val releases: List<UpdateReleaseInfo>,
) {
    fun releaseFor(flavor: String): UpdateReleaseInfo? =
        releases.firstOrNull { it.flavor.equals(flavor, ignoreCase = true) }

    companion object {
        fun parse(json: String): UpdateManifest {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", 1)
            val releasesArray = root.optJSONArray("releases") ?: JSONArray()
            val releases = buildList {
                for (index in 0 until releasesArray.length()) {
                    val item = releasesArray.optJSONObject(index) ?: continue
                    val versionCode = item.optLong("versionCode", -1L)
                    val versionName = item.optString("versionName")
                    val flavor = item.optString("flavor")
                    val apkFileName = item.optString("apkFileName")
                    val sha256 = item.optString("sha256").lowercase()
                    val apkSizeBytes = item.optLong("apkSizeBytes", -1L).takeIf { it > 0L }
                    if (versionCode < 0L || versionName.isBlank() || flavor.isBlank() ||
                        apkFileName.isBlank() || sha256.isBlank()
                    ) {
                        continue
                    }
                    add(
                        UpdateReleaseInfo(
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
            return UpdateManifest(schemaVersion = schemaVersion, releases = releases)
        }
    }
}

fun isUpdateNewer(remoteVersionCode: Long, currentVersionCode: Long): Boolean =
    remoteVersionCode > currentVersionCode
