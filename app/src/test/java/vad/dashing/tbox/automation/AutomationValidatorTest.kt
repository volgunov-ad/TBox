package vad.dashing.tbox.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class AutomationValidatorTest {
    @Test
    fun validMinimalDefinition_hasNoIssues() {
        assertTrue(AutomationValidator.validate(validDefinition()).isEmpty())
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
