package com.example.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.breakroom.data.LyricsRepository
import com.example.breakroom.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

data class SongDetailUiState(
    val song: Song? = null,
    val lyrics: List<Lyric> = emptyList(),
    val collaborators: List<SongCollaborator> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // Lyric dialog state
    val showLyricDialog: Boolean = false,
    val editingLyric: Lyric? = null,
    val isSaving: Boolean = false,
    // Collaborator dialog state
    val showCollaboratorDialog: Boolean = false,
    // Delete confirmation
    val lyricToDelete: Lyric? = null,
    val collaboratorToRemove: SongCollaborator? = null
)

class SongDetailViewModel(
    private val lyricsRepository: LyricsRepository,
    private val songId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    init {
        loadSong()
    }

    fun loadSong() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = lyricsRepository.getSong(songId)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        song = result.data.song,
                        lyrics = result.data.lyrics,
                        collaborators = result.data.collaborators,
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
            }
        }
    }

    // Lyric dialog
    fun showCreateLyricDialog() {
        _uiState.value = _uiState.value.copy(showLyricDialog = true, editingLyric = null)
    }

    fun showEditLyricDialog(lyric: Lyric) {
        _uiState.value = _uiState.value.copy(showLyricDialog = true, editingLyric = lyric)
    }

    fun hideLyricDialog() {
        _uiState.value = _uiState.value.copy(showLyricDialog = false, editingLyric = null)
    }

    fun saveLyric(
        content: String,
        sectionType: String,
        sectionOrder: Int?,
        mood: String?,
        notes: String?,
        status: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            val editingLyric = _uiState.value.editingLyric
            val result = if (editingLyric != null) {
                lyricsRepository.updateLyric(
                    lyricId = editingLyric.id,
                    content = content,
                    songId = songId,
                    sectionType = sectionType,
                    sectionOrder = sectionOrder,
                    mood = mood,
                    notes = notes,
                    status = status
                )
            } else {
                lyricsRepository.createLyric(
                    content = content,
                    songId = songId,
                    sectionType = sectionType,
                    sectionOrder = sectionOrder,
                    mood = mood,
                    notes = notes,
                    status = status
                )
            }

            when (result) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showLyricDialog = false,
                        editingLyric = null,
                        isSaving = false
                    )
                    loadSong()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired",
                        isSaving = false
                    )
                }
            }
        }
    }

    // Delete lyric
    fun confirmDeleteLyric(lyric: Lyric) {
        _uiState.value = _uiState.value.copy(lyricToDelete = lyric)
    }

    fun cancelDeleteLyric() {
        _uiState.value = _uiState.value.copy(lyricToDelete = null)
    }

    fun deleteLyric() {
        val lyric = _uiState.value.lyricToDelete ?: return
        viewModelScope.launch {
            when (val result = lyricsRepository.deleteLyric(lyric.id)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(lyricToDelete = null)
                    loadSong()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        lyricToDelete = null
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired",
                        lyricToDelete = null
                    )
                }
            }
        }
    }

    // Collaborator dialog
    fun showCollaboratorDialog() {
        _uiState.value = _uiState.value.copy(showCollaboratorDialog = true)
    }

    fun hideCollaboratorDialog() {
        _uiState.value = _uiState.value.copy(showCollaboratorDialog = false)
    }

    fun addCollaborator(handle: String, role: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            when (val result = lyricsRepository.addCollaborator(songId, handle, role)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showCollaboratorDialog = false,
                        isSaving = false
                    )
                    loadSong()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        isSaving = false
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired",
                        isSaving = false
                    )
                }
            }
        }
    }

    // Remove collaborator
    fun confirmRemoveCollaborator(collaborator: SongCollaborator) {
        _uiState.value = _uiState.value.copy(collaboratorToRemove = collaborator)
    }

    fun cancelRemoveCollaborator() {
        _uiState.value = _uiState.value.copy(collaboratorToRemove = null)
    }

    fun removeCollaborator() {
        val collaborator = _uiState.value.collaboratorToRemove ?: return
        viewModelScope.launch {
            when (val result = lyricsRepository.removeCollaborator(songId, collaborator.user_id)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(collaboratorToRemove = null)
                    loadSong()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        collaboratorToRemove = null
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired",
                        collaboratorToRemove = null
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text(uiState.song?.title ?: "Song") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (uiState.song?.isOwner == true) {
                    IconButton(onClick = { viewModel.showCollaboratorDialog() }) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = "Add collaborator")
                    }
                }
            }
        )

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.song == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Song not found")
                }
            }
            else -> {
                SongContent(
                    song = uiState.song!!,
                    lyrics = uiState.lyrics,
                    collaborators = uiState.collaborators,
                    onAddLyric = { viewModel.showCreateLyricDialog() },
                    onEditLyric = { viewModel.showEditLyricDialog(it) },
                    onDeleteLyric = { viewModel.confirmDeleteLyric(it) },
                    onRemoveCollaborator = { viewModel.confirmRemoveCollaborator(it) }
                )
            }
        }
    }

    // Lyric Dialog
    if (uiState.showLyricDialog) {
        SongLyricDialog(
            lyric = uiState.editingLyric,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.hideLyricDialog() },
            onSave = { content, sectionType, sectionOrder, mood, notes, status ->
                viewModel.saveLyric(content, sectionType, sectionOrder, mood, notes, status)
            }
        )
    }

    // Collaborator Dialog
    if (uiState.showCollaboratorDialog) {
        CollaboratorDialog(
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.hideCollaboratorDialog() },
            onAdd = { handle, role -> viewModel.addCollaborator(handle, role) }
        )
    }

    // Delete Lyric Confirmation
    uiState.lyricToDelete?.let { lyric ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteLyric() },
            title = { Text("Delete Lyric?") },
            text = { Text("Are you sure you want to delete this lyric?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteLyric() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteLyric() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Remove Collaborator Confirmation
    uiState.collaboratorToRemove?.let { collaborator ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelRemoveCollaborator() },
            title = { Text("Remove Collaborator?") },
            text = { Text("Remove ${collaborator.displayName} from this song?") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.removeCollaborator() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRemoveCollaborator() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Error Snackbar
    uiState.error?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(error)
        }
    }
}

