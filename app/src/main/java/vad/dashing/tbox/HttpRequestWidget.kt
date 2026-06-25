package vad.dashing.tbox

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.TlsVersion
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

const val DEFAULT_HTTP_REQUEST_WIDGET_YAML = "url: 'http://'"
const val DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS = 10
const val HTTP_REQUEST_POST_ACTION_BLOCK_MS = 500L

data class HttpRequestWidgetConfig(
    val url: String,
    val method: String = "get",
    val headers: Map<String, String> = emptyMap(),
    val payload: String? = null,
    val authentication: String = "basic",
    val username: String? = null,
    val password: String? = null,
    val timeoutSeconds: Int = DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS,
    val contentType: String? = null,
    val verifySsl: Boolean = true,
    val insecureCipher: Boolean = false,
    val skipUrlEncoding: Boolean = false,
) {
    val timeoutMillis: Long
        get() = timeoutSeconds.coerceAtLeast(1) * 1000L
}

sealed class HttpRequestWidgetResult {
    data class Success(val code: Int) : HttpRequestWidgetResult()
    data class Failure(val message: String, val code: Int? = null) : HttpRequestWidgetResult()
}

fun parseHttpRequestWidgetYaml(rawYaml: String): Result<HttpRequestWidgetConfig> = runCatching {
    val yaml = rawYaml.trim().ifBlank { DEFAULT_HTTP_REQUEST_WIDGET_YAML }
    val loaded = Load(LoadSettings.builder().build()).loadFromString(yaml)
    val root = loaded as? Map<*, *> ?: throw IllegalArgumentException("YAML должен быть объектом")
    val url = root.stringValue("url")?.trim()
        ?: throw IllegalArgumentException("Поле url обязательно")
    if (url.isBlank() || url == "http://" || url == "https://") {
        throw IllegalArgumentException("Укажите полный URL")
    }
    val method = root.stringValue("method")
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.ifBlank { "get" }
        ?: "get"
    if (method !in setOf("get", "patch", "post", "put", "delete")) {
        throw IllegalArgumentException("method должен быть get, patch, post, put или delete")
    }
    val authentication = root.stringValue("authentication")
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.ifBlank { "basic" }
        ?: "basic"
    if (authentication !in setOf("basic", "digest")) {
        throw IllegalArgumentException("authentication должен быть basic или digest")
    }
    val timeout = root.intValue("timeout") ?: DEFAULT_HTTP_REQUEST_TIMEOUT_SECONDS
    if (timeout !in 1..300) {
        throw IllegalArgumentException("timeout должен быть от 1 до 300 секунд")
    }
    HttpRequestWidgetConfig(
        url = url,
        method = method,
        headers = root.stringMapValue("headers"),
        payload = root.stringValue("payload"),
        authentication = authentication,
        username = root.stringValue("username")?.takeIf { it.isNotBlank() },
        password = root.stringValue("password")?.takeIf { it.isNotBlank() },
        timeoutSeconds = timeout,
        contentType = root.stringValue("content_type")?.takeIf { it.isNotBlank() },
        verifySsl = root.booleanValue("verify_ssl") ?: true,
        insecureCipher = root.booleanValue("insecure_cipher") ?: false,
        skipUrlEncoding = root.booleanValue("skip_url_encoding") ?: false,
    )
}

fun browserUrlFromHttpRequestYaml(rawYaml: String): Result<String> =
    parseHttpRequestWidgetYaml(rawYaml).map { it.url }

