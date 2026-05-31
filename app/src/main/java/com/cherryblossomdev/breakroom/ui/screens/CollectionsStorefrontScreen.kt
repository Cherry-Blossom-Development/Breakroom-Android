package com.cherryblossomdev.breakroom.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.CollectionsRepository
import com.cherryblossomdev.breakroom.data.models.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val SLUG_RE = Regex("^[a-z0-9][a-z0-9-]{1,58}[a-z0-9]$|^[a-z0-9]{3}$")

private val DEFAULT_SECTIONS = listOf(
    StorefrontSection(id = "content", type = "content", visible = true),
    StorefrontSection(id = "collections", type = "collections", visible = true, title = "My Collections")
)

// ==================== ViewModel ====================

data class CollectionsStorefrontUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isCheckingUrl: Boolean = false,
    val storeUrl: String = "",
    val urlAvailable: Boolean? = null,
    val urlReason: String = "",
    val pageTitle: String = "",
    val contentText: String = "",
    val contentVisible: Boolean = true,
    val collectionsVisible: Boolean = true,
    val collectionsHeading: String = "My Collections",
    val savedAt: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

class CollectionsStorefrontViewModel(
    private val repo: CollectionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsStorefrontUiState())
    val uiState: StateFlow<CollectionsStorefrontUiState> = _uiState.asStateFlow()

    private var urlCheckJob: Job? = null

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repo.getStorefront()) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    val sections = data?.settings?.sections ?: DEFAULT_SECTIONS
                    val contentSection = sections.find { it.type == "content" }
                    val collectionsSection = sections.find { it.type == "collections" }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        storeUrl = data?.store_url ?: "",
                        urlAvailable = if (!data?.store_url.isNullOrBlank()) true else null,
                        pageTitle = data?.page_title ?: "",
                        contentText = stripHtml(data?.content ?: ""),
                        contentVisible = contentSection?.visible ?: true,
                        collectionsVisible = collectionsSection?.visible ?: true,
                        collectionsHeading = collectionsSection?.title ?: "My Collections",
                        savedAt = formatDate(data?.updated_at)
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun onStoreUrlChange(value: String) {
        val cleaned = value.lowercase().replace(Regex("[^a-z0-9-]"), "")
        _uiState.value = _uiState.value.copy(
            storeUrl = cleaned,
            urlAvailable = null,
            urlReason = ""
        )
        urlCheckJob?.cancel()
        if (cleaned.length >= 3) {
            urlCheckJob = viewModelScope.launch {
                delay(500)
                checkUrl(cleaned)
            }
        }
    }

    private suspend fun checkUrl(slug: String) {
        _uiState.value = _uiState.value.copy(isCheckingUrl = true)
        when (val result = repo.checkStoreUrl(slug)) {
            is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                isCheckingUrl = false,
                urlAvailable = result.data.available,
                urlReason = result.data.reason ?: ""
            )
            else -> _uiState.value = _uiState.value.copy(isCheckingUrl = false)
        }
    }

    fun onPageTitleChange(v: String) { _uiState.value = _uiState.value.copy(pageTitle = v) }
    fun onContentTextChange(v: String) { _uiState.value = _uiState.value.copy(contentText = v) }
    fun onContentVisibleChange(v: Boolean) { _uiState.value = _uiState.value.copy(contentVisible = v) }
    fun onCollectionsVisibleChange(v: Boolean) { _uiState.value = _uiState.value.copy(collectionsVisible = v) }
    fun onCollectionsHeadingChange(v: String) { _uiState.value = _uiState.value.copy(collectionsHeading = v) }

    val canSave: Boolean get() {
        val s = _uiState.value
        if (s.isSaving || s.isCheckingUrl) return false
        if (s.storeUrl.isBlank()) return false
        if (s.urlAvailable == false) return false
        return true
    }

    fun save() {
        if (!canSave) return
        val s = _uiState.value
        val sections = listOf(
            StorefrontSection(id = "content", type = "content", visible = s.contentVisible),
            StorefrontSection(id = "collections", type = "collections",
                visible = s.collectionsVisible, title = s.collectionsHeading)
        )
        val request = StorefrontSaveRequest(
            store_url = s.storeUrl.takeIf { it.isNotBlank() },
            page_title = s.pageTitle,
            content = if (s.contentText.isBlank()) "" else "<p>${s.contentText.replace("\n", "<br>")}</p>",
            settings = StorefrontSettings(sections)
        )
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            when (val result = repo.saveStorefront(request)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    savedAt = formatDate(null),
                    successMessage = "Saved"
                )
                is BreakroomResult.Error -> {
                    val isUrlConflict = result.message.contains("taken", ignoreCase = true)
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = result.message,
                        urlAvailable = if (isUrlConflict) false else s.urlAvailable
                    )
                }
                else -> _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}

