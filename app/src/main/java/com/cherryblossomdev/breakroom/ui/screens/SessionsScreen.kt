package com.cherryblossomdev.breakroom.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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
import com.cherryblossomdev.breakroom.data.models.*
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
        if (granted) viewModel.startRecording(context, viewModel.selectedTab)
    }

    // Save-recording dialog
    if (viewModel.recordingState == RecordingState.SAVING) {
        SaveSessionDialog(viewModel = viewModel)
    }

    // Rating popup (my sessions)
    viewModel.ratingPopupSessionId?.let { sessionId ->
        val session = viewModel.sessions.find { it.id == sessionId }
        if (session != null) {
            RatingPopupDialog(
                currentRating = session.my_rating,
                onRate = { viewModel.submitRating(sessionId, it) },
                onClear = { viewModel.submitRating(sessionId, null) },
                onDismiss = { viewModel.closeRatingPopup() }
            )
        }
    }

    // Rating popup (band member sessions)
    viewModel.bmRatingPopupSessionId?.let { sessionId ->
        val session = viewModel.bandMemberSessions.find { it.id == sessionId }
        if (session != null) {
            RatingPopupDialog(
                currentRating = session.my_rating,
                onRate = { viewModel.submitBmRating(sessionId, it) },
                onClear = { viewModel.submitBmRating(sessionId, null) },
                onDismiss = { viewModel.closeBmRatingPopup() }
            )
        }
    }

    // Create band dialog
    if (viewModel.creatingBand) {
        CreateBandDialog(
            onConfirm = { viewModel.createBand(it) },
            onDismiss = { viewModel.cancelCreatingBand() }
        )
    }

    // Invite member dialog
    viewModel.invitingToBandId?.let { bandId ->
        InviteMemberDialog(
            onConfirm = { handle -> viewModel.inviteMember(bandId, handle) },
            onDismiss = { viewModel.cancelInviting() }
        )
    }

    // Auto-dismiss error
    viewModel.errorMessage?.let {
        LaunchedEffect(it) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab row
            TabRow(selectedTabIndex = viewModel.selectedTab) {
                Tab(
                    selected = viewModel.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Band Practice") }
                )
                Tab(
                    selected = viewModel.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Individual") }
                )
                Tab(
                    selected = viewModel.selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    text = { Text("Bands") }
                )
            }

            val requestRecording: (Int) -> Unit = { tab ->
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    viewModel.startRecording(context, tab)
                } else {
                    // Store which tab is requesting before launching permission dialog
                    viewModel.selectTab(tab)
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            when (viewModel.selectedTab) {
                0 -> BandPracticeTab(viewModel = viewModel, onRecordClick = requestRecording)
                1 -> IndividualTab(viewModel = viewModel, onRecordClick = requestRecording)
                2 -> BandsTab(viewModel = viewModel)
            }
        }

        // Error snackbar
        viewModel.errorMessage?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (viewModel.nowPlayingId != null) 130.dp else 8.dp)
                    .padding(horizontal = 16.dp)
            ) { Text(msg) }
        }

        // Now-playing bar
        if (viewModel.nowPlayingId != null) {
            NowPlayingBar(
                name = viewModel.nowPlayingName ?: "",
                streamUrl = viewModel.nowPlayingUrl,
                authCookie = viewModel.authCookie,
                onClose = { viewModel.stopPlayback() },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

// ===================== Band Practice Tab =====================

@Composable
private fun BandPracticeTab(
    viewModel: SessionsViewModel,
    onRecordClick: (Int) -> Unit
) {
    val activeBands = viewModel.activeBands
    val years = viewModel.availableBandYears()
    val grouped = viewModel.groupedBandSessions()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row with title + record button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Band Practice",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            RecordButton(
                recordingState = viewModel.recordingState,
                recordingSeconds = viewModel.recordingSeconds,
                isThisTab = viewModel.pendingForTab == 0,
                onStart = { onRecordClick(0) },
                onStop = { viewModel.stopRecording() }
            )
        }

        // Band filter
        if (activeBands.isNotEmpty()) {
            BandFilterRow(
                bands = activeBands,
                selected = viewModel.bandPracticeBandFilter,
                onSelect = { viewModel.selectBandPracticeBandFilter(it) }
            )
        }

        // Year tabs
        if (years.isNotEmpty()) {
            YearTabRow(
                years = years,
                selectedYear = viewModel.bandYear,
                onSelect = { viewModel.selectBandYear(it) }
            )
        }

        // Session list
        val bandSessions = viewModel.bandSessions
        if (viewModel.isLoading && bandSessions.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (bandSessions.isEmpty()) {
            EmptyState(message = "No band practice sessions yet", sub = "Tap Record to get started")
        } else if (grouped.isEmpty()) {
            EmptyState(message = "No sessions for this filter")
        } else {
            SessionGroupList(
                grouped = grouped,
                nowPlayingId = viewModel.nowPlayingId,
                onPlay = { viewModel.playSession(it) },
                onRate = { viewModel.openRatingPopup(it.id) },
                onNameChange = { id, name -> viewModel.updateSessionName(id, name) },
                onDateChange = { id, date -> viewModel.updateSessionDate(id, date) },
                onDelete = { viewModel.deleteSession(it) },
                extraInfo = { session -> session.band_name }
            )
        }
    }
}

