package vad.dashing.tbox.automation

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class AutomationCalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
) {
    fun plusDays(delta: Int): AutomationCalendarDate {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month - 1, day)
        calendar.add(Calendar.DAY_OF_MONTH, delta)
        return AutomationCalendarDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )
    }
}

data class AutomationSolarOccurrence(
    val date: AutomationCalendarDate,
    val minutesOfDay: Int,
)

fun AutomationWallTime.calendarDate(): AutomationCalendarDate =
    AutomationCalendarDate(year, month, dayOfMonth)

fun AutomationWallTime.sameDate(date: AutomationCalendarDate): Boolean =
    year == date.year && month == date.month && dayOfMonth == date.day

/**
 * Official sunrise / sunset (sun disk, zenith 90.833°) in local civil minutes.
 * Null when the sun does not rise or set that day.
 */
object AutomationSunTimes {
    private const val ZENITH_DEG = 90.833

    fun eventMinutesOfDay(
        event: AutomationSolarEvent,
        date: AutomationCalendarDate,
        latitude: Double,
        longitude: Double,
        utcOffsetMinutes: Int,
    ): Int? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return null
        val hours = localEventHours(
            rise = event == AutomationSolarEvent.SUNRISE,
            date = date,
            latitude = latitude,
            longitude = longitude,
            utcOffsetHours = utcOffsetMinutes / 60.0,
        ) ?: return null
        val minutes = Math.round(hours * 60.0).toInt().mod(24 * 60)
        return minutes
    }

    fun occurrence(
        instant: AutomationSolarInstant,
        date: AutomationCalendarDate,
        latitude: Double,
        longitude: Double,
        utcOffsetMinutes: Int,
    ): AutomationSolarOccurrence? {
        if (!instant.isValid()) return null
        val base = eventMinutesOfDay(
            instant.event,
            date,
            latitude,
            longitude,
            utcOffsetMinutes,
        ) ?: return null
        var minutes = base + instant.signedOffsetMinutes()
        var resolved = date
        while (minutes >= 24 * 60) {
            minutes -= 24 * 60
            resolved = resolved.plusDays(1)
        }
        while (minutes < 0) {
            minutes += 24 * 60
            resolved = resolved.plusDays(-1)
        }
        return AutomationSolarOccurrence(resolved, minutes)
    }

    private fun localEventHours(
        rise: Boolean,
        date: AutomationCalendarDate,
        latitude: Double,
        longitude: Double,
        utcOffsetHours: Double,
    ): Double? {
        val n = dayOfYear(date)
        val lngHour = longitude / 15.0
        val t = n + ((if (rise) 6.0 else 18.0) - lngHour) / 24.0
        val m = (0.9856 * t) - 3.289
        var L = m + (1.916 * sinDeg(m)) + (0.020 * sinDeg(2.0 * m)) + 282.634
        L = normalize360(L)
        var rightAscension = atanDeg(0.91764 * tanDeg(L))
        rightAscension = normalize360(rightAscension)
        val lQuadrant = floor(L / 90.0) * 90.0
        val raQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + (lQuadrant - raQuadrant)) / 15.0
        val sinDec = 0.39782 * sinDeg(L)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosDeg(ZENITH_DEG) - sinDec * sinDeg(latitude)) /
            (cosDec * cosDeg(latitude))
        if (cosH > 1.0 || cosH < -1.0) return null
        var h = if (rise) {
            360.0 - acosDeg(cosH)
        } else {
            acosDeg(cosH)
        }
        h /= 15.0
        val tLocal = h + rightAscension - (0.06571 * t) - 6.622
        val utc = normalize24(tLocal - lngHour)
        return normalize24(utc + utcOffsetHours)
    }

    private fun dayOfYear(date: AutomationCalendarDate): Int {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(date.year, date.month - 1, date.day)
        return calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun sinDeg(deg: Double): Double = sin(deg * PI / 180.0)
    private fun cosDeg(deg: Double): Double = cos(deg * PI / 180.0)
    private fun tanDeg(deg: Double): Double = tan(deg * PI / 180.0)
    private fun atanDeg(x: Double): Double = atan(x) * 180.0 / PI
    private fun acosDeg(x: Double): Double = acos(x) * 180.0 / PI

    private fun normalize360(value: Double): Double {
        var result = value % 360.0
        if (result < 0.0) result += 360.0
        return result
    }

    private fun normalize24(value: Double): Double {
        var result = value % 24.0
        if (result < 0.0) result += 24.0
        return result
    }
}

