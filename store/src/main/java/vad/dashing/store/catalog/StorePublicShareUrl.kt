package vad.dashing.store.catalog

import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel

/**
 * Stable browser URLs for a public AppStore share (QR / open on phone).
 * Temporary cloud-api download hrefs expire — do not put those in a QR code.
 */
object StorePublicShareUrl {
    fun apkBrowseUrl(
        yandexPublicKey: String,
        mailRuPublicUrl: String,
        cloudSource: CloudSource,
        channel: UpdateChannel,
        appId: String,
        apkFileName: String,
    ): String {
        val path = YandexDiskPath.appApkPath(channel, appId, apkFileName)
        val base = when (cloudSource) {
            CloudSource.MAILRU -> mailRuPublicUrl
            CloudSource.YANDEX, CloudSource.AUTO -> yandexPublicKey
        }.trim().trimEnd('/')
        return base + path
    }
}
