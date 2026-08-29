package vad.dashing.tbox.automation

import java.util.Calendar
import java.util.Locale

enum class AutomationWeekday(
    val storageKey: String,
    val calendarDay: Int,
) {
    MONDAY("mon", Calendar.MONDAY),
    TUESDAY("tue", Calendar.TUESDAY),
    WEDNESDAY("wed", Calendar.WEDNESDAY),
    THURSDAY("thu", Calendar.THURSDAY),
    FRIDAY("fri", Calendar.FRIDAY),
    SATURDAY("sat", Calendar.SATURDAY),
    SUNDAY("sun", Calendar.SUNDAY);

    companion object {
        fun fromStorageKey(raw: String?): AutomationWeekday? =
            entries.firstOrNull { it.storageKey == raw?.trim()?.lowercase() }

        fun fromCalendarDay(day: Int): AutomationWeekday? =
            entries.firstOrNull { it.calendarDay == day }
    }
}

data class AutomationTimeOfDay(
    val hour: Int,
    val minute: Int,
) {
    val minutesOfDay: Int get() = hour * 60 + minute

    fun isValid(): Boolean = hour in 0..23 && minute in 0..59

    fun toStorageKey(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    companion object {
        val DEFAULT = AutomationTimeOfDay(7, 30)

        fun fromStorageKey(raw: String?): AutomationTimeOfDay? {
            val match = TIME_PATTERN.matchEntire(raw?.trim().orEmpty()) ?: return null
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            return AutomationTimeOfDay(hour, minute).takeIf { it.isValid() }
        }

        private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})(?::\d{2})?$""")
    }
}

data class AutomationWallTime(
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val hour: Int,
    val minute: Int,
    val weekday: AutomationWeekday,
) {
    val minutesOfDay: Int get() = hour * 60 + minute

    val minuteKey: String
        get() = String.format(
            Locale.US,
            "%04d-%02d-%02dT%02d:%02d",
            year,
            month,
            dayOfMonth,
            hour,
            minute,
        )
}

fun interface AutomationClock {
    fun wallTime(): AutomationWallTime

    companion object {
        val System: AutomationClock = AutomationClock {
            val calendar = Calendar.getInstance()
            val weekday = requireNotNull(
                AutomationWeekday.fromCalendarDay(calendar.get(Calendar.DAY_OF_WEEK)),
            )
            AutomationWallTime(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
                hour = calendar.get(Calendar.HOUR_OF_DAY),
                minute = calendar.get(Calendar.MINUTE),
                weekday = weekday,
            )
        }
    }
}

object AutomationTimeLogic {
    fun triggerMatches(
        at: AutomationTimeOfDay,
        weekdays: Set<AutomationWeekday>,
        wall: AutomationWallTime,
    ): Boolean {
        if (!at.isValid()) return false
        if (weekdays.isNotEmpty() && wall.weekday !in weekdays) return false
        return wall.hour == at.hour && wall.minute == at.minute
    }

    fun conditionMatches(
        after: AutomationTimeOfDay?,
        before: AutomationTimeOfDay?,
        weekdays: Set<AutomationWeekday>,
        wall: AutomationWallTime,
    ): Boolean {
        if (weekdays.isNotEmpty() && wall.weekday !in weekdays) return false
        val afterMinutes = after?.takeIf { it.isValid() }?.minutesOfDay
        val beforeMinutes = before?.takeIf { it.isValid() }?.minutesOfDay
        if (afterMinutes == null && beforeMinutes == null) return true
        val now = wall.minutesOfDay
        return when {
            afterMinutes != null && beforeMinutes == null -> now >= afterMinutes
            afterMinutes == null && beforeMinutes != null -> now < beforeMinutes
            afterMinutes == beforeMinutes -> now == afterMinutes
            afterMinutes!! < beforeMinutes!! -> now >= afterMinutes && now < beforeMinutes
            else -> now >= afterMinutes || now < beforeMinutes
        }
    }
}

fun automationWeekdayShortLabel(day: AutomationWeekday): String = when (day) {
    AutomationWeekday.MONDAY -> "Пн"
    AutomationWeekday.TUESDAY -> "Вт"
    AutomationWeekday.WEDNESDAY -> "Ср"
    AutomationWeekday.THURSDAY -> "Чт"
    AutomationWeekday.FRIDAY -> "Пт"
    AutomationWeekday.SATURDAY -> "Сб"
    AutomationWeekday.SUNDAY -> "Вс"
}
