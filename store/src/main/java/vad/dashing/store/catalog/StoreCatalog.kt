package vad.dashing.store.catalog

import org.json.JSONArray
import org.json.JSONObject

/**
 * Where the APK is meant to be installed.
 * [PHONE] — show on HU catalog, but never install here: QR for the phone + save to Downloads.
 */
enum class StoreInstallMode {
    HU,
    PHONE,
    ;

    companion object {
        fun fromRaw(raw: String?): StoreInstallMode =
            when (raw?.trim()?.lowercase()) {
                "phone", "phone-only", "phone_only", "downloads", "downloads-only",
                "downloads_only", "mobile",
                -> PHONE
                else -> HU
            }
    }
}

data class StoreSection(
    val id: String,
    val title: String,
    val titleEn: String = "",
) {
    fun displayTitle(lang: String): String =
        if (lang.equals("en", ignoreCase = true) && titleEn.isNotBlank()) titleEn else title
}

data class StoreDownloadFile(
    val name: String,
    val sha256: String,
    val sizeBytes: Long? = null,
)

data class StoreDownloads(
    val folder: String,
    val files: List<StoreDownloadFile>,
)

/** Name rules shared with the catalog manager (§3.5). */
object StoreFileNames {
    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun isSafeFileName(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 120) return false
        if (value.startsWith(".")) return false
        if (value.endsWith(".") || value.endsWith(" ")) return false
        if (value.endsWith(".part", ignoreCase = true)) return false
        if (forbidden.containsMatchIn(value)) return false
        return true
    }

    fun safeFolder(raw: String, fallback: String): String {
        var value = raw.trim()
        value = forbidden.replace(value, "_")
        while (value.startsWith(".") || value.startsWith(" ")) {
            value = value.drop(1)
        }
        while (value.endsWith(".") || value.endsWith(" ")) {
            value = value.dropLast(1)
        }
        if (value.isBlank() || value == "." || value == "..") {
            value = fallback.trim().ifBlank { "app" }
        }
        return value.take(64)
    }
}

data class StoreCatalogEntry(
    val id: String,
    val packageName: String,
    val title: String,
    val summary: String = "",
    val publisher: String = "",
    val hu: Set<String> = setOf("a9", "a10"),
    val iconUrl: String = "",
    val manifestUrl: String = "",
    val signingCertSha256: List<String> = emptyList(),
    val minStoreVersionCode: Long = 0L,
    val sections: List<String> = emptyList(),
    val downloads: StoreDownloads? = null,
    /**
     * [StoreInstallMode.PHONE] for companion apps (e.g. `:phone`) that must not be
     * installed on the head unit — QR + Downloads only.
     */
    val installMode: StoreInstallMode = StoreInstallMode.HU,
    /** Optional override; default file is `apps/<id>/changelog.ru.txt`. */
    val changelogRuUrl: String = "",
    /** Optional override; default file is `apps/<id>/changelog.en.txt`. */
    val changelogEnUrl: String = "",
) {
    val isPhoneDistribution: Boolean get() = installMode == StoreInstallMode.PHONE

    val hasDownloadFiles: Boolean get() = downloads?.files?.isNotEmpty() == true

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

    /**
     * Logical path for a localized changelog file (`ru` / `en`).
     * Catalog `changelogRu` / `changelogEn` override the convention
     * `/apps/<id>/changelog.<lang>.txt`.
     */
    fun resolveChangelogLogicalPath(lang: String): String {
        val preferEn = lang.equals("en", ignoreCase = true)
        val override = if (preferEn) changelogEnUrl else changelogRuUrl
        if (override.isNotBlank()) {
            val fromUrl = YandexDiskPath.toLogicalPath(override)
            if (!fromUrl.isNullOrBlank()) return fromUrl
            return if (override.startsWith("/")) override else "/$override"
        }
        val tag = if (preferEn) "en" else "ru"
        return "/apps/$id/changelog.$tag.txt"
    }
}

