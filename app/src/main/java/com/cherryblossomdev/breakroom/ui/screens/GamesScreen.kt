package com.cherryblossomdev.breakroom.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.HaulonautRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.HaulonautCharacter
import com.cherryblossomdev.breakroom.data.models.HaulonautGame
import com.cherryblossomdev.breakroom.data.models.HaulonautInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ==================== ViewModel ====================

data class GamesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val game: HaulonautGame? = null,
    val instances: List<HaulonautInstance> = emptyList(),
    val characters: List<HaulonautCharacter> = emptyList(),
    val showCreateDialog: Boolean = false,
    val newCharacterName: String = "",
    val selectedInstanceId: Int? = null,
    val isCreating: Boolean = false,
    val createError: String? = null,
    // One-shot signal: set on successful character creation, consumed by the screen to
    // navigate then cleared, so it doesn't refire on recomposition/config change.
    val createdCharacterId: Int? = null
) {
    val mostRecentCharacter: HaulonautCharacter? get() = characters.firstOrNull()
}

class GamesViewModel(
    private val repository: HaulonautRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GamesUiState())
    val uiState: StateFlow<GamesUiState> = _uiState.asStateFlow()

    fun loadGame() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = repository.getGameInfo()) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        game = data.game,
                        instances = data.instances,
                        characters = data.characters
                    )
                }
                else -> _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load Haulonaut."
                )
            }
        }
    }

    fun openCreateDialog(instanceId: Int? = null) {
        val target = instanceId ?: _uiState.value.instances.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            newCharacterName = "",
            selectedInstanceId = target,
            createError = null
        )
    }

    fun dismissCreateDialog() {
        _uiState.value = _uiState.value.copy(showCreateDialog = false)
    }

    fun setNewCharacterName(value: String) {
        _uiState.value = _uiState.value.copy(newCharacterName = value)
    }

    fun selectInstance(instanceId: Int) {
        _uiState.value = _uiState.value.copy(selectedInstanceId = instanceId)
    }

    fun createCharacter() {
        val state = _uiState.value
        val name = state.newCharacterName.trim()
        val instanceId = state.selectedInstanceId
        if (name.isEmpty() || instanceId == null || state.isCreating) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)
            when (val result = repository.createCharacter(name, instanceId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        showCreateDialog = false,
                        createdCharacterId = result.data.id
                    )
                }
                is BreakroomResult.Error -> _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createError = result.message
                )
                else -> _uiState.value = _uiState.value.copy(
                    isCreating = false,
                    createError = "Failed to launch"
                )
            }
        }
    }

    fun consumeCreatedCharacter() {
        _uiState.value = _uiState.value.copy(createdCharacterId = null)
    }
}

// ==================== Screen ====================

private fun formatGameDate(dateStr: String?): String {
    if (dateStr == null) return "—"
    return try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val date = fmt.parse(dateStr) ?: return dateStr
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(date)
    } catch (e: Exception) {
        dateStr
    }
}

@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    onNavigateToPlay: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadGame()
    }

    LaunchedEffect(state.createdCharacterId) {
        state.createdCharacterId?.let { characterId ->
            onNavigateToPlay(characterId)
            viewModel.consumeCreatedCharacter()
        }
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).testTag("screen-games")) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = state.error ?: "", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadGame() }) { Text("Retry") }
                }
                state.game != null -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        HaulonautAdCard(
                            game = state.game!!,
                            hasMostRecentCharacter = state.mostRecentCharacter != null,
                            hasInstances = state.instances.isNotEmpty(),
                            onPlayNow = {
                                val recent = state.mostRecentCharacter
                                if (recent != null) {
                                    onNavigateToPlay(recent.id)
                                } else if (state.instances.isNotEmpty()) {
                                    viewModel.openCreateDialog()
                                }
                            }
                        )
                    }

                    if (state.instances.isNotEmpty()) {
                        item {
                            Text(
                                text = "Active Universes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        items(state.instances, key = { it.id }) { instance ->
                            UniverseCard(
                                instance = instance,
                                onNewCharacter = { viewModel.openCreateDialog(instance.id) }
                            )
                        }
                    }

                    if (state.characters.isNotEmpty()) {
                        item {
                            Text(
                                text = "Your Current Games",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(state.characters, key = { it.id }) { character ->
                            CharacterCard(
                                character = character,
                                onResume = { onNavigateToPlay(character.id) }
                            )
                        }
                    }
                }
            }

            if (state.showCreateDialog) {
                CreateCharacterDialog(
                    instances = state.instances,
                    selectedInstanceId = state.selectedInstanceId,
                    name = state.newCharacterName,
                    isCreating = state.isCreating,
                    createError = state.createError,
                    onNameChange = viewModel::setNewCharacterName,
                    onSelectInstance = viewModel::selectInstance,
                    onConfirm = { viewModel.createCharacter() },
                    onDismiss = { viewModel.dismissCreateDialog() }
                )
            }
        }
    }
}

