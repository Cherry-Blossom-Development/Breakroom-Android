package com.example.breakroom.data.models

import com.google.gson.annotations.SerializedName

// Block types matching the web version
enum class BlockType {
    CHAT,
    PLACEHOLDER,
    UPDATES,
    CALENDAR,
    WEATHER,
    NEWS,
    BLOG
}

// Breakroom block/widget data class
data class BreakroomBlock(
    val id: Int,
    val block_type: String,
    val title: String? = null,
    val content_id: Int? = null,      // For chat rooms - the room ID
    val content_name: String? = null,  // For chat rooms - the room name
    val x: Int = 0,
    val y: Int = 0,
    val w: Int = 2,
    val h: Int = 2
) {
    val blockType: BlockType
        get() = when (block_type.lowercase()) {
            "chat" -> BlockType.CHAT
            "updates" -> BlockType.UPDATES
            "calendar" -> BlockType.CALENDAR
            "weather" -> BlockType.WEATHER
            "news" -> BlockType.NEWS
            "blog" -> BlockType.BLOG
            else -> BlockType.PLACEHOLDER
        }

    val displayTitle: String
        get() = title ?: when (blockType) {
            BlockType.CHAT -> content_name ?: "Chat"
            BlockType.UPDATES -> "Breakroom Updates"
            BlockType.CALENDAR -> "Calendar"
            BlockType.WEATHER -> "Weather"
            BlockType.NEWS -> "News"
            BlockType.BLOG -> "Blog Posts"
            BlockType.PLACEHOLDER -> "Placeholder"
        }
}

// API Response wrappers
data class BreakroomLayoutResponse(
    val blocks: List<BreakroomBlock>
)

data class BreakroomUpdatesResponse(
    val updates: List<BreakroomUpdate>
)

data class BreakroomUpdate(
    val id: Int,
    val title: String? = null,
    val content: String? = null,
    val summary: String? = null,
    val created_at: String
) {
    // Use summary if available, fall back to content or title
    val displayText: String
        get() = summary ?: content ?: title ?: ""
}

// Request for adding a new block
data class AddBlockRequest(
    val block_type: String,
    val content_id: Int? = null,
    val title: String? = null,
    val w: Int = 2,
    val h: Int = 2
)

// Request for updating layout positions
data class UpdateLayoutRequest(
    val blocks: List<BlockPosition>
)

data class BlockPosition(
    val id: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

// Result class for breakroom operations
sealed class BreakroomResult<out T> {
    data class Success<T>(val data: T) : BreakroomResult<T>()
    data class Error(val message: String) : BreakroomResult<Nothing>()
    data object AuthenticationError : BreakroomResult<Nothing>()
}

// News models
data class NewsResponse(
    val title: String? = null,
    val items: List<NewsItem>
)

data class NewsItem(
    val title: String,
    val description: String? = null,
    val link: String? = null,
    val source: String? = null,
    val pubDate: String? = null
)

// Blog models
data class BlogFeedResponse(
    val posts: List<BlogPost>
)

data class BlogPost(
    val id: Int,
    val title: String,
    val content: String? = null,
    val is_published: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
    val author_id: Int? = null,
    val author_handle: String? = null,
    val author_first_name: String? = null,
    val author_last_name: String? = null,
    val author_photo: String? = null
) {
    val isPublished: Boolean
        get() = is_published == 1

    val authorName: String
        get() {
            val firstName = author_first_name ?: ""
            val lastName = author_last_name ?: ""
            val fullName = "$firstName $lastName".trim()
            return fullName.ifEmpty { author_handle ?: "Unknown" }
        }

    // Strip HTML tags for preview text
    val contentPreview: String
        get() = content?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
}

// Blog management models
data class BlogPostsResponse(
    val posts: List<BlogPost>
)

data class BlogPostResponse(
    val post: BlogPost
)

data class BlogViewResponse(
    val post: BlogPost
)

data class CreateBlogPostRequest(
    val title: String,
    val content: String,
    val isPublished: Boolean = false
)

data class UpdateBlogPostRequest(
    val title: String,
    val content: String,
    val isPublished: Boolean = false
)

// Friends models
data class Friend(
    val id: Int,
    val handle: String,
    val first_name: String? = null,
    val last_name: String? = null,
    val email: String? = null,
    val profile_photo: String? = null,
    val friends_since: String? = null
) {
    val displayName: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            val fullName = "$firstName $lastName".trim()
            return fullName.ifEmpty { handle }
        }

    val initials: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            return if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                "${firstName.first()}${lastName.first()}".uppercase()
            } else if (firstName.isNotEmpty()) {
                firstName.take(2).uppercase()
            } else {
                handle.take(2).uppercase()
            }
        }
}

