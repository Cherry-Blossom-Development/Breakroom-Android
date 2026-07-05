package com.cherryblossomdev.breakroom.ui.screens.bandpage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cherryblossomdev.breakroom.network.RetrofitClient

private val PRESET_BG_COLORS = listOf(
    "#1a1a2e", "#16213e", "#0f3460", "#533483",
    "#7d1e6a", "#2d3436", "#1e272e", "#000000"
)

private fun parseHexColorOrNull(hex: String): Color? {
    if (!hex.matches(Regex("^#[0-9a-fA-F]{6}$"))) return null
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: IllegalArgumentException) {
        null
    }
}

@Composable
fun BandPageSetupScreen(
    viewModel: BandPageSetupViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadBackground(it) }
    }

    LaunchedEffect(state.saveMessage) {
        if (state.saveMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSaveMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (state.bandName.isNotEmpty()) "${state.bandName} — Band Page" else "Band Page",
                style = MaterialTheme.typography.titleMedium
            )
        }

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.error?.let { error ->
                        item {
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    state.saveMessage?.let { message ->
                        item {
                            Text(message, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }

                    item {
                        PublishCard(
                            isPublished = state.isPublished,
                            bandUrl = state.bandUrl,
                            onTogglePublished = viewModel::updatePublished
                        )
                    }

                    item {
                        PageSettingsCard(
                            bandUrl = state.bandUrl,
                            story = state.story,
                            backgroundColor = state.backgroundColor,
                            isSaving = state.isSavingSettings,
                            onBandUrlChange = viewModel::updateBandUrl,
                            onStoryChange = viewModel::updateStory,
                            onBackgroundColorChange = viewModel::updateBackgroundColor,
                            onSave = viewModel::saveSettings
                        )
                    }

                    item {
                        BackgroundPhotoCard(
                            photoUrl = state.backgroundPhotoUrl,
                            isUploading = state.isUploadingBackground,
                            onUpload = { backgroundPicker.launch("image/*") },
                            onRemove = viewModel::removeBackground
                        )
                    }

                    item {
                        Text(
                            text = "Members & Instruments",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Check the instruments each member plays. Changes save automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(state.members, key = { "member-${it.id}" }) { member ->
                        MemberInstrumentsCard(
                            member = member,
                            instruments = state.instruments,
                            onToggleInstrument = { instId -> viewModel.toggleInstrument(member, instId) }
                        )
                    }

                    item {
                        Text(
                            text = "Featured Songs",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Check songs to feature on your band page. Use arrows to reorder.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    if (state.songs.isEmpty()) {
                        item {
                            Text(
                                "No band sessions uploaded yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val sortedSongs = state.songs.sortedWith(
                        compareByDescending<BandPageSongUi> { it.onPage }.thenBy { it.displayOrder }
                    )
                    items(sortedSongs, key = { "song-${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            onToggle = { viewModel.toggleSong(song) },
                            onMoveUp = { viewModel.moveSong(song, -1) },
                            onMoveDown = { viewModel.moveSong(song, 1) }
                        )
                    }

                    if (state.isPublished && state.bandUrl.isNotEmpty()) {
                        item {
                            val publicUrl = RetrofitClient.BASE_URL.trimEnd('/') + "/band/" + state.bandUrl
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(publicUrl)))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View Public Page →")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishCard(
    isPublished: Boolean,
    bandUrl: String,
    onTogglePublished: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Publish Band Page", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (bandUrl.isNotEmpty()) "/band/$bandUrl" else "Set a URL below to publish your page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isPublished, onCheckedChange = onTogglePublished)
        }
    }
}

@Composable
private fun PageSettingsCard(
    bandUrl: String,
    story: String,
    backgroundColor: String,
    isSaving: Boolean,
    onBandUrlChange: (String) -> Unit,
    onStoryChange: (String) -> Unit,
    onBackgroundColorChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Page Settings", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = bandUrl,
                onValueChange = onBandUrlChange,
                label = { Text("Band URL") },
                placeholder = { Text("my-band-name") },
                prefix = { Text("/band/") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Lowercase letters, numbers, and hyphens only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = story,
                onValueChange = onStoryChange,
                label = { Text("Band Story") },
                placeholder = { Text("Tell the world about your band…") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Text("Background Color", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PRESET_BG_COLORS.forEach { hex ->
                    val color = parseHexColorOrNull(hex) ?: Color.Black
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onBackgroundColorChange(hex) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = backgroundColor,
                    onValueChange = onBackgroundColorChange,
                    placeholder = { Text("#1a1a2e") },
                    singleLine = true,
                    modifier = Modifier.width(140.dp)
                )
                if (backgroundColor.isNotEmpty()) {
                    TextButton(onClick = { onBackgroundColorChange("") }) {
                        Text("Reset to default")
                    }
                }
            }
            Text(
                "Shown behind your page content (and behind the background photo, if set).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Button(onClick = onSave, enabled = !isSaving) {
                Text(if (isSaving) "Saving…" else "Save Settings")
            }
        }
    }
}

@Composable
private fun BackgroundPhotoCard(
    photoUrl: String?,
    isUploading: Boolean,
    onUpload: () -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Background Photo", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRemove, enabled = !isUploading) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text(
                    "No background photo set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onUpload, enabled = !isUploading) {
                Text(
                    when {
                        isUploading -> "Uploading…"
                        photoUrl != null -> "Replace Photo"
                        else -> "Upload Photo"
                    }
                )
            }
        }
    }
}

@Composable
private fun MemberInstrumentsCard(
    member: BandPageMemberUi,
    instruments: List<com.cherryblossomdev.breakroom.data.models.Instrument>,
    onToggleInstrument: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member.photoUrl != null) {
                    AsyncImage(
                        model = RetrofitClient.BASE_URL.trimEnd('/') + member.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (member.firstName?.take(1) ?: member.handle.take(1)).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = listOfNotNull(member.firstName, member.lastName).joinToString(" ").ifEmpty { member.handle },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "@${member.handle}" + if (member.role == "owner") " · owner" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (member.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRowChips(instruments = instruments, selectedIds = member.instrumentIds, onToggle = onToggleInstrument)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowRowChips(
    instruments: List<com.cherryblossomdev.breakroom.data.models.Instrument>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit
) {
    // Simple wrap layout using a Column of Rows since this BOM predates Compose's FlowRow
    val rows = mutableListOf<MutableList<com.cherryblossomdev.breakroom.data.models.Instrument>>()
    var currentRow = mutableListOf<com.cherryblossomdev.breakroom.data.models.Instrument>()
    var currentLength = 0
    instruments.forEach { inst ->
        val length = inst.name.length + 4
        if (currentLength + length > 32 && currentRow.isNotEmpty()) {
            rows.add(currentRow)
            currentRow = mutableListOf()
            currentLength = 0
        }
        currentRow.add(inst)
        currentLength += length
    }
    if (currentRow.isNotEmpty()) rows.add(currentRow)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { inst ->
                    FilterChip(
                        selected = selectedIds.contains(inst.id),
                        onClick = { onToggle(inst.id) },
                        label = { Text(inst.name, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: BandPageSongUi,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = song.onPage, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(song.name?.ifEmpty { "Untitled" } ?: "Untitled", style = MaterialTheme.typography.bodyMedium)
                val meta = listOfNotNull(
                    song.uploaderHandle,
                    song.instrumentName
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (song.onPage) {
                Column {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
