package vad.dashing.store.catalog

import vad.dashing.store.disk.UpdateChannel

/**
 * Paths inside the dedicated AppStore Yandex Disk share
 * (`https://disk.yandex.ru/d/KDaHLxbFtPAjYA`):
 *
 * ```
 * /release/apps/catalog.json
 * /release/apps/<id>/version.json
 * /dev/apps/…
 * ```
 *
 * Catalog JSON may keep logical URLs `yandex-disk:/apps/…`; [physicalPath]
 * prefixes the channel root (`/release` or `/dev`).
 */
object YandexDiskPath {
    private const val PREFIX = "yandex-disk:"

    fun isYandexDiskUrl(url: String): Boolean = url.startsWith(PREFIX)

    fun toLogicalPath(url: String): String? {
        if (!isYandexDiskUrl(url)) return null
        val path = url.removePrefix(PREFIX)
        return if (path.startsWith("/")) path else "/$path"
    }

    fun channelRoot(channel: UpdateChannel): String =
        when (channel) {
            UpdateChannel.RELEASE -> "/release"
            UpdateChannel.DEVELOPMENT -> "/dev"
        }

    fun catalogPath(channel: UpdateChannel): String =
        "${channelRoot(channel)}/apps/catalog.json"

    fun appManifestPath(channel: UpdateChannel, appId: String): String =
        "${channelRoot(channel)}/apps/$appId/version.json"

    fun appApkPath(channel: UpdateChannel, appId: String, apkFileName: String): String =
        "${channelRoot(channel)}/apps/$appId/$apkFileName"

    fun appIconPath(channel: UpdateChannel, appId: String, fileName: String = "icon.png"): String =
        "${channelRoot(channel)}/apps/$appId/$fileName"

    /**
     * Per-app localized changelog on the share: `changelog.ru.txt` / `changelog.en.txt`.
     * [lang] is `ru` or `en` (other values → `ru`).
     */
    fun appChangelogPath(channel: UpdateChannel, appId: String, lang: String): String {
        val normalized = if (lang.equals("en", ignoreCase = true)) "en" else "ru"
        return "${channelRoot(channel)}/apps/$appId/changelog.$normalized.txt"
    }

    fun changelogFileName(lang: String): String {
        val normalized = if (lang.equals("en", ignoreCase = true)) "en" else "ru"
        return "changelog.$normalized.txt"
    }

    /**
     * Maps a logical catalog path (`/apps/…` or already `/release|dev/…`)
     * to a physical path on the AppStore share.
     */
    fun physicalPath(channel: UpdateChannel, logicalPath: String): String {
        val path = logicalPath.trim().let { raw ->
            when {
                raw.startsWith(PREFIX) -> toLogicalPath(raw) ?: raw
                raw.startsWith("/") -> raw
                else -> "/$raw"
            }
        }
        if (path.startsWith("/release/") || path == "/release" ||
            path.startsWith("/dev/") || path == "/dev"
        ) {
            return path
        }
        val root = channelRoot(channel)
        if (path.startsWith("/apps/") || path == "/apps") {
            return root + path
        }
        return "$root/apps$path"
    }
}
