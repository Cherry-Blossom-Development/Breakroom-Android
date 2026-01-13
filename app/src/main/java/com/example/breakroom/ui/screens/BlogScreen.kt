package com.example.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.breakroom.data.models.BlogPost
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogScreen(
    viewModel: BlogViewModel,
    onNavigateToEditor: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<BlogPost?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadPosts()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Blog",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { onNavigateToEditor(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Post")
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = uiState.error ?: "Unknown error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadPosts() }) { Text("Retry") }
                    }
                }
                uiState.posts.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "No blog posts yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Click New Post to get started!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.posts, key = { it.id }) { post ->
                            BlogPostCard(post = post, onEdit = { onNavigateToEditor(post.id) }, onDelete = { showDeleteDialog = post })
                        }
                    }
                }
            }
        }
    }

    showDeleteDialog?.let { post ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to delete this post?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePost(post.id); showDeleteDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BlogPostCard(post: BlogPost, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(isPublished = post.isPublished)
                    Text(text = "Updated " + formatBlogDate(post.updated_at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(isPublished: Boolean) {
    val bgColor = if (isPublished) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFFFF9800).copy(alpha = 0.15f)
    val txtColor = if (isPublished) Color(0xFF4CAF50) else Color(0xFFFF9800)
    Box(modifier = Modifier.background(color = bgColor, shape = RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text = if (isPublished) "Published" else "Draft", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = txtColor)
    }
}

private fun formatBlogDate(dateStr: String?): String {
    if (dateStr == null) return ""
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val date = fmt.parse(dateStr) ?: return dateStr
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
    } catch (e: Exception) { dateStr }
}
