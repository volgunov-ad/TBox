package vad.dashing.rootsvc

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult
import vad.dashing.rootsvc.client.WireCodec

class WireCodecTest {
    @Test
    fun requestRoundTrip() {
        val line = WireCodec.requestLine("abc", "hello", null, "vad.dashing.camd", "0.9.9")
        val o = WireCodec.parseLine(line)
        assertEquals(1, o.getInt("v"))
        assertEquals("abc", o.getString("id"))
        assertEquals("hello", o.getString("cmd"))
        assertEquals("vad.dashing.camd", o.getString("client"))
    }

    @Test
    fun parseAck() {
        assertTrue(WireCodec.isAck(JSONObject("""{"id":"x","ack":true}""")))
        assertFalse(WireCodec.isAck(JSONObject("""{"id":"x","ok":true}""")))
    }

    @Test
    fun parseOkResult() {
        val r = WireCodec.parseResult(JSONObject("""{"id":"c1","ok":true,"exit":0,"ms":12}"""))
        assertEquals(RootSvcResult.Kind.OK, r.kind)
        assertEquals(0, r.exit)
    }

    @Test
    fun parseForbidden() {
        val r = WireCodec.parseResult(
            JSONObject("""{"id":"c1","ok":false,"err":"forbidden","msg":"cap svc.ctl"}"""),
        )
        assertEquals(RootSvcResult.Kind.REJECTED, r.kind)
        assertTrue(r.isErr(RootSvcProtocol.ERR_FORBIDDEN))
    }

    @Test
    fun parseFailedWithExit() {
        val r = WireCodec.parseResult(
            JSONObject("""{"id":"c1","ok":false,"err":"failed","exit":1,"stderr":"boom"}"""),
        )
        assertEquals(RootSvcResult.Kind.FAILED, r.kind)
        assertEquals(1, r.exit)
        assertEquals("boom", r.stderr)
    }
}
