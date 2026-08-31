package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEvaluatorTest {
    @Test
    fun needsPeriodicTick_falseUntilHoldStarts_trueForTimeTrigger() {
        val hold = evaluator(
            rpmTrigger(
                reset = 900.0,
                holdMillis = 2_000L,
                startup = AutomationStartupBehavior.FIRE_IF_MATCHING,
            ),
        )
        assertFalse(hold.needsPeriodicTick())
        assertNull(hold.onSignalSample(rpmSample(1_100.0, 0L)))
        assertTrue(hold.needsPeriodicTick())

        val timeOnly = evaluator(timeTrigger())
        assertTrue(timeOnly.needsPeriodicTick())
    }

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
    fun stateEquals_unavailableAfterBaselineRearmsAndFiresOnReturn() {
        val trigger = AutomationTrigger.StateEquals(
            id = "fg",
            signal = AutomationSignalId.FOREGROUND_APP,
            source = AutomationSignalSource.APP,
            expectedState = "com.mengbo.avm",
        )
        val evaluator = evaluator(trigger, allowStartupFire = false)
        val key = AutomationSignalKey(
            AutomationSignalId.FOREGROUND_APP,
            AutomationSignalSource.APP,
        )
        fun sample(value: AutomationSignalValue, at: Long) = AutomationSignalSample(
            key = key,
            value = value,
            observedAtElapsedMillis = at,
        )
        assertNull(
            evaluator.onSignalSample(
                sample(AutomationSignalValue.State("com.mengbo.avm"), 0L),
            ),
        )
        assertNull(
            evaluator.onSignalSample(sample(AutomationSignalValue.Unavailable, 1_000L)),
        )
        assertEquals(
            "fg",
            evaluator.onSignalSample(
                sample(AutomationSignalValue.State("com.mengbo.avm"), 2_000L),
            )?.triggerId,
        )
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
    fun withoutRearm_firesOnEachNewValueWhileMatching() {
        val trigger = AutomationTrigger.NumericThreshold(
            id = "temp",
            signal = AutomationSignalId.ENGINE_TEMPERATURE,
            source = AutomationSignalSource.TBOX,
            direction = AutomationThresholdDirection.BELOW,
            threshold = 10.0,
            rearmEnabled = false,
            holdMillis = 0L,
            startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY,
        )
        val evaluator = evaluator(trigger, allowStartupFire = false)
        val key = AutomationSignalKey(
            AutomationSignalId.ENGINE_TEMPERATURE,
            AutomationSignalSource.TBOX,
        )
        fun sample(value: Double, elapsed: Long) = evaluator.onSignalSample(
            AutomationSignalSample(key, AutomationSignalValue.Number(value), elapsed),
        )

        assertNull(sample(9.0, 0L))
        assertNull(sample(9.0, 100L))
        assertEquals("temp", sample(8.0, 200L)?.triggerId)
        assertNull(sample(8.0, 300L))
        assertEquals("temp", sample(7.0, 400L)?.triggerId)
    }

    @Test
    fun withoutRearm_unavailableThenSameValueDoesNotRefire() {
        val trigger = AutomationTrigger.NumericThreshold(
            id = "temp",
            signal = AutomationSignalId.ENGINE_TEMPERATURE,
            source = AutomationSignalSource.TBOX,
            direction = AutomationThresholdDirection.BELOW,
            threshold = 10.0,
            rearmEnabled = false,
            holdMillis = 0L,
        )
        val evaluator = evaluator(trigger, allowStartupFire = false)
        val key = AutomationSignalKey(
            AutomationSignalId.ENGINE_TEMPERATURE,
            AutomationSignalSource.TBOX,
        )
        fun sample(value: AutomationSignalValue, elapsed: Long) = evaluator.onSignalSample(
            AutomationSignalSample(key, value, elapsed),
        )

        assertNull(sample(AutomationSignalValue.Number(9.0), 0L))
        assertEquals("temp", sample(AutomationSignalValue.Number(8.0), 100L)?.triggerId)
        assertNull(sample(AutomationSignalValue.Unavailable, 200L))
        assertNull(sample(AutomationSignalValue.Number(8.0), 300L))
        assertEquals("temp", sample(AutomationSignalValue.Number(7.0), 400L)?.triggerId)
    }

    @Test
    fun withoutRearm_holdRestartsOnNewValue() {
        val trigger = AutomationTrigger.NumericThreshold(
            id = "temp",
            signal = AutomationSignalId.ENGINE_TEMPERATURE,
            source = AutomationSignalSource.TBOX,
            direction = AutomationThresholdDirection.BELOW,
            threshold = 10.0,
            rearmEnabled = false,
            holdMillis = 1_000L,
        )
        val evaluator = evaluator(trigger, allowStartupFire = false)
        val key = AutomationSignalKey(
            AutomationSignalId.ENGINE_TEMPERATURE,
            AutomationSignalSource.TBOX,
        )
        fun sample(value: Double, elapsed: Long) = evaluator.onSignalSample(
            AutomationSignalSample(key, AutomationSignalValue.Number(value), elapsed),
        )

        assertNull(sample(12.0, 0L))
        assertNull(sample(9.0, 100L))
        assertNull(evaluator.onTick(1_099L))
        assertNull(sample(8.0, 200L))
        assertNull(evaluator.onTick(1_199L))
        assertEquals("temp", evaluator.onTick(1_200L)?.triggerId)
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

    @Test
    fun timeTrigger_firesOnceWhenMinuteIsEntered() {
        val clock = MutableAutomationClock(wall(7, 29))
        val evaluator = AutomationEvaluator(
            definition(timeTrigger()),
            allowStartupFire = false,
            clock = clock,
        )
        assertNull(evaluator.onTick(0L))
        clock.time = wall(7, 30)
        assertEquals("morning", evaluator.onTick(250L)?.triggerId)
        assertNull(evaluator.onTick(500L))
        clock.time = wall(7, 31)
        assertNull(evaluator.onTick(750L))
        clock.time = wall(7, 30, dayOfMonth = 1, month = 9, weekday = AutomationWeekday.TUESDAY)
        assertEquals("morning", evaluator.onTick(1_000L)?.triggerId)
    }

    @Test
    fun timeTrigger_catchUpAfterRestartWhenAlreadyPast() {
        val clock = MutableAutomationClock(wall(10, 0))
        val evaluator = AutomationEvaluator(
            definition(
                timeTrigger().copy(startupBehavior = AutomationStartupBehavior.FIRE_IF_MATCHING),
            ),
            allowStartupFire = true,
            clock = clock,
        )
        assertEquals("morning", evaluator.onTick(0L)?.triggerId)
        assertNull(evaluator.onTick(250L))
    }

    @Test
    fun timeTrigger_initializeOnlyDoesNotCatchUpAfterRestart() {
        val clock = MutableAutomationClock(wall(10, 0))
        val evaluator = AutomationEvaluator(
            definition(timeTrigger()),
            allowStartupFire = true,
            clock = clock,
        )
        assertNull(evaluator.onTick(0L))
    }

    @Test
    fun solarTrigger_catchUpWhenPositionArrivesAfterSunsetOffset() {
        val date = AutomationCalendarDate(2024, 12, 21)
        val lat = 55.7558
        val lon = 37.6173
        val msk = 180
        val sunset = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNSET, date, lat, lon, msk,
        )!!
        val after = sunset + 120
        assertTrue(after < 24 * 60)
        val clock = MutableAutomationClock(
            AutomationWallTime(
                year = date.year,
                month = date.month,
                dayOfMonth = date.day,
                hour = (after + 60) / 60,
                minute = (after + 60) % 60,
                weekday = AutomationWeekday.SATURDAY,
                utcOffsetMinutes = msk,
            ),
        )
        val evaluator = AutomationEvaluator(
            definition(
                AutomationTrigger.Solar(
                    id = "dusk",
                    event = AutomationSolarEvent.SUNSET,
                    offsetMinutes = 120,
                    offsetDirection = AutomationSolarOffsetDirection.AFTER,
                    startupBehavior = AutomationStartupBehavior.FIRE_IF_MATCHING,
                ),
            ),
            allowStartupFire = true,
            clock = clock,
        )
        assertNull(evaluator.onTick(0L))
        assertEquals(
            "dusk",
            evaluator.onSignalSample(
                AutomationSignalSample(
                    key = AUTOMATION_GEO_DISPLAY_KEY,
                    value = AutomationSignalValue.Position(lat, lon),
                    observedAtElapsedMillis = 100L,
                ),
            )?.triggerId,
        )
    }

    @Test
    fun timeTrigger_skipsCurrentMinuteOnStart() {
        val clock = MutableAutomationClock(wall(7, 30))
        val evaluator = AutomationEvaluator(
            definition(timeTrigger()),
            allowStartupFire = false,
            clock = clock,
        )
        assertNull(evaluator.onTick(0L))
        clock.time = wall(7, 31)
        assertNull(evaluator.onTick(250L))
    }

    @Test
    fun timeTrigger_respectsWeekdays() {
        val clock = MutableAutomationClock(wall(7, 29, weekday = AutomationWeekday.SUNDAY))
        val evaluator = AutomationEvaluator(
            definition(
                timeTrigger(weekdays = setOf(AutomationWeekday.MONDAY, AutomationWeekday.FRIDAY)),
            ),
            allowStartupFire = false,
            clock = clock,
        )
        clock.time = wall(7, 30, weekday = AutomationWeekday.SUNDAY)
        assertNull(evaluator.onTick(0L))
        clock.time = wall(7, 30, weekday = AutomationWeekday.MONDAY, dayOfMonth = 1, month = 9)
        assertEquals("morning", evaluator.onTick(250L)?.triggerId)
    }

    @Test
    fun timeTrigger_staysMatchingWhileWaitingOnConditions() {
        val clock = MutableAutomationClock(wall(7, 29))
        val definition = definition(timeTrigger()).copy(
            conditions = listOf(
                AutomationCondition.Numeric(
                    AutomationSignalId.ENGINE_RPM,
                    AutomationSignalSource.TBOX,
                    AutomationComparison.BELOW,
                    1_000.0,
                ),
            ),
        )
        val evaluator = AutomationEvaluator(definition, allowStartupFire = false, clock = clock)
        val context = AutomationTriggerContext(
            automationId = definition.id,
            triggerId = "morning",
            firedAtEpochMillis = 0L,
        )
        assertTrue(evaluator.triggerStillMatching("morning"))
        assertFalse(evaluator.isReadyToRun(context))
        evaluator.onSignalSample(rpmSample(900.0, 0L))
        assertTrue(evaluator.isReadyToRun(context))
    }

    @Test
    fun timeCondition_usesInjectedWallClock() {
        val night = AutomationCondition.Time(
            after = AutomationTimeOfDay(22, 0),
            before = AutomationTimeOfDay(6, 0),
        )
        val context = AutomationTriggerContext(
            automationId = "automation",
            triggerId = "any",
            firedAtEpochMillis = 0L,
        )
        assertTrue(
            AutomationEvaluator.evaluateCondition(
                night,
                context,
                emptyMap(),
                wall(23, 15),
            ),
        )
        assertFalse(
            AutomationEvaluator.evaluateCondition(
                night,
                context,
                emptyMap(),
                wall(12, 0),
            ),
        )
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

    private fun timeTrigger(
        weekdays: Set<AutomationWeekday> = emptySet(),
    ) = AutomationTrigger.Time(
        id = "morning",
        at = AutomationTimeOfDay(7, 30),
        weekdays = weekdays,
    )

    private fun wall(
        hour: Int,
        minute: Int,
        weekday: AutomationWeekday = AutomationWeekday.MONDAY,
        year: Int = 2026,
        month: Int = 8,
        dayOfMonth: Int = 31,
    ) = AutomationWallTime(year, month, dayOfMonth, hour, minute, weekday)

    private class MutableAutomationClock(
        var time: AutomationWallTime,
    ) : AutomationClock {
        override fun wallTime(): AutomationWallTime = time
    }
}
