package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.Ticket
import com.cherryblossomdev.breakroom.data.models.TicketDependency
import com.cherryblossomdev.breakroom.data.models.TicketTimelineEntry

// GANTT schedule for one project's tickets. A port of web's
// frontend/src/utilities/ganttSchedule.js (buildGanttSchedule); keep the two
// in step.
//
// Rules (agreed 2026-09-28):
//   - Scheduling runs in working hours: 8h per working day, Mon-Fri, weekends
//     skipped (see WorkingTime.kt). An unestimated ticket is treated as 1
//     working day and flagged.
//   - Unfinished work is scheduled forward from today. A ticket can't start
//     until the tickets it depends on (migration 079) finish.
//   - One person works one ticket at a time: tickets with the same assignee
//     run back to back. Unassigned tickets run in parallel.
//   - In-progress tickets start when they actually moved to in_progress
//     (status history, migration 081) and finish once their estimate's worth
//     of working time has passed -- or today, flagged overdue, if it already
//     has.
//   - Done (resolved/closed) tickets, when shown, use their real start/finish
//     from status history, falling back to resolved_at and the estimate.

const val DEFAULT_UNESTIMATED_HOURS = HOURS_PER_DAY

enum class GanttStage(val label: String) {
    BACKLOG("Backlog"),
    ON_DECK("On Deck"),
    IN_PROGRESS("In Progress"),
    DONE("Done")
}

fun stageOf(status: String): GanttStage = when (status) {
    "resolved", "closed" -> GanttStage.DONE
    "in_progress" -> GanttStage.IN_PROGRESS
    "on-deck" -> GanttStage.ON_DECK
    else -> GanttStage.BACKLOG // backlog and the Help Desk's legacy 'open'
}

data class GanttRow(
    val ticket: Ticket,
    val stage: GanttStage,
    val start: Long,
    val end: Long,
    val hours: Double,
    val remainingHours: Double,
    val unestimated: Boolean,
    val overdue: Boolean,
    // false when the start wasn't recorded and was estimated backwards
    val startKnown: Boolean,
    // Unfinished dependencies in another project (not scheduled here)
    val externalBlockers: List<Int> = emptyList(),
    val dependsOn: List<Int> = emptyList()
)

data class GanttSummary(
    val activeCount: Int,
    val remainingHours: Double,
    val unestimatedCount: Int,
    val overdueCount: Int,
    val projectedFinish: Long?
)

data class GanttSchedule(val rows: List<GanttRow>, val summary: GanttSummary, val anchor: Long)

private val PRIORITY_RANK = mapOf("urgent" to 0, "high" to 1, "medium" to 2, "low" to 3)
// Among tickets ready at the same time, on-deck work goes before backlog
private val STAGE_RANK = mapOf("on-deck" to 0, "backlog" to 1, "open" to 1)

private fun isDoneStatus(status: String?) = status == "resolved" || status == "closed"

class GanttScheduler(private val cal: WorkCalendar = WorkCalendar()) {

