package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZteGoformAuthTest {

    @Test
    fun mf79uHarLoginHashMatchesAdminPlusLd() {
        // Exact values from ZTE_MF79U.har (successful LOGIN after cmd=LD)
        val ld = "BC52DD634277C4A34A2D6210994A9A5E2AB6D33BB4A3A8963410E00CA6C15A02"
        val expected = "B3B4C8CC7AFA2C012937C80E97ED94BEA18D5768BB13A2251B7A006A311D55D6"
        assertEquals(expected, ZteGoformAuth.encodeLoginPassword("admin", ld))
    }

    @Test
    fun wrongPasswordDoesNotMatchHarSuccessHash() {
        val ld = "BC52DD634277C4A34A2D6210994A9A5E2AB6D33BB4A3A8963410E00CA6C15A02"
        val expected = "B3B4C8CC7AFA2C012937C80E97ED94BEA18D5768BB13A2251B7A006A311D55D6"
        assertFalse(ZteGoformAuth.encodeLoginPassword("wrong", ld) == expected)
    }

    @Test
    fun loginSuccessCodes() {
        assertTrue(ZteGoformAuth.isLoginSuccess("0"))
        assertFalse(ZteGoformAuth.isLoginSuccess("3"))
        assertFalse(ZteGoformAuth.isLoginSuccess(null))
    }

    @Test
    fun loginFormBody() {
        assertEquals(
            "isTest=false&goformId=LOGIN&password=ABC",
            ZteGoformAuth.loginFormBody("ABC"),
        )
    }

    @Test
    fun pathsMatchHar() {
        assertEquals("/goform/goform_get_cmd_process", ZteGoformAuth.GET_PATH)
        assertEquals("/goform/goform_set_cmd_process", ZteGoformAuth.SET_PATH)
    }
}