data class FriendRequest(
    val id: Int,
    val handle: String,
    val first_name: String? = null,
    val last_name: String? = null,
    val email: String? = null,
    val profile_photo: String? = null,
    val requested_at: String? = null,
    val sent_at: String? = null
) {
    val displayName: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            val fullName = "$firstName $lastName".trim()
            return fullName.ifEmpty { handle }
        }

    val initials: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            return if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                "${firstName.first()}${lastName.first()}".uppercase()
            } else if (firstName.isNotEmpty()) {
                firstName.take(2).uppercase()
            } else {
                handle.take(2).uppercase()
            }
        }
}

data class BlockedUser(
    val id: Int,
    val handle: String,
    val first_name: String? = null,
    val last_name: String? = null,
    val blocked_at: String? = null
) {
    val displayName: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            val fullName = "$firstName $lastName".trim()
            return fullName.ifEmpty { handle }
        }

    val initials: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            return if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                "${firstName.first()}${lastName.first()}".uppercase()
            } else if (firstName.isNotEmpty()) {
                firstName.take(2).uppercase()
            } else {
                handle.take(2).uppercase()
            }
        }
}

data class SearchUser(
    val id: Int,
    val handle: String,
    val first_name: String? = null,
    val last_name: String? = null,
    val email: String? = null,
    val profile_photo: String? = null
) {
    val displayName: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            val fullName = "$firstName $lastName".trim()
            return fullName.ifEmpty { handle }
        }

    val initials: String
        get() {
            val firstName = first_name ?: ""
            val lastName = last_name ?: ""
            return if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                "${firstName.first()}${lastName.first()}".uppercase()
            } else if (firstName.isNotEmpty()) {
                firstName.take(2).uppercase()
            } else {
                handle.take(2).uppercase()
            }
        }
}

// Friends API responses
data class FriendsListResponse(
    val friends: List<Friend>
)

data class FriendRequestsResponse(
    val requests: List<FriendRequest>
)

data class SentRequestsResponse(
    val requests: List<FriendRequest>
)

data class BlockedUsersResponse(
    val blocked: List<BlockedUser>
)

data class AllUsersResponse(
    val users: List<SearchUser>
)

data class FriendActionResponse(
    val message: String
)

// Profile request/response classes (UserProfile, Skill, UserJob, ProfileResponse are in WeatherModels.kt)
data class UpdateProfileRequest(
    val firstName: String,
    val lastName: String,
    val bio: String?,
    val workBio: String?
)

data class UpdateLocationRequest(
    val city: String
)

data class UpdateTimezoneRequest(
    val timezone: String
)

data class AddSkillRequest(
    val name: String
)

data class AddJobRequest(
    val title: String,
    val company: String,
    val location: String? = null,
    val startDate: String,
    val endDate: String? = null,
    val isCurrent: Boolean = false,
    val description: String? = null
)

data class SkillResponse(
    val id: Int,
    val name: String
)

data class SkillsSearchResponse(
    val skills: List<Skill>
)

data class JobResponse(
    val job: UserJob
)

data class PhotoUploadResponse(
    val photo_path: String
)

data class ProfileActionResponse(
    val message: String
)

