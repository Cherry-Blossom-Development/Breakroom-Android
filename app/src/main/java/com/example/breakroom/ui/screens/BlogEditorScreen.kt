package com.example.breakroom.ui.screens

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BlogEditorScreen(
    viewModel: BlogViewModel,
    postId: Int?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var title by remember { mutableStateOf("") }
    var isPublished by remember { mutableStateOf(false) }
    var hasLoadedPost by remember { mutableStateOf(false) }

    // Hold a reference to the WebView so we can call evaluateJavascript from buttons
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Flag set by the JS interface on the background thread, read on main thread
    var imagePickerRequested by remember { mutableStateOf(false) }

    val isEditing = postId != null

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            viewModel.uploadImage(selectedUri) { imageUrl ->
                // Build the full URL and inject into the WebView
                val fullUrl = "https://www.prosaurus.com$imageUrl"
                val escaped = fullUrl.replace("'", "\\'")
                Handler(Looper.getMainLooper()).post {
                    webView?.evaluateJavascript("insertImage('$escaped')", null)
                }
            }
        }
    }

    // Launch image picker when JS requests it
    LaunchedEffect(imagePickerRequested) {
        if (imagePickerRequested) {
            imagePickerLauncher.launch("image/*")
            imagePickerRequested = false
        }
    }

    LaunchedEffect(postId) {
        if (postId != null) {
            viewModel.loadPost(postId)
        } else {
            viewModel.clearCurrentPost()
        }
    }

    // Populate fields and editor when post loads
    LaunchedEffect(uiState.currentPost) {
        if (isEditing && uiState.currentPost != null && !hasLoadedPost) {
            uiState.currentPost?.let { post ->
                title = post.title
                isPublished = post.isPublished
                hasLoadedPost = true
                // Inject HTML content into WebView once it's ready
                val html = (post.content ?: "").replace("'", "\\'").replace("\n", "\\n")
                webView?.evaluateJavascript("setContent('$html')", null)
            }
        }
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveState()
            viewModel.clearCurrentPost()
            onNavigateBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSaveState()
            viewModel.clearCurrentPost()
        }
    }

    // Helper: get content from WebView and save
    fun savePost(publish: Boolean) {
        webView?.evaluateJavascript("getContent()") { rawValue ->
            val html = rawValue
                ?.removeSurrounding("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\'", "'")
                ?.replace("\\n", "\n")
                ?.replace("\\u003C", "<")
                ?.replace("\\u003E", ">")
                ?.replace("\\u0026", "&")
                ?: ""
            if (isEditing && postId != null) {
                viewModel.updatePost(postId, title, html, publish)
            } else {
                viewModel.createPost(title, html, publish)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = if (isEditing) "Edit Post" else "New Post",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isEditing && uiState.isLoading && !hasLoadedPost) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Title field
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                placeholder = { Text("Enter post title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Uploading indicator
            if (uiState.isUploadingImage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uploading image...", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Rich text WebView editor
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true

                            addJavascriptInterface(object : Any() {
                                @JavascriptInterface
                                fun onImageRequest() {
                                    Handler(Looper.getMainLooper()).post {
                                        imagePickerRequested = true
                                    }
                                }
                            }, "AndroidInterface")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    // If editing and post already loaded, inject content now
                                    if (hasLoadedPost) {
                                        val html = (uiState.currentPost?.content ?: "")
                                            .replace("'", "\\'").replace("\n", "\\n")
                                        view.evaluateJavascript("setContent('$html')", null)
                                    }
                                }
                            }

                            loadUrl("file:///android_asset/blog_editor.html")
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Error
            uiState.saveError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Publish toggle
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
                Text("Published", style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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

                OutlinedButton(
                    onClick = { savePost(publish = false) },
                    enabled = !uiState.isSaving && title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isSaving && !isPublished) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Save Draft")
                }

                Button(
                    onClick = { savePost(publish = true) },
                    enabled = !uiState.isSaving && title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.isSaving && isPublished) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp), strokeWidth = 2.dp,
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