    fun build(
        tickets: List<Ticket>,
        dependencies: List<TicketDependency>,
        timeline: List<TicketTimelineEntry>,
        now: Long,
        includeDone: Boolean
    ): GanttSchedule {
        val anchor = cal.nextWorkingDay(now) // "today" -- scheduling works in whole days
        val history = timeline.associateBy { it.ticket_id }
        val byId = tickets.associateBy { it.id }
        fun assigneeOf(t: Ticket) = t.assigned_to ?: t.assignee_id

        fun durationOf(t: Ticket): Pair<Double, Boolean> {
            val hours = estimateWorkingHours(t.estimate_amount, t.estimate_unit)
            return if (hours != null) hours to false else DEFAULT_UNESTIMATED_HOURS to true
        }

        // ticket id -> edges to what it depends on
        val predecessors = dependencies.groupBy { it.ticket_id }

        val rows = mutableListOf<GanttRow>()
        val finishOffset = mutableMapOf<Int, Double>() // active ticket id -> working-hour offset it finishes at
        val assigneeFree = mutableMapOf<Int, Double>() // assignee id -> offset they're next free

        // 1. In-progress tickets: already underway, occupy their assignee first
        for (t in tickets.filter { stageOf(it.status) == GanttStage.IN_PROGRESS }) {
            val (hours, unestimated) = durationOf(t)
            val startedAt = parseApiTime(history[t.id]?.started_at)
            val elapsed = startedAt?.let { cal.workingHoursBetween(it, anchor) } ?: 0.0
            val remaining = maxOf(hours - elapsed, 0.0)
            val overdue = startedAt != null && hours - elapsed <= 0
            finishOffset[t.id] = remaining
            assigneeOf(t)?.let { assigneeFree[it] = maxOf(assigneeFree[it] ?: 0.0, remaining) }
            rows += GanttRow(
                ticket = t,
                stage = GanttStage.IN_PROGRESS,
                start = if (startedAt != null && startedAt < anchor) startedAt else anchor,
                end = if (overdue) anchor else cal.workingOffsetToDate(anchor, remaining, asEnd = true),
                hours = hours,
                remainingHours = remaining,
                unestimated = unestimated,
                overdue = overdue,
                startKnown = startedAt != null
            )
        }

        // 2. Not-started tickets: list scheduling in dependency order
        val pending = tickets
            .filter { stageOf(it.status) == GanttStage.BACKLOG || stageOf(it.status) == GanttStage.ON_DECK }
            .map { it.id }
            .toMutableSet()
        val blockersOutside = mutableMapOf<Int, MutableSet<Int>>()

        // Offset a ticket can start at, or null if a predecessor isn't scheduled yet
        fun readyAt(id: Int): Double? {
            var earliest = 0.0
            for (d in predecessors[id].orEmpty()) {
                val pred = byId[d.depends_on_ticket_id]
                if (isDoneStatus(pred?.status ?: d.depends_on_status)) continue
                if (pred == null) {
                    // Unfinished ticket in another project -- can't schedule it here
                    blockersOutside.getOrPut(id) { mutableSetOf() } += d.depends_on_ticket_id
                    continue
                }
                val predFinish = finishOffset[pred.id] ?: return null
                earliest = maxOf(earliest, predFinish)
            }
            return earliest
        }

        while (pending.isNotEmpty()) {
            var candidates = pending.mapNotNull { id -> readyAt(id)?.let { id to it } }
            // Loops are blocked on save; if one slipped in anyway, don't hang
            if (candidates.isEmpty()) candidates = pending.map { it to 0.0 }

            val (id, ready) = candidates.sortedWith(
                compareBy<Pair<Int, Double>>(
                    { it.second },
                    { STAGE_RANK[byId.getValue(it.first).status] ?: 1 },
                    { PRIORITY_RANK[byId.getValue(it.first).priority] ?: 2 },
                    { it.first }
                )
            ).first()

            val t = byId.getValue(id)
            val (hours, unestimated) = durationOf(t)
            val assignee = assigneeOf(t)
            val start = maxOf(ready, assignee?.let { assigneeFree[it] } ?: 0.0)
            val end = start + hours
            finishOffset[id] = end
            if (assignee != null) assigneeFree[assignee] = end
            pending -= id

            rows += GanttRow(
                ticket = t,
                stage = stageOf(t.status),
                start = cal.workingOffsetToDate(anchor, start),
                end = cal.workingOffsetToDate(anchor, end, asEnd = true),
                hours = hours,
                remainingHours = hours,
                unestimated = unestimated,
                overdue = false,
                startKnown = true
            )
        }

        val withLinks = rows.map { row ->
            row.copy(
                externalBlockers = blockersOutside[row.ticket.id].orEmpty().toList(),
                dependsOn = predecessors[row.ticket.id].orEmpty().map { it.depends_on_ticket_id }
            )
        }.toMutableList()

        // 3. Done tickets (optional): real history where we have it
        if (includeDone) {
            for (t in tickets.filter { stageOf(it.status) == GanttStage.DONE }) {
                val (hours, unestimated) = durationOf(t)
                val h = history[t.id]
                val end = parseApiTime(h?.done_at) ?: parseApiTime(t.resolved_at) ?: parseApiTime(t.updated_at) ?: anchor
                val startedAt = parseApiTime(h?.started_at)
                val startKnown = startedAt != null && startedAt < end
                withLinks += GanttRow(
                    ticket = t,
                    stage = GanttStage.DONE,
                    start = if (startKnown) startedAt!! else cal.subtractWorkingHours(end, hours),
                    end = end,
                    hours = hours,
                    remainingHours = 0.0,
                    unestimated = unestimated,
                    overdue = false,
                    startKnown = startKnown,
                    dependsOn = predecessors[t.id].orEmpty().map { it.depends_on_ticket_id }
                )
            }
        }

        val sorted = withLinks.sortedWith(compareBy({ it.start }, { it.end }, { it.ticket.id }))
        val active = sorted.filter { it.stage != GanttStage.DONE }
        val summary = GanttSummary(
            activeCount = active.size,
            remainingHours = active.sumOf { it.remainingHours },
            unestimatedCount = active.count { it.unestimated },
            overdueCount = active.count { it.overdue },
            projectedFinish = active.maxOfOrNull { it.end }
        )
        return GanttSchedule(rows = sorted, summary = summary, anchor = anchor)
    }
}
