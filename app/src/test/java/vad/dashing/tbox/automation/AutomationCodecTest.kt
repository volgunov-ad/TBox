package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.freeform.FreeformLaunchSide

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationCodecTest {
    @Test
    fun roundTrip_preservesTriggersConditionsAndNestedActions() {
        val canEntry = AutomationCanCatalog.entries.first()
        val triggers = listOf(
            AutomationTrigger.SystemEvent(
                id = "service-ready",
                event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
            ),
            AutomationTrigger.SystemEvent(
                id = "main-open",
                event = AutomationSystemEvent.MAIN_SCREEN_OPENED,
            ),
            AutomationTrigger.SystemEvent(
                id = "menu-open",
                event = AutomationSystemEvent.MENU_OPENED,
            ),
            AutomationTrigger.NumericThreshold(
                id = "rpm",
                signal = AutomationSignalId.ENGINE_RPM,
                source = AutomationSignalSource.HEAD_UNIT,
                direction = AutomationThresholdDirection.ABOVE,
                threshold = 1_000.0,
                resetThreshold = 900.0,
                holdMillis = 2_500L,
                startupBehavior = AutomationStartupBehavior.FIRE_IF_MATCHING,
            ),
            AutomationTrigger.Geofence(
                id = "home",
                queryText = "55.750000, 37.620000",
                latitude = 55.75,
                longitude = 37.62,
                direction = AutomationGeofenceDirection.ENTER,
                zoneRadiusMeters = 50.0,
                rearmRadiusMeters = 60.0,
                holdMillis = 3_000L,
                startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
            ),
        )
        val definition = AutomationDefinition(
            id = "automation-1",
            name = "Проверка",
            description = "Полная модель",
            enabled = true,
            triggers = triggers,
            conditions = listOf(
                AutomationCondition.TriggeredBy(setOf("rpm", "service-ready")),
            ),
            actions = listOf(
                AutomationAction.IfThenElse(
                    condition = AutomationCondition.Numeric(
                        signal = AutomationSignalId.CAR_SPEED,
                        source = AutomationSignalSource.TBOX,
                        comparison = AutomationComparison.AT_MOST,
                        expectedValue = 1.0,
                    ),
                    thenActions = listOf(
                        AutomationAction.CanCommand(
                            bus = canEntry.bus,
                            propertyId = canEntry.propertyId,
                            operation = canEntry.allowedOperations.first(),
                            value = canEntry.defaultValue,
                        ),
                        AutomationAction.Delay(2_000L),
                    ),
                    elseActions = listOf(
                        AutomationAction.OpenMainScreen(2),
                    ),
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SHOW_TOAST,
                    stringValue = "Toast текст",
                ),
                AutomationAction.Builtin(
                    type = AutomationBuiltinActionType.SHOW_ALERT,
                    stringValue = "Сообщение на экране",
                ),
                AutomationAction.LaunchApplication(
                    packageName = "example.navigation",
                    launchMode = AppLauncherLaunchMode.FREEFORM,
                    freeformSide = FreeformLaunchSide.RIGHT,
                    freeformPercent = 40,
                    freeformOverlayPage = 2,
                    freeformOverlayCrop = true,
                ),
            ),
            runMode = AutomationRunMode.QUEUED,
            maxRuns = 3,
            conditionWaitMillis = 15_000L,
        )
        val document = AutomationDocument(automations = listOf(definition))

        val decoded = AutomationCodec.decode(AutomationCodec.encode(document)).getOrThrow()

        assertEquals(document, decoded)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }

    @Test
    fun decode_rejectsMissingRequiredFields() {
        val result = AutomationCodec.decode(
            """{"formatVersion":1,"automations":[{"id":"a","enabled":true}]}""",
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun decode_missingConditionWaitDefaultsToZero() {
        val result = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":false,
                "triggers":[{"type":"system_event","id":"t","event":"menu_opened"}],
                "conditions":[],
                "actions":[{"type":"delay","durationMillis":0}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        )
        val decoded = result.getOrThrow()
        assertEquals(0L, decoded.automations.single().conditionWaitMillis)
        assertTrue(AutomationValidator.validate(decoded).isEmpty())
    }

    @Test
    fun decode_rejectsUnknownActionType() {
        val result = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":false,
                "triggers":[{"type":"system_event","id":"t","event":"menu_opened"}],
                "conditions":[],
                "actions":[{"type":"raw_can"}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun decode_rejectsNonBooleanEnabled() {
        val result = AutomationCodec.decode(
            """
            {
              "formatVersion":1,
              "automations":[{
                "id":"a",
                "name":"x",
                "description":"",
                "enabled":"yes",
                "triggers":[{"type":"system_event","id":"t","event":"menu_opened"}],
                "conditions":[],
                "actions":[{"type":"delay","durationMillis":0}],
                "runMode":"single",
                "maxRuns":1
              }]
            }
            """.trimIndent(),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun decode_rejectsUnsupportedVersion() {
        val result = AutomationCodec.decode("""{"formatVersion":99,"automations":[]}""")

        assertTrue(result.isFailure)
    }
}