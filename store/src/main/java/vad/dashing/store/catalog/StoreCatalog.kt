package vad.dashing.store.catalog

import org.json.JSONArray
import org.json.JSONObject

enum class StoreAppOrigin {
    FIRST_PARTY,
    THIRD_PARTY,
    ;

    companion object {
        fun fromRaw(raw: String?): StoreAppOrigin =
            when (raw?.lowercase()) {
                "third-party", "third_party", "thirdparty" -> THIRD_PARTY
                else -> FIRST_PARTY
            }
    }
}

enum class StoreAppCategory {
    APP,
    TOOL,
    ONESHOT,
    ;

    companion object {
        fun fromRaw(raw: String?): StoreAppCategory =
            when (raw?.lowercase()) {
                "tool" -> TOOL
                "oneshot", "one-shot", "one_shot" -> ONESHOT
                else -> APP
            }
    }
}

data class StoreCatalogEntry(
    val id: String,
    val packageName: String,
    val title: String,
    val summary: String = "",
    val publisher: String = "",
    val origin: StoreAppOrigin = StoreAppOrigin.FIRST_PARTY,
    val hu: Set<String> = setOf("a9", "a10"),
    val category: StoreAppCategory = StoreAppCategory.APP,
    val verified: Boolean = true,
    val iconUrl: String = "",
    val manifestUrl: String = "",
    val signingCertSha256: List<String> = emptyList(),
    val minStoreVersionCode: Long = 0L,
    val reviewNotes: String = "",
) {
    fun supportsHu(huTag: String?): Boolean {
        if (huTag.isNullOrBlank() || huTag == "unknown") return true
        return hu.any { it.equals(huTag, ignoreCase = true) }
    }

    /** Logical path (`/apps/<id>/version.json`); pass through [YandexDiskPath.physicalPath]. */
    fun resolveManifestLogicalPath(): String {
        val fromUrl = YandexDiskPath.toLogicalPath(manifestUrl)
        if (!fromUrl.isNullOrBlank()) return fromUrl
        return "/apps/$id/version.json"
    }

    /**
     * Logical icon path from catalog `icon` field only.
     * When absent, the UI uses the installed app icon or a letter placeholder
     * (avoids a Disk round-trip / 404 per app).
     */
    fun resolveIconLogicalPath(): String? {
        if (iconUrl.isBlank()) return null
        val fromUrl = YandexDiskPath.toLogicalPath(iconUrl)
        if (!fromUrl.isNullOrBlank()) return fromUrl
        return if (iconUrl.startsWith("/")) iconUrl else "/$iconUrl"
    }

    fun hasExplicitIcon(): Boolean = iconUrl.isNotBlank()
}

data class StoreCatalog(
    val schemaVersion: Int,
    val publishedAt: String = "",
    val apps: List<StoreCatalogEntry>,
) {
    companion object {
        fun parse(json: String): StoreCatalog {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", 1)
            val publishedAt = root.optString("publishedAt")
            val appsArray = root.optJSONArray("apps") ?: JSONArray()
            val apps = buildList {
                for (index in 0 until appsArray.length()) {
                    val item = appsArray.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val packageName = item.optString("packageName").trim()
                    val title = item.optString("title").trim()
                    if (id.isBlank() || packageName.isBlank() || title.isBlank()) continue
                    val verified = item.optBoolean("verified", true)
                    if (!verified) continue
                    val hu = parseHu(item.opt("hu"))
                    val certs = parseStringList(item.optJSONArray("signingCertSha256"))
                        .map { it.lowercase() }
                    add(
                        StoreCatalogEntry(
                            id = id,
                            packageName = packageName,
                            title = title,
                            summary = item.optString("summary"),
                            publisher = item.optString("publisher"),
                            origin = StoreAppOrigin.fromRaw(item.optString("origin")),
                            hu = hu,
                            category = StoreAppCategory.fromRaw(item.optString("category")),
                            verified = true,
                            iconUrl = item.optString("icon"),
                            manifestUrl = item.optString("manifest"),
                            signingCertSha256 = certs,
                            minStoreVersionCode = item.optLong("minStoreVersionCode", 0L),
                            reviewNotes = item.optString("reviewNotes"),
                        ),
                    )
                }
            }
            return StoreCatalog(
                schemaVersion = schemaVersion,
                publishedAt = publishedAt,
                apps = apps,
            )
        }

        private fun parseHu(raw: Any?): Set<String> {
            return when (raw) {
                is JSONArray -> buildSet {
                    for (i in 0 until raw.length()) {
                        val tag = raw.optString(i).trim().lowercase()
                        if (tag.isNotBlank()) add(tag)
                    }
                }.ifEmpty { setOf("a9", "a10") }
                is String -> {
                    val tag = raw.trim().lowercase()
                    if (tag.isBlank()) setOf("a9", "a10") else setOf(tag)
                }
                else -> setOf("a9", "a10")
            }
        }

        private fun parseStringList(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim()
                    if (value.isNotBlank()) add(value)
                }
            }
        }
    }
}
