package com.cherryblossomdev.breakroom.projects

import com.cherryblossomdev.breakroom.data.models.Ticket

// The backlog list's grouping and order. A port of the backlog code in web's
// ProjectPage.vue (rebuildBacklog, visibleBacklogKeys, persistBacklogOrder)
// and BacklogGroup.vue; keep them in step.
//
//   - Subtasks are grouped under their split parent (migration 086), which
//     is otherwise off the board. A parent appears only while some of its
//     subtasks are in the backlog. Splits nest, so groups do too: a subtask
//     that was split again is a group inside its parent's group.
//   - Order (migration 087) is manual. A group is ordered by its parent's
//     rank, items by theirs within the group. Tickets never placed (rank
//     null, e.g. new ones) sit at the top in the incoming order -- the API's
//     priority, then newest -- so they get noticed and placed.
//   - Search (#id or title text): a parent match shows its whole group, a
//     subtask match shows that subtask under its parents.

sealed class BacklogEntry {
    abstract val key: String
    abstract val rank: Int?

    data class Item(val ticket: Ticket) : BacklogEntry() {
        override val key get() = "t${ticket.id}"
        override val rank get() = ticket.backlog_rank
    }

    data class Group(val parent: Ticket, val children: List<BacklogEntry>) : BacklogEntry() {
        override val key get() = "p${parent.id}"
        override val rank get() = parent.backlog_rank
    }
}

// One row of the list as shown: a ticket or a group header, indented by depth
data class BacklogRow(val entry: BacklogEntry, val depth: Int)

object BacklogTree {

    // Stable: unranked first (keeping their incoming order), then by rank
    fun <T> byRank(items: List<T>, rankOf: (T) -> Int?): List<T> =
        items.withIndex().sortedWith { a, b ->
            val ra = rankOf(a.value)
            val rb = rankOf(b.value)
            when {
                ra == null && rb == null -> a.index - b.index
                ra == null -> -1
                rb == null -> 1
                else -> compareValues(ra, rb).takeIf { it != 0 } ?: (a.index - b.index)
            }
        }.map { it.value }

    /**
     * @param backlogTickets the backlog, in the API's default order
     * @param ancestorsOf    the split tickets above a ticket, outermost first
     */
    fun build(backlogTickets: List<Ticket>, ancestorsOf: (Ticket) -> List<Ticket>): List<BacklogEntry> {
        // Mutable while building: parent id -> (parent, children)
        class Node(val parent: Ticket?, val children: MutableList<Any> = mutableListOf())
        val root = Node(null)
        val groups = mutableMapOf<Int, Node>()
        for (ticket in backlogTickets) {
            var container = root
            for (ancestor in ancestorsOf(ticket)) {
                container = groups.getOrPut(ancestor.id) {
                    Node(ancestor).also { container.children.add(it) }
                }
            }
            container.children.add(ticket)
        }
        fun freeze(node: Node): List<BacklogEntry> = byRank(
            node.children.map { child ->
                if (child is Node) BacklogEntry.Group(child.parent!!, freeze(child))
                else BacklogEntry.Item(child as Ticket)
            }
        ) { it.rank }
        return freeze(root)
    }

    private fun matches(t: Ticket, q: String) = t.id.toString() == q || t.title.lowercase().contains(q)

    fun normalizeQuery(query: String) = query.trim().lowercase().removePrefix("#")

    /** Keys of the entries that show for a search, or null when not searching. */
    fun visibleKeys(entries: List<BacklogEntry>, query: String): Set<String>? {
        val q = normalizeQuery(query)
        if (q.isEmpty()) return null
        val keys = mutableSetOf<String>()
        fun walk(entry: BacklogEntry, groupMatched: Boolean) {
            when (entry) {
                is BacklogEntry.Item -> if (groupMatched || matches(entry.ticket, q)) keys.add(entry.key)
                is BacklogEntry.Group -> {
                    val matched = groupMatched || matches(entry.parent, q)
                    entry.children.forEach { walk(it, matched) }
                    if (entry.children.any { it.key in keys }) keys.add(entry.key)
                }
            }
        }
        entries.forEach { walk(it, false) }
        return keys
    }

    /**
     * The rows to show, depth first. Collapsed groups hide their contents,
     * except while searching (so a subtask match always shows).
     */
    fun rows(entries: List<BacklogEntry>, visible: Set<String>?, collapsed: Set<Int>): List<BacklogRow> {
        val out = mutableListOf<BacklogRow>()
        fun walk(list: List<BacklogEntry>, depth: Int) {
            for (entry in list) {
                if (visible != null && entry.key !in visible) continue
                out += BacklogRow(entry, depth)
                if (entry is BacklogEntry.Group && (visible != null || entry.parent.id !in collapsed)) {
                    walk(entry.children, depth + 1)
                }
            }
        }
        walk(entries, 0)
        return out
    }

    private fun BacklogEntry.contains(key: String): Boolean =
        this.key == key || (this is BacklogEntry.Group && children.any { it.contains(key) })

    // Applies `change` to the sibling list holding `key`, wherever it is
    private fun inContainerOf(
        entries: List<BacklogEntry>,
        key: String,
        change: (List<BacklogEntry>, Int) -> List<BacklogEntry>?
    ): List<BacklogEntry>? {
        val index = entries.indexOfFirst { it.key == key }
        if (index >= 0) return change(entries, index)
        for ((i, entry) in entries.withIndex()) {
            if (entry is BacklogEntry.Group && entry.contains(key)) {
                val children = inContainerOf(entry.children, key, change) ?: return null
                return entries.toMutableList().also { it[i] = entry.copy(children = children) }
            }
        }
        return null
    }

    private fun moved(list: List<BacklogEntry>, from: Int, to: Int) =
        list.toMutableList().apply { add(to, removeAt(from)) }

    /**
     * Drag: moves `fromKey` to where the sibling that is (or contains)
     * `toKey` sits. Entries only move among their siblings -- subtasks stay
     * in their group, a group moves with everything in it. Null when the
     * target isn't a sibling (the move is ignored).
     */
    fun move(entries: List<BacklogEntry>, fromKey: String, toKey: String): List<BacklogEntry>? =
        inContainerOf(entries, fromKey) { siblings, from ->
            val to = siblings.indexOfFirst { it.contains(toKey) }
            if (to < 0 || to == from) null else moved(siblings, from, to)
        }

    /** Moves an entry up (-1) or down (+1) among its siblings; null at an end. */
    fun moveBy(entries: List<BacklogEntry>, key: String, delta: Int): List<BacklogEntry>? =
        inContainerOf(entries, key) { siblings, from ->
            val to = from + delta
            if (to !in siblings.indices) null else moved(siblings, from, to)
        }

    /** Ticket ids in display order: a group's parent, then everything inside it. */
    fun flattenOrder(entries: List<BacklogEntry>): List<Int> {
        val out = mutableListOf<Int>()
        fun walk(list: List<BacklogEntry>) {
            for (entry in list) when (entry) {
                is BacklogEntry.Item -> out += entry.ticket.id
                is BacklogEntry.Group -> {
                    out += entry.parent.id
                    walk(entry.children)
                }
            }
        }
        walk(entries)
        return out
    }
}
