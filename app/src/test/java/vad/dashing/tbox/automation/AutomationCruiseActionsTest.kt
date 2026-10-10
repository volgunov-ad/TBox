package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
import vad.dashing.tbox.CruiseControlType
import vad.dashing.tbox.GlobalCruiseControlType
import vad.dashing.tbox.VehicleFeatureSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationCruiseActionsTest {
    @Test
    fun parseForcedMode_acceptsAccAndCcsOnly() {
        assertEquals(CruiseControlType.ACC, AutomationCruiseActions.parseForcedMode("acc"))
        assertEquals(CruiseControlType.CCS, AutomationCruiseActions.parseForcedMode("CCS"))
        assertEquals(CruiseControlType.ACC, AutomationCruiseActions.parseForcedMode("acc:150:200"))
        assertNull(AutomationCruiseActions.parseForcedMode("auto"))
        assertNull(AutomationCruiseActions.parseForcedMode(""))
        assertNull(AutomationCruiseActions.parseForcedMode("default"))
    }

    @Test
    fun parseEngageParams_normalizesTargetAndOptionalIntervals() {
        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.ACC)
        val defaults = AutomationCruiseActions.parseEngageParams(
            AutomationAction.Builtin(
                type = AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
                intValue = 0,
                stringValue = "acc",
            ),
        )
        assertEquals(CruiseControlType.ACC, defaults.cruiseControlType)
        assertEquals(ACC_CRUISE_TARGET_KMH_DEFAULT, defaults.targetKmh)
        assertEquals(ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT, defaults.increaseIntervalMs)
        assertEquals(ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT, defaults.decreaseIntervalMs)

        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.CCS)
        val custom = AutomationCruiseActions.parseEngageParams(
            AutomationAction.Builtin(
                type = AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
                intValue = 120,
                stringValue = "ccs:200:100",
            ),
        )
        assertEquals(CruiseControlType.CCS, custom.cruiseControlType)
        assertEquals(120, custom.targetKmh)
        assertEquals(200, custom.increaseIntervalMs)
        assertEquals(100, custom.decreaseIntervalMs)

        val clamped = AutomationCruiseActions.parseEngageParams(
            AutomationAction.Builtin(
                type = AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
                intValue = 999,
                stringValue = "",
            ),
        )
        assertEquals(150, clamped.targetKmh)
        assertEquals(CruiseControlType.CCS, clamped.cruiseControlType)
        VehicleFeatureSettings.updateCruise(GlobalCruiseControlType.DEFAULT)
    }

    @Test
    fun encodeMode_omitsDefaultIntervals() {
        assertEquals("acc", AutomationCruiseActions.encodeMode(CruiseControlType.ACC))
        assertEquals("ccs", AutomationCruiseActions.encodeMode(CruiseControlType.CCS))
        assertEquals(
            "acc:200:100",
            AutomationCruiseActions.encodeMode(
                CruiseControlType.ACC,
                increaseIntervalMs = 200,
                decreaseIntervalMs = 100,
            ),
        )
        assertEquals(
            "acc",
            AutomationCruiseActions.encodeMode(
                CruiseControlType.ACC,
                increaseIntervalMs = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT,
                decreaseIntervalMs = ACC_CRUISE_STEP_INTERVAL_MS_DEFAULT,
            ),
        )
    }

    @Test
    fun builtinStorageKeys_areStable() {
        assertEquals("cruise_engage_to_target", AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET.storageKey)
        assertEquals("cruise_pause", AutomationBuiltinActionType.CRUISE_PAUSE.storageKey)
        assertEquals("cruise_full_off", AutomationBuiltinActionType.CRUISE_FULL_OFF.storageKey)
        assertEquals("cruise_resume", AutomationBuiltinActionType.CRUISE_RESUME.storageKey)
        assertEquals(
            "cruise_activate_at_current_speed",
            AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED.storageKey,
        )
        assertEquals("cruise_nudge", AutomationBuiltinActionType.CRUISE_NUDGE.storageKey)
        assertEquals(
            AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
            AutomationBuiltinActionType.fromStorageKey("cruise_engage_to_target"),
        )
        assertEquals(
            AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED,
            AutomationBuiltinActionType.fromStorageKey("cruise_activate_at_current_speed"),
        )
    }

    @Test
    fun codec_roundTripsCruiseBuiltins() {
        val definition = AutomationDefinition(
            id = "cruise-test",
            name = "cruise",
            triggers = listOf(
                AutomationTrigger.SystemEvent(
                    id = "1",
                    event = AutomationSystemEvent.MENU_OPENED,
                ),
            ),
            actions = listOf(
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET,
                    intValue = 100,
                    stringValue = "acc:200:100",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_PAUSE,
                    stringValue = "ccs",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_FULL_OFF,
                    stringValue = "acc",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_RESUME,
                    stringValue = "ccs",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED,
                    stringValue = "acc",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.CRUISE_NUDGE,
                    intValue = -1,
                    stringValue = "acc",
                ),
            ),
        )
        val document = AutomationDocument(automations = listOf(definition))
        val decoded = AutomationCodec.decode(AutomationCodec.encode(document)).getOrThrow()
        assertEquals(document, decoded)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }
}
