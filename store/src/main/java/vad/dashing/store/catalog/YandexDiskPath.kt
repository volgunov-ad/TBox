package vad.dashing.store.catalog

/** Catalog URL conventions for files inside the public Yandex Disk OTA share. */
object YandexDiskPath {
    private const val PREFIX = "yandex-disk:"
    const val CATALOG_PATH = "/apps/catalog.json"

    fun isYandexDiskUrl(url: String): Boolean = url.startsWith(PREFIX)

    fun toDiskPath(url: String): String? {
        if (!isYandexDiskUrl(url)) return null
        val path = url.removePrefix(PREFIX)
        return if (path.startsWith("/")) path else "/$path"
    }

    fun appManifestPath(appId: String): String = "/apps/$appId/version.json"

    fun appApkPath(appId: String, apkFileName: String): String = "/apps/$appId/$apkFileName"
}