// Employment/Position models
data class Position(
    val id: Int,
    val company_id: Int,
    val company_name: String,
    val company_city: String? = null,
    val company_state: String? = null,
    val title: String,
    val description: String? = null,
    val requirements: String? = null,
    val benefits: String? = null,
    val department: String? = null,
    val employment_type: String? = null,  // full-time, part-time, contract, internship, temporary
    val location_type: String? = null,     // remote, onsite, hybrid
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val pay_type: String? = null,          // hourly, salary
    val pay_rate_min: Double? = null,
    val pay_rate_max: Double? = null,
    val status: String? = null,            // open, closed, filled
    val created_at: String? = null
) {
    val locationString: String
        get() {
            val parts = mutableListOf<String>()
            if (!city.isNullOrBlank()) parts.add(city)
            if (!state.isNullOrBlank()) parts.add(state)
            if (parts.isEmpty() && !company_city.isNullOrBlank()) parts.add(company_city)
            if (parts.isEmpty() && !company_state.isNullOrBlank()) parts.add(company_state)
            return parts.joinToString(", ").ifEmpty { "Location not specified" }
        }

    val formattedPay: String
        get() {
            if (pay_rate_min == null && pay_rate_max == null) return "Negotiable"

            fun formatNum(n: Double): String {
                return if (n >= 1000) {
                    val k = n / 1000
                    if (k == k.toLong().toDouble()) "\$${k.toLong()}k" else "\$${String.format("%.1f", k)}k"
                } else {
                    "\$${n.toInt()}"
                }
            }

            val typeLabel = when (pay_type) {
                "hourly" -> "/hr"
                "salary" -> "/yr"
                else -> ""
            }

            return when {
                pay_rate_min != null && pay_rate_max != null ->
                    "${formatNum(pay_rate_min)} - ${formatNum(pay_rate_max)}$typeLabel"
                pay_rate_min != null ->
                    "${formatNum(pay_rate_min)}+$typeLabel"
                else ->
                    "Up to ${formatNum(pay_rate_max!!)}$typeLabel"
            }
        }

    val formattedEmploymentType: String
        get() = employment_type?.split("-")?.joinToString("-") {
            it.replaceFirstChar { c -> c.uppercase() }
        } ?: ""

    val formattedLocationType: String
        get() = location_type?.replaceFirstChar { it.uppercase() } ?: ""

    val descriptionPreview: String
        get() = description?.take(150)?.let { if (description.length > 150) "$it..." else it } ?: ""
}

data class PositionsResponse(
    val positions: List<Position>
)

data class CreatePositionRequest(
    val company_id: Int,
    val title: String,
    val description: String? = null,
    val requirements: String? = null,
    val benefits: String? = null,
    val department: String? = null,
    val employment_type: String? = null,
    val location_type: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pay_type: String? = null,
    val pay_rate_min: Double? = null,
    val pay_rate_max: Double? = null
)

data class CreatePositionResponse(
    val position: Position
)

data class DeletePositionResponse(
    val message: String
)

data class UpdatePositionRequest(
    val title: String? = null,
    val description: String? = null,
    val requirements: String? = null,
    val benefits: String? = null,
    val department: String? = null,
    val employment_type: String? = null,
    val location_type: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val pay_type: String? = null,
    val pay_rate_min: Double? = null,
    val pay_rate_max: Double? = null,
    val status: String? = null
)

data class UpdatePositionResponse(
    val position: Position
)

// HelpDesk/Ticket models
data class Ticket(
    val id: Int,
    val company_id: Int,
    val creator_id: Int,
    val creator_handle: String? = null,
    val creator_first_name: String? = null,
    val creator_last_name: String? = null,
    val assigned_to: Int? = null,
    val assignee_id: Int? = null,
    val assignee_handle: String? = null,
    val assignee_first_name: String? = null,
    val assignee_last_name: String? = null,
    val title: String,
    val description: String? = null,
    val status: String = "backlog",  // backlog, on-deck, in_progress, resolved, closed
    val priority: String = "medium",  // low, medium, high, urgent
    val created_at: String? = null,
    val updated_at: String? = null,
    val resolved_at: String? = null
) {
    val creatorName: String
        get() {
            val firstName = creator_first_name ?: ""
            val lastName = creator_last_name ?: ""
            val fullName = "$firstName $lastName".trim()
            return fullName.ifEmpty { creator_handle ?: "Unknown" }
        }

    val assigneeName: String?
        get() {
            val id = assignee_id ?: assigned_to ?: return null
            val firstName = assignee_first_name ?: ""
            val lastName = assignee_last_name ?: ""
            val fullName = "$firstName $lastName".trim()
            return fullName.ifEmpty { assignee_handle }
        }

    val formattedStatus: String
        get() = status.replace("_", " ").replace("-", " ").split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    val formattedPriority: String
        get() = priority.replaceFirstChar { it.uppercase() }

    val isOpen: Boolean
        get() = status in listOf("backlog", "on-deck", "in_progress")

    val isClosed: Boolean
        get() = status in listOf("resolved", "closed")
}

