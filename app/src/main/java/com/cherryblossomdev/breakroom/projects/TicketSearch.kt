package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.Ticket

// Ranks tickets for the "depends on" search box. A port of web's
// frontend/src/utilities/ticketSearch.js (rankDependencyCandidates); keep the
// two in step.
//
//   - With nothing typed, only the ticket's own category is offered: the
//     other subtasks of the same split ticket (migration 086). A ticket
//     outside any category gets no suggestions until you type.
//   - Typed text is matched against #id and title: an exact id beats an id
//     prefix, which beats a title that starts with the text, then a word
//     that starts with it, then the text anywhere. Several words must all
//     match; a number among them (with or without #) may match the id, so
//     a picked "#5 Payment webhooks" still finds #5 after it's edited.
//   - Category matches always come first; other tickets only fill the
//     remaining slots, below them.
//   - At most `limit` results.

data class RankedTicket(val ticket: Ticket, val inCategory: Boolean)

object TicketSearch {
    private val ID_QUERY = Regex("^#?\\d+$")
    private val DIGITS = Regex("^\\d+$")
    private val NON_WORD = Regex("[^a-z0-9]+")
    private val SPACES = Regex("\\s+")

    private fun matchScore(ticket: Ticket, terms: List<String>, idQuery: String?): Int {
        val id = ticket.id.toString()
        if (idQuery != null) {
            if (id == idQuery) return 100
            if (id.startsWith(idQuery)) return 80
        }
        val title = ticket.title.lowercase()
        val words = title.split(NON_WORD).filter { it.isNotEmpty() }
        var score = 0
        for (term in terms) {
            score += when {
                DIGITS.matches(term) && id.startsWith(term) -> if (id == term) 40 else 25
                title.startsWith(term) -> 30
                words.any { it.startsWith(term) } -> 20
                title.contains(term) -> 10
                else -> return 0 // every word typed must match
            }
        }
        return score
    }

    fun rankDependencyCandidates(
        candidates: List<Ticket>,
        query: String,
        categoryIds: Set<Int>,
        limit: Int = 10
    ): List<RankedTicket> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return candidates
                .filter { it.id in categoryIds }
                .sortedBy { it.id }
                .take(limit)
                .map { RankedTicket(it, inCategory = true) }
        }

        val idQuery = if (ID_QUERY.matches(q)) q.replace("#", "") else null
        val terms = q.split(SPACES).map { it.removePrefix("#") }.filter { it.isNotEmpty() }
        return candidates
            .map { Triple(it, matchScore(it, terms, idQuery), it.id in categoryIds) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Triple<Ticket, Int, Boolean>> { it.third }
                    .thenByDescending { it.second }
                    .thenBy { it.first.id }
            )
            .take(limit)
            .map { RankedTicket(it.first, it.third) }
    }
}
