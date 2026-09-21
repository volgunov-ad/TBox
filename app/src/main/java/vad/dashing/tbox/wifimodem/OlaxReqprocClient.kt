package vad.dashing.tbox.wifimodem

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP client for Olax F95 **reqproc** admin API.
 *
 * Verified from real-device HAR (`OlaxF95.har`, `OlaxF95_on_off_connection.har`):
 * - Login: `POST /reqproc/proc_post` with `goformId=LOGIN` and Base64 password
 * - Status: `GET /reqproc/proc_get?multi_data=1&cmd=…`
 * - Data/reboot: CONNECT/DISCONNECT/REBOOT without AD/RD (unlike ZTE MF79U goform)
 *
 * Safe for sequential use from a single poller coroutine.
 */
class OlaxReqprocClient(
    host: String,
    private val password: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val baseHost: String = host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .substringBefore(':')
        .ifBlank { WifiModemModel.OLAX_F95.defaultHost }

    @Volatile
    private var loggedIn: Boolean = false

    /**
     * Full status poll: home (bars / PPP / thrpt) then radio+device (RSSI / IMEI).
     * Pass [cmds] for a single targeted multi_data read.
     */
    fun fetchStatus(cmds: List<String>? = null): Map<String, String> {
        ensureLoggedIn()
        if (cmds != null) {
            return fetchCmdFields(cmds)
        }
        val home = fetchCmdFields(OlaxReqprocStatusCmds.HOME)
        val radioDevice = fetchCmdFields(
            OlaxReqprocStatusCmds.RADIO + OlaxReqprocStatusCmds.DEVICE,
        )
        return OlaxReqprocStatusCmds.mergePreferNonBlank(home, radioDevice)
    }

    private fun fetchCmdFields(cmds: List<String>): Map<String, String> {
        val url = "http://$baseHost${OlaxReqprocStatusCmds.GET_PATH}".toHttpUrl().newBuilder()
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
                throw OlaxReqprocException("status HTTP ${response.code}")
            }
            val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(body)
            val loginfo = fields["loginfo"].orEmpty()
            if (loginfo.equals("error", ignoreCase = true) ||
                loginfo.contains("not login", ignoreCase = true)
            ) {
                loggedIn = false
                throw OlaxReqprocException("session expired ($loginfo)")
            }
            return fields
        }
    }

    fun ensureLoggedIn() {
        if (!loggedIn) login()
    }

    fun login() {
        val encoded = ZteReqprocAuth.encodePassword(
            ZteReqprocAuth.Dialect.BASE64_PASSWORD,
            password,
        )
        val form = FormBody.Builder()
            .add("goformId", "LOGIN")
            .add("password", encoded)
            .build()
        val request = Request.Builder()
            .url("http://$baseHost${OlaxReqprocStatusCmds.POST_PATH}")
            .header("Referer", "http://$baseHost/index.html")
            .header("Origin", "http://$baseHost")
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OlaxReqprocException("login HTTP ${response.code}: $text")
            }
            val fields = runCatching { ZteReqprocStatusMapper.fieldsFromJsonObject(text) }
                .getOrDefault(emptyMap())
            val result = fields["result"]
            if (!ZteReqprocAuth.isLoginSuccess(result)) {
                throw OlaxReqprocException("login rejected (wrong password?): result=$result body=$text")
            }
            loggedIn = true
        }
    }

    /** Enable / disable mobile data (CONNECT_NETWORK / DISCONNECT_NETWORK). */
    fun setMobileDataEnabled(enabled: Boolean) {
        postGoform(
            goformId = if (enabled) "CONNECT_NETWORK" else "DISCONNECT_NETWORK",
            notCallback = true,
        )
    }

    /** Soft-reboot the modem (REBOOT_DEVICE). */
    fun rebootDevice() {
        postGoform(goformId = "REBOOT_DEVICE", notCallback = false)
    }

    private fun postGoform(goformId: String, notCallback: Boolean) {
        ensureLoggedIn()
        val formBuilder = FormBody.Builder()
            .add("goformId", goformId)
        if (notCallback) {
            formBuilder.add("notCallback", "true")
        }
        val request = Request.Builder()
            .url("http://$baseHost${OlaxReqprocStatusCmds.POST_PATH}")
            .header("Referer", "http://$baseHost/index.html")
            .header("Origin", "http://$baseHost")
            .post(formBuilder.build())
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw OlaxReqprocException("$goformId HTTP ${response.code}: $text")
            }
            // REBOOT may return empty body / connection drop; CONNECT/DISCONNECT return JSON.
            if (text.isBlank()) return
            val fields = runCatching { ZteReqprocStatusMapper.fieldsFromJsonObject(text) }
                .getOrDefault(emptyMap())
            val result = fields["result"].orEmpty()
            if (result.isNotEmpty() &&
                !result.equals("success", ignoreCase = true) &&
                result != "0"
            ) {
                throw OlaxReqprocException("$goformId rejected: result=$result body=$text")
            }
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

class OlaxReqprocException(message: String) : Exception(message)
