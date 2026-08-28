package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEvaluatorTest {
    @Test
    fun initializeOnly_doesNotFireUntilValueRearmsAndCrossesAgain() {
        val trigger = rpmTrigger(
            reset = 900.0,
            holdMillis = 2_000L,
            startup = AutomationStartupBehavior.INITIALIZE_ONLY,
        )
        val evaluator = evaluator(trigger, allowStartupFire = true)

        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 0L)))
        assertNull(evaluator.onTick(5_000L))
        assertNull(evaluator.onSignalSample(rpmSample(899.0, 6_000L)))
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 7_000L)))
        assertNull(evaluator.onTick(8_999L))

        val fire = evaluator.onTick(9_000L)
        assertEquals("rpm", fire?.triggerId)
    }

    @Test
    fun fireIfMatching_respectsConfigurableHoldDuration() {
        val trigger = rpmTrigger(
            reset = 900.0,
            holdMillis = 2_500L,
            startup = AutomationStartupBehavior.FIRE_IF_MATCHING,
        )
        val evaluator = evaluator(trigger, allowStartupFire = true)

        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 10_000L)))
        assertNull(evaluator.onTick(12_499L))
        assertEquals("rpm", evaluator.onTick(12_500L)?.triggerId)
        assertNull(evaluator.onTick(20_000L))
    }

    @Test
    fun editingOrEnabling_alwaysInitializesEvenWhenStartupOptionRequestsFire() {
        val trigger = rpmTrigger(
            reset = 900.0,
            holdMillis = 0L,
            startup = AutomationStartupBehavior.FIRE_IF_MATCHING,
        )
        val evaluator = evaluator(trigger, allowStartupFire = false)

        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 0L)))
        assertNull(evaluator.onTick(1_000L))
    }

    @Test
    fun multipleSystemTriggers_returnFirstTriggerInDefinitionOrder() {
        val first = AutomationTrigger.SystemEvent(
            id = "first",
            event = AutomationSystemEvent.MENU_OPENED,
        )
        val second = AutomationTrigger.SystemEvent(
            id = "second",
            event = AutomationSystemEvent.MENU_OPENED,
        )
        val evaluator = evaluator(first, second)

        assertEquals(
            "first",
            evaluator.onSystemEvent(AutomationSystemEvent.MENU_OPENED)?.triggerId,
        )
    }

    @Test
    fun serviceStartedTriggerClaimsStartupBeforeMatchingNumericTrigger() {
        val service = AutomationTrigger.SystemEvent(
            id = "service",
            event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
        )
        val rpm = rpmTrigger(
            reset = 900.0,
            holdMillis = 0L,
            startup = AutomationStartupBehavior.FIRE_IF_MATCHING,
        )
        val evaluator = evaluator(service, rpm, allowStartupFire = true)

        assertEquals(
            "service",
            evaluator.onSystemEvent(AutomationSystemEvent.BACKGROUND_SERVICE_STARTED)?.triggerId,
        )
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 0L)))
    }

    @Test
    fun unavailableSample_rebaselinesWithoutFiringMatchingValue() {
        val trigger = rpmTrigger(reset = 900.0, holdMillis = 0L)
        val evaluator = evaluator(trigger, allowStartupFire = true)

        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 0L)))
        assertNull(
            evaluator.onSignalSample(
                rpmSampleValue(AutomationSignalValue.Unavailable, 1_000L),
            ),
        )
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 2_000L)))
        assertNull(evaluator.onSignalSample(rpmSample(800.0, 3_000L)))
        assertEquals("rpm", evaluator.onSignalSample(rpmSample(1_100.0, 4_000L))?.triggerId)
    }

    @Test
    fun unavailableDuringHold_restartsHoldInsteadOfDisarming() {
        val trigger = rpmTrigger(reset = 900.0, holdMillis = 2_000L)
        val evaluator = evaluator(trigger, allowStartupFire = false)

        assertNull(evaluator.onSignalSample(rpmSample(800.0, 0L)))
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 100L)))
        assertNull(
            evaluator.onSignalSample(
                rpmSampleValue(AutomationSignalValue.Unavailable, 200L),
            ),
        )
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 300L)))
        assertNull(evaluator.onTick(2_299L))
        assertEquals("rpm", evaluator.onTick(2_300L)?.triggerId)
    }

    @Test
    fun sampleAfterHoldElapsed_firesWithoutWaitingForTick() {
        val trigger = rpmTrigger(reset = 900.0, holdMillis = 2_000L)
        val evaluator = evaluator(trigger, allowStartupFire = false)

        assertNull(evaluator.onSignalSample(rpmSample(800.0, 0L)))
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 100L)))
        assertEquals("rpm", evaluator.onSignalSample(rpmSample(1_200.0, 2_100L))?.triggerId)
    }

    @Test
    fun jumpBelowReset_rearmsWithoutVisitingExactResetValue() {
        val trigger = rpmTrigger(reset = 1_000.0, holdMillis = 0L)
        val evaluator = evaluator(trigger, allowStartupFire = false)

        assertNull(evaluator.onSignalSample(rpmSample(800.0, 0L)))
        assertEquals("rpm", evaluator.onSignalSample(rpmSample(1_500.0, 100L))?.triggerId)
        assertNull(evaluator.onSignalSample(rpmSample(900.0, 200L)))
        assertEquals("rpm", evaluator.onSignalSample(rpmSample(1_500.0, 300L))?.triggerId)
    }

    @Test
    fun firstReadyTriggerWinsWhenHoldsExpireTogether() {
        val slower = rpmTrigger(id = "slower", reset = 900.0, holdMillis = 2_000L)
        val faster = rpmTrigger(id = "faster", reset = 900.0, holdMillis = 1_000L)
        val evaluator = evaluator(slower, faster)

        assertNull(evaluator.onSignalSample(rpmSample(800.0, 0L)))
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 100L)))
        assertEquals("faster", evaluator.onTick(1_100L)?.triggerId)
    }

    @Test
    fun simultaneousReadyTriggers_useDefinitionOrder() {
        val first = rpmTrigger(id = "first", reset = 900.0, holdMillis = 1_000L)
        val second = rpmTrigger(id = "second", reset = 900.0, holdMillis = 1_000L)
        val evaluator = evaluator(first, second)

        assertNull(evaluator.onSignalSample(rpmSample(800.0, 0L)))
        assertNull(evaluator.onSignalSample(rpmSample(1_100.0, 100L)))
        assertEquals("first", evaluator.onTick(1_100L)?.triggerId)
    }

    @Test
    fun belowThreshold_respectsResetOnTheHighSide() {
        val trigger = AutomationTrigger.NumericThreshold(
            id = "speed",
            signal = AutomationSignalId.CAR_SPEED,
            source = AutomationSignalSource.TBOX,
            direction = AutomationThresholdDirection.BELOW,
            threshold = 5.0,
            resetThreshold = 10.0,
            holdMillis = 0L,
            startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
        )
        val evaluator = evaluator(trigger)
        val key = AutomationSignalKey(AutomationSignalId.CAR_SPEED, AutomationSignalSource.TBOX)

        assertNull(
            evaluator.onSignalSample(
                AutomationSignalSample(key, AutomationSignalValue.Number(3.0), 0L),
            ),
        )
        assertNull(
            evaluator.onSignalSample(
                AutomationSignalSample(key, AutomationSignalValue.Number(12.0), 1_000L),
            ),
        )
        assertEquals(
            "speed",
            evaluator.onSignalSample(
                AutomationSignalSample(key, AutomationSignalValue.Number(4.0), 2_000L),
            )?.triggerId,
        )
    }

    @Test
    fun unavailableNumeric_makesConditionsFailUntilValueReturns() {
        val trigger = rpmTrigger(reset = 900.0, holdMillis = 0L)
        val definition = definition(trigger).copy(
            conditions = listOf(
                AutomationCondition.Numeric(
                    AutomationSignalId.CAR_SPEED,
                    AutomationSignalSource.TBOX,
                    AutomationComparison.BELOW,
                    5.0,
                ),
            ),
        )
        val evaluator = AutomationEvaluator(definition, allowStartupFire = false)
        val speedKey = AutomationSignalKey(
            AutomationSignalId.CAR_SPEED,
            AutomationSignalSource.TBOX,
        )
        evaluator.onSignalSample(
            AutomationSignalSample(speedKey, AutomationSignalValue.Number(0.0), 0L),
        )
        val context = AutomationTriggerContext(
            automationId = definition.id,
            triggerId = "rpm",
            firedAtEpochMillis = 0L,
        )
        assertTrue(evaluator.conditionsPass(context))
        evaluator.onSignalSample(
            AutomationSignalSample(speedKey, AutomationSignalValue.Unavailable, 1L),
        )
        assertFalse(evaluator.conditionsPass(context))
    }

    @Test
    fun conditions_supportNumericStateBooleanGroupsAndTriggerId() {
        val trigger = rpmTrigger(reset = 900.0, holdMillis = 0L)
        val definition = definition(trigger).copy(
            conditions = listOf(
                AutomationCondition.All(
                    listOf(
                        AutomationCondition.Numeric(
                            AutomationSignalId.CAR_SPEED,
                            AutomationSignalSource.TBOX,
                            AutomationComparison.BELOW,
                            5.0,
                        ),
                        AutomationCondition.TriggeredBy(setOf("rpm")),
                        AutomationCondition.Not(
                            AutomationCondition.State(
                                AutomationSignalId.GEAR_MODE,
                                AutomationSignalSource.TBOX,
                                "R",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val evaluator = AutomationEvaluator(definition, allowStartupFire = false)
        evaluator.onSignalSample(
            AutomationSignalSample(
                AutomationSignalKey(AutomationSignalId.CAR_SPEED, AutomationSignalSource.TBOX),
                AutomationSignalValue.Number(0.0),
                0L,
            ),
        )
        evaluator.onSignalSample(
            AutomationSignalSample(
                AutomationSignalKey(AutomationSignalId.GEAR_MODE, AutomationSignalSource.TBOX),
                AutomationSignalValue.State("P"),
                0L,
            ),
        )
        val context = AutomationTriggerContext(
            automationId = definition.id,
            triggerId = "rpm",
            firedAtEpochMillis = 0L,
        )

        assertTrue(evaluator.conditionsPass(context))
        assertFalse(evaluator.conditionsPass(context.copy(triggerId = "other")))
    }

    @Test
    fun isReadyToRun_requiresTriggerMatchAndAllConditions() {
        val trigger = AutomationTrigger.NumericThreshold(
            id = "temp",
            signal = AutomationSignalId.ENGINE_TEMPERATURE,
            source = AutomationSignalSource.TBOX,
            direction = AutomationThresholdDirection.BELOW,
            threshold = 10.0,
            resetThreshold = 10.0,
        )
        val definition = definition(trigger).copy(
            conditions = listOf(
                AutomationCondition.Numeric(
                    AutomationSignalId.ENGINE_RPM,
                    AutomationSignalSource.TBOX,
                    AutomationComparison.BELOW,
                    1_000.0,
                ),
            ),
        )
        val evaluator = AutomationEvaluator(definition, allowStartupFire = false)
        val context = AutomationTriggerContext(
            automationId = definition.id,
            triggerId = "temp",
            firedAtEpochMillis = 0L,
        )
        fun sample(signal: AutomationSignalId, value: Double, elapsed: Long) {
            evaluator.onSignalSample(
                AutomationSignalSample(
                    AutomationSignalKey(signal, AutomationSignalSource.TBOX),
                    AutomationSignalValue.Number(value),
                    elapsed,
                ),
            )
        }

        sample(AutomationSignalId.ENGINE_TEMPERATURE, 5.0, 0L)
        sample(AutomationSignalId.ENGINE_RPM, 1_500.0, 0L)
        assertTrue(evaluator.triggerStillMatching("temp"))
        assertFalse(evaluator.isReadyToRun(context))

        sample(AutomationSignalId.ENGINE_RPM, 900.0, 1L)
        assertTrue(evaluator.isReadyToRun(context))

        sample(AutomationSignalId.ENGINE_TEMPERATURE, 12.0, 2L)
        assertFalse(evaluator.triggerStillMatching("temp"))
        assertFalse(evaluator.isReadyToRun(context))
    }

    @Test
    fun systemEventTrigger_staysMatchingWhileWaitingOnConditions() {
        val trigger = AutomationTrigger.SystemEvent(
            id = "menu",
            event = AutomationSystemEvent.MENU_OPENED,
        )
        val definition = definition(trigger).copy(
            conditions = listOf(
                AutomationCondition.Numeric(
                    AutomationSignalId.ENGINE_RPM,
                    AutomationSignalSource.TBOX,
                    AutomationComparison.BELOW,
                    1_000.0,
                ),
            ),
        )
        val evaluator = AutomationEvaluator(definition, allowStartupFire = false)
        val context = AutomationTriggerContext(
            automationId = definition.id,
            triggerId = "menu",
            firedAtEpochMillis = 0L,
        )
        assertTrue(evaluator.triggerStillMatching("menu"))
        assertFalse(evaluator.isReadyToRun(context))
        evaluator.onSignalSample(
            AutomationSignalSample(
                AutomationSignalKey(AutomationSignalId.ENGINE_RPM, AutomationSignalSource.TBOX),
                AutomationSignalValue.Number(900.0),
                0L,
            ),
        )
        assertTrue(evaluator.isReadyToRun(context))
    }

    private fun evaluator(
        vararg triggers: AutomationTrigger,
        allowStartupFire: Boolean = true,
    ): AutomationEvaluator =
        AutomationEvaluator(definition(*triggers), allowStartupFire)

    private fun definition(vararg triggers: AutomationTrigger): AutomationDefinition =
        AutomationDefinition(
            id = "automation",
            name = "Test",
            enabled = true,
            triggers = triggers.toList(),
            actions = listOf(AutomationAction.Delay(0L)),
        )

    private fun rpmTrigger(
        reset: Double,
        holdMillis: Long,
        startup: AutomationStartupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
        id: String = "rpm",
    ): AutomationTrigger.NumericThreshold =
        AutomationTrigger.NumericThreshold(
            id = id,
            signal = AutomationSignalId.ENGINE_RPM,
            source = AutomationSignalSource.TBOX,
            direction = AutomationThresholdDirection.ABOVE,
            threshold = 1_000.0,
            resetThreshold = reset,
            holdMillis = holdMillis,
            startupBehavior = startup,
        )

    private fun rpmSample(value: Double, elapsedMillis: Long): AutomationSignalSample =
        rpmSampleValue(AutomationSignalValue.Number(value), elapsedMillis)

    private fun rpmSampleValue(
        value: AutomationSignalValue,
        elapsedMillis: Long,
    ): AutomationSignalSample =
        AutomationSignalSample(
            key = AutomationSignalKey(
                AutomationSignalId.ENGINE_RPM,
                AutomationSignalSource.TBOX,
            ),
            value = value,
            observedAtElapsedMillis = elapsedMillis,
        )
}
