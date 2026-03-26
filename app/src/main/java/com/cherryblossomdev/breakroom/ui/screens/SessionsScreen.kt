package com.cherryblossomdev.breakroom.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MONTH_NAMES = arrayOf(
    "", "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

@Composable
fun SessionsScreen(viewModel: SessionsViewModel) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording(context)
    }

    // Save-recording dialog shown when recording stops
    if (viewModel.recordingState == RecordingState.SAVING) {
        SaveSessionDialog(
            defaultName = "Session - ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}",
            onSave = { name, date -> viewModel.saveRecording(name, date) },
            onDiscard = { viewModel.discardPendingRecording() }
        )
    }

    // Rating popup
    viewModel.ratingPopupSessionId?.let { sessionId ->
        val session = viewModel.sessions.find { it.id == sessionId }
        if (session != null) {
            RatingPopupDialog(
                currentRating = session.my_rating,
                onRate = { rating -> viewModel.submitRating(sessionId, rating) },
                onClear = { viewModel.submitRating(sessionId, null) },
                onDismiss = { viewModel.closeRatingPopup() }
            )
        }
    }

    // Auto-dismiss error after 3 s
    viewModel.errorMessage?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header + record button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                RecordButton(
                    recordingState = viewModel.recordingState,
                    recordingSeconds = viewModel.recordingSeconds,
                    onStart = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) viewModel.startRecording(context)
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStop = { viewModel.stopRecording() }
                )
            }

            // Year tab bar
            val years = viewModel.availableYears()
            if (years.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = run {
                        val idx = years.indexOf(viewModel.selectedYear)
                        if (idx == -1) years.size else idx
                    },
                    edgePadding = 8.dp
                ) {
                    years.forEach { year ->
                        Tab(
                            selected = viewModel.selectedYear == year,
                            onClick = { viewModel.selectYear(year) },
                            text = { Text(year.toString()) }
                        )
                    }
                    Tab(
                        selected = viewModel.selectedYear == null,
                        onClick = { viewModel.selectYear(null) },
                        text = { Text("All") }
                    )
                }
            }

            if (viewModel.isLoading && viewModel.sessions.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (viewModel.sessions.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MicNone,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No sessions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Tap the record button to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val grouped = viewModel.groupedSessions()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (viewModel.nowPlayingId != null) 80.dp else 0.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    grouped.forEach { (year, monthMap) ->
                        item {
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        monthMap.forEach { (month, monthSessions) ->
                            item { MonthHeader(month = month) }
                            items(monthSessions, key = { it.id }) { session ->
                                SessionRow(
                                    session = session,
                                    isPlaying = viewModel.nowPlayingId == session.id,
                                    onPlay = { viewModel.playSession(session) },
                                    onRate = { viewModel.openRatingPopup(session.id) },
                                    onNameChange = { name -> viewModel.updateSessionName(session.id, name) },
                                    onDateChange = { date -> viewModel.updateSessionDate(session.id, date) },
                                    onDelete = { viewModel.deleteSession(session.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Error snackbar
        viewModel.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (viewModel.nowPlayingId != null) 88.dp else 8.dp)
                    .padding(horizontal = 16.dp)
            ) { Text(msg) }
        }

        // Now-playing bar
        if (viewModel.nowPlayingId != null) {
            NowPlayingBar(
                name = viewModel.nowPlayingName ?: "",
                streamUrl = viewModel.nowPlayingUrl,
                onClose = { viewModel.stopPlayback() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun RecordButton(
    recordingState: RecordingState,
    recordingSeconds: Int,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    when (recordingState) {
        RecordingState.IDLE -> {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Record")
            }
        }
        RecordingState.RECORDING -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatDuration(recordingSeconds),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
        }
        RecordingState.SAVING -> {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun MonthHeader(month: Int) {
    val name = if (month in 1..12) MONTH_NAMES[month] else "Unknown"
    Text(
        text = name,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
    Divider()
}

@Composable
private fun SessionRow(
    session: com.cherryblossomdev.breakroom.data.models.Session,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRate: () -> Unit,
    onNameChange: (String) -> Unit,
    onDateChange: (String?) -> Unit,
    onDelete: () -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var nameValue by remember(session.name) { mutableStateOf(session.name) }
    var editingDate by remember { mutableStateOf(false) }
    var dateValue by remember(session.recorded_at) { mutableStateOf(session.recorded_at ?: "") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Play/stop button
        IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                tint = if (isPlaying) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }

        // Name + date column
        Column(modifier = Modifier.weight(1f)) {
            if (editingName) {
                OutlinedTextField(
                    value = nameValue,
                    onValueChange = { nameValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { onNameChange(nameValue); editingName = false }) {
                                Icon(Icons.Default.Check, "Save")
                            }
                            IconButton(onClick = { nameValue = session.name; editingName = false }) {
                                Icon(Icons.Default.Close, "Cancel")
                            }
                        }
                    }
                )
            } else {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { editingName = true }
                )
            }

            if (editingDate) {
                OutlinedTextField(
                    value = dateValue,
                    onValueChange = { dateValue = it },
                    singleLine = true,
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = {
                                onDateChange(dateValue.ifBlank { null })
                                editingDate = false
                            }) {
                                Icon(Icons.Default.Check, "Save")
                            }
                            IconButton(onClick = {
                                dateValue = session.recorded_at ?: ""
                                editingDate = false
                            }) {
                                Icon(Icons.Default.Close, "Cancel")
                            }
                        }
                    }
                )
            } else {
                Text(
                    text = session.recorded_at?.take(10) ?: "No date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { editingDate = true }
                )
            }
        }

        // File size
        session.file_size?.let { size ->
            Text(
                text = formatFileSize(size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Rating chip
        RatingChip(
            avgRating = session.avg_rating,
            ratingCount = session.rating_count,
            myRating = session.my_rating,
            onClick = onRate
        )

        // Delete
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun RatingChip(
    avgRating: Double?,
    ratingCount: Int,
    myRating: Int?,
    onClick: () -> Unit
) {
    val text = if (avgRating != null && ratingCount > 0) {
        "★ %.1f (%d)".format(avgRating, ratingCount)
    } else {
        "Rate"
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (myRating != null) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RatingPopupDialog(
    currentRating: Int?,
    onRate: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Rate this session", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n -> RatingButton(n, n == currentRating, onRate) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (6..10).forEach { n -> RatingButton(n, n == currentRating, onRate) }
                }
                if (currentRating != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onClear) { Text("Clear rating") }
                }
            }
        }
    }
}

@Composable
private fun RatingButton(value: Int, selected: Boolean, onRate: (Int) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onRate(value) }
    ) {
        Text(
            text = value.toString(),
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SaveSessionDialog(
    defaultName: String,
    onSave: (name: String, recordedAt: String?) -> Unit,
    onDiscard: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }

    Dialog(onDismissRequest = { /* require explicit button */ }) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Save Recording", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Session name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDiscard) { Text("Discard") }
                    Button(onClick = { onSave(name, date.ifBlank { null }) }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    name: String,
    streamUrl: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(streamUrl) {
        if (streamUrl != null) {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(streamUrl)
                mediaPlayer.prepareAsync()
                mediaPlayer.setOnPreparedListener { mp ->
                    mp.start()
                    isPlaying = true
                }
                mediaPlayer.setOnCompletionListener { isPlaying = false }
            } catch (e: Exception) {
                // ignore
            }
        }
        onDispose {
            try { mediaPlayer.stop() } catch (e: Exception) { }
            mediaPlayer.release()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (streamUrl == null) Icons.Default.HourglassEmpty else Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        streamUrl == null -> "Loading..."
                        isPlaying -> "Playing"
                        else -> "Ready"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close player")
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
    }
}
