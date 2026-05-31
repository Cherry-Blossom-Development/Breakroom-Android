package com.cherryblossomdev.breakroom.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cherryblossomdev.breakroom.data.CollectionsRepository
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Preset colours the user can choose for a collection background ──────────
val collectionColorPresets = listOf(
    "#FFFFFF" to "White",
    "#FEE2E2" to "Red",
    "#FEF3C7" to "Yellow",
    "#DCFCE7" to "Green",
    "#DBEAFE" to "Blue",
    "#EDE9FE" to "Purple",
    "#FCE7F3" to "Pink",
    "#F3F4F6" to "Gray",
    "#1F2937" to "Dark"
)

// ==================== ViewModel ====================

data class CollectionsUiState(
    val collections: List<StoreCollection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    // Create / edit dialog
    val showCollectionDialog: Boolean = false,
    val editingCollection: StoreCollection? = null,
    val nameInput: String = "",
    val colorInput: String = "#FFFFFF",
    val backgroundType: String = "color",        // "color" or "image"
    val backgroundImageSource: String = "upload", // "upload" or "fromItems"
    val backgroundImagePath: String? = null,      // existing S3 key picked from items
    val backgroundImageUri: Uri? = null,          // new file from gallery
    val collectionItems: List<CollectionItem> = emptyList(),
    val itemsLoading: Boolean = false,
    val isSaving: Boolean = false,
    // Reorder mode
    val isReordering: Boolean = false,
    // Delete confirmation
    val collectionToDelete: StoreCollection? = null
)

