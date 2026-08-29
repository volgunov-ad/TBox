package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationTimeLogicTest {
    @Test
    fun timeOfDay_parsesHourMinuteAndOptionalSeconds() {
        assertEquals(AutomationTimeOfDay(7, 30), AutomationTimeOfDay.fromStorageKey("07:30"))
        assertEquals(AutomationTimeOfDay(7, 30), AutomationTimeOfDay.fromStorageKey("7:30"))
        assertEquals(AutomationTimeOfDay(7, 30), AutomationTimeOfDay.fromStorageKey("07:30:00"))
        assertEquals("07:30", AutomationTimeOfDay(7, 30).toStorageKey())
        assertNull(AutomationTimeOfDay.fromStorageKey("24:00"))
        assertNull(AutomationTimeOfDay.fromStorageKey("07:60"))
        assertNull(AutomationTimeOfDay.fromStorageKey("morning"))
    }

    @Test
    fun trigger_matchesExactMinuteAndOptionalWeekdays() {
        val at = AutomationTimeOfDay(7, 30)
        assertTrue(AutomationTimeLogic.triggerMatches(at, emptySet(), wall(7, 30)))
        assertFalse(AutomationTimeLogic.triggerMatches(at, emptySet(), wall(7, 31)))
        assertFalse(
            AutomationTimeLogic.triggerMatches(
                at,
                setOf(AutomationWeekday.MONDAY),
                wall(7, 30, AutomationWeekday.TUESDAY),
            ),
        )
        assertTrue(
            AutomationTimeLogic.triggerMatches(
                at,
                setOf(AutomationWeekday.MONDAY, AutomationWeekday.FRIDAY),
                wall(7, 30, AutomationWeekday.FRIDAY),
            ),
        )
    }

    @Test
    fun condition_afterOnly_isInclusive() {
        val after = AutomationTimeOfDay(8, 0)
        assertFalse(AutomationTimeLogic.conditionMatches(after, null, emptySet(), wall(7, 59)))
        assertTrue(AutomationTimeLogic.conditionMatches(after, null, emptySet(), wall(8, 0)))
        assertTrue(AutomationTimeLogic.conditionMatches(after, null, emptySet(), wall(23, 0)))
    }

    @Test
    fun condition_beforeOnly_isExclusive() {
        val before = AutomationTimeOfDay(18, 0)
        assertTrue(AutomationTimeLogic.conditionMatches(null, before, emptySet(), wall(17, 59)))
        assertFalse(AutomationTimeLogic.conditionMatches(null, before, emptySet(), wall(18, 0)))
    }

    @Test
    fun condition_sameAfterAndBefore_isThatMinuteOnly() {
        val noon = AutomationTimeOfDay(12, 0)
        assertTrue(AutomationTimeLogic.conditionMatches(noon, noon, emptySet(), wall(12, 0)))
        assertFalse(AutomationTimeLogic.conditionMatches(noon, noon, emptySet(), wall(12, 1)))
    }

    @Test
    fun condition_sameDayWindow() {
        val after = AutomationTimeOfDay(8, 0)
        val before = AutomationTimeOfDay(18, 0)
        assertFalse(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(7, 59)))
        assertTrue(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(8, 0)))
        assertTrue(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(17, 59)))
        assertFalse(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(18, 0)))
    }

    @Test
    fun condition_wrapsMidnight() {
        val after = AutomationTimeOfDay(22, 0)
        val before = AutomationTimeOfDay(6, 0)
        assertTrue(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(22, 0)))
        assertTrue(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(23, 30)))
        assertTrue(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(0, 0)))
        assertTrue(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(5, 59)))
        assertFalse(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(6, 0)))
        assertFalse(AutomationTimeLogic.conditionMatches(after, before, emptySet(), wall(12, 0)))
    }

    @Test
    fun condition_weekdaysOnly() {
        assertTrue(
            AutomationTimeLogic.conditionMatches(
                null,
                null,
                setOf(AutomationWeekday.SATURDAY, AutomationWeekday.SUNDAY),
                wall(15, 0, AutomationWeekday.SUNDAY),
            ),
        )
        assertFalse(
            AutomationTimeLogic.conditionMatches(
                null,
                null,
                setOf(AutomationWeekday.SATURDAY, AutomationWeekday.SUNDAY),
                wall(15, 0, AutomationWeekday.MONDAY),
            ),
        )
    }

    private fun wall(
        hour: Int,
        minute: Int,
        weekday: AutomationWeekday = AutomationWeekday.MONDAY,
    ) = AutomationWallTime(
        year = 2026,
        month = 8,
        dayOfMonth = 31,
        hour = hour,
        minute = minute,
        weekday = weekday,
    )
}
