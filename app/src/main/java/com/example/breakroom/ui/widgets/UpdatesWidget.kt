package com.example.breakroom.ui.widgets

import androidx.compose.foundation.background
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
import com.example.breakroom.data.models.BreakroomResult
import com.example.breakroom.data.models.BreakroomUpdate
import com.example.breakroom.network.RetrofitClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun UpdatesWidget(
    token: String,
    modifier: Modifier = Modifier
) {
    var updatesState by remember { mutableStateOf<UpdatesState>(UpdatesState.Loading) }
    val scope = rememberCoroutineScope()

    // Load updates on mount
    LaunchedEffect(Unit) {
        scope.launch {
            updatesState = fetchUpdates(token)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        when (val state = updatesState) {
            is UpdatesState.Loading -> {
                LoadingState()
            }
            is UpdatesState.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = {
                        updatesState = UpdatesState.Loading
                        scope.launch {
                            updatesState = fetchUpdates(token)
                        }
                    }
                )
            }
            is UpdatesState.Success -> {
                if (state.updates.isEmpty()) {
                    EmptyState()
                } else {
                    UpdatesList(updates = state.updates)
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
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Loading updates...",
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
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = MaterialTheme.colorScheme.onErrorContainer,
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
                containerColor = MaterialTheme.colorScheme.primary
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
            text = "No updates yet",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UpdatesList(updates: List<BreakroomUpdate>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(updates, key = { it.id }) { update ->
            UpdateItem(update = update)
        }
    }
}

@Composable
private fun UpdateItem(update: BreakroomUpdate) {
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp)),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Date and time row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatDate(update.created_at),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor
                    )
                    Text(
                        text = formatTime(update.created_at),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Content
                Text(
                    text = update.displayText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// State sealed class
private sealed class UpdatesState {
    object Loading : UpdatesState()
    data class Success(val updates: List<BreakroomUpdate>) : UpdatesState()
    data class Error(val message: String) : UpdatesState()
}

private suspend fun fetchUpdates(token: String): UpdatesState {
    return try {
        val response = RetrofitClient.breakroomApiService.getUpdates(
            token = "Bearer $token",
            limit = 20
        )

        if (response.isSuccessful) {
            val updates = response.body()?.updates ?: emptyList()
            UpdatesState.Success(updates)
        } else {
            UpdatesState.Error("Failed to load updates")
        }
    } catch (e: Exception) {
        UpdatesState.Error(e.message ?: "Unknown error")
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
            diffDays < 7 -> "$diffDays days ago"
            else -> {
                val outputFormat = SimpleDateFormat("MMM d", Locale.US)
                outputFormat.format(date)
            }
        }
    } catch (e: Exception) {
        dateStr
    }
}

private fun formatTime(dateStr: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateStr) ?: return ""

        val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
        outputFormat.format(date)
    } catch (e: Exception) {
        ""
    }
}
