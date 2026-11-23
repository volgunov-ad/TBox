package vad.dashing.store.dexopt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexoptCodecTest {
    private fun oatImage(store: String, junkBefore: Int = 4096): ByteArray {
        val header = "oat\n138\u0000".toByteArray(Charsets.US_ASCII)
        val kv = store.toByteArray(Charsets.US_ASCII)
        return ByteArray(junkBefore) { (it % 251).toByte() } + header + ByteArray(64) + kv + ByteArray(32)
    }

    @Test
    fun readsCompilerFilterFromOatKeyValueStore() {
        val bytes = oatImage(
            "classpath\u0000\u0000compiler-filter\u0000speed-profile\u0000concurrent-copying\u0000true\u0000",
        )
        assertEquals("speed-profile", DexoptCodec.parseOatCompilerFilter(bytes))
    }

    @Test
    fun readsSpeedAndHonoursLength() {
        val bytes = oatImage("compiler-filter\u0000speed\u0000")
        assertEquals("speed", DexoptCodec.parseOatCompilerFilter(bytes))
        assertNull(DexoptCodec.parseOatCompilerFilter(bytes, 4096))
    }

    @Test
    fun missingOrTruncatedValueIsNull() {
        assertNull(DexoptCodec.parseOatCompilerFilter(oatImage("dex2oat-host\u0000arm64\u0000")))
        val truncated = "compiler-filter\u0000spe".toByteArray(Charsets.US_ASCII)
        assertNull(DexoptCodec.parseOatCompilerFilter(truncated))
    }

    @Test
    fun parsesDumpsysStatusAndFullCompilation() {
        val output = """
                arm64: [status=speed] [reason=unknown]
                arm: [status=speed] [reason=unknown]
        """.trimIndent()
        val filters = DexoptCodec.parseFilters(output)
        assertEquals(listOf("speed", "speed"), filters)
        assertTrue(DexoptCodec.isFullyCompiled(filters))
        assertFalse(DexoptCodec.isFullyCompiled(listOf("speed", "verify")))
        assertFalse(DexoptCodec.isFullyCompiled("speed-profile"))
    }

    @Test
    fun compileCommandIsDetached() {
        val command = DexoptCodec.compileCommand(" vad.dashing.mqtt ")
        assertTrue(command.startsWith("trap '' HUP; nohup cmd package compile -m speed -f vad.dashing.mqtt"))
        assertTrue(command.endsWith("&"))
    }
}
