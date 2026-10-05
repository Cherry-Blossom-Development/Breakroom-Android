package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.Ticket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BacklogTreeTest {

    private fun ticket(id: Int, title: String = "T$id", parent: Int? = null, split: String? = null, rank: Int? = null) =
        Ticket(
            id = id, company_id = 1, creator_id = 1, title = title,
            parent_ticket_id = parent, split_mode = split, backlog_rank = rank
        )

    // 10 is split into 11, 12; 12 is split again into 13, 14
    private val p10 = ticket(10, "Checkout", split = "category", rank = 1)
    private val p12 = ticket(12, "Payments", parent = 10, split = "hidden", rank = 3)
    private val parents = listOf(p10, p12).associateBy { it.id }

    private fun ancestorsOf(t: Ticket): List<Ticket> {
        val chain = mutableListOf<Ticket>()
        var p = t.parent_ticket_id?.let { parents[it] }
        while (p != null) {
            chain.add(0, p)
            p = p.parent_ticket_id?.let { parents[it] }
        }
        return chain
    }

    private val backlog = listOf(
        ticket(1, "Login page", rank = 2),
        ticket(11, "Cart", parent = 10, rank = 2),
        ticket(13, "Card form", parent = 12, rank = 5),
        ticket(14, "Webhook", parent = 12, rank = 4),
        ticket(2, "New thing"),  // unranked: first
        ticket(3, "Logout", rank = 0)
    )

    private val tree = BacklogTree.build(backlog, ::ancestorsOf)

    @Test
    fun nestsGroupsAndOrdersByRank() {
        assertEquals(listOf("t2", "t3", "p10", "t1"), tree.map { it.key })
        val g10 = tree[2] as BacklogEntry.Group
        assertEquals(listOf("t11", "p12"), g10.children.map { it.key })
        val g12 = g10.children[1] as BacklogEntry.Group
        assertEquals(listOf("t14", "t13"), g12.children.map { it.key })
    }

    @Test
    fun flattensDepthFirst() {
        assertEquals(listOf(2, 3, 10, 11, 12, 14, 13, 1), BacklogTree.flattenOrder(tree))
    }

    @Test
    fun rowsHonourCollapse() {
        val all = BacklogTree.rows(tree, null, emptySet())
        assertEquals(listOf(2, 3, 10, 11, 12, 14, 13, 1).size, all.size)
        assertEquals(2, all.first { it.entry.key == "t14" }.depth)
        val collapsed = BacklogTree.rows(tree, null, setOf(12))
        assertEquals(listOf("t2", "t3", "p10", "t11", "p12", "t1"), collapsed.map { it.entry.key })
    }

    @Test
    fun searchShowsMatchUnderItsParents() {
        assertNull(BacklogTree.visibleKeys(tree, "  "))
        val keys = BacklogTree.visibleKeys(tree, "webhook")!!
        assertEquals(setOf("t14", "p12", "p10"), keys)
        // Shows even when its group is collapsed
        val rows = BacklogTree.rows(tree, keys, setOf(10, 12))
        assertEquals(listOf("p10", "p12", "t14"), rows.map { it.entry.key })
    }

    @Test
    fun moveStaysAmongSiblings() {
        // Top level: t2 onto p10 -> t3, p10, t2, t1
        val a = BacklogTree.move(tree, "t2", "p10")!!
        assertEquals(listOf(3, 10, 11, 12, 14, 13, 2, 1), BacklogTree.flattenOrder(a))
        // Onto a row inside a sibling group targets that group
        val b = BacklogTree.move(tree, "t1", "t13")!!
        assertEquals(listOf(2, 3, 1, 10, 11, 12, 14, 13), BacklogTree.flattenOrder(b))
        // Within a nested group
        val c = BacklogTree.move(tree, "t13", "t14")!!
        assertEquals(listOf(2, 3, 10, 11, 12, 13, 14, 1), BacklogTree.flattenOrder(c))
        // A subtask can't leave its group, and a group can't go into itself
        assertNull(BacklogTree.move(tree, "t11", "t1"))
        assertNull(BacklogTree.move(tree, "p10", "t14"))
    }

    @Test
    fun moveByStepsWithinSiblings() {
        val up = BacklogTree.moveBy(tree, "p12", -1)!!
        assertEquals(listOf(2, 3, 10, 12, 14, 13, 11, 1), BacklogTree.flattenOrder(up))
        assertNull(BacklogTree.moveBy(tree, "t2", -1))
        assertNull(BacklogTree.moveBy(tree, "p12", 1))
    }

    @Test
    fun parentMatchShowsWholeGroup() {
        val keys = BacklogTree.visibleKeys(tree, "payments")!!
        assertEquals(setOf("p10", "p12", "t13", "t14"), keys)
        assertEquals(setOf("t3"), BacklogTree.visibleKeys(tree, "#3"))
    }
}
