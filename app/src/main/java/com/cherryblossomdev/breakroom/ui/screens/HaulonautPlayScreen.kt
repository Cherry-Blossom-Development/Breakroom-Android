package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.HaulonautRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.HaulonautCharacter
import com.cherryblossomdev.breakroom.data.models.HaulonautConnectedSector
import com.cherryblossomdev.breakroom.data.models.HaulonautInventoryItem
import com.cherryblossomdev.breakroom.data.models.HaulonautItem
import com.cherryblossomdev.breakroom.data.models.HaulonautPlayerHere
import com.cherryblossomdev.breakroom.data.models.HaulonautSector
import com.cherryblossomdev.breakroom.data.models.HaulonautSectorFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

enum class HaulonautViewportMode { SPACE, OUTPOST, CARGO }

data class HaulonautPlayUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val character: HaulonautCharacter? = null,
    val currentSector: HaulonautSector? = null,
    val connectedSectors: List<HaulonautConnectedSector> = emptyList(),
    val features: List<HaulonautSectorFeature> = emptyList(),
    val playersHere: List<HaulonautPlayerHere> = emptyList(),
    val credits: Int = 0,
    val rations: Int = 0,
    val inventory: List<HaulonautInventoryItem> = emptyList(),
    val itemsCatalog: List<HaulonautItem> = emptyList(),
    val viewportMode: HaulonautViewportMode = HaulonautViewportMode.SPACE,
    val isNavigating: Boolean = false,
    val isPurchasing: Boolean = false,
    // One-shot signal, mirrors GamesUiState.createdCharacterId -- consumed by the screen
    // to show a Snackbar then cleared, so it doesn't refire on recomposition.
    val snackbarMessage: String? = null
) {
    val planetFeature: HaulonautSectorFeature? get() = features.firstOrNull { it.feature_type == "planet" }
    val outpostFeature: HaulonautSectorFeature? get() = features.firstOrNull { it.feature_type == "trading_outpost" }
    fun inventoryQuantity(itemKey: String): Int = inventory.firstOrNull { it.item_key == itemKey }?.quantity ?: 0
}

class HaulonautPlayViewModel(
    private val repository: HaulonautRepository,
    private val characterId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(HaulonautPlayUiState())
    val uiState: StateFlow<HaulonautPlayUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getCharacter(characterId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        character = data.character,
                        currentSector = data.currentSector,
                        connectedSectors = data.connectedSectors,
                        features = data.features,
                        playersHere = data.playersHere,
                        credits = data.credits,
                        rations = data.rations,
                        inventory = data.inventory
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to load character.")
                    return@launch
                }
            }
            // Non-fatal if this fails -- the outpost view just shows nothing for sale.
            when (val itemsResult = repository.getItems()) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(itemsCatalog = itemsResult.data)
                else -> {}
            }
        }
    }

    fun visitOutpost() {
        val outpostName = _uiState.value.outpostFeature?.name ?: "the outpost"
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.OUTPOST,
            snackbarMessage = "Docking at $outpostName."
        )
    }

    fun viewCargo() {
        _uiState.value = _uiState.value.copy(
            viewportMode = HaulonautViewportMode.CARGO,
            snackbarMessage = "Pulling up the cargo manifest."
        )
    }

    fun planetOverview() {
        _uiState.value = _uiState.value.copy(snackbarMessage = "Planetary survey systems are not available yet.")
    }

    fun exitViewportOverlay() {
        val message = if (_uiState.value.viewportMode == HaulonautViewportMode.OUTPOST) {
            "Departing the outpost."
        } else {
            "Closing the cargo manifest."
        }
        _uiState.value = _uiState.value.copy(viewportMode = HaulonautViewportMode.SPACE, snackbarMessage = message)
    }

    fun purchase(item: HaulonautItem) {
        if (_uiState.value.isPurchasing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPurchasing = true)
            when (val result = repository.purchase(characterId, item.item_key, 1)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        isPurchasing = false,
                        credits = data.credits,
                        rations = data.rations,
                        inventory = data.inventory,
                        snackbarMessage = "Purchased 1 ${item.name}. (-${item.base_price} Credits)"
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isPurchasing = false,
                    snackbarMessage = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isPurchasing = false, snackbarMessage = "Purchase failed")
            }
        }
    }

    fun navigate(sector: HaulonautConnectedSector) {
        if (_uiState.value.isNavigating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isNavigating = true)
            when (val result = repository.navigate(characterId, sector.id)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        isNavigating = false,
                        currentSector = data.currentSector,
                        connectedSectors = data.connectedSectors,
                        features = data.features,
                        playersHere = data.playersHere,
                        credits = data.credits,
                        rations = data.rations,
                        viewportMode = HaulonautViewportMode.SPACE,
                        snackbarMessage = "Arrived in Sector ${data.currentSector?.sector_number ?: "?"}."
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isNavigating = false,
                    snackbarMessage = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isNavigating = false, snackbarMessage = "Navigation failed")
            }
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }
}

// ==================== Screen ====================

