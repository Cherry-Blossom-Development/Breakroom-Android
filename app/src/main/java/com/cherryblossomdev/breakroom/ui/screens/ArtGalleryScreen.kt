package com.cherryblossomdev.breakroom.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cherryblossomdev.breakroom.data.GalleryRepository
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

data class ArtGalleryUiState(
    val settings: GallerySettings? = null,
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    // Settings panel
    val showSettingsPanel: Boolean = false,
    val galleryUrlInput: String = "",
    val galleryNameInput: String = "",
    val isSavingSettings: Boolean = false,
    // Upload dialog
    val showUploadDialog: Boolean = false,
    val uploadTitle: String = "",
    val uploadDescription: String = "",
    val uploadPublished: Boolean = false,
    val pendingImageUri: Uri? = null,
    val isUploading: Boolean = false,
    // Edit dialog
    val showEditDialog: Boolean = false,
    val editingArtwork: Artwork? = null,
    val editTitle: String = "",
    val editDescription: String = "",
    val editPublished: Boolean = false,
    val isSavingEdit: Boolean = false,
    // Delete confirmation
    val artworkToDelete: Artwork? = null,
    // Lightbox
    val lightboxArtwork: Artwork? = null
)

class ArtGalleryViewModel(
    private val galleryRepository: GalleryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtGalleryUiState())
    val uiState: StateFlow<ArtGalleryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load settings
            when (val result = galleryRepository.getSettings()) {
                is BreakroomResult.Success -> {
                    val settings = result.data
                    _uiState.value = _uiState.value.copy(
                        settings = settings,
                        galleryUrlInput = settings?.gallery_url ?: "",
                        galleryNameInput = settings?.gallery_name ?: ""
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired")
                }
                else -> { }
            }

            // Load artworks
            when (val result = galleryRepository.getArtworks()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        artworks = result.data,
                        isLoading = false
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired",
                        isLoading = false
                    )
                }
                else -> { }
            }
        }
    }

    // Settings panel
    fun toggleSettingsPanel() {
        val current = _uiState.value
        _uiState.value = current.copy(
            showSettingsPanel = !current.showSettingsPanel,
            galleryUrlInput = current.settings?.gallery_url ?: "",
            galleryNameInput = current.settings?.gallery_name ?: ""
        )
    }

    fun setGalleryUrlInput(value: String) {
        _uiState.value = _uiState.value.copy(galleryUrlInput = value)
    }

    fun setGalleryNameInput(value: String) {
        _uiState.value = _uiState.value.copy(galleryNameInput = value)
    }

    fun saveSettings() {
        val state = _uiState.value
        val url = state.galleryUrlInput.trim()
        val name = state.galleryNameInput.trim().ifEmpty { "$url's Gallery" }
        if (url.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingSettings = true, error = null)
            val result = if (state.settings == null) {
                galleryRepository.createSettings(url, name)
            } else {
                galleryRepository.updateSettings(url, name)
            }
            when (result) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        settings = result.data,
                        galleryUrlInput = result.data.gallery_url,
                        galleryNameInput = result.data.gallery_name,
                        isSavingSettings = false,
                        showSettingsPanel = false,
                        successMessage = "Gallery settings saved"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingSettings = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isSavingSettings = false,
                        error = "Session expired"
                    )
                }
                else -> { }
            }
        }
    }

    // Upload dialog
    fun showUploadDialog() {
        _uiState.value = _uiState.value.copy(
            showUploadDialog = true,
            uploadTitle = "",
            uploadDescription = "",
            uploadPublished = false,
            pendingImageUri = null
        )
    }

    fun hideUploadDialog() {
        _uiState.value = _uiState.value.copy(showUploadDialog = false)
    }

    fun setUploadTitle(value: String) {
        _uiState.value = _uiState.value.copy(uploadTitle = value)
    }

    fun setUploadDescription(value: String) {
        _uiState.value = _uiState.value.copy(uploadDescription = value)
    }

    fun setUploadPublished(value: Boolean) {
        _uiState.value = _uiState.value.copy(uploadPublished = value)
    }

    fun setPendingImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(pendingImageUri = uri)
    }

    fun uploadArtwork() {
        val state = _uiState.value
        val uri = state.pendingImageUri ?: return
        val title = state.uploadTitle.trim()
        if (title.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            when (val result = galleryRepository.uploadArtwork(
                imageUri = uri,
                title = title,
                description = state.uploadDescription.trim().ifEmpty { null },
                isPublished = state.uploadPublished
            )) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        artworks = listOf(result.data) + _uiState.value.artworks,
                        isUploading = false,
                        showUploadDialog = false,
                        successMessage = "Artwork uploaded"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = result.message
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "Session expired"
                    )
                }
                else -> { }
            }
        }
    }

    // Edit dialog
    fun showEditDialog(artwork: Artwork) {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            editingArtwork = artwork,
            editTitle = artwork.title,
            editDescription = artwork.description ?: "",
            editPublished = artwork.isPublished
        )
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(showEditDialog = false, editingArtwork = null)
    }

    fun setEditTitle(value: String) {
        _uiState.value = _uiState.value.copy(editTitle = value)
    }

    fun setEditDescription(value: String) {
        _uiState.value = _uiState.value.copy(editDescription = value)
    }

    fun setEditPublished(value: Boolean) {
        _uiState.value = _uiState.value.copy(editPublished = value)
    }

    fun saveEdit() {
        val state = _uiState.value
        val artwork = state.editingArtwork ?: return
        val title = state.editTitle.trim()
        if (title.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingEdit = true, error = null)
            when (val result = galleryRepository.updateArtwork(
                artworkId = artwork.id,
                title = title,
                description = state.editDescription.trim().ifEmpty { null },
                isPublished = state.editPublished
            )) {
                is BreakroomResult.Success -> {
                    val updated = result.data
                    _uiState.value = _uiState.value.copy(
                        artworks = _uiState.value.artworks.map { if (it.id == updated.id) updated else it },
                        isSavingEdit = false,
                        showEditDialog = false,
                        editingArtwork = null,
                        successMessage = "Artwork updated"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(isSavingEdit = false, error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(isSavingEdit = false, error = "Session expired")
                }
                else -> { }
            }
        }
    }

    // Delete
    fun confirmDelete(artwork: Artwork) {
        _uiState.value = _uiState.value.copy(artworkToDelete = artwork)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(artworkToDelete = null)
    }

    fun deleteArtwork() {
        val artwork = _uiState.value.artworkToDelete ?: return
        viewModelScope.launch {
            when (val result = galleryRepository.deleteArtwork(artwork.id)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        artworks = _uiState.value.artworks.filter { it.id != artwork.id },
                        artworkToDelete = null,
                        successMessage = "Artwork deleted"
                    )
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(artworkToDelete = null, error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(artworkToDelete = null, error = "Session expired")
                }
                else -> { }
            }
        }
    }

    // Lightbox
    fun openLightbox(artwork: Artwork) {
        _uiState.value = _uiState.value.copy(lightboxArtwork = artwork)
    }

    fun closeLightbox() {
        _uiState.value = _uiState.value.copy(lightboxArtwork = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

// ==================== Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtGalleryScreen(
    viewModel: ArtGalleryViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success/error messages in snackbar
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showUploadDialog() }) {
                Icon(Icons.Filled.Add, contentDescription = "Upload artwork")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header card
                    GalleryHeaderCard(
                        settings = state.settings,
                        artworkCount = state.artworks.size,
                        publishedCount = state.artworks.count { it.isPublished },
                        onSettingsClick = { viewModel.toggleSettingsPanel() },
                        onCopyLink = { link ->
                            clipboardManager.setText(AnnotatedString(link))
                        }
                    )

                    // Settings panel (inline expand)
                    if (state.showSettingsPanel) {
                        GallerySettingsPanel(
                            galleryUrl = state.galleryUrlInput,
                            galleryName = state.galleryNameInput,
                            isSaving = state.isSavingSettings,
                            hasExistingSettings = state.settings != null,
                            onUrlChange = viewModel::setGalleryUrlInput,
                            onNameChange = viewModel::setGalleryNameInput,
                            onSave = viewModel::saveSettings,
                            onDismiss = { viewModel.toggleSettingsPanel() }
                        )
                    }

                    // Artwork grid
                    if (state.artworks.isEmpty()) {
                        EmptyGalleryMessage(
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.artworks, key = { it.id }) { artwork ->
                                ArtworkCard(
                                    artwork = artwork,
                                    onClick = { viewModel.openLightbox(artwork) },
                                    onEdit = { viewModel.showEditDialog(artwork) },
                                    onDelete = { viewModel.confirmDelete(artwork) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Upload dialog
    if (state.showUploadDialog) {
        UploadArtworkDialog(
            title = state.uploadTitle,
            description = state.uploadDescription,
            isPublished = state.uploadPublished,
            pendingImageUri = state.pendingImageUri,
            isUploading = state.isUploading,
            onTitleChange = viewModel::setUploadTitle,
            onDescriptionChange = viewModel::setUploadDescription,
            onPublishedChange = viewModel::setUploadPublished,
            onImageSelected = viewModel::setPendingImageUri,
            onConfirm = viewModel::uploadArtwork,
            onDismiss = viewModel::hideUploadDialog
        )
    }

    // Edit dialog
    if (state.showEditDialog) {
        EditArtworkDialog(
            title = state.editTitle,
            description = state.editDescription,
            isPublished = state.editPublished,
            isSaving = state.isSavingEdit,
            onTitleChange = viewModel::setEditTitle,
            onDescriptionChange = viewModel::setEditDescription,
            onPublishedChange = viewModel::setEditPublished,
            onConfirm = viewModel::saveEdit,
            onDismiss = viewModel::hideEditDialog
        )
    }

    // Delete confirmation
    state.artworkToDelete?.let { artwork ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete Artwork") },
            text = { Text("Delete \"${artwork.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteArtwork,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            }
        )
    }

    // Lightbox
    state.lightboxArtwork?.let { artwork ->
        ArtworkLightbox(
            artwork = artwork,
            onDismiss = viewModel::closeLightbox
        )
    }
}

// ==================== Sub-composables ====================

@Composable
private fun GalleryHeaderCard(
    settings: GallerySettings?,
    artworkCount: Int,
    publishedCount: Int,
    onSettingsClick: () -> Unit,
    onCopyLink: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = settings?.gallery_name ?: "My Art Gallery",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Gallery settings")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "$artworkCount artwork${if (artworkCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$publishedCount published",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (settings != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val publicLink = "https://www.prosaurus.com/g/${settings.gallery_url}"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onCopyLink(publicLink) }
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "prosaurus.com/g/${settings.gallery_url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun GallerySettingsPanel(
    galleryUrl: String,
    galleryName: String,
    isSaving: Boolean,
    hasExistingSettings: Boolean,
    onUrlChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (hasExistingSettings) "Edit Gallery Settings" else "Set Up Your Gallery",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = galleryName,
                onValueChange = onNameChange,
                label = { Text("Gallery Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = galleryUrl,
                onValueChange = { onUrlChange(it.lowercase().replace(" ", "-")) },
                label = { Text("Public URL") },
                prefix = { Text("prosaurus.com/g/", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    enabled = galleryUrl.isNotBlank() && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ArtworkCard(
    artwork: Artwork,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box {
            Column(modifier = Modifier.clickable { onClick() }) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("${RetrofitClient.BASE_URL}uploads/${artwork.image_path}")
                        .crossfade(true)
                        .build(),
                    contentDescription = artwork.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                )

                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(
                        text = artwork.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!artwork.isPublished) {
                        Text(
                            text = "Draft",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            // Action buttons overlay (top-right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                SmallIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Edit",
                    onClick = onEdit,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                SmallIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = tint
        )
    }
}

@Composable
private fun EmptyGalleryMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No artwork yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Tap the + button to upload your first piece",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun UploadArtworkDialog(
    title: String,
    description: String,
    isPublished: Boolean,
    pendingImageUri: Uri?,
    isUploading: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPublishedChange: (Boolean) -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> onImageSelected(uri) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Upload Artwork", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Image picker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (pendingImageUri != null) {
                        AsyncImage(
                            model = pendingImageUri,
                            contentDescription = "Selected image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("Tap to select image", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Publish publicly", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Switch(checked = isPublished, onCheckedChange = onPublishedChange)
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss, enabled = !isUploading) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = title.isNotBlank() && pendingImageUri != null && !isUploading
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Upload")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditArtworkDialog(
    title: String,
    description: String,
    isPublished: Boolean,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPublishedChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Artwork") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Published", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Switch(checked = isPublished, onCheckedChange = onPublishedChange)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = title.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
        }
    )
}

@Composable
private fun ArtworkLightbox(
    artwork: Artwork,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            Column {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("${RetrofitClient.BASE_URL}uploads/${artwork.image_path}")
                        .crossfade(true)
                        .build(),
                    contentDescription = artwork.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                )

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = artwork.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (!artwork.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = artwork.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