private fun stripHtml(html: String): String =
    html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("&amp;"), "&")
        .replace(Regex("&lt;"), "<")
        .replace(Regex("&gt;"), ">")
        .replace(Regex("&nbsp;"), " ")
        .trim()

private fun formatDate(iso: String?): String {
    if (iso == null) {
        val now = java.util.Date()
        val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        return fmt.format(now)
    }
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(iso.take(19)) ?: return ""
        val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
        fmt.format(date)
    } catch (e: Exception) { "" }
}

// ==================== Screen ====================

@Composable
fun CollectionsStorefrontScreen(
    viewModel: CollectionsStorefrontViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbar.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // ── Store URL ──
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Store URL", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "Your store will be publicly accessible at prosaurus.com/store/[url]",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = uiState.storeUrl,
                    onValueChange = viewModel::onStoreUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("my-store") },
                    prefix = { Text("store/", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    trailingIcon = {
                        if (uiState.storeUrl.isNotBlank() && !uiState.isCheckingUrl) {
                            if (uiState.urlAvailable == true) {
                                Text("✓", color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 12.dp))
                            } else if (uiState.urlAvailable == false) {
                                Text("✗", color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 12.dp))
                            }
                        }
                    },
                    supportingText = when {
                        uiState.isCheckingUrl -> { { Text("Checking…", fontSize = 12.sp) } }
                        uiState.urlAvailable == false -> { {
                            Text(uiState.urlReason.ifBlank { "Not available" },
                                color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        } }
                        uiState.urlAvailable == true -> { {
                            Text("Available", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        } }
                        else -> null
                    }
                )
                if (uiState.storeUrl.isNotBlank() && uiState.urlAvailable == true) {
                    TextButton(
                        onClick = {
                            val url = "https://www.prosaurus.com/store/${uiState.storeUrl}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("View Store ↗", fontSize = 13.sp)
                    }
                }
            }

            // ── Page Title ──
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Page Title", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Shown as the main heading on your public store.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = uiState.pageTitle,
                    onValueChange = viewModel::onPageTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. My Art Store") },
                    singleLine = true
                )
            }

            // ── Page Sections ──
            Text("Page Sections", fontWeight = FontWeight.Bold, fontSize = 14.sp)

            // Content section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Content", fontWeight = FontWeight.SemiBold)
                            Text("A free-form text block shown above your collections.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = uiState.contentVisible,
                            onCheckedChange = viewModel::onContentVisibleChange
                        )
                    }
                    if (uiState.contentVisible) {
                        OutlinedTextField(
                            value = uiState.contentText,
                            onValueChange = viewModel::onContentTextChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Write something about your store…") },
                            minLines = 4,
                            maxLines = 10
                        )
                        Text("Rich text formatting (headings, bold, lists) is available in the web app.",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Collections section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Collections", fontWeight = FontWeight.SemiBold)
                            Text("Displays all your collections on the public store.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = uiState.collectionsVisible,
                            onCheckedChange = viewModel::onCollectionsVisibleChange
                        )
                    }
                    if (uiState.collectionsVisible) {
                        OutlinedTextField(
                            value = uiState.collectionsHeading,
                            onValueChange = viewModel::onCollectionsHeadingChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Section heading") },
                            singleLine = true
                        )
                    }
                }
            }

            // ── Save row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (uiState.savedAt.isNotBlank()) {
                    Text("Saved ${uiState.savedAt}", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Button(
                    onClick = viewModel::save,
                    enabled = viewModel.canSave
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…")
                    } else {
                        Text("Save Storefront")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
