package vad.dashing.tbox.wifimodem

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Login helpers for ZTE UFI/MiFi **goform** admin API
 * (`/goform/goform_get_cmd_process` + `/goform/goform_set_cmd_process`).
 *
 * Verified against a live **ZTE MF79U** HAR capture:
 * 1. `GET ...?isTest=false&cmd=LD` → `{"LD":"<64 hex>"}`
 * 2. `POST ... goformId=LOGIN&password=<hash>` where
 *    `hash = SHA256_hex_upper( SHA256_hex_upper(password) + LD )`
 * 3. `{"result":"0"}` = ok, `"3"` = wrong password.
 */
object ZteGoformAuth {

    const val GET_PATH = "/goform/goform_get_cmd_process"
    const val SET_PATH = "/goform/goform_set_cmd_process"

    fun encodeLoginPassword(plaintextPassword: String, ld: String): String {
        require(ld.isNotBlank()) { "LD challenge must not be blank" }
        val inner = sha256HexUpper(plaintextPassword)
        return sha256HexUpper(inner + ld)
    }

    fun sha256HexUpper(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { b -> "%02X".format(b) }
    }

    fun isLoginSuccess(result: String?): Boolean =
        result == "0" || result.equals("success", ignoreCase = true)

    /** Build `application/x-www-form-urlencoded` body for LOGIN. */
    fun loginFormBody(hashedPassword: String): String =
        "isTest=false&goformId=LOGIN&password=$hashedPassword"

    fun md5HexLower(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    /**
     * One-shot AD for set commands (except LOGIN), verified on MF79U HAR:
     * `AD = MD5_hex_lower( MD5_hex_lower(wa_inner_version + cr_version) + RD )`.
     */
    fun computeAd(waInnerVersion: String, crVersion: String, rd: String): String {
        require(rd.isNotBlank()) { "RD challenge must not be blank" }
        val inner = md5HexLower(waInnerVersion + crVersion)
        return md5HexLower(inner + rd)
    }

}
