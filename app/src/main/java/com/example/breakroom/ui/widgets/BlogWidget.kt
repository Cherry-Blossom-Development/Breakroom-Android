package com.example.breakroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.breakroom.data.models.BlogPost
import com.example.breakroom.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Blog accent color (green)
private val BlogAccentColor = Color(0xFF4CAF50)

@Composable
fun BlogWidget(
    token: String,
    modifier: Modifier = Modifier
) {
    var blogState by remember { mutableStateOf<BlogState>(BlogState.Loading) }
    val scope = rememberCoroutineScope()

    // Load blog posts on mount
    LaunchedEffect(Unit) {
        scope.launch {
            blogState = fetchBlogPosts(token)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        when (val state = blogState) {
            is BlogState.Loading -> {
                LoadingState()
            }
            is BlogState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = {
                        blogState = BlogState.Loading
                        scope.launch {
                            blogState = fetchBlogPosts(token)
                        }
                    }
                )
            }
            is BlogState.Success -> {
                if (state.posts.isEmpty()) {
                    EmptyState()
                } else {
                    BlogPostsList(posts = state.posts.take(5))
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            strokeWidth = 3.dp,
            modifier = Modifier.size(24.dp),
            color = BlogAccentColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Loading posts...",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = BlogAccentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = BlogAccentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = BlogAccentColor
            )
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No blog posts yet",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BlogPostsList(posts: List<BlogPost>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(posts, key = { it.id }) { post ->
            BlogPostCard(post = post)
        }
    }
}

@Composable
private fun BlogPostCard(post: BlogPost) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                // Future: navigate to blog post detail
            },
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(BlogAccentColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Header row with author and date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = post.authorName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BlogAccentColor,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    post.updated_at?.let { date ->
                        Text(
                            text = formatDate(date),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title
                Text(
                    text = post.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Content preview
                if (post.contentPreview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = post.contentPreview,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// State sealed class
private sealed class BlogState {
    object Loading : BlogState()
    data class Success(val posts: List<BlogPost>) : BlogState()
    data class Error(val message: String) : BlogState()
}

private suspend fun fetchBlogPosts(token: String): BlogState {
    return try {
        val response = RetrofitClient.breakroomApiService.getBlogFeed(
            token = "Bearer $token"
        )

        if (response.isSuccessful) {
            val posts = response.body()?.posts ?: emptyList()
            BlogState.Success(posts)
        } else {
            BlogState.Error("Failed to load posts")
        }
    } catch (e: Exception) {
        BlogState.Error(e.message ?: "Unknown error")
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateStr) ?: return dateStr

        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = date }

        // Compare calendar days
        val nowDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val thenDay = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffDays = ((nowDay.timeInMillis - thenDay.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()

        when {
            diffDays == 0 -> "Today"
            diffDays == 1 -> "Yesterday"
            diffDays < 7 -> "${diffDays}d ago"
            else -> {
                val outputFormat = SimpleDateFormat("MMM d", Locale.US)
                outputFormat.format(date)
            }
        }
    } catch (e: Exception) {
        dateStr
    }
}