class CollectionsViewModel(
    private val repo: CollectionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val r = repo.getCollections()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    collections = r.data, isLoading = false
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    error = r.message, isLoading = false
                )
                is BreakroomResult.AuthenticationError -> _uiState.value = _uiState.value.copy(
                    error = "Session expired", isLoading = false
                )
                else -> _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    // Returns true when the bg section should be shown (more than one collection will exist)
    fun showBgOption(): Boolean {
        val state = _uiState.value
        val resultCount = if (state.editingCollection == null)
            state.collections.size + 1 else state.collections.size
        return resultCount > 1
    }

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCollectionDialog = true,
            editingCollection = null,
            nameInput = "",
            colorInput = "#FFFFFF",
            backgroundType = "color",
            backgroundImageSource = "upload",
            backgroundImagePath = null,
            backgroundImageUri = null,
            collectionItems = emptyList()
        )
    }

    fun showEditDialog(collection: StoreCollection) {
        val s = collection.settings
        val isImage = s?.background_type == "image"
        _uiState.value = _uiState.value.copy(
            showCollectionDialog = true,
            editingCollection = collection,
            nameInput = collection.name,
            colorInput = s?.background_color ?: "#FFFFFF",
            backgroundType = if (isImage) "image" else "color",
            backgroundImageSource = "upload",
            backgroundImagePath = if (isImage) s?.background_image else null,
            backgroundImageUri = null,
            collectionItems = emptyList()
        )
        if (isImage) fetchCollectionItems(collection.id)
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(
            showCollectionDialog = false,
            editingCollection = null,
            backgroundImageUri = null,
            backgroundImagePath = null,
            collectionItems = emptyList()
        )
    }

    fun setNameInput(v: String) { _uiState.value = _uiState.value.copy(nameInput = v) }
    fun setColorInput(v: String) { _uiState.value = _uiState.value.copy(colorInput = v) }

    fun setBackgroundType(type: String) {
        _uiState.value = _uiState.value.copy(
            backgroundType = type,
            backgroundImageSource = "upload",
            backgroundImagePath = null,
            backgroundImageUri = null
        )
        if (type == "image" && _uiState.value.collectionItems.isEmpty()) {
            _uiState.value.editingCollection?.let { fetchCollectionItems(it.id) }
        }
    }

    fun setBackgroundImageSource(source: String) {
        _uiState.value = _uiState.value.copy(backgroundImageSource = source)
        if (source == "fromItems" && _uiState.value.collectionItems.isEmpty()) {
            _uiState.value.editingCollection?.let { fetchCollectionItems(it.id) }
        }
    }

    fun setBackgroundImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(backgroundImageUri = uri, backgroundImagePath = null)
    }

    fun setBackgroundImagePath(path: String?) {
        _uiState.value = _uiState.value.copy(backgroundImagePath = path, backgroundImageUri = null)
    }

    private fun fetchCollectionItems(collectionId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(itemsLoading = true)
            when (val r = repo.getItems(collectionId)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    collectionItems = r.data.filter { it.image_path != null },
                    itemsLoading = false
                )
                else -> _uiState.value = _uiState.value.copy(itemsLoading = false)
            }
        }
    }

    fun saveCollection() {
        val state = _uiState.value
        val name = state.nameInput.trim()
        if (name.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = if (state.editingCollection == null) {
                val color = state.colorInput.ifBlank { null }
                repo.createCollection(name, color)
            } else {
                repo.updateCollection(
                    id = state.editingCollection.id,
                    name = name,
                    backgroundType = state.backgroundType,
                    backgroundColor = state.colorInput,
                    backgroundImagePath = state.backgroundImagePath,
                    imageUri = state.backgroundImageUri
                )
            }
            when (result) {
                is BreakroomResult.Success -> {
                    val updated = if (state.editingCollection == null) {
                        listOf(result.data) + _uiState.value.collections
                    } else {
                        _uiState.value.collections.map { if (it.id == result.data.id) result.data else it }
                    }
                    _uiState.value = _uiState.value.copy(
                        collections = updated,
                        isSaving = false,
                        showCollectionDialog = false,
                        editingCollection = null,
                        backgroundImageUri = null,
                        collectionItems = emptyList(),
                        successMessage = if (state.editingCollection == null) "Collection created" else "Collection updated"
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isSaving = false, error = result.message
                )
                is BreakroomResult.AuthenticationError -> _uiState.value = _uiState.value.copy(
                    isSaving = false, error = "Session expired"
                )
                else -> _uiState.value = _uiState.value.copy(isSaving = false)
            }
        }
    }

    // ── Reorder ──────────────────────────────────────────────────────────────

    fun toggleReorder() {
        val entering = !_uiState.value.isReordering
        _uiState.value = _uiState.value.copy(isReordering = entering)
        if (!entering) saveOrder()
    }

    fun moveUp(id: Int) {
        val list = _uiState.value.collections.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx > 0) {
            val tmp = list[idx]; list[idx] = list[idx - 1]; list[idx - 1] = tmp
            _uiState.value = _uiState.value.copy(collections = list)
        }
    }

    fun moveDown(id: Int) {
        val list = _uiState.value.collections.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0 && idx < list.size - 1) {
            val tmp = list[idx]; list[idx] = list[idx + 1]; list[idx + 1] = tmp
            _uiState.value = _uiState.value.copy(collections = list)
        }
    }

    private fun saveOrder() {
        val ids = _uiState.value.collections.map { it.id }
        viewModelScope.launch {
            repo.reorderCollections(ids)
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun confirmDelete(collection: StoreCollection) {
        _uiState.value = _uiState.value.copy(collectionToDelete = collection)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(collectionToDelete = null)
    }

    fun deleteCollection() {
        val collection = _uiState.value.collectionToDelete ?: return
        viewModelScope.launch {
            when (repo.deleteCollection(collection.id)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    collections = _uiState.value.collections.filter { it.id != collection.id },
                    collectionToDelete = null,
                    successMessage = "Collection deleted"
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    collectionToDelete = null, error = "Failed to delete"
                )
                is BreakroomResult.AuthenticationError -> _uiState.value = _uiState.value.copy(
                    collectionToDelete = null, error = "Session expired"
                )
                else -> _uiState.value = _uiState.value.copy(collectionToDelete = null)
            }
        }
    }

    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

// ==================== Screen ====================

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onNavigateToCollection: (StoreCollection) -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToShipping: () -> Unit,
    onNavigateToPayment: () -> Unit = {},
    onNavigateToStorefront: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearSuccess() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (!state.isReordering) {
                FloatingActionButton(
                    onClick = viewModel::showCreateDialog,
                    modifier = Modifier.testTag("collections-fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "New collection")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).testTag("screen-collections")) {

            // Sub-navigation rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onNavigateToOrders,
                        modifier = Modifier.weight(1f).testTag("collections-orders-btn")
                    ) {
                        Icon(Icons.Outlined.ShoppingBag, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Orders")
                    }
                    OutlinedButton(
                        onClick = onNavigateToShipping,
                        modifier = Modifier.weight(1f).testTag("collections-shipping-btn")
                    ) {
                        Icon(Icons.Outlined.LocalShipping, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shipping")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onNavigateToPayment,
                        modifier = Modifier.weight(1f).testTag("collections-payment-btn")
                    ) {
                        Icon(Icons.Outlined.CreditCard, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Payouts")
                    }
                    OutlinedButton(
                        onClick = onNavigateToStorefront,
                        modifier = Modifier.weight(1f).testTag("collections-storefront-btn")
                    ) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Storefront")
                    }
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.collections.isEmpty()) {
                EmptyCollectionsMessage(modifier = Modifier.fillMaxSize())
            } else if (state.isReordering) {
                // ── Reorder mode ───────────────────────────────────────────
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Drag to reorder",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = viewModel::toggleReorder) { Text("Done") }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.collections, key = { it.id }) { col ->
                            ReorderRow(
                                collection = col,
                                isFirst = state.collections.first().id == col.id,
                                isLast = state.collections.last().id == col.id,
                                onMoveUp = { viewModel.moveUp(col.id) },
                                onMoveDown = { viewModel.moveDown(col.id) }
                            )
                        }
                    }
                }
            } else {
                // ── Normal grid ────────────────────────────────────────────
                if (state.collections.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = viewModel::toggleReorder) { Text("Reorder") }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.collections, key = { it.id }) { collection ->
                        CollectionCard(
                            collection = collection,
                            onClick = { onNavigateToCollection(collection) },
                            onEdit = { viewModel.showEditDialog(collection) },
                            onDelete = { viewModel.confirmDelete(collection) }
                        )
                    }
                }
            }
        }
    }

    if (state.showCollectionDialog) {
        CollectionSettingsDialog(
            state = state,
            showBgOption = viewModel.showBgOption(),
            onNameChange = viewModel::setNameInput,
            onColorChange = viewModel::setColorInput,
            onBackgroundTypeChange = viewModel::setBackgroundType,
            onBackgroundImageSourceChange = viewModel::setBackgroundImageSource,
            onBackgroundImageUriChange = viewModel::setBackgroundImageUri,
            onBackgroundImagePathChange = viewModel::setBackgroundImagePath,
            onConfirm = viewModel::saveCollection,
            onDismiss = viewModel::hideDialog
        )
    }

    state.collectionToDelete?.let { c ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete Collection") },
            text = { Text("Delete \"${c.name}\" and all its items? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteCollection,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("collection-delete-confirm")
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::cancelDelete,
                    modifier = Modifier.testTag("collection-delete-cancel")
                ) { Text("Cancel") }
            }
        )
    }
}