suspend fun executeHttpRequestWidget(config: HttpRequestWidgetConfig): HttpRequestWidgetResult =
    withContext(Dispatchers.IO) {
        runCatching {
            val client = httpRequestClient(config)
            val request = httpRequestBuilder(config).build()
            client.newCall(request).execute().use { response ->
                if (response.code in 200..399) {
                    HttpRequestWidgetResult.Success(response.code)
                } else {
                    HttpRequestWidgetResult.Failure("HTTP ${response.code}", response.code)
                }
            }
        }.getOrElse { e ->
            HttpRequestWidgetResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

fun openHttpRequestWidgetUrlInBrowser(context: Context, url: String): Boolean {
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

internal fun httpRequestWidgetIsSuccess(result: HttpRequestWidgetResult): Boolean =
    result is HttpRequestWidgetResult.Success

internal fun httpRequestWidgetErrorMessage(result: HttpRequestWidgetResult): String =
    when (result) {
        is HttpRequestWidgetResult.Success -> ""
        is HttpRequestWidgetResult.Failure -> result.message
    }

private fun httpRequestBuilder(config: HttpRequestWidgetConfig): Request.Builder {
    val body = when (config.method) {
        "get", "delete" -> config.payload?.toRequestBody(config.requestMediaType())
        else -> (config.payload ?: "").toRequestBody(config.requestMediaType())
    }
    val builder = Request.Builder()
        .url(config.url)
    config.headers.forEach { (name, value) ->
        if (name.isNotBlank()) builder.header(name, value)
    }
    config.contentType?.takeIf { it.isNotBlank() }?.let { builder.header("Content-Type", it) }
    if (config.authentication == "basic" &&
        !config.username.isNullOrBlank() &&
        config.password != null &&
        config.headers.keys.none { it.equals("authorization", ignoreCase = true) }
    ) {
        builder.header("Authorization", Credentials.basic(config.username.orEmpty(), config.password.orEmpty()))
    }
    return when (config.method) {
        "get" -> builder.get()
        "delete" -> if (body == null) builder.delete() else builder.delete(body)
        "post" -> builder.post(body ?: "".toRequestBody(config.requestMediaType()))
        "put" -> builder.put(body ?: "".toRequestBody(config.requestMediaType()))
        "patch" -> builder.patch(body ?: "".toRequestBody(config.requestMediaType()))
        else -> builder.get()
    }
}

private fun HttpRequestWidgetConfig.requestMediaType() =
    (contentType ?: headers.entries.firstOrNull {
        it.key.equals("content-type", ignoreCase = true)
    }?.value ?: "text/plain").toMediaTypeOrNull()

private fun httpRequestClient(config: HttpRequestWidgetConfig): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .callTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
    if (config.authentication == "digest" &&
        !config.username.isNullOrBlank() &&
        config.password != null
    ) {
        builder.authenticator(DigestRequestAuthenticator(config.username.orEmpty(), config.password.orEmpty()))
    }
    if (!config.verifySsl) {
        builder.trustAllSsl()
    }
    if (config.insecureCipher) {
        builder.connectionSpecs(
            listOf(
                ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                    .tlsVersions(TlsVersion.TLS_1_0, TlsVersion.TLS_1_1, TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                    .allEnabledCipherSuites()
                    .build(),
                ConnectionSpec.CLEARTEXT,
            )
        )
    }
    return builder.build()
}

private fun OkHttpClient.Builder.trustAllSsl(): OkHttpClient.Builder {
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    sslSocketFactory(sslContext.socketFactory, trustManager)
    hostnameVerifier { _, _ -> true }
    return this
}

private class DigestRequestAuthenticator(
    private val username: String,
    private val password: String,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val challenge = response.headers("WWW-Authenticate")
            .firstOrNull { it.trimStart().startsWith("Digest", ignoreCase = true) }
            ?: return null
        val values = parseDigestChallenge(challenge)
        val realm = values["realm"] ?: return null
        val nonce = values["nonce"] ?: return null
        val algorithm = values["algorithm"]?.uppercase(Locale.ROOT) ?: "MD5"
        if (algorithm != "MD5") return null
        val qop = values["qop"]
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.equals("auth", ignoreCase = true) }
        val uri = response.request.url.encodedPath.let { path ->
            val query = response.request.url.encodedQuery
            if (query == null) path else "$path?$query"
        }
        val nc = "00000001"
        val cnonce = BigInteger(64, SecureRandom()).toString(16)
        val ha1 = md5Hex("$username:$realm:$password")
        val ha2 = md5Hex("${response.request.method}:$uri")
        val digestResponse = if (qop != null) {
            md5Hex("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
        } else {
            md5Hex("$ha1:$nonce:$ha2")
        }
        val header = buildString {
            append("""Digest username="$username", realm="$realm", nonce="$nonce", uri="$uri", response="$digestResponse"""")
            values["opaque"]?.let { append(""", opaque="$it"""") }
            append(", algorithm=MD5")
            if (qop != null) {
                append(""", qop=$qop, nc=$nc, cnonce="$cnonce"""")
            }
        }
        return response.request.newBuilder()
            .header("Authorization", header)
            .build()
    }
}

private fun responseCount(response: Response): Int {
    var result = 1
    var prior = response.priorResponse
    while (prior != null) {
        result++
        prior = prior.priorResponse
    }
    return result
}

private fun parseDigestChallenge(header: String): Map<String, String> {
    val raw = header.substringAfter("Digest", "").trim()
    val out = linkedMapOf<String, String>()
    var idx = 0
    while (idx < raw.length) {
        while (idx < raw.length && (raw[idx] == ',' || raw[idx].isWhitespace())) idx++
        val keyStart = idx
        while (idx < raw.length && raw[idx] != '=' && raw[idx] != ',') idx++
        if (idx >= raw.length || raw[idx] != '=') break
        val key = raw.substring(keyStart, idx).trim().lowercase(Locale.ROOT)
        idx++
        val value = if (idx < raw.length && raw[idx] == '"') {
            idx++
            val valueStart = idx
            while (idx < raw.length && raw[idx] != '"') idx++
            raw.substring(valueStart, idx).also {
                if (idx < raw.length && raw[idx] == '"') idx++
            }
        } else {
            val valueStart = idx
            while (idx < raw.length && raw[idx] != ',') idx++
            raw.substring(valueStart, idx).trim()
        }
        if (key.isNotBlank()) out[key] = value
    }
    return out
}

private fun md5Hex(value: String): String =
    MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.ISO_8859_1))
        .joinToString("") { "%02x".format(it) }

private fun Map<*, *>.stringValue(key: String): String? =
    this[key]?.let { value ->
        when (value) {
            is String -> value
            is Number, is Boolean -> value.toString()
            else -> throw IllegalArgumentException("Поле $key должно быть строкой")
        }
    }

private fun Map<*, *>.intValue(key: String): Int? =
    this[key]?.let { value ->
        when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
                ?: throw IllegalArgumentException("Поле $key должно быть числом")
            else -> throw IllegalArgumentException("Поле $key должно быть числом")
        }
    }

private fun Map<*, *>.booleanValue(key: String): Boolean? =
    this[key]?.let { value ->
        when (value) {
            is Boolean -> value
            is String -> when (value.trim().lowercase(Locale.ROOT)) {
                "true", "yes", "1" -> true
                "false", "no", "0" -> false
                else -> throw IllegalArgumentException("Поле $key должно быть true или false")
            }
            else -> throw IllegalArgumentException("Поле $key должно быть true или false")
        }
    }

private fun Map<*, *>.stringMapValue(key: String): Map<String, String> {
    val value = this[key] ?: return emptyMap()
    val map = value as? Map<*, *> ?: throw IllegalArgumentException("Поле $key должно быть map")
    return map.entries.associate { (k, v) ->
        val name = k?.toString()?.trim().orEmpty()
        val headerValue = when (v) {
            is String -> v
            is Number, is Boolean -> v.toString()
            else -> throw IllegalArgumentException("Значения $key должны быть строками")
        }
        name to headerValue
    }.filterKeys { it.isNotBlank() }
}