// ===================== Individual Tab =====================

@Composable
private fun IndividualTab(
    viewModel: SessionsViewModel,
    onRecordClick: (Int) -> Unit
) {
    val years = viewModel.availableIndivYears()
    val grouped = viewModel.groupedIndivSessions()
    val bmYears = viewModel.availableBmYears()
    val bmGrouped = viewModel.groupedBmSessions()
    val bmBands = viewModel.availableBmBands()
    val indivSessions = viewModel.individualSessions

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (viewModel.nowPlayingId != null) 80.dp else 0.dp)
    ) {
        // Header + record button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Individual",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                RecordButton(
                    recordingState = viewModel.recordingState,
                    recordingSeconds = viewModel.recordingSeconds,
                    isThisTab = viewModel.pendingForTab == 1,
                    onStart = { onRecordClick(1) },
                    onStop = { viewModel.stopRecording() }
                )
            }
        }

        // Your Previous Recordings section header
        item {
            Text(
                text = "Your Previous Recordings",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Year tabs for individual sessions
        if (years.isNotEmpty()) {
            item {
                YearTabRow(
                    years = years,
                    selectedYear = viewModel.indivYear,
                    onSelect = { viewModel.selectIndivYear(it) }
                )
            }
        }

        if (viewModel.isLoading && indivSessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else if (indivSessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No individual sessions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            grouped.forEach { (_, monthMap) ->
                monthMap.forEach { (month, monthSessions) ->
                    item {
                        MonthHeader(month = month, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(monthSessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            isPlaying = viewModel.nowPlayingId == session.id,
                            onPlay = { viewModel.playSession(session) },
                            onRate = { viewModel.openRatingPopup(session.id) },
                            onNameChange = { viewModel.updateSessionName(session.id, it) },
                            onDateChange = { viewModel.updateSessionDate(session.id, it) },
                            onDelete = { viewModel.deleteSession(session.id) },
                            extraInfo = session.instrument_name,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // Band Members section
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Band Members",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        // Band filter for band members
        if (bmBands.isNotEmpty()) {
            item {
                BandFilterRow(
                    bands = bmBands,
                    selected = viewModel.bmBandFilter,
                    onSelect = { viewModel.selectBmBandFilter(it) }
                )
            }
        }

        // Year tabs for band members
        if (bmYears.isNotEmpty()) {
            item {
                YearTabRow(
                    years = bmYears,
                    selectedYear = viewModel.bmYear,
                    onSelect = { viewModel.selectBmYear(it) }
                )
            }
        }

        if (viewModel.bandMemberSessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No band member sessions yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            bmGrouped.forEach { (_, monthMap) ->
                monthMap.forEach { (month, monthSessions) ->
                    item {
                        MonthHeader(month = month, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(monthSessions, key = { "bm_${it.id}" }) { session ->
                        BandMemberSessionRow(
                            session = session,
                            isPlaying = viewModel.nowPlayingId == session.id,
                            onPlay = { viewModel.playSession(session) },
                            onRate = { viewModel.openBmRatingPopup(session.id) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ===================== Bands Tab =====================

@Composable
private fun BandsTab(viewModel: SessionsViewModel) {
    val myHandle = viewModel.myHandle

    Column(modifier = Modifier.fillMaxSize()) {
        // Header + create button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bands",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = { viewModel.startCreatingBand() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Create Band")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Pending invites section
            val pending = viewModel.pendingInvites
            if (pending.isNotEmpty()) {
                item {
                    Text(
                        "Pending Invites",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(pending, key = { "invite_${it.id}" }) { band ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(band.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Invited to join", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.respondToInvite(band.id, false) }) {
                                Text("Decline", color = MaterialTheme.colorScheme.error)
                            }
                            Button(onClick = { viewModel.respondToInvite(band.id, true) }) {
                                Text("Accept")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            // Active bands
            val active = viewModel.activeBands
            if (active.isNotEmpty()) {
                item {
                    Text(
                        "Your Bands",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            items(active, key = { it.id }) { band ->
                BandCard(
                    band = band,
                    detail = viewModel.bandDetails[band.id],
                    isExpanded = viewModel.expandedBandId == band.id,
                    myHandle = myHandle,
                    onToggleExpand = { viewModel.toggleExpandBand(band.id) },
                    onInvite = { viewModel.startInviting(band.id) },
                    onRemoveMember = { userId, isSelf ->
                        viewModel.removeBandMember(band.id, userId, isSelf)
                    }
                )
            }

            if (viewModel.bands.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No bands yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Create a band or wait for an invite",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BandCard(
    band: BandListEntry,
    detail: BandDetail?,
    isExpanded: Boolean,
    myHandle: String?,
    onToggleExpand: () -> Unit,
    onInvite: () -> Unit,
    onRemoveMember: (userId: Int, isSelf: Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Band header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(band.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${band.member_count} member${if (band.member_count != 1) "s" else ""} · ${band.role}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Divider()
                if (detail == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                } else {
                    // Member list
                    detail.members.forEach { member ->
                        val isSelf = member.handle == myHandle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "@${member.handle}${if (isSelf) " (you)" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    MemberChip(
                                        text = member.role,
                                        color = if (member.role == "owner")
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    if (member.status != "active") {
                                        MemberChip(
                                            text = member.status,
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    }
                                }
                            }
                            // Remove/leave button
                            if (isSelf && member.role != "owner") {
                                IconButton(
                                    onClick = { onRemoveMember(member.id, true) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ExitToApp,
                                        contentDescription = "Leave band",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else if (!isSelf && detail.my_role == "owner") {
                                IconButton(
                                    onClick = { onRemoveMember(member.id, false) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PersonRemove,
                                        contentDescription = "Remove member",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // Invite button (owner only)
                    if (detail.my_role == "owner") {
                        TextButton(
                            onClick = onInvite,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Invite member")
                        }
                    }
                }
            }
        }
    }
}

// ===================== Shared Components =====================

@Composable
private fun RecordButton(
    recordingState: RecordingState,
    recordingSeconds: Int,
    isThisTab: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    when {
        recordingState == RecordingState.RECORDING && isThisTab -> {
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
        recordingState == RecordingState.SAVING && isThisTab -> {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        recordingState == RecordingState.IDLE -> {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Record")
            }
        }
        else -> {
            // Another tab is recording — show disabled button
            Button(
                onClick = { },
                enabled = false,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Record")
            }
        }
    }
}

@Composable
private fun YearTabRow(
    years: List<Int>,
    selectedYear: Int?,
    onSelect: (Int?) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = run {
            val idx = years.indexOf(selectedYear)
            if (idx == -1) years.size else idx
        },
        edgePadding = 8.dp
    ) {
        years.forEach { year ->
            Tab(
                selected = selectedYear == year,
                onClick = { onSelect(year) },
                text = { Text(year.toString()) }
            )
        }
        Tab(
            selected = selectedYear == null,
            onClick = { onSelect(null) },
            text = { Text("All") }
        )
    }
}

@Composable
private fun BandFilterRow(
    bands: List<BandListEntry>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedBand = bands.find { it.id == selected }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Band:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(selectedBand?.name ?: "All", style = MaterialTheme.typography.bodySmall)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("All bands") },
                    onClick = { onSelect(null); expanded = false }
                )
                bands.forEach { band ->
                    DropdownMenuItem(
                        text = { Text(band.name) },
                        onClick = { onSelect(band.id); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionGroupList(
    grouped: Map<Int, Map<Int, List<Session>>>,
    nowPlayingId: Int?,
    onPlay: (Session) -> Unit,
    onRate: (Session) -> Unit,
    onNameChange: (Int, String) -> Unit,
    onDateChange: (Int, String?) -> Unit,
    onDelete: (Int) -> Unit,
    extraInfo: (Session) -> String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                        isPlaying = nowPlayingId == session.id,
                        onPlay = { onPlay(session) },
                        onRate = { onRate(session) },
                        onNameChange = { onNameChange(session.id, it) },
                        onDateChange = { onDateChange(session.id, it) },
                        onDelete = { onDelete(session.id) },
                        extraInfo = extraInfo(session)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: Int, modifier: Modifier = Modifier) {
    val name = if (month in 1..12) MONTH_NAMES[month] else "Unknown"
    Column(modifier = modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
        Divider()
    }
}

@Composable
private fun SessionRow(
    session: Session,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRate: () -> Unit,
    onNameChange: (String) -> Unit,
    onDateChange: (String?) -> Unit,
    onDelete: () -> Unit,
    extraInfo: String?,
    modifier: Modifier = Modifier
) {
    var editingName by remember { mutableStateOf(false) }
    var nameValue by remember(session.name) { mutableStateOf(session.name) }
    var editingDate by remember { mutableStateOf(false) }
    var dateValue by remember(session.recorded_at) { mutableStateOf(session.recorded_at ?: "") }

    Row(
        modifier = modifier
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
                tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        // Name + date + extra info column
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
                            IconButton(onClick = { onDateChange(dateValue.ifBlank { null }); editingDate = false }) {
                                Icon(Icons.Default.Check, "Save")
                            }
                            IconButton(onClick = { dateValue = session.recorded_at ?: ""; editingDate = false }) {
                                Icon(Icons.Default.Close, "Cancel")
                            }
                        }
                    }
                )
            } else {
                val subLine = buildString {
                    append(session.recorded_at?.take(10) ?: "No date")
                    if (!extraInfo.isNullOrBlank()) append(" · $extraInfo")
                }
                Text(
                    text = subLine,
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

        // Rating
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
private fun BandMemberSessionRow(
    session: Session,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play",
                tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subLine = buildString {
                if (!session.uploader_handle.isNullOrBlank()) append("@${session.uploader_handle}")
                if (!session.band_name.isNullOrBlank()) append(" · ${session.band_name}")
                session.recorded_at?.take(10)?.let { append(" · $it") }
            }
            Text(
                text = subLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        RatingChip(
            avgRating = session.avg_rating,
            ratingCount = session.rating_count,
            myRating = session.my_rating,
            onClick = onRate
        )
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
private fun EmptyState(message: String, sub: String? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MicNone,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sub != null) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ===================== Dialogs =====================

@Composable
private fun SaveSessionDialog(viewModel: SessionsViewModel) {
    val defaultName = "Session - ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
    var name by remember { mutableStateOf(defaultName) }
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var selectedBandId by remember { mutableStateOf<Int?>(null) }
    var selectedInstrumentId by remember { mutableStateOf<Int?>(null) }
    var bandDropdownExpanded by remember { mutableStateOf(false) }
    var instrumentDropdownExpanded by remember { mutableStateOf(false) }

    val isBandPractice = viewModel.pendingForTab == 0
    val activeBands = viewModel.activeBands
    val instruments = viewModel.instruments
    val selectedBand = activeBands.find { it.id == selectedBandId }
    val selectedInstrument = instruments.find { it.id == selectedInstrumentId }

    Dialog(onDismissRequest = { /* require explicit button */ }) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Save Recording",
                    style = MaterialTheme.typography.titleMedium
                )
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
                Spacer(Modifier.height(8.dp))

                if (isBandPractice && activeBands.isNotEmpty()) {
                    // Band selector
                    Box {
                        OutlinedButton(
                            onClick = { bandDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedBand?.name ?: "Select band (optional)", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = bandDropdownExpanded,
                            onDismissRequest = { bandDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("No band") }, onClick = {
                                selectedBandId = null; bandDropdownExpanded = false
                            })
                            activeBands.forEach { band ->
                                DropdownMenuItem(text = { Text(band.name) }, onClick = {
                                    selectedBandId = band.id; bandDropdownExpanded = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (!isBandPractice && instruments.isNotEmpty()) {
                    // Instrument selector
                    Box {
                        OutlinedButton(
                            onClick = { instrumentDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedInstrument?.name ?: "Select instrument (optional)", modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = instrumentDropdownExpanded,
                            onDismissRequest = { instrumentDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(text = { Text("No instrument") }, onClick = {
                                selectedInstrumentId = null; instrumentDropdownExpanded = false
                            })
                            instruments.forEach { instrument ->
                                DropdownMenuItem(text = { Text(instrument.name) }, onClick = {
                                    selectedInstrumentId = instrument.id; instrumentDropdownExpanded = false
                                })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { viewModel.discardPendingRecording() }) { Text("Discard") }
                    Button(onClick = {
                        viewModel.saveRecording(name, date.ifBlank { null }, selectedBandId, selectedInstrumentId)
                    }) { Text("Save") }
                }
            }
        }
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
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CreateBandDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Create Band", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Band name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                        enabled = name.isNotBlank()
                    ) { Text("Create") }
                }
            }
        }
    }
}

@Composable
private fun InviteMemberDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var handle by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Invite Member", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter their username handle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it },
                    label = { Text("Handle") },
                    prefix = { Text("@") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { if (handle.isNotBlank()) onConfirm(handle.trim()) },
                        enabled = handle.isNotBlank()
                    ) { Text("Send Invite") }
                }
            }
        }
    }
}

// ===================== Now Playing Bar =====================

@Composable
private fun NowPlayingBar(
    name: String,
    streamUrl: String?,
    authCookie: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    // tempFile holds the downloaded audio path; null until download is complete
    var tempFilePath by remember { mutableStateOf<String?>(null) }
    val playerRef = remember { mutableStateOf<ExoPlayer?>(null) }
    val httpClient = remember { OkHttpClient.Builder().followRedirects(true).build() }

    // Step 1: download the file to app cache before playing.
    // Streaming the WebM/Opus format via ExoPlayer causes silent audio on the emulator
    // due to an AudioTrack flag mismatch (FAST requested, PRIMARY available). Local file
    // playback uses a different AudioTrack initialization path that works correctly.
    LaunchedEffect(streamUrl) {
        tempFilePath = null
        hasError = false
        isDownloading = streamUrl != null
        if (streamUrl == null) return@LaunchedEffect
        try {
            val path = withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url(streamUrl)
                    .apply { if (authCookie != null) addHeader("Cookie", authCookie) }
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val tmp = File.createTempFile("audio_", ".tmp", context.cacheDir)
                resp.body!!.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                tmp.absolutePath
            }
            tempFilePath = path
        } catch (e: Exception) {
            hasError = true
        }
        isDownloading = false
    }

    // Step 2: create ExoPlayer from the local temp file once download is complete.
    DisposableEffect(tempFilePath) {
        isPrepared = false
        isPlaying = false
        position = 0L
        duration = 0L

        val path = tempFilePath
        val exo = if (path != null) ExoPlayer.Builder(context).build() else null
        playerRef.value = exo

        if (exo != null && path != null) {
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        isPrepared = true
                        duration = exo.duration.coerceAtLeast(0L)
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        isPlaying = false
                        isPrepared = false
                        position = 0L
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlayerError(error: PlaybackException) {
                    hasError = true
                    isPlaying = false
                    isPrepared = false
                }
            })
            exo.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(File(path))))
            exo.prepare()
            exo.playWhenReady = true
        }

        onDispose {
            playerRef.value = null
            exo?.release()
            path?.let { File(it).delete() }
        }
    }

    // Poll position while playing
    val player = playerRef.value
    LaunchedEffect(player, isPrepared) {
        while (player != null && isPrepared) {
            position = player.currentPosition.coerceAtLeast(0L)
            kotlinx.coroutines.delay(500)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        player?.let { if (it.isPlaying) it.pause() else it.play() }
                    },
                    enabled = isPrepared && !hasError
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close player")
                }
            }

            when {
                hasError -> Text(
                    text = "Playback error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 30.dp, bottom = 6.dp)
                )
                isDownloading -> LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp, end = 8.dp, bottom = 6.dp)
                )
                else -> {
                    val durationMs = duration.coerceAtLeast(1L)
                    val positionMs = position.coerceIn(0L, durationMs)
                    val sliderValue = positionMs.toFloat() / durationMs.toFloat()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formatMs(position),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { frac ->
                                player?.let { p ->
                                    val seekMs = (frac * duration).toLong()
                                    p.seekTo(seekMs)
                                    position = seekMs
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            enabled = isPrepared && duration > 0L
                        )
                        Text(
                            text = formatMs(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = color) {
        Text(
            text = text,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

// ===================== Helpers =====================

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
    }
}
