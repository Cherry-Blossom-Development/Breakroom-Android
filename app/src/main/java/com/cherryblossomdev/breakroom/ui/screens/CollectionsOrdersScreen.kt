package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

private val statusFilters = listOf(
    "all" to "All",
    "pending_payment" to "Awaiting Payment",
    "paid" to "Paid",
    "processing" to "Processing",
    "shipped" to "Shipped",
    "delivered" to "Delivered",
    "cancelled" to "Cancelled"
)

data class CollectionsOrdersUiState(
    val orders: List<CollectionOrder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val selectedFilter: String = "all",
    val expandedOrderId: Int? = null,
    // Ship dialog
    val orderToShip: CollectionOrder? = null,
    val shipCarrier: String = "",
    val shipTracking: String = "",
    val isShipping: Boolean = false
)

class CollectionsOrdersViewModel(
    private val repo: CollectionsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionsOrdersUiState())
    val uiState: StateFlow<CollectionsOrdersUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val r = repo.getOrders()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    orders = r.data, isLoading = false
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

    fun setFilter(filter: String) { _uiState.value = _uiState.value.copy(selectedFilter = filter) }

    fun toggleExpand(orderId: Int) {
        val current = _uiState.value.expandedOrderId
        _uiState.value = _uiState.value.copy(
            expandedOrderId = if (current == orderId) null else orderId
        )
    }

    fun showShipDialog(order: CollectionOrder) {
        _uiState.value = _uiState.value.copy(
            orderToShip = order, shipCarrier = "", shipTracking = ""
        )
    }

    fun dismissShipDialog() { _uiState.value = _uiState.value.copy(orderToShip = null) }
    fun setShipCarrier(v: String) { _uiState.value = _uiState.value.copy(shipCarrier = v) }
    fun setShipTracking(v: String) { _uiState.value = _uiState.value.copy(shipTracking = v) }

    fun markShipped() {
        val order = _uiState.value.orderToShip ?: return
        val carrier = _uiState.value.shipCarrier.trim().ifEmpty { null }
        val tracking = _uiState.value.shipTracking.trim().ifEmpty { null }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isShipping = true, error = null)
            when (val r = repo.markOrderShipped(order.id, carrier, tracking)) {
                is BreakroomResult.Success -> {
                    val updated = _uiState.value.orders.map {
                        if (it.id == order.id) it.copy(
                            status = "shipped",
                            tracking_carrier = carrier,
                            tracking_number = tracking
                        ) else it
                    }
                    _uiState.value = _uiState.value.copy(
                        orders = updated,
                        isShipping = false,
                        orderToShip = null,
                        successMessage = "Order marked as shipped"
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isShipping = false, error = r.message
                )
                is BreakroomResult.AuthenticationError -> _uiState.value = _uiState.value.copy(
                    isShipping = false, error = "Session expired"
                )
                else -> _uiState.value = _uiState.value.copy(isShipping = false)
            }
        }
    }

    fun clearSuccess() { _uiState.value = _uiState.value.copy(successMessage = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

// ==================== Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsOrdersScreen(viewModel: CollectionsOrdersViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { snackbar.showSnackbar(it); viewModel.clearSuccess() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); viewModel.clearError() }
    }

    val filteredOrders = remember(state.orders, state.selectedFilter) {
        if (state.selectedFilter == "all") state.orders
        else state.orders.filter { it.status == state.selectedFilter }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Filter chips
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(statusFilters) { (key, label) ->
                    FilterChip(
                        selected = state.selectedFilter == key,
                        onClick = { viewModel.setFilter(key) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredOrders.isEmpty()) {
                EmptyOrdersMessage(modifier = Modifier.fillMaxSize(),
                    hasOrders = state.orders.isNotEmpty())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp, top = 4.dp)
                ) {
                    items(filteredOrders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            isExpanded = state.expandedOrderId == order.id,
                            onToggleExpand = { viewModel.toggleExpand(order.id) },
                            onMarkShipped = { viewModel.showShipDialog(order) }
                        )
                    }
                }
            }
        }
    }

    state.orderToShip?.let { order ->
        AlertDialog(
            onDismissRequest = viewModel::dismissShipDialog,
            title = { Text("Mark as Shipped") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Order #${order.id} · ${order.item_name ?: "Item"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = state.shipCarrier,
                        onValueChange = viewModel::setShipCarrier,
                        label = { Text("Carrier (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("USPS, UPS, FedEx…") }
                    )
                    OutlinedTextField(
                        value = state.shipTracking,
                        onValueChange = viewModel::setShipTracking,
                        label = { Text("Tracking number (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = viewModel::markShipped, enabled = !state.isShipping) {
                    if (state.isShipping)
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Mark Shipped")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissShipDialog, enabled = !state.isShipping) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ==================== Sub-composables ====================

@Composable
private fun OrderCard(
    order: CollectionOrder,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMarkShipped: () -> Unit
) {
    val statusColor = when (order.status) {
        "paid", "processing" -> MaterialTheme.colorScheme.primaryContainer
        "shipped" -> MaterialTheme.colorScheme.secondaryContainer
        "delivered" -> MaterialTheme.colorScheme.tertiaryContainer
        "cancelled", "refunded" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusTextColor = when (order.status) {
        "paid", "processing" -> MaterialTheme.colorScheme.onPrimaryContainer
        "shipped" -> MaterialTheme.colorScheme.onSecondaryContainer
        "delivered" -> MaterialTheme.colorScheme.onTertiaryContainer
        "cancelled", "refunded" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)) {
                    if (order.item_image_path != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("${RetrofitClient.BASE_URL}uploads/${order.item_image_path}")
                                .crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp))
                                .then(Modifier.aspectRatio(1f))
                                .padding(0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.ShoppingBag, contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(order.item_name ?: "Item #${order.collection_item_id}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(order.buyer_name ?: "Unknown buyer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(order.totalFormatted, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(4.dp), color = statusColor) {
                        Text(order.statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusTextColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            // Expand/collapse toggle
            TextButton(
                onClick = onToggleExpand,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    if (isExpanded) "Hide details" else "Show details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Expanded detail
            if (isExpanded) {
                Divider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!order.buyer_email.isNullOrBlank()) {
                        OrderDetailRow("Email", order.buyer_email)
                    }
                    val shipAddress = buildString {
                        order.ship_to_name?.let { append(it); append("\n") }
                        order.ship_to_address1?.let { append(it); append("\n") }
                        if (!order.ship_to_address2.isNullOrBlank()) append(order.ship_to_address2 + "\n")
                        val cityStateZip = listOfNotNull(order.ship_to_city, order.ship_to_state, order.ship_to_zip)
                            .joinToString(", ")
                        if (cityStateZip.isNotBlank()) append(cityStateZip)
                    }
                    if (shipAddress.isNotBlank()) OrderDetailRow("Ship to", shipAddress)

                    Divider()
                    OrderDetailRow("Item price", order.itemPriceFormatted)
                    OrderDetailRow("Shipping", order.shippingFormatted)
                    OrderDetailRow("Total", order.totalFormatted)

                    if (!order.tracking_number.isNullOrBlank()) {
                        Divider()
                        if (!order.tracking_carrier.isNullOrBlank()) {
                            OrderDetailRow("Carrier", order.tracking_carrier)
                        }
                        OrderDetailRow("Tracking", order.tracking_number)
                    }

                    if (order.canMarkShipped) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onMarkShipped,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.LocalShipping, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mark as Shipped")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun EmptyOrdersMessage(modifier: Modifier = Modifier, hasOrders: Boolean) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(Modifier.height(16.dp))
        Text(if (hasOrders) "No orders match this filter" else "No orders yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!hasOrders) {
            Text("Orders will appear here once buyers purchase your items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp))
        }
    }
}
