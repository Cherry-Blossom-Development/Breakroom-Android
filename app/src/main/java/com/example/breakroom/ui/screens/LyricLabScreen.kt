package com.example.breakroom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MusicNote
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
import java.text.SimpleDateFormat
import java.util.*

// ==================== ViewModel ====================

data class LyricLabUiState(
    val songs: List<Song> = emptyList(),
    val standaloneLyrics: List<Lyric> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedTab: Int = 0,  // 0 = Songs, 1 = Ideas
    // Song dialog state
    val showSongDialog: Boolean = false,
    val editingSong: Song? = null,
    val isSaving: Boolean = false,
    // Lyric dialog state
    val showLyricDialog: Boolean = false,
    val editingLyric: Lyric? = null,
    // Delete confirmation
    val songToDelete: Song? = null,
    val lyricToDelete: Lyric? = null
)

class LyricLabViewModel(
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LyricLabUiState())
    val uiState: StateFlow<LyricLabUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Load songs
            when (val result = lyricsRepository.getSongs()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(songs = result.data)
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(error = "Session expired")
                }
            }

            // Load standalone lyrics
            when (val result = lyricsRepository.getStandaloneLyrics()) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        standaloneLyrics = result.data,
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

    fun setSelectedTab(tab: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    // Song dialog
    fun showCreateSongDialog() {
        _uiState.value = _uiState.value.copy(showSongDialog = true, editingSong = null)
    }

    fun showEditSongDialog(song: Song) {
        _uiState.value = _uiState.value.copy(showSongDialog = true, editingSong = song)
    }

    fun hideSongDialog() {
        _uiState.value = _uiState.value.copy(showSongDialog = false, editingSong = null)
    }

    fun saveSong(
        title: String,
        description: String?,
        genre: String?,
        status: String,
        visibility: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            val editingSong = _uiState.value.editingSong
            val result = if (editingSong != null) {
                lyricsRepository.updateSong(
                    songId = editingSong.id,
                    title = title,
                    description = description,
                    genre = genre,
                    status = status,
                    visibility = visibility
                )
            } else {
                lyricsRepository.createSong(
                    title = title,
                    description = description,
                    genre = genre,
                    status = status,
                    visibility = visibility
                )
            }

            when (result) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        showSongDialog = false,
                        editingSong = null,
                        isSaving = false
                    )
                    loadData()
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
        songId: Int?,
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
                    loadData()
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

    // Delete operations
    fun confirmDeleteSong(song: Song) {
        _uiState.value = _uiState.value.copy(songToDelete = song)
    }

    fun cancelDeleteSong() {
        _uiState.value = _uiState.value.copy(songToDelete = null)
    }

    fun deleteSong() {
        val song = _uiState.value.songToDelete ?: return
        viewModelScope.launch {
            when (val result = lyricsRepository.deleteSong(song.id)) {
                is BreakroomResult.Success -> {
                    _uiState.value = _uiState.value.copy(songToDelete = null)
                    loadData()
                }
                is BreakroomResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message,
                        songToDelete = null
                    )
                }
                is BreakroomResult.AuthenticationError -> {
                    _uiState.value = _uiState.value.copy(
                        error = "Session expired",
                        songToDelete = null
                    )
                }
            }
        }
    }

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
                    loadData()
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricLabScreen(
    viewModel: LyricLabViewModel,
    onNavigateToSong: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Lyric Lab",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Capture and organize your lyrics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { viewModel.showCreateLyricDialog() }
                        ) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quick Idea")
                        }
                        Button(
                            onClick = { viewModel.showCreateSongDialog() }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Song")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs
                TabRow(selectedTabIndex = uiState.selectedTab) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        text = { Text("Songs") },
                        icon = { Icon(Icons.Outlined.MusicNote, contentDescription = null) }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        text = { Text("Ideas") },
                        icon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null) }
                    )
                }
            }
        }

        // Content
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.selectedTab == 0 -> {
                SongsTab(
                    songs = uiState.songs,
                    onSongClick = onNavigateToSong,
                    onEditSong = { viewModel.showEditSongDialog(it) },
                    onDeleteSong = { viewModel.confirmDeleteSong(it) }
                )
            }
            else -> {
                IdeasTab(
                    lyrics = uiState.standaloneLyrics,
                    onEditLyric = { viewModel.showEditLyricDialog(it) },
                    onDeleteLyric = { viewModel.confirmDeleteLyric(it) }
                )
            }
        }
    }

    // Song Dialog
    if (uiState.showSongDialog) {
        SongDialog(
            song = uiState.editingSong,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.hideSongDialog() },
            onSave = { title, description, genre, status, visibility ->
                viewModel.saveSong(title, description, genre, status, visibility)
            }
        )
    }

    // Lyric Dialog
    if (uiState.showLyricDialog) {
        LyricDialog(
            lyric = uiState.editingLyric,
            songs = uiState.songs,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.hideLyricDialog() },
            onSave = { content, songId, sectionType, sectionOrder, mood, notes, status ->
                viewModel.saveLyric(content, songId, sectionType, sectionOrder, mood, notes, status)
            }
        )
    }

    // Delete Song Confirmation
    uiState.songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteSong() },
            title = { Text("Delete Song?") },
            text = { Text("Are you sure you want to delete \"${song.title}\"? The lyrics will become standalone ideas.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSong() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteSong() }) {
                    Text("Cancel")
                }
            }
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

