package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.BurndownTicket
import com.cherryblossomdev.breakroom.data.models.TicketStatusChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class BurndownTest {

    private val utc = WorkCalendar(TimeZone.getTimeZone("UTC"))
    private val calc = BurndownCalculator(utc)

    private fun t(iso: String): Long = parseApiTime(iso)!!

    private fun ticket(
        id: Int,
        status: String = "backlog",
        amount: String? = null,
        unit: String? = null,
        created: String = "2026-09-25T09:00:00.000Z", // before the first sprint
        resolved: String? = null
    ) = BurndownTicket(
        id = id, title = "T$id", status = status, estimate_amount = amount, estimate_unit = unit,
        created_at = created, updated_at = created, resolved_at = resolved
    )

    private fun change(ticketId: Int, from: String?, to: String, at: String) =
        TicketStatusChange(ticket_id = ticketId, from_status = from, to_status = to, changed_at = at)

    @Test
    fun parsesBackendTimestamps() {
        assertEquals(t("2026-09-28T00:00:00Z"), parseApiTime("2026-09-28T00:00:00.000Z"))
        assertNull(parseApiTime(""))
        assertNull(parseApiTime("not a date"))
    }

    @Test
    fun estimatesConvertToWorkingHours() {
        assertEquals(3.0, estimateWorkingHours("3", "hours")!!, 1e-9)
        assertEquals(16.0, estimateWorkingHours("2.00", "days")!!, 1e-9)
        assertEquals(40.0, estimateWorkingHours("1", "weeks")!!, 1e-9)
        assertEquals(52.0 / 12.0 * 40.0, estimateWorkingHours("1", "months")!!, 1e-9)
        assertNull(estimateWorkingHours(null, "days"))
        assertNull(estimateWorkingHours("0", "days"))
        assertNull(estimateWorkingHours("3", null))
    }

    @Test
    fun workingHoursSkipWeekends() {
        // Fri 2026-10-02 00:00 -> Mon 2026-10-05 00:00: only Friday counts
        assertEquals(8.0, utc.workingHoursBetween(t("2026-10-02T00:00:00Z"), t("2026-10-05T00:00:00Z")), 1e-9)
        // Half a Tuesday
        assertEquals(4.0, utc.workingHoursBetween(t("2026-09-29T00:00:00Z"), t("2026-09-29T12:00:00Z")), 1e-9)
    }

    @Test
    fun sprintsAnchorOnMondayOfCreationWeek() {
        // Wednesday 2026-09-30 -> Monday 2026-09-28
        val anchor = calc.sprintAnchor(t("2026-09-30T15:00:00Z"))
        assertEquals(t("2026-09-28T00:00:00Z"), anchor)
        val second = calc.sprintBounds(anchor, 14, 1)
        assertEquals(t("2026-10-12T00:00:00Z"), second.start)
        assertEquals(t("2026-10-26T00:00:00Z"), second.end)
        assertEquals(1, calc.sprintIndexAt(anchor, 14, t("2026-10-20T10:00:00Z")))
        assertEquals(0, calc.sprintIndexAt(anchor, 14, t("2026-09-01T10:00:00Z")))
    }

    @Test
    fun statusIsReplayedFromHistory() {
        val tk = ticket(1, status = "resolved")
        val changes = listOf(
            BurndownCalculator.TimedChange(change(1, "backlog", "in_progress", "2026-09-29T10:00:00Z"), t("2026-09-29T10:00:00Z")),
            BurndownCalculator.TimedChange(change(1, "in_progress", "resolved", "2026-09-30T10:00:00Z"), t("2026-09-30T10:00:00Z"))
        )
        assertEquals("backlog", calc.statusAt(tk, changes, t("2026-09-28T12:00:00Z")))
        assertEquals("in_progress", calc.statusAt(tk, changes, t("2026-09-29T12:00:00Z")))
        assertEquals("resolved", calc.statusAt(tk, changes, t("2026-10-01T12:00:00Z")))
    }

    @Test
    fun doneTicketWithoutHistoryCountsFromResolvedAt() {
        val tk = ticket(1, status = "closed", resolved = "2026-09-30T10:00:00Z")
        assertEquals("backlog", calc.statusAt(tk, emptyList(), t("2026-09-29T00:00:00Z")))
        assertEquals("closed", calc.statusAt(tk, emptyList(), t("2026-09-30T11:00:00Z")))
    }

    @Test
    fun buildsWorkBurndownForASprint() {
        val anchor = t("2026-09-28T00:00:00Z") // Monday
        val sprint = calc.sprintBounds(anchor, 14, 0)
        val tickets = listOf(
            ticket(1, amount = "2", unit = "days"),            // resolved Tuesday
            ticket(2, amount = "4", unit = "hours"),           // still open
            ticket(3),                                         // unestimated -> 1 day
            ticket(4, amount = "1", unit = "days", created = "2026-09-30T09:00:00.000Z") // added Wednesday
        )
        val history = listOf(
            change(1, "backlog", "resolved", "2026-09-29T15:00:00.000Z")
        )
        val now = t("2026-09-30T18:00:00Z") // Wednesday evening

        val b = calc.build(tickets, history, sprint.start, sprint.end, now, BurndownMeasure.WORK)

        assertEquals(14, b.days.size)
        assertEquals(3.5, b.startRemaining, 1e-9)             // 2 + 0.5 + 1
        assertEquals(2.5, b.remaining, 1e-9)                  // 0.5 + 1 + 1 (ticket 4)
        assertEquals(2.0, b.completed, 1e-9)
        assertEquals(1.0, b.added, 1e-9)
        assertEquals(1, b.unestimatedCount)
        assertTrue(b.started)
        assertFalse(b.finished)
        // Tuesday: ticket 1 completed
        assertEquals(2.0, b.days[1].completed, 1e-9)
        assertEquals(1.5, b.days[1].remaining!!, 1e-9)
        // Wednesday is today; Thursday hasn't begun
        assertTrue(b.days[2].isToday)
        assertNull(b.days[3].remaining)
        // Ideal reaches zero at the end and stays flat over the weekend
        assertEquals(0.0, b.days.last().ideal, 1e-9)
        assertEquals(b.days[4].ideal, b.days[5].ideal, 1e-9) // Fri end == Sat end
        assertTrue(b.days[5].weekend)
    }

    @Test
    fun ticketMeasureCountsTickets() {
        val anchor = t("2026-09-28T00:00:00Z")
        val sprint = calc.sprintBounds(anchor, 7, 0)
        val tickets = listOf(ticket(1, amount = "5", unit = "days"), ticket(2))
        val b = calc.build(tickets, emptyList(), sprint.start, sprint.end, t("2026-09-29T12:00:00Z"), BurndownMeasure.TICKETS)
        assertEquals(2.0, b.startRemaining, 1e-9)
        assertEquals(0, b.unestimatedCount)
        assertTrue(b.approximate) // no history recorded
    }
}
