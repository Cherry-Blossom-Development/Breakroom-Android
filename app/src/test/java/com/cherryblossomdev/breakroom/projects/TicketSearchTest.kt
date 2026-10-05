package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.EstimateUnits
import com.cherryblossomdev.breakroom.data.models.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketSearchTest {

    private fun ticket(id: Int, title: String) =
        Ticket(id = id, company_id = 1, creator_id = 1, title = title)

    private val tickets = listOf(
        ticket(5, "Payment webhooks"),
        ticket(12, "Pay invoices"),
        ticket(51, "Refund flow"),
        ticket(7, "Set up payment provider"),
        ticket(8, "Webhook retries")
    )

    private fun ids(query: String, category: Set<Int> = emptySet(), limit: Int = 10) =
        TicketSearch.rankDependencyCandidates(tickets, query, category, limit).map { it.ticket.id }

    @Test
    fun emptyQueryOffersOnlyTheCategory() {
        assertEquals(emptyList<Int>(), ids(""))
        assertEquals(listOf(7, 8), ids("  ", setOf(8, 7)))
    }

    @Test
    fun exactIdBeatsIdPrefix() {
        assertEquals(listOf(5, 51), ids("5"))
        assertEquals(listOf(5, 51), ids("#5"))
    }

    @Test
    fun titlePrefixBeatsWordPrefixBeatsSubstring() {
        // "pay": 12 and 5 start with it, 7 has a word starting with it
        assertEquals(listOf(5, 12, 7), ids("pay"))
        // "hook": substring only
        assertEquals(listOf(5, 8), ids("hook"))
    }

    @Test
    fun everyTermMustMatch() {
        assertEquals(listOf(5), ids("payment webhooks"))
        assertEquals(emptyList<Int>(), ids("payment refund"))
    }

    @Test
    fun aNumberAmongWordsMatchesTheId() {
        assertEquals(listOf(5), ids("#5 Payment webhooks"))
    }

    @Test
    fun categoryMatchesComeFirst() {
        val ranked = TicketSearch.rankDependencyCandidates(tickets, "pay", setOf(7))
        assertEquals(listOf(7, 5, 12), ranked.map { it.ticket.id })
        assertTrue(ranked[0].inCategory)
    }

    @Test
    fun limitApplies() {
        assertEquals(listOf(5), ids("pay", limit = 1))
    }

    @Test
    fun splitEvenlyAddsUpExactly() {
        assertEquals(listOf("3.33", "3.33", "3.34"), EstimateUnits.splitEvenly(10.0, 3))
        assertEquals(listOf("2", "2"), EstimateUnits.splitEvenly(4.0, 2))
        assertEquals(listOf("", ""), EstimateUnits.splitEvenly(null, 2))
        assertEquals(listOf("", "", "", "", ""), EstimateUnits.splitEvenly(0.0, 5))
        // Too small to show at 2 decimals
        assertEquals(listOf("", "", "0.01"), EstimateUnits.splitEvenly(0.01, 3))
    }
}