object AutomationSolarLogic {
    fun triggerExact(
        trigger: AutomationTrigger.Solar,
        latitude: Double,
        longitude: Double,
        wall: AutomationWallTime,
    ): Boolean {
        if (!weekdayOk(trigger.weekdays, wall)) return false
        val instant = trigger.instant()
        val today = occurrenceOnWallDate(instant, wall.calendarDate(), latitude, longitude, wall)
        val yesterday = occurrenceOnWallDate(
            instant,
            wall.calendarDate().plusDays(-1),
            latitude,
            longitude,
            wall,
        )
        return matchesMinute(today, wall) || matchesMinute(yesterday, wall)
    }

    fun triggerCatchUp(
        trigger: AutomationTrigger.Solar,
        latitude: Double,
        longitude: Double,
        wall: AutomationWallTime,
    ): Boolean {
        if (trigger.startupBehavior != AutomationStartupBehavior.FIRE_IF_MATCHING) return false
        if (!weekdayOk(trigger.weekdays, wall)) return false
        val occurrence = AutomationSunTimes.occurrence(
            trigger.instant(),
            wall.calendarDate(),
            latitude,
            longitude,
            wall.utcOffsetMinutes,
        ) ?: return false
        return wall.sameDate(occurrence.date) && wall.minutesOfDay >= occurrence.minutesOfDay
    }

    fun conditionMatches(
        after: AutomationSolarInstant?,
        before: AutomationSolarInstant?,
        weekdays: Set<AutomationWeekday>,
        latitude: Double?,
        longitude: Double?,
        wall: AutomationWallTime,
    ): Boolean {
        if (!weekdayOk(weekdays, wall)) return false
        val lat = latitude ?: return after == null && before == null
        val lon = longitude ?: return after == null && before == null
        val afterMinutes = after?.let {
            clockMinutesOnWallDate(it, lat, lon, wall) ?: return false
        }
        val beforeMinutes = before?.let {
            clockMinutesOnWallDate(it, lat, lon, wall) ?: return false
        }
        return AutomationTimeLogic.conditionMatches(
            after = afterMinutes?.let { AutomationTimeOfDay(it / 60, it % 60) },
            before = beforeMinutes?.let { AutomationTimeOfDay(it / 60, it % 60) },
            weekdays = emptySet(),
            wall = wall,
        )
    }

    fun clockMinutesOnWallDate(
        instant: AutomationSolarInstant,
        latitude: Double,
        longitude: Double,
        wall: AutomationWallTime,
    ): Int? {
        val today = occurrenceOnWallDate(
            instant,
            wall.calendarDate(),
            latitude,
            longitude,
            wall,
        )
        if (today != null) return today.minutesOfDay
        return occurrenceOnWallDate(
            instant,
            wall.calendarDate().plusDays(-1),
            latitude,
            longitude,
            wall,
        )?.minutesOfDay
    }

    private fun occurrenceOnWallDate(
        instant: AutomationSolarInstant,
        sourceDate: AutomationCalendarDate,
        latitude: Double,
        longitude: Double,
        wall: AutomationWallTime,
    ): AutomationSolarOccurrence? {
        val occurrence = AutomationSunTimes.occurrence(
            instant,
            sourceDate,
            latitude,
            longitude,
            wall.utcOffsetMinutes,
        ) ?: return null
        return occurrence.takeIf { wall.sameDate(it.date) }
    }

    private fun matchesMinute(
        occurrence: AutomationSolarOccurrence?,
        wall: AutomationWallTime,
    ): Boolean = occurrence != null && wall.minutesOfDay == occurrence.minutesOfDay

    private fun weekdayOk(weekdays: Set<AutomationWeekday>, wall: AutomationWallTime): Boolean =
        weekdays.isEmpty() || wall.weekday in weekdays
}

fun AutomationTrigger.Solar.instant(): AutomationSolarInstant =
    AutomationSolarInstant(event, offsetMinutes, offsetDirection)