data class HelpDeskCompany(
    val id: Int,
    val name: String
)

data class HelpDeskCompanyResponse(
    val company: HelpDeskCompany
)

data class TicketsResponse(
    val tickets: List<Ticket>
)

data class TicketResponse(
    val ticket: Ticket
)

data class CreateTicketRequest(
    val company_id: Int,
    val title: String,
    val description: String?,
    val priority: String
)

data class UpdateTicketRequest(
    val status: String? = null,
    val assigned_to: Int? = null
)

// Company models
data class Company(
    val id: Int,
    val name: String,
    val description: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val postal_code: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    // For my companies list
    val title: String? = null,
    val is_owner: Int = 0,
    val is_admin: Int = 0
) {
    val isOwner: Boolean
        get() = is_owner == 1

    val isAdmin: Boolean
        get() = is_admin == 1

    val locationString: String
        get() {
            val parts = mutableListOf<String>()
            if (!city.isNullOrBlank()) parts.add(city)
            if (!state.isNullOrBlank()) parts.add(state)
            if (parts.isEmpty() && !country.isNullOrBlank()) parts.add(country)
            return parts.joinToString(", ").ifEmpty { "Location not specified" }
        }
}

data class CompanySearchResponse(
    val companies: List<Company>
)

data class MyCompaniesResponse(
    val companies: List<Company>? = null,
    // Alternative field name the API might use
    val data: List<Company>? = null
) {
    // Return whichever list is populated
    fun getCompanyList(): List<Company> = companies ?: data ?: emptyList()
}

data class CompanyResponse(
    val company: Company
)

data class CreateCompanyRequest(
    val name: String,
    val description: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val postal_code: String?,
    val phone: String?,
    val email: String?,
    val website: String?,
    val employee_title: String
)

// Company Employee model
data class CompanyEmployee(
    val id: Int,
    val user_id: Int,
    val handle: String,
    val first_name: String,
    val last_name: String,
    val title: String? = null,
    val department: String? = null,
    val hire_date: String? = null,
    val photo_url: String? = null,
    val photo_path: String? = null,
    val is_owner: Int = 0,
    val is_admin: Int = 0,
    val status: String = "active"
) {
    val isOwner: Boolean get() = is_owner == 1
    val isAdmin: Boolean get() = is_admin == 1
    val fullName: String get() = "$first_name $last_name"
    val displayName: String get() = fullName.ifBlank { handle }
    val initials: String get() = "${first_name.firstOrNull() ?: ""}${last_name.firstOrNull() ?: ""}".uppercase()
}

data class CompanyEmployeesResponse(
    val employees: List<CompanyEmployee>? = null,
    val data: List<CompanyEmployee>? = null
) {
    fun getEmployeeList(): List<CompanyEmployee> = employees ?: data ?: emptyList()
}

data class UpdateEmployeeRequest(
    val title: String?,
    val department: String?,
    val hire_date: String?,
    val is_admin: Int
)

data class UpdateEmployeeResponse(
    val employee: CompanyEmployee? = null,
    val message: String? = null
)

// Project models
data class Project(
    val id: Int,
    val title: String,
    val description: String? = null,
    val company_id: Int = 0,
    val company_name: String? = null,
    val is_default: Int = 0,
    val is_active: Int = 1,
    val is_public: Int = 0,
    val ticket_count: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null
) {
    val isDefault: Boolean get() = is_default == 1
    val isActive: Boolean get() = is_active == 1
    val isPublic: Boolean get() = is_public == 1

    val ticketCountText: String
        get() = if (ticket_count == 1) "1 ticket" else "$ticket_count tickets"
}

data class ProjectsResponse(
    val projects: List<Project>
)

data class CreateProjectRequest(
    val company_id: Int,
    val title: String,
    val description: String?,
    val is_public: Boolean = false
)

data class CreateProjectResponse(
    val project: Project
)

data class UpdateProjectRequest(
    val title: String?,
    val description: String?,
    val is_public: Boolean?,
    val is_active: Boolean?
)

data class UpdateProjectResponse(
    val project: Project
)

data class ProjectWithTicketsResponse(
    val project: Project,
    val tickets: List<Ticket>
)
