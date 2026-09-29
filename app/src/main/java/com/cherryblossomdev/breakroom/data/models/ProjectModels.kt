package com.cherryblossomdev.breakroom.data.models

// Models for the Projects feature: the cross-company Projects page, project
// settings/members, ticket dependencies, estimates, the Burndown/GANTT data
// and ticket attachments. Mirrors backend/routes/projects.js and helpdesk.js.

// ---- Estimates ----

object EstimateUnits {
    val ALL = listOf("hours", "days", "weeks", "months")

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
    val resolved_at: String? = null
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
) {
    // "512 B", "14.2 KB", "3.1 MB"
    val formattedSize: String
        get() = when {
            size_bytes < 1024 -> "$size_bytes B"
            size_bytes < 1024 * 1024 -> String.format("%.1f KB", size_bytes / 1024.0)
            else -> String.format("%.1f MB", size_bytes / (1024.0 * 1024.0))
        }
}

data class TicketAttachmentsResponse(val attachments: List<TicketAttachment>)
