package vad.dashing.tbox.externalapi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationCanCatalog
import vad.dashing.tbox.automation.AutomationCanOperation
import vad.dashing.tbox.automation.AutomationCanValueCodec
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.mbcan.HvacClimateDomain
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiCatalogWriteTest {
    @Test
    fun everyWritePairIsAllowedOnSupportedModesAndStatesMatchSignal() {
        val seenSignals = mutableSetOf<String>()
        AutomationCanCatalog.entries.forEach { entry ->
            val schema = ExternalApiCatalogWrite.forEntry(entry)
            val signalId = schema.signalId
            if (signalId != null) {
                assertTrue(
                    "signal $signalId is linked twice",
                    seenSignals.add(signalId),
                )
            }
            val write = schema.write ?: return@forEach
            val signal = signalId?.let { id ->
                AutomationSignalCatalog.entries.first { it.id.storageKey == id }
            }
            if (write.kind != ExternalApiCatalogWrite.KIND_NUMBER &&
                write.kind != ExternalApiCatalogWrite.KIND_PULSE &&
                signal != null
            ) {
                write.options.forEach { option ->
                    assertTrue(
                        "${entry.label} state ${option.state} missing from ${signal.id.storageKey}",
                        option.state in signal.stateOptions,
                    )
                }
            }
            val operation = if (write.kind == ExternalApiCatalogWrite.KIND_PULSE) {
                AutomationCanOperation.TRUNK_PULSE
            } else {
                AutomationCanOperation.SET
            }
            write.options.forEach { option ->
                val action = when (val value = option.value) {
                    is String -> AutomationAction.CanCommand(
                        bus = entry.bus,
                        propertyId = entry.propertyId,
                        operation = operation,
                        valueKey = value,
                    )
                    is Int -> AutomationAction.CanCommand(
                        bus = entry.bus,
                        propertyId = entry.propertyId,
                        operation = operation,
                        value = value,
                    )
                    else -> error("unexpected value type for ${entry.label}")
                }
                val canonical = AutomationCanValueCodec.canonicalize(action)
                assertTrue(AutomationCanCatalog.isAllowed(canonical))
                entry.supportedModes.forEach { mode ->
                    assertTrue(
                        "${entry.label} ${option.state} not resolvable on $mode",
                        AutomationCanValueCodec.isResolvable(canonical, mode),
                    )
                }
            }
        }
    }

    @Test
    fun driveModeIsOneSelectWithSignalStates() {
        val entry = AutomationCanCatalog.entries.first {
            it.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE
        }
        val schema = ExternalApiCatalogWrite.forEntry(entry)
        assertEquals("drive_mode", schema.signalId)
        val write = schema.write
        assertNotNull(write)
        assertEquals(ExternalApiCatalogWrite.KIND_OPTIONS, write!!.kind)
        val byState = write.options.associate { it.state to it.value }
        assertEquals(2, byState["ECO"])
        assertEquals(0, byState["NOR"])
        assertEquals(1, byState["SPT"])
    }

    @Test
    fun hvacTemperaturePairsUseMbCanRaw() {
        val entry = AutomationCanCatalog.entries.first {
            it.propertyId == MbCanKnownVehiclePropertyId.HVAC_TEMPERATURE_LEFT
        }
        val write = ExternalApiCatalogWrite.forEntry(entry).write
        assertNotNull(write)
        assertEquals(ExternalApiCatalogWrite.KIND_NUMBER, write!!.kind)
        assertEquals("°C", write.unit)
        val raw = HvacClimateDomain.celsiusToMbCanTempRaw(22.5f)
        val pair = write.options.first { it.state == "22.5" }
        assertEquals(raw, pair.value)
    }

    @Test
    fun trunkPulseIsNotTheDoorSensor() {
        val entry = AutomationCanCatalog.entries.first {
            it.propertyId == MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL
        }
        val schema = ExternalApiCatalogWrite.forEntry(entry)
        assertEquals(null, schema.signalId)
        assertEquals(ExternalApiCatalogWrite.KIND_PULSE, schema.write?.kind)
        assertTrue(schema.operations.contains("trunk_pulse"))
    }

    @Test
    fun windowWriteUsesPortableKeysAndSignalPercents() {
        val entry = AutomationCanCatalog.entries.first {
            it.propertyId == MbCanKnownVehiclePropertyId.WINDOW_FL_POS
        }
        val write = ExternalApiCatalogWrite.forEntry(entry).write
        assertNotNull(write)
        assertEquals(ExternalApiCatalogWrite.KIND_OPTIONS, write!!.kind)
        val byState = write.options.associate { it.state to it.value }
        assertEquals("close", byState["0%"])
        assertEquals("vent", byState["20%"])
        assertEquals("comfort_open", byState["80%"])
        assertEquals("open", byState["100%"])
    }

    @Test
    fun catalogJsonIncludesWriteAndKeepsVoiceAliases() {
        val token = "write-token"
        val clients = listOf(
            ExternalApiPairedClient(
                clientId = "mqtt",
                clientName = "TBox MQTT",
                tokenHash = ExternalApiAuth.sha256Hex(token),
                createdAtEpochMs = 1L,
            ),
        )
        val router = ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { clients },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { emptyList() },
            runAutomationNow = { null },
        )
        val response = router.handle(
            "GET",
            ExternalApiConstants.PATH_CATALOG,
            emptyMap(),
            mapOf("authorization" to "Bearer $token"),
            "",
        )
        assertEquals(200, response.status)
        val json = JSONObject(response.body)
        assertEquals(4, json.getInt("catalogVersion"))
        val actions = json.getJSONArray("actionTypes")
        var drive: JSONObject? = null
        for (i in 0 until actions.length()) {
            val item = actions.getJSONObject(i)
            if (item.optString("type") == "can_command" &&
                item.optInt("propertyId") == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE
            ) {
                drive = item
            }
            if (item.optString("type") == "builtin") {
                assertFalse(item.has("write"))
            }
        }
        assertNotNull(drive)
        assertEquals("drive_mode", drive!!.getString("signalId"))
        assertTrue(drive.getJSONArray("voiceAliasesRu").length() > 0)
        assertEquals("options", drive.getJSONObject("write").getString("kind"))
    }
}