// Fixed dark-green-on-black "retro terminal" palette, deliberately the same in light and
// dark app theme (this is a themed ad card, not a data-bearing surface) -- mirrors the
// web ad's CRT look. Contrast is very high in both directions so it doesn't need the
// Increase Contrast treatment other badges got.
private val HaulonautAdBackground = Color(0xFF05130A)
private val HaulonautAdBorder = Color(0xFF1F8A4C)
private val HaulonautAdTitle = Color(0xFF4DFF88)
private val HaulonautAdTagline = Color(0xFFBAFFCF)
private val HaulonautAdDescription = Color(0xFF8FE6AB)
private val HaulonautAdStat = Color(0xFF2FD66E)

@Composable
private fun HaulonautAdCard(
    game: HaulonautGame,
    hasMostRecentCharacter: Boolean,
    hasInstances: Boolean,
    onPlayNow: () -> Unit
) {
    val canPlay = hasMostRecentCharacter || hasInstances
    val buttonLabel = when {
        hasMostRecentCharacter -> "RESUME ▶"
        hasInstances -> "PLAY NOW ▶"
        else -> "NO UNIVERSES ONLINE"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = HaulonautAdBackground),
        border = androidx.compose.foundation.BorderStroke(2.dp, HaulonautAdBorder)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "»»» INCOMING TRANSMISSION",
                style = MaterialTheme.typography.labelMedium,
                color = HaulonautAdStat
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = game.name.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = HaulonautAdTitle
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Haul cargo. Chart the void. Make your fortune — or lose everything.",
                style = MaterialTheme.typography.bodyMedium,
                color = HaulonautAdTagline
            )
            if (!game.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = game.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = HaulonautAdDescription
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "1000-SECTOR UNIVERSES · TEXT-BASED · PERMADEATH",
                style = MaterialTheme.typography.labelSmall,
                color = HaulonautAdStat
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onPlayNow,
                enabled = canPlay,
                modifier = Modifier.testTag("games-play-now-btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HaulonautAdBorder,
                    contentColor = HaulonautAdTagline,
                    disabledContainerColor = HaulonautAdBorder.copy(alpha = 0.4f),
                    disabledContentColor = HaulonautAdTagline.copy(alpha = 0.6f)
                )
            ) {
                Text(buttonLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun UniverseCard(
    instance: HaulonautInstance,
    onNewCharacter: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(instance.name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${instance.sector_count} sectors · ${instance.player_count} player${if (instance.player_count == 1) "" else "s"} · since ${formatGameDate(instance.started_at)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onNewCharacter, modifier = Modifier.testTag("games-new-character-btn")) {
                Text("+ New Character")
            }
        }
    }
}

@Composable
private fun CharacterCard(
    character: HaulonautCharacter,
    onResume: () -> Unit
) {
    val statusColor = when (character.status) {
        "active" -> MaterialTheme.colorScheme.primary
        "dead" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val endedSuffix = if (character.instance_status != null && character.instance_status != "active") " (ended)" else ""
    val metaLabel = "${character.status.replaceFirstChar { it.uppercase() }} in ${character.instance_name ?: "Unknown"}$endedSuffix · " +
        "Started ${formatGameDate(character.created_at)} · Last played ${formatGameDate(character.last_played_at)}"

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp)
                .semantics { contentDescription = "${character.display_name}. $metaLabel" },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(character.display_name, fontWeight = FontWeight.SemiBold)
                Text(
                    text = metaLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor.takeIf { character.status != "active" } ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onResume, modifier = Modifier.testTag("games-character-resume-btn")) {
                Text("Resume")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun CreateCharacterDialog(
    instances: List<HaulonautInstance>,
    selectedInstanceId: Int?,
    name: String,
    isCreating: Boolean,
    createError: String?,
    onNameChange: (String) -> Unit,
    onSelectInstance: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var instanceMenuExpanded by remember { mutableStateOf(false) }
    val selectedInstance = instances.firstOrNull { it.id == selectedInstanceId }

    Dialog(onDismissRequest = onDismiss) {
        // Dialog() hosts its own window with an independent composition root, so it does
        // not inherit the testTagsAsResourceId semantics set on MainActivity's root Surface —
        // it has to be re-applied here for the testTag()s below to map to resource-ids.
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .testTag("games-create-character-dialog")
                .semantics { testTagsAsResourceId = true }
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Name Your Captain", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                if (instances.size > 1) {
                    ExposedDropdownMenuBox(
                        expanded = instanceMenuExpanded,
                        onExpandedChange = { instanceMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedInstance?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Universe") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = instanceMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = instanceMenuExpanded, onDismissRequest = { instanceMenuExpanded = false }) {
                            instances.forEach { instance ->
                                DropdownMenuItem(
                                    text = { Text(instance.name) },
                                    onClick = { onSelectInstance(instance.id); instanceMenuExpanded = false }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 64) onNameChange(it) },
                    label = { Text("Captain name") },
                    placeholder = { Text("e.g. Captain Vex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("games-captain-name-input")
                )

                if (createError != null) {
                    Text(createError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = name.isNotBlank() && selectedInstanceId != null && !isCreating,
                        modifier = Modifier.testTag("games-launch-btn")
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isCreating) "Launching..." else "Launch")
                    }
                }
            }
        }
    }
}