@Composable
private fun SongContent(
    song: Song,
    lyrics: List<Lyric>,
    collaborators: List<SongCollaborator>,
    onAddLyric: () -> Unit,
    onEditLyric: (Lyric) -> Unit,
    onDeleteLyric: (Lyric) -> Unit,
    onRemoveCollaborator: (SongCollaborator) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Song info card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        SongStatusBadge(status = song.status)
                    }

                    if (!song.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = song.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        song.genre?.let { genre ->
                            AssistChip(
                                onClick = {},
                                label = { Text(genre) }
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(song.visibility.replaceFirstChar { it.uppercase() }) }
                        )
                        if (!song.isOwner) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${song.user_role?.replaceFirstChar { it.uppercase() } ?: "Viewer"}") }
                            )
                        }
                    }
                }
            }
        }

        // Collaborators section (only if there are any or user is owner)
        if (collaborators.isNotEmpty() || song.isOwner) {
            item {
                Text(
                    text = "Collaborators",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (collaborators.isEmpty()) {
                item {
                    Text(
                        text = "No collaborators yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(collaborators) { collaborator ->
                    CollaboratorCard(
                        collaborator = collaborator,
                        canRemove = song.isOwner,
                        onRemove = { onRemoveCollaborator(collaborator) }
                    )
                }
            }
        }

        // Lyrics section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (song.canEdit) {
                    FilledTonalButton(onClick = onAddLyric) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Lyric")
                    }
                }
            }
        }

        if (lyrics.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No lyrics yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (song.canEdit) {
                                Text(
                                    "Tap 'Add Lyric' to get started",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(lyrics) { lyric ->
                SongLyricCard(
                    lyric = lyric,
                    canEdit = song.canEdit,
                    onEdit = { onEditLyric(lyric) },
                    onDelete = { onDeleteLyric(lyric) }
                )
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CollaboratorCard(
    collaborator: SongCollaborator,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = collaborator.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = collaborator.formattedRole,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SongLyricCard(
    lyric: Lyric,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canEdit) Modifier.clickable(onClick = onEdit) else Modifier),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = lyric.sectionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    lyric.section_order?.let { order ->
                        Text(
                            text = "#$order",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LyricStatusBadge(status = lyric.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            Text(
                text = lyric.content,
                style = MaterialTheme.typography.bodyMedium
            )

            // Mood and actions
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                lyric.mood?.let { mood ->
                    Text(
                        text = mood,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } ?: Spacer(modifier = Modifier.width(1.dp))

                if (canEdit) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongStatusBadge(status: String) {
    val (backgroundColor, textColor) = when (status) {
        "idea" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        "writing" -> Color(0xFF1976D2) to Color.White
        "complete" -> Color(0xFF388E3C) to Color.White
        "recorded" -> Color(0xFF7B1FA2) to Color.White
        "released" -> Color(0xFFFFB300) to Color.Black
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = backgroundColor
    ) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LyricStatusBadge(status: String) {
    val (backgroundColor, textColor) = when (status) {
        "draft" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        "in-progress" -> Color(0xFF1976D2) to Color.White
        "complete" -> Color(0xFF388E3C) to Color.White
        "archived" -> Color(0xFF757575) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = backgroundColor
    ) {
        Text(
            text = status.replace("-", " ").replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ==================== Lyric Dialog for Song ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongLyricDialog(
    lyric: Lyric?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (content: String, sectionType: String, sectionOrder: Int?, mood: String?, notes: String?, status: String) -> Unit
) {
    var content by remember(lyric) { mutableStateOf(lyric?.content ?: "") }
    var sectionType by remember(lyric) { mutableStateOf(lyric?.section_type ?: "verse") }
    var sectionOrder by remember(lyric) { mutableStateOf(lyric?.section_order?.toString() ?: "") }
    var mood by remember(lyric) { mutableStateOf(lyric?.mood ?: "") }
    var notes by remember(lyric) { mutableStateOf(lyric?.notes ?: "") }
    var status by remember(lyric) { mutableStateOf(lyric?.status ?: "draft") }

    val sectionTypes = listOf("idea", "verse", "chorus", "bridge", "pre-chorus", "hook", "intro", "outro", "other")
    val statuses = listOf("draft", "in-progress", "complete", "archived")
    val moods = listOf("Happy", "Sad", "Angry", "Melancholy", "Hopeful", "Nostalgic", "Romantic", "Energetic")

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (lyric == null) "Add Lyric" else "Edit Lyric") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content *") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                // Section type dropdown
                var sectionExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = sectionExpanded,
                    onExpandedChange = { sectionExpanded = it }
                ) {
                    OutlinedTextField(
                        value = sectionType.replace("-", " ").replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Section Type") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = sectionExpanded,
                        onDismissRequest = { sectionExpanded = false }
                    ) {
                        sectionTypes.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.replace("-", " ").replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    sectionType = s
                                    sectionExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = sectionOrder,
                    onValueChange = { sectionOrder = it.filter { c -> c.isDigit() } },
                    label = { Text("Order #") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mood,
                    onValueChange = { mood = it },
                    label = { Text("Mood") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Mood suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    moods.take(4).forEach { m ->
                        SuggestionChip(
                            onClick = { mood = m },
                            label = { Text(m, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Status dropdown
                var statusExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it }
                ) {
                    OutlinedTextField(
                        value = status.replace("-", " ").replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Status") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        statuses.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.replace("-", " ").replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    status = s
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Private Notes") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        content,
                        sectionType,
                        sectionOrder.toIntOrNull(),
                        mood.ifBlank { null },
                        notes.ifBlank { null },
                        status
                    )
                },
                enabled = content.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (lyric == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

// ==================== Collaborator Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollaboratorDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onAdd: (handle: String, role: String) -> Unit
) {
    var handle by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("editor") }

    val roles = listOf("editor", "viewer")

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Add Collaborator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Username *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Role dropdown
                var roleExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = it }
                ) {
                    OutlinedTextField(
                        value = role.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Role") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roles.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    role = r
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "Editor: Can view and edit lyrics\nViewer: Can only view",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(handle, role) },
                enabled = handle.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}
