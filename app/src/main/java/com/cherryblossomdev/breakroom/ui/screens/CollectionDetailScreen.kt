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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

// ==================== ViewModel ====================

data class CollectionDetailUiState(
    val items: List<CollectionItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    // Item dialog state (shared for create + edit)
    val showItemDialog: Boolean = false,
    val editingItem: CollectionItem? = null,
    val itemName: String = "",
    val itemDescription: String = "",
    val itemPrice: String = "",
    val itemShipping: String = "",
    val itemAvailable: Boolean = true,
    val itemWeight: String = "",
    val itemLength: String = "",
    val itemWidth: String = "",
    val itemHeight: String = "",
    val pendingImageUri: Uri? = null,
    val isSaving: Boolean = false,
    // Delete confirmation
    val itemToDelete: CollectionItem? = null
)

class CollectionDetailViewModel(
    private val repo: CollectionsRepository,
    val collectionId: Int,
    val collectionName: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val r = repo.getItems(collectionId)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    items = r.data, isLoading = false
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
            showItemDialog = true,
            editingItem = null,
            itemName = "", itemDescription = "",
            itemPrice = "", itemShipping = "",
            itemAvailable = true,
            itemWeight = "", itemLength = "", itemWidth = "", itemHeight = "",
            pendingImageUri = null
        )
    }

    fun showEditDialog(item: CollectionItem) {
        _uiState.value = _uiState.value.copy(
            showItemDialog = true,
            editingItem = item,
            itemName = item.name,
            itemDescription = item.description ?: "",
            itemPrice = item.price_cents?.let { String.format("%.2f", it / 100.0) } ?: "",
            itemShipping = item.shipping_cost_cents?.let { String.format("%.2f", it / 100.0) } ?: "",
            itemAvailable = item.isAvailable,
            itemWeight = item.weight_oz?.toString() ?: "",
            itemLength = item.length_in?.toString() ?: "",
            itemWidth = item.width_in?.toString() ?: "",
            itemHeight = item.height_in?.toString() ?: "",
            pendingImageUri = null
        )
    }

    fun hideDialog() { _uiState.value = _uiState.value.copy(showItemDialog = false, editingItem = null) }

    fun setItemName(v: String) { _uiState.value = _uiState.value.copy(itemName = v) }
    fun setItemDescription(v: String) { _uiState.value = _uiState.value.copy(itemDescription = v) }
    fun setItemPrice(v: String) { _uiState.value = _uiState.value.copy(itemPrice = v) }
    fun setItemShipping(v: String) { _uiState.value = _uiState.value.copy(itemShipping = v) }
    fun setItemAvailable(v: Boolean) { _uiState.value = _uiState.value.copy(itemAvailable = v) }
    fun setItemWeight(v: String) { _uiState.value = _uiState.value.copy(itemWeight = v) }
    fun setItemLength(v: String) { _uiState.value = _uiState.value.copy(itemLength = v) }
    fun setItemWidth(v: String) { _uiState.value = _uiState.value.copy(itemWidth = v) }
    fun setItemHeight(v: String) { _uiState.value = _uiState.value.copy(itemHeight = v) }
    fun setPendingImageUri(uri: Uri?) { _uiState.value = _uiState.value.copy(pendingImageUri = uri) }

    fun saveItem() {
        val s = _uiState.value
        val name = s.itemName.trim()
        if (name.isEmpty()) return

        val priceCents = s.itemPrice.trim().toDoubleOrNull()?.let { (it * 100).toInt() }
        val shipCents = s.itemShipping.trim().toDoubleOrNull()?.let { (it * 100).toInt() }
        val weightOz = s.itemWeight.trim().toDoubleOrNull()
        val lengthIn = s.itemLength.trim().toDoubleOrNull()
        val widthIn = s.itemWidth.trim().toDoubleOrNull()
        val heightIn = s.itemHeight.trim().toDoubleOrNull()
        val desc = s.itemDescription.trim().ifEmpty { null }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = if (s.editingItem == null) {
                repo.createItem(collectionId, name, desc, priceCents, s.itemAvailable,
                    shipCents, weightOz, lengthIn, widthIn, heightIn, s.pendingImageUri)
            } else {
                repo.updateItem(collectionId, s.editingItem.id, name, desc, priceCents,
                    s.itemAvailable, shipCents, weightOz, lengthIn, widthIn, heightIn, s.pendingImageUri)
            }
            when (result) {
                is BreakroomResult.Success -> {
                    val updated = if (s.editingItem == null) {
                        _uiState.value.items + result.data
                    } else {
                        _uiState.value.items.map { if (it.id == result.data.id) result.data else it }
                    }
                    _uiState.value = _uiState.value.copy(
                        items = updated,
                        isSaving = false,
                        showItemDialog = false,
                        editingItem = null,
                        successMessage = if (s.editingItem == null) "Item added" else "Item updated"
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

    fun confirmDelete(item: CollectionItem) { _uiState.value = _uiState.value.copy(itemToDelete = item) }
    fun cancelDelete() { _uiState.value = _uiState.value.copy(itemToDelete = null) }

    fun deleteItem() {
        val item = _uiState.value.itemToDelete ?: return
        viewModelScope.launch {
            when (val r = repo.deleteItem(collectionId, item.id)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items.filter { it.id != item.id },
                    itemToDelete = null,
                    successMessage = "Item deleted"
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    itemToDelete = null, error = r.message
                )
                is BreakroomResult.AuthenticationError -> _uiState.value = _uiState.value.copy(
                    itemToDelete = null, error = "Session expired"
                )
                else -> _uiState.value = _uiState.value.copy(itemToDelete = null)
            }
        }
    }

    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

// ==================== Screen ====================

@Composable
fun CollectionDetailScreen(
    viewModel: CollectionDetailViewModel,
    onNavigateBack: () -> Unit
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
            FloatingActionButton(onClick = viewModel::showCreateDialog) {
                Icon(Icons.Filled.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.items.isEmpty()) {
                EmptyItemsMessage(modifier = Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        CollectionItemCard(
                            item = item,
                            onEdit = { viewModel.showEditDialog(item) },
                            onDelete = { viewModel.confirmDelete(item) }
                        )
                    }
                }
            }
        }
    }

    if (state.showItemDialog) {
        CollectionItemDialog(
            isEditing = state.editingItem != null,
            name = state.itemName,
            description = state.itemDescription,
            price = state.itemPrice,
            shipping = state.itemShipping,
            isAvailable = state.itemAvailable,
            weight = state.itemWeight,
            length = state.itemLength,
            width = state.itemWidth,
            height = state.itemHeight,
            pendingImageUri = state.pendingImageUri,
            existingImagePath = state.editingItem?.image_path,
            isSaving = state.isSaving,
            onNameChange = viewModel::setItemName,
            onDescriptionChange = viewModel::setItemDescription,
            onPriceChange = viewModel::setItemPrice,
            onShippingChange = viewModel::setItemShipping,
            onAvailableChange = viewModel::setItemAvailable,
            onWeightChange = viewModel::setItemWeight,
            onLengthChange = viewModel::setItemLength,
            onWidthChange = viewModel::setItemWidth,
            onHeightChange = viewModel::setItemHeight,
            onImageSelected = viewModel::setPendingImageUri,
            onConfirm = viewModel::saveItem,
            onDismiss = viewModel::hideDialog
        )
    }

    state.itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Delete Item") },
            text = { Text("Delete \"${item.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deleteItem,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) { Text("Cancel") }
            }
        )
    }
}

