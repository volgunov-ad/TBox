package vad.dashing.tbox.location.roadmatch

/** Catalog URL conventions for files inside the public Yandex Disk OTA share. */
object RoadMapRemoteUrl {
    private const val YANDEX_PREFIX = "yandex-disk:"
    const val REMOTE_CATALOG_PATH = "/maps/catalog.json"

    fun yandexPathOrNull(url: String): String? {
        if (!url.startsWith(YANDEX_PREFIX)) return null
        val path = url.removePrefix(YANDEX_PREFIX)
        if (!path.startsWith("/maps/") || ".." in path || path.endsWith('/')) return null
        return path
    }
}