// Deterministic-per-name hue so the same planet always renders the same color when
// revisited, without needing to store a color anywhere -- ports hashHue() from the web
// client's viewport scene.
private fun hashHue(str: String): Float {
    var hash = 0
    for (c in str) hash = (hash * 31 + c.code) % 360
    return hash.toFloat()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HaulonautPlayScreen(
    viewModel: HaulonautPlayViewModel,
    onExit: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbarMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.character?.display_name ?: "Haulonaut") },
                navigationIcon = {
                    IconButton(onClick = onExit, modifier = Modifier.semantics { contentDescription = "Exit to Games" }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.character != null) {
                        ResourcePill(label = "Credits", value = state.credits)
                        Spacer(modifier = Modifier.width(8.dp))
                        ResourcePill(label = "Rations", value = state.rations)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).testTag("screen-haulonaut-play")) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.load() }) { Text("Retry") }
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (state.viewportMode) {
                            HaulonautViewportMode.SPACE -> SpaceSceneContent(state)
                            HaulonautViewportMode.OUTPOST -> OutpostContent(
                                state = state,
                                onPurchase = { viewModel.purchase(it) }
                            )
                            HaulonautViewportMode.CARGO -> CargoContent(state)
                        }
                    }
                    HaulonautBottomBar(
                        state = state,
                        onVisitOutpost = { viewModel.visitOutpost() },
                        onPlanetOverview = { viewModel.planetOverview() },
                        onViewCargo = { viewModel.viewCargo() },
                        onBackToSector = { viewModel.exitViewportOverlay() },
                        onWarp = { viewModel.navigate(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ResourcePill(label: String, value: Int) {
    val color = if (value <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    // Deliberately no mergeDescendants/contentDescription here -- an earlier attempt at
    // merging "<value> <label>" into one TalkBack stop made the value untestable (Appium's
    // UiAutomator2 driver reads the merged AccessibilityNodeInfo tree, and the child Text's
    // own node -- and its testTag-mapped resource-id -- stopped exposing text/content-desc
    // once merged into this Column). Two TalkBack stops per pill is an acceptable trade for
    // a reliably-readable value.
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.testTag("haulonaut-resource-${label.lowercase()}")
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpaceSceneContent(state: HaulonautPlayUiState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SECTOR ${state.currentSector?.sector_number ?: "—"}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("haulonaut-sector-number")
        )

        if (state.planetFeature != null || state.outpostFeature != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                state.planetFeature?.let { planet ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val hue = hashHue(planet.name)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color.hsv(hue, 0.55f, 0.85f),
                                            Color.hsv(hue, 0.7f, 0.35f)
                                        )
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(planet.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
                state.outpostFeature?.let { outpost ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(outpost.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        state.currentSector?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.playersHere.isNotEmpty()) {
            Column {
                Text(
                    text = "Pilots here",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                state.playersHere.forEach { player ->
                    Text(player.display_name, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun OutpostContent(
    state: HaulonautPlayUiState,
    onPurchase: (HaulonautItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = (state.outpostFeature?.name ?: "Trading Outpost").uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (state.itemsCatalog.isEmpty()) {
            Text(
                "Nothing for sale right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.itemsCatalog.forEach { item ->
                val owned = state.inventoryQuantity(item.item_key)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Medium)
                            val ownedSuffix = if (owned > 0) " · owned $owned" else ""
                            Text(
                                text = "${item.base_price} Credits$ownedSuffix",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onPurchase(item) },
                            enabled = !state.isPurchasing && state.credits >= item.base_price,
                            modifier = Modifier
                                .testTag("haulonaut-outpost-buy-${item.item_key}")
                                .semantics { contentDescription = "Buy ${item.name} for ${item.base_price} Credits" }
                        ) {
                            Text("Buy")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CargoContent(state: HaulonautPlayUiState) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("CARGO MANIFEST", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.inventory.isEmpty()) {
            Text(
                "Cargo hold is empty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.inventory.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(entry.name)
                    Text("×${entry.quantity}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HaulonautBottomBar(
    state: HaulonautPlayUiState,
    onVisitOutpost: () -> Unit,
    onPlanetOverview: () -> Unit,
    onViewCargo: () -> Unit,
    onBackToSector: () -> Unit,
    onWarp: (HaulonautConnectedSector) -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (state.viewportMode == HaulonautViewportMode.SPACE) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.outpostFeature != null) {
                        AssistChip(
                            onClick = onVisitOutpost,
                            leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                            label = { Text("Visit Outpost") },
                            modifier = Modifier.testTag("haulonaut-visit-outpost-btn")
                        )
                    }
                    if (state.planetFeature != null) {
                        AssistChip(
                            onClick = onPlanetOverview,
                            leadingIcon = { Icon(Icons.Default.Public, contentDescription = null) },
                            label = { Text("Planet Overview") },
                            modifier = Modifier.testTag("haulonaut-planet-overview-btn")
                        )
                    }
                    AssistChip(
                        onClick = onViewCargo,
                        leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        label = { Text("Cargo") },
                        modifier = Modifier.testTag("haulonaut-view-cargo-btn")
                    )
                }
            } else {
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    TextButton(onClick = onBackToSector, modifier = Modifier.testTag("haulonaut-back-to-sector-btn")) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back to Sector")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WARP TO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                if (state.connectedSectors.isEmpty()) {
                    Text(
                        "no warps available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        state.connectedSectors.forEach { sector ->
                            val label = if (sector.visited) "${sector.sector_number} ✓" else "${sector.sector_number}"
                            OutlinedButton(
                                onClick = { onWarp(sector) },
                                enabled = !state.isNavigating,
                                modifier = Modifier
                                    .testTag("haulonaut-warp-btn-${sector.sector_number}")
                                    .semantics {
                                        contentDescription = "Warp to Sector ${sector.sector_number}" +
                                            if (sector.visited) ", visited" else ", unexplored"
                                    }
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}
