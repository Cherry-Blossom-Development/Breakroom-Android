package com.cherryblossomdev.breakroom.projects

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// Working-time helpers shared by the Burndown and GANTT charts. A port of the
// helpers at the top of web's frontend/src/utilities/ganttSchedule.js:
//   - 8 working hours per working day, Mon-Fri; weekends are skipped
//   - estimates are stored as entered (amount + unit, migration 083) and
//     converted here only: a day is one working day, a week five, a month
//     52/12 weeks (~21.7 working days)
//
// Instants are epoch millis. Calendar math (midnights, weekdays, adding
// days) happens in [zone], the device's time zone by default -- the same as
// the browser's local time on web. minSdk is 24, so no java.time.

const val HOURS_PER_DAY = 8.0
const val DAY_MS = 24L * 60 * 60 * 1000
private const val WORKING_DAYS_PER_WEEK = 5.0
private const val WEEKS_PER_MONTH = 52.0 / 12.0

// Scheduler working hours for one estimate unit
private val UNIT_HOURS = mapOf(
    "hours" to 1.0,
    "days" to HOURS_PER_DAY,
    "weeks" to WORKING_DAYS_PER_WEEK * HOURS_PER_DAY,
    "months" to WEEKS_PER_MONTH * WORKING_DAYS_PER_WEEK * HOURS_PER_DAY
)

/** A ticket's estimate in working hours, or null if it has none. */
fun estimateWorkingHours(amount: String?, unit: String?): Double? {
    val perUnit = UNIT_HOURS[unit] ?: return null
    val value = amount?.toDoubleOrNull() ?: return null
    return if (value > 0) value * perUnit else null
}

private val API_TIME_PATTERNS = listOf(
    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
    "yyyy-MM-dd'T'HH:mm:ssX",
    "yyyy-MM-dd HH:mm:ss"
)

/** Parses the backend's timestamps ("2026-09-29T14:03:11.000Z"); null if missing/unparseable. */
fun parseApiTime(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    for (pattern in API_TIME_PATTERNS) {
        try {
            val format = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            return format.parse(value)?.time
        } catch (e: ParseException) {
            // try the next pattern
        } catch (e: IllegalArgumentException) {
            // pattern letter not supported on this platform; try the next
        }
    }
    return null
}

class WorkCalendar(val zone: TimeZone = TimeZone.getDefault()) {

    private fun calendarAt(time: Long): Calendar =
        Calendar.getInstance(zone, Locale.US).apply { timeInMillis = time }

    fun startOfDay(time: Long): Long = calendarAt(time).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Calendar days, so DST changes keep midnights at midnight (like JS setDate). */
    fun addDays(time: Long, days: Int): Long = calendarAt(time).apply { add(Calendar.DAY_OF_MONTH, days) }.timeInMillis

    /** JS getDay(): 0 = Sunday .. 6 = Saturday */
    fun dayOfWeek(time: Long): Int = calendarAt(time).get(Calendar.DAY_OF_WEEK) - 1

    fun dayOfMonth(time: Long): Int = calendarAt(time).get(Calendar.DAY_OF_MONTH)

    fun isWeekend(time: Long): Boolean = dayOfWeek(time).let { it == 0 || it == 6 }

    /** First working-day midnight at or after [time]'s day. */
    fun nextWorkingDay(time: Long): Long {
        var d = startOfDay(time)
        while (isWeekend(d)) d = addDays(d, 1)
        return d
    }

    /** Working hours between two instants (weekdays only, 8h per full weekday). */
    fun workingHoursBetween(from: Long, to: Long): Double {
        if (to <= from) return 0.0
        var total = 0.0
        var day = startOfDay(from)
        while (day < to) {
            val next = addDays(day, 1)
            if (!isWeekend(day)) {
                val overlap = minOf(next, to) - maxOf(day, from)
                // A calendar day may be 23/25h around DST; scale by its real length
                if (overlap > 0) total += overlap.toDouble() / (next - day) * HOURS_PER_DAY
            }
            day = next
        }
        return total
    }
}
