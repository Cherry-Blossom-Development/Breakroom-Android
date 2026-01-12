package com.example.breakroom.ui.widgets

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.breakroom.data.models.NewsItem
import com.example.breakroom.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// News accent color (red)
private val NewsAccentColor = Color(0xFFD32F2F)

@Composable
fun NewsWidget(
    token: String,
    modifier: Modifier = Modifier
) {
    var newsState by remember { mutableStateOf<NewsState>(NewsState.Loading) }
    val scope = rememberCoroutineScope()

    // Load news on mount
    LaunchedEffect(Unit) {
        scope.launch {
            newsState = fetchNews(token)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        when (val state = newsState) {
            is NewsState.Loading -> {
                LoadingState()
            }
            is NewsState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = {
                        newsState = NewsState.Loading
                        scope.launch {
                            newsState = fetchNews(token)
                        }
                    }
                )
            }
            is NewsState.Success -> {
                if (state.items.isEmpty()) {
                    EmptyState()
                } else {
                    NewsList(items = state.items)
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
            color = NewsAccentColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Loading news...",
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
                    color = NewsAccentColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = NewsAccentColor,
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
                containerColor = NewsAccentColor
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
            text = "No news available",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewsList(items: List<NewsItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            NewsItemCard(item = item)
        }
    }
}

@Composable
private fun NewsItemCard(item: NewsItem) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                item.link?.let { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Ignore if can't open URL
                    }
                }
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
                    .background(NewsAccentColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Header row with source and time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.source?.let { source ->
                        Text(
                            text = source.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NewsAccentColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                    item.pubDate?.let { date ->
                        Text(
                            text = formatTime(date),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Description
                item.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = desc,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// State sealed class
private sealed class NewsState {
    object Loading : NewsState()
    data class Success(val items: List<NewsItem>) : NewsState()
    data class Error(val message: String) : NewsState()
}

private suspend fun fetchNews(token: String): NewsState {
    return try {
        val response = RetrofitClient.breakroomApiService.getNews(
            token = "Bearer $token"
        )

        if (response.isSuccessful) {
            val items = response.body()?.items ?: emptyList()
            NewsState.Success(items)
        } else {
            NewsState.Error("Failed to load news")
        }
    } catch (e: Exception) {
        NewsState.Error(e.message ?: "Unknown error")
    }
}

private fun formatTime(dateStr: String): String {
    return try {
        // Try parsing ISO format
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")

        val date = try {
            inputFormat.parse(dateStr)
        } catch (e: Exception) {
            // Try alternative format (RSS format)
            val rssFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            rssFormat.parse(dateStr)
        } ?: return dateStr

        val now = System.currentTimeMillis()
        val diffMs = now - date.time
        val diffMins = (diffMs / (1000 * 60)).toInt()
        val diffHours = (diffMs / (1000 * 60 * 60)).toInt()

        when {
            diffMins < 60 -> "${diffMins}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            else -> {
                val outputFormat = SimpleDateFormat("MMM d", Locale.US)
                outputFormat.format(date)
            }
        }
    } catch (e: Exception) {
        dateStr
    }
}
