package vad.dashing.tbox.wifimodem

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * HTTP client for Huawei HiLink XML admin API (E3372-325 and close cousins).
 *
 * Session token is taken from `/api/webserver/SesTokInfo` (or response headers).
 * Safe for sequential use from a single poller coroutine.
 */
class HuaweiHilinkClient(
    host: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val baseHost: String = host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
        .ifBlank { WifiModemModel.HUAWEI_E3372.defaultHost }

    @Volatile
    private var sessionCookie: String = ""

    @Volatile
    private var requestToken: String = ""

    fun fetchStatus(): WifiModemSnapshot {
        ensureSession()
        val information = getXmlMap("/api/device/information")
        val monitoring = getXmlMap("/api/monitoring/status")
        val signal = getXmlMap("/api/device/signal")
        val traffic = getXmlMap("/api/monitoring/traffic-statistics")
        return HuaweiHilinkStatusMapper.map(
            information = information,
            monitoring = monitoring,
            signal = signal,
            traffic = traffic,
            previous = null,
        )
    }

    fun fetchStatus(previous: WifiModemSnapshot?): WifiModemSnapshot {
        ensureSession()
        val information = getXmlMap("/api/device/information")
        val monitoring = getXmlMap("/api/monitoring/status")
        val signal = getXmlMap("/api/device/signal")
        val traffic = getXmlMap("/api/monitoring/traffic-statistics")
        return HuaweiHilinkStatusMapper.map(
            information = information,
            monitoring = monitoring,
            signal = signal,
            traffic = traffic,
            previous = previous,
        )
    }

    fun setMobileDataEnabled(enabled: Boolean) {
        ensureSession()
        val body = "<request><dataswitch>${if (enabled) 1 else 0}</dataswitch></request>"
        postXml("/api/dialup/mobile-dataswitch", body)
    }

    fun rebootDevice() {
        ensureSession()
        postXml("/api/device/control", "<request><Control>1</Control></request>")
    }

    fun invalidateSession() {
        sessionCookie = ""
        requestToken = ""
    }

    private fun ensureSession() {
        if (sessionCookie.isNotBlank() && requestToken.isNotBlank()) return
        refreshSesTok()
    }

    private fun refreshSesTok() {
        val request = Request.Builder()
            .url("http://$baseHost/api/webserver/SesTokInfo")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HuaweiHilinkException("SesTokInfo HTTP ${response.code}")
            }
            val ses = HuaweiHilinkXml.tagValue(text, "SesInfo").orEmpty()
            val tok = HuaweiHilinkXml.tagValue(text, "TokInfo").orEmpty()
            if (ses.isBlank() || tok.isBlank()) {
                throw HuaweiHilinkException("SesTokInfo missing SesInfo/TokInfo")
            }
            sessionCookie = ses
            requestToken = tok
        }
    }

    private fun getXmlMap(path: String): Map<String, String> {
        val responseText = getRaw(path)
        return HuaweiHilinkXml.tagValues(responseText)
    }

    private fun getRaw(path: String): String {
        ensureSession()
        val request = Request.Builder()
            .url("http://$baseHost$path")
            .header("__RequestVerificationToken", requestToken)
            .header("Cookie", sessionCookie)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            captureRotatedToken(response)
            if (!response.isSuccessful) {
                if (response.code == 401 || "error" in text.lowercase()) {
                    invalidateSession()
                }
                throw HuaweiHilinkException("GET $path HTTP ${response.code}")
            }
            // HiLink error envelope
            if (HuaweiHilinkXml.tagValue(text, "code") != null &&
                HuaweiHilinkXml.tagValue(text, "message") != null &&
                HuaweiHilinkXml.tagValue(text, "response") == null
            ) {
                invalidateSession()
                throw HuaweiHilinkException("GET $path error XML: $text")
            }
            return text
        }
    }

    private fun postXml(path: String, xmlBody: String) {
        ensureSession()
        // HiLink accepts raw XML body with verification token header.
        val body = xmlBody.toRequestBody("application/xml".toMediaType())
        val request = Request.Builder()
            .url("http://$baseHost$path")
            .header("__RequestVerificationToken", requestToken)
            .header("Cookie", sessionCookie)
            .header("Referer", "http://$baseHost/html/home.html")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            captureRotatedToken(response)
            if (!response.isSuccessful) {
                invalidateSession()
                throw HuaweiHilinkException("POST $path HTTP ${response.code}: $text")
            }
            val ok = HuaweiHilinkXml.tagValue(text, "response")
            if (ok == null || !ok.equals("OK", ignoreCase = true)) {
                // Some firmwares return empty response on reboot — accept blank body.
                if (text.isNotBlank() && ok == null) {
                    throw HuaweiHilinkException("POST $path rejected: $text")
                }
            }
        }
    }

    private fun captureRotatedToken(response: okhttp3.Response) {
        val headerToken = response.header("__RequestVerificationToken")
            ?: response.header("__requestverificationtoken")
        if (!headerToken.isNullOrBlank()) {
            // Some devices return "token#token2#..." — use the first token.
            requestToken = headerToken.substringBefore('#')
        }
        val setCookie = response.header("Set-Cookie")
        if (!setCookie.isNullOrBlank() && setCookie.contains("SessionID", ignoreCase = true)) {
            sessionCookie = setCookie.substringBefore(';')
        }
    }

    companion object {
        fun defaultClient(timeoutSeconds: Long = 8): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(timeoutSeconds + 2, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
    }
}

class HuaweiHilinkException(message: String) : Exception(message)
