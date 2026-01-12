package com.example.breakroom.data.models

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
    val is_published: Boolean = false,
    val created_at: String? = null,
    val updated_at: String? = null,
    val author_id: Int? = null,
    val author_handle: String? = null,
    val author_first_name: String? = null,
    val author_last_name: String? = null,
    val author_photo: String? = null
) {
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
