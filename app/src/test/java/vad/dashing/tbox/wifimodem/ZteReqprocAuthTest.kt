package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZteReqprocAuthTest {

    @Test
    fun base64PasswordMatchesKnownVector() {
        // password "admin" → YWRtaW4= (Habr / Olax M100 style)
        assertEquals(
            "YWRtaW4=",
            ZteReqprocAuth.encodePassword(ZteReqprocAuth.Dialect.BASE64_PASSWORD, "admin"),
        )
    }

    @Test
    fun sha256NonceMatchesSpecShape() {
        // nonce "abc" + password "admin" → base64(sha256_hex("abcadmin"))
        val expectedHex = ZteReqprocAuth.sha256Hex("abcadmin")
        val expected = ZteReqprocAuth.base64Utf8(expectedHex)
        assertEquals(
            expected,
            ZteReqprocAuth.encodePassword(ZteReqprocAuth.Dialect.SHA256_NONCE, "admin", "abc"),
        )
        assertEquals(64, expectedHex.length)
    }

    @Test
    fun loginSuccessCodes() {
        assertTrue(ZteReqprocAuth.isLoginSuccess("0"))
        assertTrue(ZteReqprocAuth.isLoginSuccess("4"))
        assertTrue(ZteReqprocAuth.isLoginSuccess("success"))
        assertFalse(ZteReqprocAuth.isLoginSuccess("3"))
        assertFalse(ZteReqprocAuth.isLoginSuccess(null))
    }

    @Test
    fun encodeUsername() {
        assertEquals("YWRtaW4=", ZteReqprocAuth.encodeUsername("admin"))
    }
}
