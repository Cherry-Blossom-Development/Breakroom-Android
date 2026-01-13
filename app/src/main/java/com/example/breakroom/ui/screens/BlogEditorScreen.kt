package com.example.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.breakroom.data.models.BlogPost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlogEditorScreen(
    viewModel: BlogViewModel,
    postId: Int?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isPublished by remember { mutableStateOf(false) }
    var hasLoadedPost by remember { mutableStateOf(false) }

    val isEditing = postId != null

    // Load existing post if editing
    LaunchedEffect(postId) {
        if (postId != null) {
            viewModel.loadPost(postId)
        } else {
            viewModel.clearCurrentPost()
        }
    }

    // Populate fields when post is loaded
    LaunchedEffect(uiState.currentPost) {
        if (isEditing && uiState.currentPost != null && !hasLoadedPost) {
            uiState.currentPost?.let { post ->
                title = post.title
                content = post.content ?: ""
                isPublished = post.isPublished
                hasLoadedPost = true
            }
        }
    }

    // Navigate back on successful save
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveState()
            viewModel.clearCurrentPost()
            onNavigateBack()
        }
    }

    // Clean up when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSaveState()
            viewModel.clearCurrentPost()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = if (isEditing) "Edit Post" else "New Post",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Show loading while fetching post for editing
        if (isEditing && uiState.isLoading && !hasLoadedPost) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Title TextField
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Enter post title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !uiState.isSaving
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Content TextField (multiline, fill available space)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    placeholder = { Text("Write your blog post content here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    minLines = 8,
                    enabled = !uiState.isSaving
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Published checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isPublished,
                        onCheckedChange = { isPublished = it },
                        enabled = !uiState.isSaving
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Published",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Error message
                uiState.saveError?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel button
                OutlinedButton(
                    onClick = {
                        viewModel.clearSaveState()
                        viewModel.clearCurrentPost()
                        onNavigateBack()
                    },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                // Save Draft button
                OutlinedButton(
                    onClick = {
                        if (isEditing && postId != null) {
                            viewModel.updatePost(postId, title, content, isPublished = false)
                        } else {
                            viewModel.createPost(title, content, isPublished = false)
                        }
                    },
                    enabled = !uiState.isSaving && title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isSaving && !isPublished) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save Draft")
                }

                // Save & Publish button
                Button(
                    onClick = {
                        if (isEditing && postId != null) {
                            viewModel.updatePost(postId, title, content, isPublished = true)
                        } else {
                            viewModel.createPost(title, content, isPublished = true)
                        }
                    },
                    enabled = !uiState.isSaving && title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isSaving && isPublished) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Publish")
                }
            }
        }
    }
}