// ==================== Songs Tab ====================

@Composable
private fun SongsTab(
    songs: List<Song>,
    onSongClick: (Int) -> Unit,
    onEditSong: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit
) {
    val mySongs = songs.filter { it.isOwner }
    val collaborations = songs.filter { !it.isOwner }

    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No songs yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Tap 'New Song' to create your first song",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (mySongs.isNotEmpty()) {
                item {
                    Text(
                        "My Songs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(mySongs) { song ->
                    SongCard(
                        song = song,
                        onClick = { onSongClick(song.id) },
                        onEdit = { onEditSong(song) },
                        onDelete = { onDeleteSong(song) }
                    )
                }
            }

            if (collaborations.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Collaborations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(collaborations) { song ->
                    SongCard(
                        song = song,
                        isCollaboration = true,
                        onClick = { onSongClick(song.id) },
                        onEdit = if (song.canEdit) {{ onEditSong(song) }} else null,
                        onDelete = null  // Only owner can delete
                    )
                }
            }
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    isCollaboration: Boolean = false,
    onClick: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Collaboration indicator
            if (isCollaboration) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(IntrinsicSize.Max)
                        .fillMaxHeight()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.tertiary
                    ) {}
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Status badge
                    StatusBadge(status = song.status, type = "song")
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Info row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    song.genre?.let { genre ->
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${song.lyric_count} lyrics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isCollaboration) {
                        Text(
                            text = "by ${song.ownerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Actions
                if (onEdit != null || onDelete != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onEdit?.let {
                            TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                        onDelete?.let {
                            TextButton(
                                onClick = it,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== Ideas Tab ====================

@Composable
private fun IdeasTab(
    lyrics: List<Lyric>,
    onEditLyric: (Lyric) -> Unit,
    onDeleteLyric: (Lyric) -> Unit
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No standalone ideas yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Tap 'Quick Idea' to capture a lyric idea",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(lyrics) { lyric ->
                LyricCard(
                    lyric = lyric,
                    onEdit = { onEditLyric(lyric) },
                    onDelete = { onDeleteLyric(lyric) }
                )
            }
        }
    }
}

@Composable
private fun LyricCard(
    lyric: Lyric,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Content preview
            Text(
                text = lyric.contentPreview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lyric.mood?.let { mood ->
                        Text(
                            text = mood,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    StatusBadge(status = lyric.status, type = "lyric")
                }

                // Actions
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

// ==================== Status Badge ====================

@Composable
private fun StatusBadge(status: String, type: String) {
    val (backgroundColor, textColor) = when {
        type == "song" -> when (status) {
            "idea" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            "writing" -> Color(0xFF1976D2) to Color.White
            "complete" -> Color(0xFF388E3C) to Color.White
            "recorded" -> Color(0xFF7B1FA2) to Color.White
            "released" -> Color(0xFFFFB300) to Color.Black
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
        else -> when (status) {
            "draft" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            "in-progress" -> Color(0xFF1976D2) to Color.White
            "complete" -> Color(0xFF388E3C) to Color.White
            "archived" -> Color(0xFF757575) to Color.White
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
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

// ==================== Song Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongDialog(
    song: Song?,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String?, genre: String?, status: String, visibility: String) -> Unit
) {
    var title by remember(song) { mutableStateOf(song?.title ?: "") }
    var description by remember(song) { mutableStateOf(song?.description ?: "") }
    var genre by remember(song) { mutableStateOf(song?.genre ?: "") }
    var status by remember(song) { mutableStateOf(song?.status ?: "idea") }
    var visibility by remember(song) { mutableStateOf(song?.visibility ?: "private") }

    val statuses = listOf("idea", "writing", "complete", "recorded", "released")
    val visibilities = listOf("private", "collaborators", "public")
    val genres = listOf("Rock", "Pop", "Country", "Hip-Hop", "R&B", "Jazz", "Blues", "Folk", "Electronic", "Indie", "Alternative", "Metal", "Punk", "Soul", "Gospel")

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (song == null) "New Song" else "Edit Song") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Genre suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    genres.take(5).forEach { g ->
                        SuggestionChip(
                            onClick = { genre = g },
                            label = { Text(g, style = MaterialTheme.typography.labelSmall) }
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
                        value = status.replaceFirstChar { it.uppercase() },
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
                                text = { Text(s.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    status = s
                                    statusExpanded = false
                                }
                            )
                        }
                    }
                }

                // Visibility dropdown
                var visibilityExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = visibilityExpanded,
                    onExpandedChange = { visibilityExpanded = it }
                ) {
                    OutlinedTextField(
                        value = visibility.replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        label = { Text("Visibility") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = visibilityExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = visibilityExpanded,
                        onDismissRequest = { visibilityExpanded = false }
                    ) {
                        visibilities.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    visibility = v
                                    visibilityExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title,
                        description.ifBlank { null },
                        genre.ifBlank { null },
                        status,
                        visibility
                    )
                },
                enabled = title.isNotBlank() && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (song == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

// ==================== Lyric Dialog ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricDialog(
    lyric: Lyric?,
    songs: List<Song>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (content: String, songId: Int?, sectionType: String, sectionOrder: Int?, mood: String?, notes: String?, status: String) -> Unit
) {
    var content by remember(lyric) { mutableStateOf(lyric?.content ?: "") }
    var selectedSongId by remember(lyric) { mutableStateOf(lyric?.song_id) }
    var sectionType by remember(lyric) { mutableStateOf(lyric?.section_type ?: "idea") }
    var sectionOrder by remember(lyric) { mutableStateOf(lyric?.section_order?.toString() ?: "") }
    var mood by remember(lyric) { mutableStateOf(lyric?.mood ?: "") }
    var notes by remember(lyric) { mutableStateOf(lyric?.notes ?: "") }
    var status by remember(lyric) { mutableStateOf(lyric?.status ?: "draft") }

    val sectionTypes = listOf("idea", "verse", "chorus", "bridge", "pre-chorus", "hook", "intro", "outro", "other")
    val statuses = listOf("draft", "in-progress", "complete", "archived")
    val moods = listOf("Happy", "Sad", "Angry", "Melancholy", "Hopeful", "Nostalgic", "Romantic", "Energetic", "Peaceful", "Defiant", "Reflective", "Playful")

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(if (lyric == null) "New Lyric" else "Edit Lyric") },
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

                // Song assignment dropdown
                if (songs.isNotEmpty()) {
                    var songExpanded by remember { mutableStateOf(false) }
                    val selectedSong = songs.find { it.id == selectedSongId }
                    ExposedDropdownMenuBox(
                        expanded = songExpanded,
                        onExpandedChange = { songExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedSong?.title ?: "(Standalone idea)",
                            onValueChange = {},
                            label = { Text("Assign to Song") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = songExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = songExpanded,
                            onDismissRequest = { songExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("(Standalone idea)") },
                                onClick = {
                                    selectedSongId = null
                                    songExpanded = false
                                }
                            )
                            songs.filter { it.canEdit }.forEach { song ->
                                DropdownMenuItem(
                                    text = { Text(song.title) },
                                    onClick = {
                                        selectedSongId = song.id
                                        songExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

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
                        selectedSongId,
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
                Text(if (lyric == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}
