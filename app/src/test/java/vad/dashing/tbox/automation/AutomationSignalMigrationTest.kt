package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationSignalMigrationTest {
    @Test
    fun decode_migratesNumericDriveModeConditionToState() {
        val decoded = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":false,
                "triggers":[{"type":"system_event","id":"t","event":"menu_opened"}],
                "conditions":[{
                  "type":"numeric",
                  "signal":"drive_mode",
                  "source":"head_unit",
                  "comparison":"equal",
                  "expectedValue":2
                }],
                "actions":[{"type":"delay","durationMillis":0}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        ).getOrThrow()

        val condition = decoded.automations.single().conditions.single()
        assertTrue(condition is AutomationCondition.State)
        condition as AutomationCondition.State
        assertEquals(AutomationSignalId.DRIVE_MODE, condition.signal)
        assertEquals("ECO", condition.expectedState)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }

    @Test
    fun decode_migratesNumericHeadlightConditionToState() {
        val decoded = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":false,
                "triggers":[{"type":"system_event","id":"t","event":"menu_opened"}],
                "conditions":[{
                  "type":"numeric",
                  "signal":"headlight_mode",
                  "source":"head_unit",
                  "comparison":"equal",
                  "expectedValue":1
                }],
                "actions":[{"type":"delay","durationMillis":0}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        ).getOrThrow()

        val condition = decoded.automations.single().conditions.single()
        assertTrue(condition is AutomationCondition.State)
        assertEquals("AUTO", (condition as AutomationCondition.State).expectedState)
    }

    @Test
    fun decode_migratesNumericThresholdDriveModeTriggerToStateEquals() {
        val decoded = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":false,
                "triggers":[{
                  "type":"numeric_threshold",
                  "id":"1",
                  "signal":"drive_mode",
                  "source":"head_unit",
                  "direction":"above",
                  "threshold":2,
                  "resetThreshold":1,
                  "holdMillis":500,
                  "startupBehavior":"initialize_only"
                }],
                "conditions":[],
                "actions":[{"type":"delay","durationMillis":0}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        ).getOrThrow()

        val trigger = decoded.automations.single().triggers.single() as AutomationTrigger.StateEquals
        assertEquals(AutomationSignalId.DRIVE_MODE, trigger.signal)
        assertEquals("ECO", trigger.expectedState)
        assertEquals(500L, trigger.holdMillis)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }

    @Test
    fun decode_migratesNumericThresholdHeadlightTriggerToStateEquals() {
        val decoded = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":false,
                "triggers":[{
                  "type":"numeric_threshold",
                  "id":"1",
                  "signal":"headlight_mode",
                  "source":"head_unit",
                  "direction":"below",
                  "threshold":4,
                  "holdMillis":0,
                  "startupBehavior":"initialize_only"
                }],
                "conditions":[],
                "actions":[{"type":"delay","durationMillis":0}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        ).getOrThrow()

        val trigger = decoded.automations.single().triggers.single() as AutomationTrigger.StateEquals
        assertEquals("OFF", trigger.expectedState)
    }

    @Test
    fun decode_migratesStateEqualsTriggerWithNumericHeadlightRaw() {
        val decoded = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":false,
                "triggers":[{
                  "type":"state_equals",
                  "id":"1",
                  "signal":"headlight_mode",
                  "source":"head_unit",
                  "expectedState":"4",
                  "holdMillis":0,
                  "startupBehavior":"initialize_only"
                }],
                "conditions":[],
                "actions":[{"type":"delay","durationMillis":0}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        ).getOrThrow()

        val trigger = decoded.automations.single().triggers.single() as AutomationTrigger.StateEquals
        assertEquals("OFF", trigger.expectedState)
    }

    @Test
    fun roundTrip_preservesGeofenceAndUiStateConditions() {
        val definition = AutomationDefinition(
            id = "a",
            name = "Geo UI",
            enabled = true,
            triggers = listOf(
                AutomationTrigger.SystemEvent(id = "1", event = AutomationSystemEvent.MENU_OPENED),
            ),
            conditions = listOf(
                AutomationCondition.Geofence(
                    queryText = "55.75, 37.62",
                    latitude = 55.75,
                    longitude = 37.62,
                    presence = AutomationGeofencePresence.INSIDE,
                    zoneRadiusMeters = 100.0,
                ),
                AutomationCondition.UiState(state = AutomationUiState.MAIN_SCREEN_OPEN),
            ),
            actions = listOf(AutomationAction.Delay(0L)),
        )
        val decoded = AutomationCodec.decode(
            AutomationCodec.encode(AutomationDocument(automations = listOf(definition))),
        ).getOrThrow()
        assertEquals(definition, decoded.automations.single())
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }
}
