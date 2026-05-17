package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.CollectionsRepository
import com.cherryblossomdev.breakroom.data.models.*
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
    val isSaving: Boolean = false,
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

    fun showCreateDialog() {
        _uiState.value = _uiState.value.copy(
            showCollectionDialog = true,
            editingCollection = null,
            nameInput = "",
            colorInput = "#FFFFFF"
        )
    }

    fun showEditDialog(collection: StoreCollection) {
        _uiState.value = _uiState.value.copy(
            showCollectionDialog = true,
            editingCollection = collection,
            nameInput = collection.name,
            colorInput = collection.settings?.background_color ?: "#FFFFFF"
        )
    }

    fun hideDialog() {
        _uiState.value = _uiState.value.copy(showCollectionDialog = false, editingCollection = null)
    }

    fun setNameInput(v: String) { _uiState.value = _uiState.value.copy(nameInput = v) }
    fun setColorInput(v: String) { _uiState.value = _uiState.value.copy(colorInput = v) }

    fun saveCollection() {
        val state = _uiState.value
        val name = state.nameInput.trim()
        if (name.isEmpty()) return
        val color = state.colorInput.ifBlank { null }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = if (state.editingCollection == null) {
                repo.createCollection(name, color)
            } else {
                repo.updateCollection(state.editingCollection.id, name, color)
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

    fun confirmDelete(collection: StoreCollection) {
        _uiState.value = _uiState.value.copy(collectionToDelete = collection)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(collectionToDelete = null)
    }

    fun deleteCollection() {
        val collection = _uiState.value.collectionToDelete ?: return
        viewModelScope.launch {
            when (val r = repo.deleteCollection(collection.id)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    collections = _uiState.value.collections.filter { it.id != collection.id },
                    collectionToDelete = null,
                    successMessage = "Collection deleted"
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    collectionToDelete = null, error = r.message
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::showCreateDialog,
                modifier = Modifier.testTag("collections-fab")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New collection")
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
            } else {
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
        CollectionDialog(
            isEditing = state.editingCollection != null,
            name = state.nameInput,
            color = state.colorInput,
            isSaving = state.isSaving,
            onNameChange = viewModel::setNameInput,
            onColorChange = viewModel::setColorInput,
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
    val bgColor = remember(collection.settings?.background_color) {
        runCatching {
            Color(android.graphics.Color.parseColor(collection.settings?.background_color ?: "#F3F4F6"))
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
                    Icon(
                        Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
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
                SmallCollectionIconButton(Icons.Filled.Edit, "Edit", onEdit,
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

@Composable
private fun CollectionDialog(
    isEditing: Boolean,
    name: String,
    color: String,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Collection" else "New Collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Collection name *") },
                    modifier = Modifier.fillMaxWidth().testTag("collection-name-input"),
                    singleLine = true
                )
                Text("Background color", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    collectionColorPresets.take(5).forEach { (hex, label) ->
                        ColorChip(hex = hex, label = label, selected = color == hex,
                            onClick = { onColorChange(hex) })
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    collectionColorPresets.drop(5).forEach { (hex, label) ->
                        ColorChip(hex = hex, label = label, selected = color == hex,
                            onClick = { onColorChange(hex) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank() && !isSaving,
                modifier = Modifier.testTag("collection-dialog-save")
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (isEditing) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.testTag("collection-dialog-cancel")
            ) { Text("Cancel") }
        }
    )
}

@Composable
private fun ColorChip(hex: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val parsed = remember(hex) {
        runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.White }
    }
    val isDark = remember(hex) { hex == "#1F2937" }
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
