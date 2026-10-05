package com.cherryblossomdev.breakroom.data.models

// Models for the Projects feature: the cross-company Projects page, project
// settings/members, ticket dependencies, estimates, the Burndown/GANTT data
// and ticket attachments. Mirrors backend/routes/projects.js and helpdesk.js.

// ---- Estimates ----

object EstimateUnits {
    val ALL = listOf("hours", "days", "weeks", "months")

    // Same limit as backend/utilities/ticketEstimates.js
    const val MAX_AMOUNT = 9999.99

    // Error for an entered amount, or null if it's valid (blank = no estimate)
    fun validate(amountText: String): String? {
        if (amountText.isBlank()) return null
        val amount = amountText.trim().toDoubleOrNull()
        return if (amount == null || amount <= 0 || amount > MAX_AMOUNT) {
            "Estimate must be a number greater than 0 and at most ${formatAmount(MAX_AMOUNT)}"
        } else null
    }

    fun singular(unit: String): String = when (unit) {
        "hours" -> "hour"
        "days" -> "day"
        "weeks" -> "week"
        "months" -> "month"
        else -> unit
    }

    fun short(unit: String): String = when (unit) {
        "hours" -> "h"
        "days" -> "d"
        "weeks" -> "w"
        "months" -> "mo"
        else -> ""
    }

    /**
     * Divides an estimate amount into n parts in the same unit, to 2
     * decimals; the last part takes the rounding remainder so the parts add up
     * exactly. Parts too small to show at 2 decimals are blank (estimates must
     * be above zero). Splitting a ticket into subtasks pre-fills with this.
     * Port of web's ticketEstimates.js splitEvenly.
     */
    fun splitEvenly(amount: Double?, n: Int): List<String> {
        if (n < 1) return emptyList()
        if (amount == null || amount <= 0) return List(n) { "" }
        val each = Math.round(amount / n * 100) / 100.0
        val last = Math.round((amount - each * (n - 1)) * 100) / 100.0
        // A tiny remainder can't go to zero or below; fall back to equal parts
        val parts = if (last > 0) List(n - 1) { each } + last else List(n) { each }
        return parts.map { if (it > 0) formatAmount(it) else "" }
    }

    // 3.0 -> "3", 0.5 -> "0.5", 1.25 -> "1.25"
    fun formatAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString()
        else amount.toBigDecimal().stripTrailingZeros().toPlainString()
}

// ---- Cross-company Projects page ----

data class ProjectInvite(
    val project_id: Int,
    val role: String,
    val invited_at: String? = null,
    val project_title: String,
    val company_name: String? = null,
    val inviter_handle: String? = null,
    val inviter_first_name: String? = null,
    val inviter_last_name: String? = null
) {
    val inviterName: String?
        get() {
            val fullName = "${inviter_first_name ?: ""} ${inviter_last_name ?: ""}".trim()
            return fullName.ifEmpty { inviter_handle?.let { "@$it" } }
        }
}

data class ProjectInvitesResponse(val invites: List<ProjectInvite>)

data class ProjectMessageResponse(val message: String? = null)

// ---- Project board extras (GET /api/projects/:id) ----

// "ticket_id can't be finished until depends_on_ticket_id is"
data class TicketDependency(
    val ticket_id: Int,
    val depends_on_ticket_id: Int,
    val ticket_title: String? = null,
    val ticket_status: String? = null,
    val depends_on_title: String? = null,
    val depends_on_status: String? = null
) {
    val isSatisfied: Boolean
        get() = depends_on_status == "resolved" || depends_on_status == "closed"
}

data class TicketDependenciesResponse(val dependencies: List<TicketDependency>)

data class AddTicketDependencyRequest(val depends_on_ticket_id: Int)

// First move to in_progress / last move to resolved or closed
data class TicketTimelineEntry(
    val ticket_id: Int,
    val started_at: String? = null,
    val done_at: String? = null
)

data class ProjectAssignee(
    val user_id: Int,
    val handle: String? = null,
    val first_name: String? = null,
    val last_name: String? = null
) {
    val displayName: String
        get() = "${first_name ?: ""} ${last_name ?: ""}".trim().ifEmpty { handle ?: "Unknown" }
}

// ---- Settings + members ----

object ProjectRoles {
    val ALL = listOf("owner", "manager", "member", "viewer")
}

data class ProjectSettings(val sprint_duration_days: Int = 14)

