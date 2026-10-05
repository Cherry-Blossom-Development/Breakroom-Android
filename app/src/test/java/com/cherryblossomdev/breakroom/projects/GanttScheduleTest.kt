package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.Ticket
import com.cherryblossomdev.breakroom.data.models.TicketDependency
import com.cherryblossomdev.breakroom.data.models.TicketTimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class GanttScheduleTest {

    private val utc = WorkCalendar(TimeZone.getTimeZone("UTC"))
    private val scheduler = GanttScheduler(utc)

    private fun t(iso: String): Long = parseApiTime(iso)!!

    // Wednesday 2026-09-30, mid-morning
    private val now = t("2026-09-30T10:00:00Z")
    private val wed = t("2026-09-30T00:00:00Z")

    private fun ticket(
        id: Int,
        status: String = "backlog",
        amount: String? = "1",
        unit: String? = "days",
        assignee: Int? = null,
        priority: String = "medium",
        resolved: String? = null
    ) = Ticket(
        id = id, company_id = 1, creator_id = 1, assigned_to = assignee, title = "T$id",
        status = status, priority = priority, estimate_amount = amount, estimate_unit = unit,
        created_at = "2026-09-20T09:00:00.000Z", updated_at = resolved, resolved_at = resolved
    )

    private fun dep(ticketId: Int, dependsOn: Int, dependsOnStatus: String = "backlog") =
        TicketDependency(ticket_id = ticketId, depends_on_ticket_id = dependsOn, depends_on_status = dependsOnStatus)

    private fun build(
        tickets: List<Ticket>,
        deps: List<TicketDependency> = emptyList(),
        timeline: List<TicketTimelineEntry> = emptyList(),
        includeDone: Boolean = false
    ) = scheduler.build(tickets, deps, timeline, now, includeDone)

    private fun split(id: Int, mode: String, parent: Int? = null) = Ticket(
        id = id, company_id = 1, creator_id = 1, title = "S$id", split_mode = mode, parent_ticket_id = parent
    )

    private fun sub(id: Int, parent: Int, status: String = "backlog") =
        ticket(id, status = status).copy(parent_ticket_id = parent)

    @Test
    fun dependenciesThroughSplitTicketsFollowTheSubtasks() {
        // 10 split into 11 + 12 (12 split again into 13); 1 depends on 10, 10 depends on 2
        val parents = listOf(split(10, "hidden"), split(12, "category", parent = 10))
        val tickets = listOf(ticket(1), ticket(2), sub(11, 10), sub(13, 12, status = "resolved"))
        val deps = expandSplitDependencies(listOf(dep(1, 10), dep(10, 2)), tickets, parents)
        val edges = deps.map { it.ticket_id to it.depends_on_ticket_id }.toSet()
        assertEquals(setOf(1 to 11, 1 to 13, 11 to 2, 13 to 2), edges)
        assertEquals("resolved", deps.first { it.depends_on_ticket_id == 13 }.depends_on_status)
        // No split parents: untouched
        assertEquals(listOf(dep(1, 2)), expandSplitDependencies(listOf(dep(1, 2)), tickets, emptyList()))
    }

    @Test
    fun categoriesGetNestedSummaryRows() {
        // 10 (category) -> 11, 12 (hidden) -> 13; 20 (category, nested in 10) -> 21
        val parents = listOf(split(10, "category"), split(12, "hidden", parent = 10), split(20, "category", parent = 10))
        val tickets = listOf(ticket(1), sub(11, 10), sub(13, 12), sub(21, 20, status = "resolved"))
        val s = build(tickets, includeDone = true)
        val rows = withCategoryRows(s.rows, parents, tickets)
        assertEquals(listOf(1, 10, 11, 13, 20, 21).sorted(), rows.map { it.ticket.id }.sorted())
        val cat = rows.first { it.ticket.id == 10 }
        assertTrue(cat.isCategory)
        assertEquals(0, cat.depth)
        assertEquals(3, cat.subtaskCount) // 11, 13 (through the hidden split) and 21
        assertEquals(1, cat.doneCount)
        val inner = rows.first { it.ticket.id == 20 }
        assertEquals(1, inner.depth)
        assertEquals(2, rows.first { it.ticket.id == 21 }.depth)
        assertEquals(1, rows.first { it.ticket.id == 13 }.depth)
        // Summary rows sit right above their members and span them
        val i = rows.indexOf(cat)
        val members = rows.drop(i + 1).takeWhile { it.depth > 0 }
        assertEquals(setOf(11, 13, 20, 21), members.map { it.ticket.id }.toSet())
        assertEquals(members.filterNot { it.isCategory }.minOf { it.start }, cat.start)
        assertEquals(members.filterNot { it.isCategory }.maxOf { it.end }, cat.end)
        // Without categories, rows are untouched
        assertEquals(s.rows, withCategoryRows(s.rows, listOf(split(10, "hidden")), tickets))
    }

    @Test
    fun workingOffsetsSkipWeekendsAndKeepFridayFinishes() {
        val fri = t("2026-10-02T00:00:00Z")
        // 8h from Friday midnight: asEnd stays at the end of Friday
        assertEquals(t("2026-10-03T00:00:00Z"), utc.workingOffsetToDate(fri, 8.0, asEnd = true))
        // ...but as a start it moves on to Monday
        assertEquals(t("2026-10-05T00:00:00Z"), utc.workingOffsetToDate(fri, 8.0))
        // A working day's 8h are spread over its 24 calendar hours, so Monday
        // noon is 4 working hours into Monday: 4h back is Monday midnight,
        // and 12h back takes all of Friday too (the weekend is skipped)
        assertEquals(t("2026-10-05T00:00:00Z"), utc.subtractWorkingHours(t("2026-10-05T12:00:00Z"), 4.0))
        assertEquals(t("2026-10-02T00:00:00Z"), utc.subtractWorkingHours(t("2026-10-05T12:00:00Z"), 12.0))
    }

    @Test
    fun anchorsOnTodayAndSchedulesDependencyChains() {
        val s = build(
            listOf(ticket(1), ticket(2), ticket(3, amount = "2")),
            deps = listOf(dep(2, 1), dep(3, 2))
        )
        assertEquals(wed, s.anchor)
        val byId = s.rows.associateBy { it.ticket.id }
        assertEquals(wed, byId.getValue(1).start)
        assertEquals(t("2026-10-01T00:00:00Z"), byId.getValue(1).end)
        assertEquals(byId.getValue(1).end, byId.getValue(2).start) // waits for #1
        // #3 (2 days) starts Friday and skips the weekend, finishing end of Monday
        assertEquals(t("2026-10-02T00:00:00Z"), byId.getValue(3).start)
        assertEquals(t("2026-10-06T00:00:00Z"), byId.getValue(3).end)
        assertEquals(listOf(2), byId.getValue(3).dependsOn)
        assertEquals(32.0, s.summary.remainingHours, 1e-9)
        assertEquals(t("2026-10-06T00:00:00Z"), s.summary.projectedFinish)
    }

    @Test
    fun sameAssigneeRunsBackToBackUnassignedRunInParallel() {
        val s = build(
            listOf(
                ticket(1, assignee = 7, priority = "low"),
                ticket(2, assignee = 7, priority = "urgent"),
                ticket(3), ticket(4)
            )
        )
        val byId = s.rows.associateBy { it.ticket.id }
        // Urgent first for the shared assignee
        assertEquals(wed, byId.getValue(2).start)
        assertEquals(byId.getValue(2).end, byId.getValue(1).start)
        // Unassigned tickets both start today
        assertEquals(wed, byId.getValue(3).start)
        assertEquals(wed, byId.getValue(4).start)
    }

    @Test
    fun onDeckGoesBeforeBacklogAtTheSameTime() {
        val s = build(listOf(ticket(1, assignee = 7), ticket(2, status = "on-deck", assignee = 7)))
        assertEquals(2, s.rows.first().ticket.id)
    }

    @Test
    fun inProgressUsesRecordedStartAndFlagsOverdue() {
        val s = build(
            listOf(
                ticket(1, status = "in_progress", amount = "4", unit = "hours"),
                ticket(2, status = "in_progress", amount = "2", unit = "days")
            ),
            timeline = listOf(
                TicketTimelineEntry(1, started_at = "2026-09-28T00:00:00.000Z"), // 16h worked > 4h estimate
                TicketTimelineEntry(2, started_at = "2026-09-29T00:00:00.000Z")  // 8h of 16h worked
            )
        )
        val byId = s.rows.associateBy { it.ticket.id }
        assertTrue(byId.getValue(1).overdue)
        assertEquals(wed, byId.getValue(1).end)
        assertEquals(t("2026-09-28T00:00:00Z"), byId.getValue(1).start)
        assertFalse(byId.getValue(2).overdue)
        assertEquals(8.0, byId.getValue(2).remainingHours, 1e-9)
        assertEquals(1, s.summary.overdueCount)
    }

    @Test
    fun blockersInAnotherProjectAreReportedNotScheduled() {
        val s = build(listOf(ticket(1)), deps = listOf(dep(1, 99)))
        val row = s.rows.single()
        assertEquals(listOf(99), row.externalBlockers)
        assertEquals(wed, row.start)
    }

    @Test
    fun unestimatedTicketsCountAsOneDayAndDoneOnesAreOptional() {
        val tickets = listOf(
            ticket(1, amount = null, unit = null),
            ticket(2, status = "resolved", resolved = "2026-09-29T12:00:00.000Z")
        )
        val hidden = build(tickets)
        assertEquals(1, hidden.rows.size)
        assertTrue(hidden.rows.single().unestimated)
        assertEquals(1, hidden.summary.unestimatedCount)

        val shown = build(tickets, includeDone = true)
        val done = shown.rows.single { it.stage == GanttStage.DONE }
        assertEquals(t("2026-09-29T12:00:00Z"), done.end)
        assertFalse(done.startKnown)
        assertEquals(t("2026-09-28T12:00:00Z"), done.start) // 1 day of work back
        assertEquals(1, shown.summary.activeCount)
    }
}
