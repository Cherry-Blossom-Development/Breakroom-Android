package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.BurndownTicket
import com.cherryblossomdev.breakroom.data.models.TicketStatusChange

// Sprint burndown for one project. A port of web's
// frontend/src/utilities/burndown.js; keep the two in step.
//
// Rules:
//   - Sprints are back-to-back blocks of the project's sprint length
//     (Settings, migration 082), counted from the Monday of the week the
//     project was created.
//   - Remaining work at an instant = every ticket in the project that exists
//     by then and isn't resolved/closed then. Tickets added or reopened
//     mid-sprint push the line up; that's scope change, shown as "added".
//   - A ticket's status at any instant is replayed from status history
//     (migration 081). Before a ticket's first recorded change, its status is
//     that change's from_status; with no history at all, a done ticket counts
//     as done from its resolved_at.
//   - Work is measured in 8h working days (estimateWorkingHours); an
//     unestimated ticket counts as 1 day and is flagged. Estimates have no
//     history, so every day uses the ticket's current estimate.
//   - The ideal line runs from the sprint's starting remaining work to zero
//     at the sprint's end, dropping only on working days (Mon-Fri).

enum class BurndownMeasure { WORK, TICKETS }

data class SprintBounds(val index: Int, val start: Long, val end: Long) // end exclusive

data class BurndownDay(
    val date: Long,
    val end: Long,
    val at: Long?,          // null for days that haven't begun
    val isToday: Boolean,
    val weekend: Boolean,
    val remaining: Double?, // null for days that haven't begun
    val ideal: Double,
    val completed: Double,
    val added: Double
)

data class BurndownResult(
    val days: List<BurndownDay>,
    val startRemaining: Double,
    val remaining: Double,
    val idealNow: Double,
    val completed: Double,
    val added: Double,
    val unestimatedCount: Int,
    // Days before any recorded history are reconstructed from resolved dates
    val approximate: Boolean,
    val started: Boolean,
    val finished: Boolean
)

private fun isDone(status: String?) = status == "resolved" || status == "closed"

class BurndownCalculator(private val cal: WorkCalendar = WorkCalendar()) {

    /** Monday on or before [date], at midnight. */
    fun sprintAnchor(date: Long): Long {
        val d = cal.startOfDay(date)
        return cal.addDays(d, -((cal.dayOfWeek(d) + 6) % 7))
    }

    fun sprintBounds(anchor: Long, sprintDays: Int, index: Int) = SprintBounds(
        index = index,
        start = cal.addDays(anchor, index * sprintDays),
        end = cal.addDays(anchor, (index + 1) * sprintDays)
    )

    /** Index of the sprint containing [date] (never below 0). */
    fun sprintIndexAt(anchor: Long, sprintDays: Int, date: Long): Int {
        val days = Math.round((cal.startOfDay(date) - anchor).toDouble() / DAY_MS).toInt()
        return maxOf(Math.floorDiv(days, sprintDays), 0)
    }

    /** Status a ticket had at instant [t]. [changes] is its history, oldest first. */
    fun statusAt(ticket: BurndownTicket, changes: List<TimedChange>, t: Long): String {
        var last: TimedChange? = null
        for (c in changes) {
            if (c.at <= t) {
                last = c
            } else {
                // First change after t: the ticket was in its from_status until then
                return last?.change?.to_status ?: (c.change.from_status ?: c.change.to_status)
            }
        }
        last?.let { return it.change.to_status }
        // No recorded history (pre-migration 081): trust the current status,
        // but a done ticket was only done from when it was resolved
        if (isDone(ticket.status)) {
            val doneAt = parseApiTime(ticket.resolved_at ?: ticket.updated_at ?: ticket.created_at) ?: 0L
            return if (doneAt <= t) ticket.status else "backlog"
        }
        return ticket.status
    }

    data class TimedChange(val change: TicketStatusChange, val at: Long)

    private class Sized(
        val ticket: BurndownTicket,
        val createdAt: Long,
        val changes: List<TimedChange>,
        val unestimated: Boolean,
        val size: Double
    )

    fun build(
        tickets: List<BurndownTicket>,
        history: List<TicketStatusChange>,
        start: Long,
        end: Long,
        now: Long,
        measure: BurndownMeasure
    ): BurndownResult {
        val timed = history.mapNotNull { c -> parseApiTime(c.changed_at)?.let { TimedChange(c, it) } }
        val changesByTicket = timed.groupBy { it.change.ticket_id }

        val sized = tickets.map { t ->
            val hours = estimateWorkingHours(t.estimate_amount, t.estimate_unit)
            Sized(
                ticket = t,
                createdAt = parseApiTime(t.created_at) ?: 0L,
                changes = changesByTicket[t.id].orEmpty(),
                unestimated = hours == null,
                // Size in the chart's unit: working days, or 1 per ticket
                size = if (measure == BurndownMeasure.TICKETS) 1.0 else (hours ?: HOURS_PER_DAY) / HOURS_PER_DAY
            )
        }

        fun exists(s: Sized, t: Long) = s.createdAt <= t
        fun openAt(s: Sized, t: Long) = exists(s, t) && !isDone(statusAt(s.ticket, s.changes, t))
        fun remainingAt(t: Long) = sized.sumOf { if (openAt(it, t)) it.size else 0.0 }

        val startRemaining = remainingAt(start)
        val totalWorking = cal.workingHoursBetween(start, end)
        fun idealAt(t: Long) = if (totalWorking > 0) {
            startRemaining * maxOf(0.0, 1 - cal.workingHoursBetween(start, t) / totalWorking)
        } else 0.0

        val days = mutableListOf<BurndownDay>()
        var dayStart = start
        while (dayStart < end) {
            val dayEnd = cal.addDays(dayStart, 1)
            val started = dayStart <= now
            val at = if (dayEnd < now) dayEnd else now // today: as of now
            var completed = 0.0
            var added = 0.0
            if (started) {
                for (s in sized) {
                    val wasOpen = openAt(s, dayStart)
                    val isOpen = openAt(s, at)
                    val createdToday = !exists(s, dayStart) && exists(s, at)
                    when {
                        createdToday -> {
                            added += s.size
                            if (!isOpen) completed += s.size // created and finished the same day
                        }
                        wasOpen && !isOpen -> completed += s.size
                        !wasOpen && isOpen -> added += s.size // reopened
                    }
                }
            }
            days += BurndownDay(
                date = dayStart,
                end = dayEnd,
                at = if (started) at else null,
                isToday = dayStart <= now && now < dayEnd,
                weekend = cal.isWeekend(dayStart),
                remaining = if (started) remainingAt(at) else null,
                ideal = idealAt(dayEnd),
                completed = completed,
                added = added
            )
            dayStart = dayEnd
        }

        val latest = days.lastOrNull { it.remaining != null }
        val current = latest?.remaining ?: startRemaining
        // This sprint's work: tickets that exist by now and weren't already
        // done when it started
        val until = latest?.at ?: start
        val inScope = sized.filter { exists(it, until) && (!exists(it, start) || openAt(it, start)) }
        val earliestHistory = timed.firstOrNull()?.at

        return BurndownResult(
            days = days,
            startRemaining = startRemaining,
            remaining = current,
            idealNow = latest?.at?.let { idealAt(it) } ?: startRemaining,
            completed = days.sumOf { it.completed },
            added = days.sumOf { it.added },
            unestimatedCount = if (measure == BurndownMeasure.WORK) inScope.count { it.unestimated } else 0,
            approximate = earliestHistory == null || start < earliestHistory,
            started = start <= now,
            finished = end <= now
        )
    }
}
