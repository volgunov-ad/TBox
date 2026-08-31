package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSunTimesTest {
    private val moscow = AutomationCalendarDate(2024, 6, 21)
    private val moscowDec = AutomationCalendarDate(2024, 12, 21)
    private val lat = 55.7558
    private val lon = 37.6173
    private val msk = 3 * 60

    @Test
    fun moscowJune_hasMorningSunriseAndLateSunset() {
        val sunrise = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNRISE, moscow, lat, lon, msk,
        )
        val sunset = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNSET, moscow, lat, lon, msk,
        )
        assertNotNull(sunrise)
        assertNotNull(sunset)
        assertTrue(sunrise!! in minutes(3, 30)..minutes(4, 10))
        assertTrue(sunset!! in minutes(20, 50)..minutes(21, 40))
    }

    @Test
    fun moscowDecember_hasLateSunriseAndEarlySunset() {
        val sunrise = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNRISE, moscowDec, lat, lon, msk,
        )
        val sunset = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNSET, moscowDec, lat, lon, msk,
        )
        assertNotNull(sunrise)
        assertNotNull(sunset)
        assertTrue(sunrise!! in minutes(8, 30)..minutes(9, 20))
        assertTrue(sunset!! in minutes(15, 30)..minutes(16, 20))
    }

    @Test
    fun tromsoJune_hasNoSunset() {
        val sunset = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNSET,
            moscow,
            latitude = 69.65,
            longitude = 18.96,
            utcOffsetMinutes = 2 * 60,
        )
        assertNull(sunset)
    }

    @Test
    fun offsetAfterSunset_canLandNextDay() {
        val instant = AutomationSolarInstant(
            event = AutomationSolarEvent.SUNSET,
            offsetMinutes = 180,
            offsetDirection = AutomationSolarOffsetDirection.AFTER,
        )
        val occurrence = AutomationSunTimes.occurrence(instant, moscowDec, lat, lon, msk)
        assertNotNull(occurrence)
        val sunset = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNSET, moscowDec, lat, lon, msk,
        )!!
        if (sunset + 180 >= 24 * 60) {
            assertEquals(moscowDec.plusDays(1), occurrence!!.date)
            assertEquals(sunset + 180 - 24 * 60, occurrence.minutesOfDay)
        }
    }

    @Test
    fun catchUp_sameDayAfterSunsetOffset() {
        val sunset = AutomationSunTimes.eventMinutesOfDay(
            AutomationSolarEvent.SUNSET, moscowDec, lat, lon, msk,
        )!!
        val trigger = AutomationTrigger.Solar(
            id = "dusk",
            event = AutomationSolarEvent.SUNSET,
            offsetMinutes = 120,
            offsetDirection = AutomationSolarOffsetDirection.AFTER,
            startupBehavior = AutomationStartupBehavior.FIRE_IF_MATCHING,
        )
        val scheduled = sunset + 120
        assertTrue(scheduled < 24 * 60)
        val wall = AutomationWallTime(
            year = moscowDec.year,
            month = moscowDec.month,
            dayOfMonth = moscowDec.day,
            hour = (scheduled + 60) / 60,
            minute = (scheduled + 60) % 60,
            weekday = AutomationWeekday.SATURDAY,
            utcOffsetMinutes = msk,
        )
        assertTrue(AutomationSolarLogic.triggerCatchUp(trigger, lat, lon, wall))
        assertFalse(
            AutomationSolarLogic.triggerCatchUp(
                trigger.copy(startupBehavior = AutomationStartupBehavior.INITIALIZE_ONLY),
                lat,
                lon,
                wall,
            ),
        )
    }

    private fun minutes(hour: Int, minute: Int): Int = hour * 60 + minute
}