// ==================== Sub-composables ====================

@Composable
private fun CollectionItemCard(
    item: CollectionItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Box {
            Column {
                if (item.image_path != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("${RetrofitClient.BASE_URL}uploads/${item.image_path}")
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val price = item.priceFormatted
                    if (price != null) {
                        Text(price, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (item.isAvailable)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (item.isAvailable) "Listed" else "Unlisted",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = if (item.isAvailable)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                SmallCollectionIconButton(Icons.Filled.Edit, "Edit", onEdit,
                    MaterialTheme.colorScheme.onSurface)
                SmallCollectionIconButton(Icons.Filled.Delete, "Delete", onDelete,
                    MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EmptyItemsMessage(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))
        Text("No items yet", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Tap + to add your first item", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
    }
}

@Composable
private fun CollectionItemDialog(
    isEditing: Boolean,
    name: String, description: String,
    price: String, shipping: String,
    isAvailable: Boolean,
    weight: String, length: String, width: String, height: String,
    pendingImageUri: Uri?,
    existingImagePath: String?,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onShippingChange: (String) -> Unit,
    onAvailableChange: (Boolean) -> Unit,
    onWeightChange: (String) -> Unit,
    onLengthChange: (String) -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> onImageSelected(uri) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(12.dp)) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(if (isEditing) "Edit Item" else "Add Item",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                // Image picker
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        pendingImageUri != null -> AsyncImage(
                            model = pendingImageUri, contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                        existingImagePath != null -> AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("${RetrofitClient.BASE_URL}uploads/$existingImagePath")
                                .crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.Image, contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap to select image (optional)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                OutlinedTextField(value = name, onValueChange = onNameChange,
                    label = { Text("Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                OutlinedTextField(value = description, onValueChange = onDescriptionChange,
                    label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 3)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = price, onValueChange = onPriceChange,
                        label = { Text("Price ($)") }, modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = shipping, onValueChange = onShippingChange,
                        label = { Text("Shipping ($)") }, modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }

                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Listed (available for sale)", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f))
                    Switch(checked = isAvailable, onCheckedChange = onAvailableChange)
                }

                Text("Dimensions & weight (optional)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(value = weight, onValueChange = onWeightChange,
                    label = { Text("Weight (oz)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = length, onValueChange = onLengthChange,
                        label = { Text("L (in)") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = width, onValueChange = onWidthChange,
                        label = { Text("W (in)") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = height, onValueChange = onHeightChange,
                        label = { Text("H (in)") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm, enabled = name.isNotBlank() && !isSaving) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(if (isEditing) "Save" else "Add")
                    }
                }
            }
        }
    }
}
