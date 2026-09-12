package vad.dashing.tbox.wifimodem

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP client for ZTE UFI **goform** admin API (MF79U and close cousins).
 *
 * Safe for sequential use from a single poller coroutine.
 * Inject [client] in unit tests (mock interceptor / dispatcher).
 */
class ZteGoformClient(
    host: String,
    private val password: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val baseHost: String = host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
        .ifBlank { WifiModemModel.ZTE_MF79U.defaultHost }

    @Volatile
    private var loggedIn: Boolean = false

    fun fetchStatus(
        cmds: List<String> = ZteGoformStatusCmds.HOME + ZteGoformStatusCmds.DEVICE,
    ): Map<String, String> {
        ensureLoggedIn()
        val url = "http://$baseHost${ZteGoformAuth.GET_PATH}".toHttpUrl().newBuilder()
            .addQueryParameter("isTest", "false")
            .addQueryParameter("multi_data", "1")
            .addQueryParameter("cmd", cmds.distinct().joinToString(","))
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "http://$baseHost/index.html")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                loggedIn = false
                throw ZteGoformException("status HTTP ${response.code}")
            }
            val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(body)
            val loginfo = fields["loginfo"].orEmpty()
            if (loginfo.equals("error", ignoreCase = true) ||
                loginfo.contains("not login", ignoreCase = true)
            ) {
                loggedIn = false
                throw ZteGoformException("session expired ($loginfo)")
            }
            return fields
        }
    }

    fun ensureLoggedIn() {
        if (!loggedIn) login()
    }

    fun login() {
        val ld = fetchLd()
        val hash = ZteGoformAuth.encodeLoginPassword(password, ld)
        val form = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "LOGIN")
            .add("password", hash)
            .build()
        val request = Request.Builder()
            .url("http://$baseHost${ZteGoformAuth.SET_PATH}")
            .header("Referer", "http://$baseHost/index.html")
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ZteGoformException("login HTTP ${response.code}: $text")
            }
            val fields = runCatching { ZteReqprocStatusMapper.fieldsFromJsonObject(text) }
                .getOrDefault(emptyMap())
            val result = fields["result"]
            if (!ZteGoformAuth.isLoginSuccess(result)) {
                throw ZteGoformException("login rejected: result=$result body=$text")
            }
            loggedIn = true
        }
    }

    private fun fetchLd(): String {
        val url = "http://$baseHost${ZteGoformAuth.GET_PATH}".toHttpUrl().newBuilder()
            .addQueryParameter("isTest", "false")
            .addQueryParameter("cmd", "LD")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "http://$baseHost/index.html")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ZteGoformException("LD HTTP ${response.code}")
            }
            val ld = ZteReqprocStatusMapper.fieldsFromJsonObject(text)["LD"].orEmpty()
            if (ld.isBlank()) throw ZteGoformException("empty LD challenge")
            return ld
        }
    }

    fun invalidateSession() {
        loggedIn = false
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

class ZteGoformException(message: String) : Exception(message)