// ==================== Sub-composables ====================

@Composable
private fun CollectionCard(
    collection: StoreCollection,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val settings = collection.settings
    val hasImage = settings?.background_type == "image" && settings.background_image != null
    val bgColor = remember(settings?.background_color) {
        runCatching {
            Color(android.graphics.Color.parseColor(settings?.background_color ?: "#F3F4F6"))
        }.getOrElse { Color(0xFFF3F4F6) }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("collection-card"),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .clickable { onClick() }
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasImage) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("${RetrofitClient.BASE_URL}api/uploads/${settings!!.background_image}")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("collection-card-name")
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                SmallCollectionIconButton(Icons.Filled.Edit, "Settings", onEdit,
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.testTag("collection-card-edit"))
                SmallCollectionIconButton(Icons.Filled.Delete, "Delete", onDelete,
                    MaterialTheme.colorScheme.error,
                    Modifier.testTag("collection-card-delete"))
            }
        }
    }
}

@Composable
private fun ReorderRow(
    collection: StoreCollection,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val settings = collection.settings
    val hasImage = settings?.background_type == "image" && settings.background_image != null
    val bgColor = remember(settings?.background_color) {
        runCatching {
            Color(android.graphics.Color.parseColor(settings?.background_color ?: "#F3F4F6"))
        }.getOrElse { Color(0xFFF3F4F6) }
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Color/image swatch
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
            ) {
                if (hasImage) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("${RetrofitClient.BASE_URL}api/uploads/${settings!!.background_image}")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = collection.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up",
                    modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down",
                    modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
internal fun SmallCollectionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, modifier = Modifier.size(16.dp), tint = tint)
    }
}

