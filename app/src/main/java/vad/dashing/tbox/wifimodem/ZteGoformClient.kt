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

    @Volatile
    private var waInnerVersion: String = ""

    @Volatile
    private var crVersion: String = ""

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
            rememberVersions(fields)
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
                throw ZteGoformException("login rejected (wrong password?): result=$result body=$text")
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


    /** Enable / disable mobile data (CONNECT_NETWORK / DISCONNECT_NETWORK). */
    fun setMobileDataEnabled(enabled: Boolean) {
        postAdCommand(
            goformId = if (enabled) "CONNECT_NETWORK" else "DISCONNECT_NETWORK",
            notCallback = true,
        )
    }

    /** Soft-reboot the modem (REBOOT_DEVICE). */
    fun rebootDevice() {
        postAdCommand(goformId = "REBOOT_DEVICE", notCallback = false)
    }

    private fun postAdCommand(goformId: String, notCallback: Boolean) {
        ensureLoggedIn()
        ensureVersionsCached()
        val rd = fetchRd()
        val ad = ZteGoformAuth.computeAd(waInnerVersion, crVersion, rd)
        val formBuilder = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", goformId)
            .add("AD", ad)
        if (notCallback) {
            formBuilder.add("notCallback", "true")
        }
        val request = Request.Builder()
            .url("http://$baseHost${ZteGoformAuth.SET_PATH}")
            .header("Referer", "http://$baseHost/index.html")
            .header("Origin", "http://$baseHost")
            .post(formBuilder.build())
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ZteGoformException("$goformId HTTP ${response.code}: $text")
            }
            val fields = runCatching { ZteReqprocStatusMapper.fieldsFromJsonObject(text) }
                .getOrDefault(emptyMap())
            val result = fields["result"].orEmpty()
            if (result.isNotEmpty() &&
                !result.equals("success", ignoreCase = true) &&
                result != "0"
            ) {
                throw ZteGoformException("$goformId rejected: result=$result body=$text")
            }
        }
    }

    private fun ensureVersionsCached() {
        if (waInnerVersion.isNotBlank()) return
        val fields = fetchStatus(listOf("wa_inner_version", "cr_version"))
        rememberVersions(fields)
        if (waInnerVersion.isBlank()) {
            throw ZteGoformException("empty wa_inner_version (needed for AD)")
        }
    }

    private fun rememberVersions(fields: Map<String, String>) {
        fields["wa_inner_version"]?.trim()?.takeIf { it.isNotEmpty() }?.let { waInnerVersion = it }
        // cr_version may legitimately be empty on MF79U; still accept explicit empty updates
        if (fields.containsKey("cr_version")) {
            crVersion = fields["cr_version"].orEmpty().trim()
        }
    }

    private fun fetchRd(): String {
        val url = "http://$baseHost${ZteGoformAuth.GET_PATH}".toHttpUrl().newBuilder()
            .addQueryParameter("isTest", "false")
            .addQueryParameter("cmd", "RD")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Referer", "http://$baseHost/index.html")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ZteGoformException("RD HTTP ${response.code}")
            }
            val rd = ZteReqprocStatusMapper.fieldsFromJsonObject(text)["RD"].orEmpty()
            if (rd.isBlank()) throw ZteGoformException("empty RD challenge")
            return rd
        }
    }

    fun invalidateSession() {
        loggedIn = false
        waInnerVersion = ""
        crVersion = ""
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
