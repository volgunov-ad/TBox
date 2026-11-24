package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.esp.EspBleRelayChannel

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EspBleRelayTargetTest {
    @Test
    fun channelIsTheExistingMaskBit() {
        assertEquals(1, EspBleRelayChannel.maskBit(0))
        assertEquals(2, EspBleRelayChannel.maskBit(1))
        assertEquals(128, EspBleRelayChannel.maskBit(7))
        assertEquals(1, EspBleRelayChannel.displayNumber(0))
        assertEquals(8, EspBleRelayChannel.displayNumber(7))
        assertTrue(EspBleRelayChannel.isClosed(0b1000_0010, 1))
        assertTrue(!EspBleRelayChannel.isClosed(0b1000_0010, 0))
    }

    @Test
    fun latchKeepsOtherBitsAndRefusesAnUnknownMask() {
        assertEquals(0b0000_0011, EspBleRelayChannel.nextLatchMask(0b0000_0010, 0, true))
        assertEquals(0b0000_0010, EspBleRelayChannel.nextLatchMask(0b0000_0011, 0, false))
        assertNull(EspBleRelayChannel.nextLatchMask(null, 0, true))
        assertNull(EspBleRelayChannel.nextLatchMask(0, 8, true))
    }

    @Test
    fun actionTextKeepsIdChannelAndPulse() {
        val legacy = AutomationAction.Builtin(
            AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE,
            intValue = 1,
            stringValue = "500",
        )
        assertEquals("", EspBleRelayActionEncoding.idOf(legacy))
        assertNull(EspBleRelayActionEncoding.channelField(legacy))
        assertEquals("500", EspBleRelayActionEncoding.pulseText(legacy))
        assertEquals(500L, EspBleRelayActionEncoding.pulseMs(legacy))

        val bareId = legacy.copy(stringValue = "AABBCCDD")
        assertEquals("aabbccdd", EspBleRelayActionEncoding.idOf(bareId))
        assertNull(EspBleRelayActionEncoding.channelField(bareId))

        val packed = EspBleRelayActionEncoding.pack(
            AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE,
            "aabbccdd",
            3,
            "500",
        )
        assertEquals("id=aabbccdd ch=3 ms=500", packed)
        val parsed = legacy.copy(stringValue = packed)
        assertEquals("aabbccdd", EspBleRelayActionEncoding.idOf(parsed))
        assertEquals(3, EspBleRelayActionEncoding.channelOf(parsed))
        assertEquals("500", EspBleRelayActionEncoding.pulseText(parsed))

        val invalid = legacy.copy(stringValue = "id=aabbccdd ch=99")
        assertEquals(99, EspBleRelayActionEncoding.channelField(invalid))
        assertEquals(0, EspBleRelayActionEncoding.channelOf(legacy.copy(stringValue = "")))
    }

    @Test
    fun migrationFillsBlankIdOnceAndThenIgnoresListOrder() {
        val blank = FloatingDashboardWidgetConfig(dataKey = "espBleRelay0")
        val filled = EspBleRelayTargetMigration.widget(blank, listOf("aaaaaaaa", "bbbbbbbb"))
        assertEquals("aaaaaaaa", filled.espBleRelayId)
        assertEquals(0, filled.espBleRelayChannel)

        val stored = FloatingDashboardWidgetConfig(
            dataKey = "espBleRelay0",
            espBleRelayId = "bbbbbbbb",
            espBleRelayChannel = 2,
        )
        assertSame(
            stored,
            EspBleRelayTargetMigration.widget(stored, listOf("cccccccc", "aaaaaaaa")),
        )

        val action = AutomationAction.Builtin(
            type = AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE,
            intValue = 1,
        )
        val trigger = AutomationTrigger.StateEquals(
            id = "t",
            signal = AutomationSignalId.ESP_BLE_RELAY_1,
            source = AutomationSignalSource.APP,
            expectedState = "on",
        )
        val document = AutomationDocument(
            automations = listOf(
                AutomationDefinition(
                    name = "relay",
                    triggers = listOf(trigger),
                    actions = listOf(action),
                ),
            ),
        )
        val once = EspBleRelayTargetMigration.document(
            document,
            listOf("aaaaaaaa", "bbbbbbbb"),
        )
        val migratedTrigger = once.automations[0].triggers[0] as AutomationTrigger.StateEquals
        val migratedAction = once.automations[0].actions[0] as AutomationAction.Builtin
        assertEquals("bbbbbbbb", migratedTrigger.relayId)
        assertEquals(0, migratedTrigger.relayChannel)
        assertEquals("id=bbbbbbbb ch=0", migratedAction.stringValue)
        val shifted = EspBleRelayTargetMigration.document(
            once,
            listOf("cccccccc", "dddddddd"),
        )
        assertSame(once, shifted)
    }

    @Test
    fun codecRoundTripsRelayTarget() {
        val definition = AutomationDefinition(
            name = "relay",
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "t",
                    signal = AutomationSignalId.ESP_BLE_RELAY_0,
                    source = AutomationSignalSource.APP,
                    expectedState = "on",
                    relayId = "AABBCCDD",
                    relayChannel = 4,
                ),
            ),
            conditions = listOf(
                AutomationCondition.State(
                    signal = AutomationSignalId.ESP_BLE_RELAY_1,
                    source = AutomationSignalSource.APP,
                    expectedState = "off",
                    relayId = "11223344",
                    relayChannel = 1,
                ),
            ),
            actions = listOf(
                AutomationAction.Builtin(AutomationBuiltinActionType.RESTART_TBOX),
            ),
        )
        val decoded = AutomationCodec.decode(AutomationCodec.encode(AutomationDocument(automations = listOf(definition))))
            .getOrThrow()
            .automations
            .single()
        val trigger = decoded.triggers.single() as AutomationTrigger.StateEquals
        val condition = decoded.conditions.single() as AutomationCondition.State
        assertEquals("aabbccdd", trigger.relayId)
        assertEquals(4, trigger.relayChannel)
        assertEquals("11223344", condition.relayId)
        assertEquals(1, condition.relayChannel)
    }

    @Test
    fun validatorAcceptsLegacySlotAndRejectsABadTarget() {
        val legacy = definition(
            AutomationAction.Builtin(
                AutomationBuiltinActionType.ESP_BLE_RELAY_SET,
                intValue = 0,
                boolValue = true,
            ),
        )
        assertTrue(AutomationValidator.validate(legacy).none { it.path.contains("stringValue") || it.path.contains("intValue") })

        val badId = definition(
            AutomationAction.Builtin(
                AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE,
                stringValue = "not-an-id",
            ),
        )
        assertTrue(AutomationValidator.validate(badId).any { it.path.endsWith("stringValue") })

        val badChannel = definition(
            AutomationAction.Builtin(
                AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE,
                stringValue = "id=aabbccdd ch=99 ms=500",
            ),
        )
        assertTrue(AutomationValidator.validate(badChannel).any { it.message.contains("0") })

        val badSignal = AutomationDefinition(
            name = "relay",
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "t",
                    signal = AutomationSignalId.ESP_BLE_RELAY_0,
                    source = AutomationSignalSource.APP,
                    expectedState = "on",
                    relayChannel = 8,
                ),
            ),
            actions = listOf(AutomationAction.Builtin(AutomationBuiltinActionType.RESTART_TBOX)),
        )
        assertTrue(AutomationValidator.validate(badSignal).any { it.path.endsWith("relayChannel") })
    }

    private fun definition(action: AutomationAction.Builtin): AutomationDefinition =
        AutomationDefinition(
            name = "relay",
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "1",
                    event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                ),
            ),
            actions = listOf(action),
        )
}
