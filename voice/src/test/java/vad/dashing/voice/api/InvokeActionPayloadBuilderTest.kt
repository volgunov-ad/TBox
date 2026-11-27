package vad.dashing.voice.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvokeActionPayloadBuilderTest {
    @Test
    fun build_builtinMediaNext() {
        val action = CatalogAction(
            type = "builtin",
            actionType = "media_next",
            voiceAliasesRu = listOf("следующий трек"),
        )
        val json = InvokeActionPayloadBuilder.build(action).getOrThrow()
        assertEquals("builtin", json.getString("type"))
        assertEquals("media_next", json.getString("actionType"))
        assertEquals("", json.getString("stringValue"))
        val wrapped = InvokeActionPayloadBuilder.wrapActions(json)
        assertTrue(wrapped.contains("\"actions\""))
    }

    @Test
    fun build_canCommandRejectedWithoutSlots() {
        val action = CatalogAction(
            type = "can_command",
            bus = "vehicle",
            propertyId = 1,
            voiceAliasesRu = listOf("климат"),
        )
        assertTrue(InvokeActionPayloadBuilder.build(action).isFailure)
    }

    @Test
    fun buildCanCommand_putsValue() {
        val json = InvokeActionPayloadBuilder.buildCanCommand(
            bus = "vehicle",
            propertyId = 188,
            operation = "set",
            value = "on",
        )
        assertEquals("can_command", json.getString("type"))
        assertEquals(188, json.getInt("propertyId"))
        assertEquals("on", json.getString("value"))
    }

    @Test
    fun buildBuiltin_withVolume() {
        val json = InvokeActionPayloadBuilder.buildBuiltin("set_media_volume", intValue = 15)
        assertEquals(15, json.getInt("intValue"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiClientInvokeParseTest {
    @Test
    fun parseInvokeResults() {
        val parsed = ExternalApiClient.parseInvokeResults(
            """{"results":[{"success":true,"message":"ok"}]}""",
        )
        assertEquals(1, parsed.results.size)
        assertTrue(parsed.results[0].success)
        assertEquals("ok", parsed.results[0].message)
    }

    @Test
    fun parseRunAutomation() {
        val parsed = ExternalApiClient.parseRunAutomation(
            """{"accepted":true,"message":""}""",
        )
        assertTrue(parsed.accepted)
    }
}