data class ProjectMember(
    val user_id: Int,
    val role: String,
    val status: String,  // active, invited
    val invited_at: String? = null,
    val joined_at: String? = null,
    val handle: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val photo_path: String? = null,
    val inviter_handle: String? = null,
    val is_employee: Boolean = false
) {
    val displayName: String
        get() = "${first_name ?: ""} ${last_name ?: ""}".trim().ifEmpty { handle ?: "Unknown" }

    val isInvited: Boolean get() = status == "invited"
}

data class ProjectSettingsResponse(
    val settings: ProjectSettings,
    val sprint_durations: List<Int>? = null,
    val members: List<ProjectMember>,
    val roles: List<String>? = null,
    val current_user_id: Int? = null,
    val can_manage: Boolean = false,
    val can_manage_owners: Boolean = false
)

data class UpdateProjectSettingsRequest(val sprint_duration_days: Int)

data class UpdateProjectSettingsResponse(val settings: ProjectSettings)

data class InviteProjectMemberRequest(
    val identifier: String,  // handle or email
    val role: String
)

data class UpdateProjectMemberRequest(val role: String)

data class ProjectMembersResponse(
    val message: String? = null,
    val members: List<ProjectMember>
)

// ---- Burndown (GET /api/projects/:id/burndown) ----

data class BurndownProject(
    val id: Int,
    val title: String,
    val sprint_duration_days: Int? = null,
    val created_at: String? = null
)

data class BurndownTicket(
    val id: Int,
    val title: String,
    val status: String,
    val estimate_amount: String? = null,
    val estimate_unit: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val resolved_at: String? = null,
    val parent_ticket_id: Int? = null,
    val split_mode: String? = null
)

data class TicketStatusChange(
    val ticket_id: Int,
    val from_status: String? = null,
    val to_status: String,
    val changed_at: String
)

data class BurndownResponse(
    val project: BurndownProject,
    val tickets: List<BurndownTicket>,
    val history: List<TicketStatusChange>
)

// ---- Attachments ----

data class TicketAttachment(
    val id: Int,
    val ticket_id: Int,
    val file_name: String,
    val content_type: String,
    val size_bytes: Long,
    val created_at: String? = null,
    val uploaded_by: Int? = null,
    val uploader_handle: String? = null,
    val is_image: Boolean = false
)

data class TicketAttachmentsResponse(val attachments: List<TicketAttachment>)

// ---- Split a ticket into subtasks (migration 086) ----

object SplitModes {
    const val HIDDEN = "hidden"      // off every board and chart
    const val CATEGORY = "category"  // summary row on GANTT, Burndown filter
    const val MAX_SUBTASKS = 20
}

data class SplitSubtask(
    val title: String,
    val estimate_amount: Double? = null,
    val estimate_unit: String? = null
)

// The backend ignores mode for a ticket that was already split (it keeps
// its mode)
data class SplitTicketRequest(val mode: String, val subtasks: List<SplitSubtask>)

data class SplitTicketResponse(val split_mode: String, val subtask_ids: List<Int>)

// ---- Backlog order (migration 087) ----

// Every ticket and split parent the backlog shows, flattened depth first
// (a group's parent, then its subtasks)
data class BacklogOrderRequest(val order: List<Int>)

// ---- Contributors (migration 088) ----

data class TicketContributor(
    val user_id: Int,
    val role: String = "",
    val created_at: String? = null,
    val handle: String? = null,
    val first_name: String? = null,
    val last_name: String? = null
) {
    val displayName: String
        get() = "${first_name ?: ""} ${last_name ?: ""}".trim().ifEmpty { handle ?: "Unknown" }
}

object Contributors {
    // Same limits as backend/routes/helpdesk.js
    const val MAX_CONTRIBUTORS = 50
    const val MAX_ROLE_LENGTH = 100
}

data class TicketContributorsResponse(val contributors: List<TicketContributor>)

data class ContributorEntry(val user_id: Int, val role: String)

// The complete new list
data class UpdateContributorsRequest(val contributors: List<ContributorEntry>)

data class ContributorRolesResponse(val roles: List<String>)

// ---- Invite autocomplete ----

data class InviteSuggestion(
    val user_id: Int,
    val handle: String,
    val first_name: String? = null,
    val last_name: String? = null,
    val in_company: Boolean = false
) {
    val fullName: String get() = "${first_name ?: ""} ${last_name ?: ""}".trim()
}

data class InviteSuggestionsResponse(val users: List<InviteSuggestion>)
