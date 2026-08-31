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
import androidx.compose.material.icons.filled.Star
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
import com.cherryblossomdev.breakroom.data.models.HaulonautKnownLocation
import com.cherryblossomdev.breakroom.data.models.HaulonautPlayerHere
import com.cherryblossomdev.breakroom.data.models.HaulonautRouteWaypoint
import com.cherryblossomdev.breakroom.data.models.HaulonautSector
import com.cherryblossomdev.breakroom.data.models.HaulonautSectorFeature
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

enum class HaulonautViewportMode { SPACE, OUTPOST, CARGO, CHARTS }

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
    // Star Charts / autopilot
    val knownLocations: List<HaulonautKnownLocation> = emptyList(),
    val isLoadingCharts: Boolean = false,
    val isTraveling: Boolean = false,
    val travelDestinationName: String? = null,
    val travelHopsRemaining: Int = 0,
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
        val message = when (_uiState.value.viewportMode) {
            HaulonautViewportMode.OUTPOST -> "Departing the outpost."
            HaulonautViewportMode.CHARTS -> "Closing star charts."
            else -> "Closing the cargo manifest."
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

    // Manual warp (click or keyboard on the nav box) always takes precedence over an
    // in-progress autopilot course -- silently cancels it before the manual warp goes
    // through (the manual warp's own "Arrived in Sector X" message follows immediately,
    // so no separate "disengaged" message is needed here, matching web's manualNavigateTo()).
    fun navigate(sector: HaulonautConnectedSector) {
        if (_uiState.value.isNavigating) return
        if (_uiState.value.isTraveling) {
            _uiState.value = _uiState.value.copy(isTraveling = false, travelDestinationName = null, travelHopsRemaining = 0)
        }
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

    // ==================== Star Charts / autopilot ====================

    fun viewStarCharts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCharts = true)
            when (val result = repository.getKnownLocations(characterId)) {
                is BreakroomResult.Success -> _uiState.value = _uiState.value.copy(
                    isLoadingCharts = false,
                    knownLocations = result.data,
                    viewportMode = HaulonautViewportMode.CHARTS,
                    snackbarMessage = "Pulling up star charts."
                )
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isLoadingCharts = false,
                    snackbarMessage = result.message
                )
                else -> _uiState.value = _uiState.value.copy(isLoadingCharts = false, snackbarMessage = "Failed to load star charts")
            }
        }
    }

    fun setCourse(location: HaulonautKnownLocation) {
        if (_uiState.value.isTraveling) return
        viewModelScope.launch {
            when (val result = repository.getRoute(characterId, location.sector_id)) {
                is BreakroomResult.Success -> {
                    val path = result.data
                    if (path.size <= 1) return@launch
                    val hops = path.size - 1
                    _uiState.value = _uiState.value.copy(
                        viewportMode = HaulonautViewportMode.SPACE,
                        isTraveling = true,
                        travelDestinationName = location.name,
                        travelHopsRemaining = hops,
                        snackbarMessage = "Course plotted to ${location.name} ($hops hop${if (hops == 1) "" else "s"}). Autopilot engaged."
                    )
                    travelAlongPath(path)
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(snackbarMessage = result.message)
                else -> _uiState.value = _uiState.value.copy(snackbarMessage = "Failed to plot course")
            }
        }
    }

    // Flies the character along a precomputed path (path[0] is the current sector,
    // skipped) one hop at a time via the same repository.navigate() a manual warp uses --
    // rations still drain and each link is still re-validated server-side. isTraveling
    // doubles as the cancellation switch: navigate() (a manual warp) or abortAutopilot()
    // can flip it false from outside, and this loop rechecks it before every hop.
    private suspend fun travelAlongPath(path: List<HaulonautRouteWaypoint>) {
        for (i in 1 until path.size) {
            if (!_uiState.value.isTraveling) return
            when (val result = repository.navigate(characterId, path[i].id)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        currentSector = data.currentSector,
                        connectedSectors = data.connectedSectors,
                        features = data.features,
                        playersHere = data.playersHere,
                        credits = data.credits,
                        rations = data.rations,
                        travelHopsRemaining = path.size - 1 - i
                    )
                }
                else -> {
                    val message = (result as? BreakroomResult.Error)?.message ?: "Autopilot error"
                    _uiState.value = _uiState.value.copy(
                        isTraveling = false,
                        travelDestinationName = null,
                        travelHopsRemaining = 0,
                        snackbarMessage = message
                    )
                    return
                }
            }
            if (_uiState.value.isTraveling && i < path.size - 1) delay(600)
        }
        if (_uiState.value.isTraveling) {
            _uiState.value = _uiState.value.copy(
                isTraveling = false,
                travelDestinationName = null,
                travelHopsRemaining = 0,
                snackbarMessage = "Arrived at destination."
            )
        }
    }

    fun abortAutopilot() {
        if (!_uiState.value.isTraveling) return
        _uiState.value = _uiState.value.copy(
            isTraveling = false,
            travelDestinationName = null,
            travelHopsRemaining = 0,
            snackbarMessage = "Autopilot disengaged."
        )
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
                            HaulonautViewportMode.CHARTS -> ChartsContent(
                                state = state,
                                onSetCourse = { viewModel.setCourse(it) }
                            )
                        }
                    }
                    HaulonautBottomBar(
                        state = state,
                        onVisitOutpost = { viewModel.visitOutpost() },
                        onPlanetOverview = { viewModel.planetOverview() },
                        onViewCargo = { viewModel.viewCargo() },
                        onViewCharts = { viewModel.viewStarCharts() },
                        onBackToSector = { viewModel.exitViewportOverlay() },
                        onWarp = { viewModel.navigate(it) },
                        onAbortAutopilot = { viewModel.abortAutopilot() }
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
private fun ChartsContent(
    state: HaulonautPlayUiState,
    onSetCourse: (HaulonautKnownLocation) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("STAR CHARTS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Known locations — tap to plot a course",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isLoadingCharts) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        } else if (state.knownLocations.isEmpty()) {
            Text(
                "No known locations yet — explore more sectors.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            state.knownLocations.forEach { location ->
                val isHere = location.distance == 0
                val icon = when (location.feature_type) {
                    "planet" -> Icons.Default.Public
                    "trading_outpost" -> Icons.Default.ShoppingCart
                    else -> Icons.Default.Star
                }
                val distanceLabel = if (isHere) {
                    "HERE"
                } else {
                    val hops = location.distance ?: 0
                    "Sector ${location.sector_number} · $hops hop${if (hops == 1) "" else "s"} away"
                }
                Card(
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "${location.name}, ${if (isHere) "current location" else "$distanceLabel, tap to set course"}"
                    },
                    onClick = { if (!isHere && !state.isTraveling) onSetCourse(location) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(location.name, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = distanceLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isHere) FontWeight.Bold else FontWeight.Normal,
                            color = if (isHere) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    onViewCharts: () -> Unit,
    onBackToSector: () -> Unit,
    onWarp: (HaulonautConnectedSector) -> Unit,
    onAbortAutopilot: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (state.isTraveling) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AUTOPILOT: ${state.travelDestinationName ?: "En route"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${state.travelHopsRemaining} hop${if (state.travelHopsRemaining == 1) "" else "s"} remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onAbortAutopilot, modifier = Modifier.testTag("haulonaut-abort-autopilot-btn")) {
                        Text("Abort", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (state.viewportMode == HaulonautViewportMode.SPACE) {
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
                    AssistChip(
                        onClick = onViewCharts,
                        leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text("Star Charts") },
                        modifier = Modifier.testTag("haulonaut-view-charts-btn")
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