data class StoreCatalog(
    val schemaVersion: Int,
    val publishedAt: String = "",
    val sections: List<StoreSection> = emptyList(),
    val apps: List<StoreCatalogEntry>,
) {
    companion object {
        private val SECTION_ID_RE = Regex("""^[a-z0-9][a-z0-9_-]{0,31}$""")
        private val SHA256_RE = Regex("""^[0-9a-f]{64}$""")

        fun parse(json: String): StoreCatalog {
            val root = JSONObject(json)
            val schemaVersion = root.optInt("schemaVersion", 1)
            val publishedAt = root.optString("publishedAt")
            val sections = parseSections(root.optJSONArray("sections"))
            val definedIds = sections.map { it.id }.toSet()
            val appsArray = root.optJSONArray("apps") ?: JSONArray()
            val apps = buildList {
                for (index in 0 until appsArray.length()) {
                    val item = appsArray.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val packageName = item.optString("packageName").trim()
                    val title = item.optString("title").trim()
                    if (id.isBlank() || packageName.isBlank() || title.isBlank()) continue
                    // Legacy verified/origin/category/copyApk… keys are ignored.
                    val hu = parseHu(item.opt("hu"))
                    val certs = parseStringList(item.optJSONArray("signingCertSha256"))
                        .map { it.lowercase() }
                    val appSections = parseStringList(item.optJSONArray("sections"))
                        .filter { it in definedIds }
                    val downloads = parseDownloads(item.optJSONObject("downloads"), id)
                    add(
                        StoreCatalogEntry(
                            id = id,
                            packageName = packageName,
                            title = title,
                            summary = item.optString("summary"),
                            publisher = item.optString("publisher"),
                            hu = hu,
                            iconUrl = item.optString("icon"),
                            manifestUrl = item.optString("manifest"),
                            signingCertSha256 = certs,
                            minStoreVersionCode = item.optLong("minStoreVersionCode", 0L),
                            sections = appSections,
                            downloads = downloads,
                            installMode = StoreInstallMode.fromRaw(
                                item.optString("installMode")
                                    .ifBlank { item.optString("distribution") },
                            ),
                            changelogRuUrl = item.optString("changelogRu")
                                .ifBlank { item.optString("changelog_ru") }
                                .trim(),
                            changelogEnUrl = item.optString("changelogEn")
                                .ifBlank { item.optString("changelog_en") }
                                .trim(),
                        ),
                    )
                }
            }
            return StoreCatalog(
                schemaVersion = schemaVersion,
                publishedAt = publishedAt,
                sections = sections,
                apps = apps,
            )
        }

        private fun parseSections(array: JSONArray?): List<StoreSection> {
            if (array == null) return emptyList()
            val seen = mutableSetOf<String>()
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    val title = item.optString("title").trim()
                    if (id.isBlank() || title.isBlank()) continue
                    if (!SECTION_ID_RE.matches(id)) continue
                    if (!seen.add(id)) continue
                    add(
                        StoreSection(
                            id = id,
                            title = title,
                            titleEn = item.optString("titleEn").trim(),
                        ),
                    )
                }
            }
        }

        private fun parseDownloads(raw: JSONObject?, appId: String): StoreDownloads? {
            if (raw == null) return null
            val folderRaw = raw.optString("folder")
            val folder = StoreFileNames.safeFolder(folderRaw, appId)
            val filesArray = raw.optJSONArray("files") ?: return null
            val seen = mutableSetOf<String>()
            val files = buildList {
                for (i in 0 until filesArray.length()) {
                    val item = filesArray.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val sha = item.optString("sha256").trim().lowercase()
                    if (!StoreFileNames.isSafeFileName(name)) continue
                    if (!SHA256_RE.matches(sha)) continue
                    val key = name.lowercase()
                    if (!seen.add(key)) continue
                    val size = item.optLong("sizeBytes", -1L).takeIf { it >= 0L }
                    add(StoreDownloadFile(name = name, sha256 = sha, sizeBytes = size))
                }
            }
            if (files.isEmpty()) return null
            return StoreDownloads(folder = folder, files = files)
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