@Composable
private fun EmptyCollectionsMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Inventory2,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(16.dp))
        Text("No collections yet", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Tap + to create your first collection",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
        )
    }
}

// ==================== Collection Settings Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionSettingsDialog(
    state: CollectionsUiState,
    showBgOption: Boolean,
    onNameChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onBackgroundTypeChange: (String) -> Unit,
    onBackgroundImageSourceChange: (String) -> Unit,
    onBackgroundImageUriChange: (Uri?) -> Unit,
    onBackgroundImagePathChange: (String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = state.editingCollection != null
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        onBackgroundImageUriChange(uri)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEditing) "Collection Settings" else "New Collection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = state.nameInput,
                    onValueChange = onNameChange,
                    label = { Text("Collection name *") },
                    modifier = Modifier.fillMaxWidth().testTag("collection-name-input"),
                    singleLine = true
                )

                // Background options — only shown when result will have > 1 collections
                if (showBgOption) {
                    // Color / Image toggle (only for editing; create always uses color)
                    if (isEditing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.backgroundType == "color",
                                onClick = { onBackgroundTypeChange("color") },
                                label = { Text("Color") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.backgroundType == "image",
                                onClick = { onBackgroundTypeChange("image") },
                                label = { Text("Image") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (state.backgroundType == "color" || !isEditing) {
                        Text("Background color", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            collectionColorPresets.take(5).forEach { (hex, label) ->
                                ColorChip(hex = hex, label = label, selected = state.colorInput == hex,
                                    onClick = { onColorChange(hex) })
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            collectionColorPresets.drop(5).forEach { (hex, label) ->
                                ColorChip(hex = hex, label = label, selected = state.colorInput == hex,
                                    onClick = { onColorChange(hex) })
                            }
                        }
                    } else {
                        // Image mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = state.backgroundImageSource == "upload",
                                onClick = { onBackgroundImageSourceChange("upload") },
                                label = { Text("Upload") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = state.backgroundImageSource == "fromItems",
                                onClick = { onBackgroundImageSourceChange("fromItems") },
                                label = { Text("From items") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (state.backgroundImageSource == "upload") {
                            BackgroundImageUploadSection(
                                imageUri = state.backgroundImageUri,
                                existingImagePath = if (state.backgroundImageUri == null) state.backgroundImagePath else null,
                                onPickImage = { imageLauncher.launch("image/*") },
                                onClear = { onBackgroundImageUriChange(null); onBackgroundImagePathChange(null) }
                            )
                        } else {
                            BackgroundImageFromItemsSection(
                                items = state.collectionItems,
                                isLoading = state.itemsLoading,
                                selectedPath = state.backgroundImagePath,
                                onSelect = { onBackgroundImagePathChange(it) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("collection-dialog-cancel")
                    ) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = state.nameInput.isNotBlank() && !state.isSaving,
                        modifier = Modifier.testTag("collection-dialog-save")
                    ) {
                        if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(if (isEditing) "Save" else "Create")
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundImageUploadSection(
    imageUri: Uri?,
    existingImagePath: String?,
    onPickImage: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val hasImage = imageUri != null || existingImagePath != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!hasImage) onPickImage() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                imageUri != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(imageUri).crossfade(true).build(),
                        contentDescription = "Background preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    )
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                existingImagePath != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("${RetrofitClient.BASE_URL}api/uploads/$existingImagePath")
                            .crossfade(true).build(),
                        contentDescription = "Background preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    )
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap to choose image",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    if (hasImage) {
        TextButton(onClick = onPickImage) { Text("Replace image") }
    }
}

@Composable
private fun BackgroundImageFromItemsSection(
    items: List<CollectionItem>,
    isLoading: Boolean,
    selectedPath: String?,
    onSelect: (String) -> Unit
) {
    when {
        isLoading -> Box(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }

        items.isEmpty() -> Text(
            "No item images available. Add images to your items first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val isSelected = item.image_path == selectedPath
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { item.image_path?.let { onSelect(it) } }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("${RetrofitClient.BASE_URL}api/uploads/${item.image_path}")
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorChip(hex: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val parsed = remember(hex) {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.White }
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(parsed, CircleShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}
