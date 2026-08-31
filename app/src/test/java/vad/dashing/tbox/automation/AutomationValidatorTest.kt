package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class AutomationValidatorTest {
    @Test
    fun nextTriggerId_usesSmallestUnusedPositiveInteger() {
        assertEquals("1", nextAutomationTriggerId(emptyList()))
        assertEquals("2", nextAutomationTriggerId(listOf("1")))
        assertEquals("3", nextAutomationTriggerId(listOf("1", "2")))
        assertEquals("2", nextAutomationTriggerId(listOf("1", "3")))
        assertEquals("1", nextAutomationTriggerId(listOf("home", "rpm")))
        assertEquals("2", nextAutomationTriggerId(listOf("1", "home")))
    }

    @Test
    fun validMinimalDefinition_hasNoIssues() {
        assertTrue(AutomationValidator.validate(validDefinition()).isEmpty())
    }

    @Test
    fun triggerIds_mayRepeatAcrossAutomations() {
        val first = validDefinition(
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "1",
                    event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                ),
                AutomationTrigger.SystemEvent(
                    id = "2",
                    event = AutomationSystemEvent.MENU_OPENED,
                ),
            ),
        ).copy(id = "auto-a", name = "A")
        val second = first.copy(id = "auto-b", name = "B")
        val issues = AutomationValidator.validate(
            AutomationDocument(automations = listOf(first, second)),
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun duplicated_keepsTriggerIdsAndDisablesCopy() {
        val source = validDefinition().copy(id = "auto-a", name = "Климат", enabled = true)
        val copy = source.duplicated()
        assertNotEquals(source.id, copy.id)
        assertEquals("Климат (копия)", copy.name)
        assertFalse(copy.enabled)
        assertEquals(source.triggers, copy.triggers)
        assertEquals(source.actions, copy.actions)
    }

    @Test
    fun blankName_isRejected() {
        val issues = AutomationValidator.validate(validDefinition().copy(name = "  "))
        assertTrue(issues.any { it.path.endsWith(".name") })
    }

    @Test
    fun unknownState_isRejected() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "park",
                    signal = AutomationSignalId.GEAR_MODE,
                    source = AutomationSignalSource.TBOX,
                    expectedState = "X",
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("expectedState") },
        )
    }

    @Test
    fun listedSeatState_isAccepted() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "heat",
                    signal = AutomationSignalId.FRONT_LEFT_SEAT_MODE,
                    source = AutomationSignalSource.HEAD_UNIT,
                    expectedState = "heat_2",
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun foregroundAppPackage_isAccepted() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "maps",
                    signal = AutomationSignalId.FOREGROUND_APP,
                    source = AutomationSignalSource.APP,
                    expectedState = "com.yandex.yandexnavi",
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun foregroundAppBlankPackage_isRejected() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "maps",
                    signal = AutomationSignalId.FOREGROUND_APP,
                    source = AutomationSignalSource.APP,
                    expectedState = "  ",
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("expectedState") },
        )
    }

    @Test
    fun espRelayStateTrigger_isAccepted() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.StateEquals(
                    id = "relay0",
                    signal = AutomationSignalId.ESP_RELAY_0,
                    source = AutomationSignalSource.APP,
                    expectedState = "on",
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun resetThresholdOnWrongSide_isRejected() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.NumericThreshold(
                    id = "rpm",
                    signal = AutomationSignalId.ENGINE_RPM,
                    source = AutomationSignalSource.TBOX,
                    direction = AutomationThresholdDirection.ABOVE,
                    threshold = 1_000.0,
                    resetThreshold = 1_100.0,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("resetThreshold") },
        )
    }

    @Test
    fun resetThresholdOnWrongSide_isIgnoredWhenRearmDisabled() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.NumericThreshold(
                    id = "rpm",
                    signal = AutomationSignalId.ENGINE_RPM,
                    source = AutomationSignalSource.TBOX,
                    direction = AutomationThresholdDirection.ABOVE,
                    threshold = 1_000.0,
                    resetThreshold = 1_100.0,
                    rearmEnabled = false,
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun conditionWaitOutOfRange_isRejected() {
        val issues = AutomationValidator.validate(
            validDefinition().copy(conditionWaitMillis = -1L),
        )
        assertTrue(issues.any { it.path.contains("conditionWaitMillis") })
    }

    @Test
    fun conditionWaitZero_isAccepted() {
        assertTrue(
            AutomationValidator.validate(validDefinition().copy(conditionWaitMillis = 0L)).isEmpty(),
        )
    }

    @Test
    fun triggeredByUnknownId_isRejected() {
        val definition = validDefinition().copy(
            conditions = listOf(AutomationCondition.TriggeredBy(setOf("missing"))),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("triggerIds") },
        )
    }

    @Test
    fun mediaActionWithoutPackage_isRejected() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.Builtin(AutomationBuiltinActionType.MEDIA_PLAY),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("stringValue") },
        )
    }

    @Test
    fun espRelaySet_isRejected() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.ESP_RELAY_SET,
                    intValue = 1,
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isNotEmpty())
    }

    @Test
    fun espRelayChannelOutOfRange_isRejected() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.ESP_RELAY_TOGGLE,
                    intValue = 8,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("intValue") },
        )
    }

    @Test
    fun userMessageWithoutText_isRejected() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.Builtin(AutomationBuiltinActionType.SHOW_TOAST),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("stringValue") },
        )
    }

    @Test
    fun userMessageWithText_isAccepted() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SHOW_ALERT,
                    stringValue = "Остановитесь",
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun alertAutoCloseNegative_isRejected() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SHOW_ALERT,
                    stringValue = "Остановитесь",
                    intValue = -1,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("intValue") },
        )
    }

    @Test
    fun trunkSet_isRejected() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
                    operation = AutomationCanOperation.SET,
                    value = 1,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.message.contains("безопасном каталоге") },
        )
    }

    @Test
    fun trunkPulse_isAccepted() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL,
                    operation = AutomationCanOperation.TRUNK_PULSE,
                    value = 1,
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun geofenceRearmOnWrongSide_isRejected() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.Geofence(
                    id = "home",
                    queryText = "55.75, 37.62",
                    latitude = 55.75,
                    longitude = 37.62,
                    direction = AutomationGeofenceDirection.ENTER,
                    zoneRadiusMeters = 50.0,
                    rearmRadiusMeters = 40.0,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("rearmRadiusMeters") },
        )
    }

    @Test
    fun geofenceUnparsedPoint_isRejected() {
        val definition = validDefinition(
            triggers = listOf(AutomationTrigger.Geofence(id = "home")),
        )
        assertTrue(
            AutomationValidator.validate(definition)
                .any { it.path.contains("latitude") },
        )
    }

    @Test
    fun solarTrigger_isAccepted() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.Solar(
                    id = "dusk",
                    event = AutomationSolarEvent.SUNSET,
                    offsetMinutes = 120,
                    offsetDirection = AutomationSolarOffsetDirection.AFTER,
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun solarOffsetOutOfRange_isRejected() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.Solar(
                    id = "dusk",
                    offsetMinutes = AUTOMATION_SOLAR_MAX_OFFSET_MINUTES + 1,
                ),
            ),
        )
        assertTrue(
            AutomationValidator.validate(definition).any { it.path.endsWith(".offsetMinutes") },
        )
    }

    @Test
    fun emptySolarCondition_isRejected() {
        val definition = validDefinition().copy(
            conditions = listOf(AutomationCondition.Solar()),
        )
        assertTrue(AutomationValidator.validate(definition).any { it.path.contains("conditions") })
    }

    @Test
    fun timeTrigger_isAccepted() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.Time(
                    id = "morning",
                    at = AutomationTimeOfDay(7, 30),
                    weekdays = setOf(AutomationWeekday.MONDAY),
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun emptyTimeCondition_isRejected() {
        val definition = validDefinition().copy(
            conditions = listOf(AutomationCondition.Time()),
        )
        assertTrue(AutomationValidator.validate(definition).any { it.path.contains("conditions") })
    }

    @Test
    fun invalidTimeOfDay_isRejected() {
        val definition = validDefinition(
            triggers = listOf(
                AutomationTrigger.Time(
                    id = "bad",
                    at = AutomationTimeOfDay(24, 0),
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).any { it.path.endsWith(".at") })
    }

    @Test
    fun shadeRoofWindows_areAccepted() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.SUNSHADE_POS,
                    operation = AutomationCanOperation.SET,
                    value = 11,
                ),
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.SUNROOF_CONTROL,
                    operation = AutomationCanOperation.SET,
                    value = 12,
                ),
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.WINDOW_POS,
                    operation = AutomationCanOperation.SET,
                    value = 0,
                ),
            ),
        )
        assertTrue(AutomationValidator.validate(definition).isEmpty())
    }

    @Test
    fun oneInvalidWindowCommand_doesNotFailDocumentIntegrity() {
        val good = validDefinition().copy(id = "good", name = "Ок")
        val bad = validDefinition(
            actions = listOf(
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
                    operation = AutomationCanOperation.SET,
                    value = 5,
                ),
            ),
        ).copy(id = "bad", name = "Стекло 5", enabled = true)
        val document = AutomationDocument(automations = listOf(good, bad))
        assertTrue(AutomationValidator.integrityIssues(document).isEmpty())
        assertTrue(AutomationValidator.validate(document).any { it.path.contains("actions") })
        assertTrue(AutomationValidator.isRunnable(good))
        assertFalse(AutomationValidator.isRunnable(bad))
        val (normalized, disabledIds) = AutomationValidator.withInvalidDisabled(document)
        assertEquals(listOf("bad"), disabledIds)
        assertTrue(normalized.automations.single { it.id == "good" }.enabled)
        assertFalse(normalized.automations.single { it.id == "bad" }.enabled)
        assertTrue(AutomationValidator.isRunnable(normalized.automations.single { it.id == "good" }))
    }

    @Test
    fun rebootProperty_isRejected() {
        val definition = validDefinition(
            actions = listOf(
                AutomationAction.CanCommand(
                    bus = AutomationCanBus.VEHICLE,
                    propertyId = MbCanKnownVehiclePropertyId.SYSTEM_REBOOT,
                    operation = AutomationCanOperation.SET,
                    value = 1,
                ),
            ),
        )
        assertFalse(AutomationValidator.validate(definition).isEmpty())
    }

    private fun validDefinition(
        triggers: List<AutomationTrigger> = listOf(
            AutomationTrigger.SystemEvent(
                id = "service",
                event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
            ),
        ),
        actions: List<AutomationAction> = listOf(AutomationAction.Delay(0L)),
    ): AutomationDefinition = AutomationDefinition(
        id = "automation",
        name = "Test",
        enabled = true,
        triggers = triggers,
        actions = actions,
    )
}
