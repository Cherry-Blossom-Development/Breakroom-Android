package com.cherryblossomdev.breakroom.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.testTag

private val MONTH_NAMES = arrayOf(
    "", "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

@Composable
fun SessionsScreen(viewModel: SessionsViewModel, subscriptionViewModel: SubscriptionViewModel) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (viewModel.pendingMashupRecord) {
                viewModel.clearPendingMashupRecord()
                viewModel.startMashupRecording(context)
            } else {
                viewModel.startRecording(context, viewModel.selectedTab)
            }
        } else {
            viewModel.clearPendingMashupRecord()
        }
    }

    // Audio Defaults dialog
    if (viewModel.showAudioDefaults) {
        AudioDefaultsDialog(viewModel = viewModel)
    }

    // Paywall dialog
    if (viewModel.showPaywall) {
        PaywallDialog(
            subscriptionViewModel = subscriptionViewModel,
            onDismiss = { viewModel.dismissPaywall() },
            onSubscribed = {
                viewModel.dismissPaywall()
                subscriptionViewModel.checkSubscription()
            }
        )
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

    val requestMashupRecording: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startMashupRecording(context)
        } else {
            viewModel.requestMashupRecord()
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag("screen-sessions")) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Audio defaults button (gated — not for general public)
            if (viewModel.hasFeature("audio_defaults")) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { viewModel.openAudioDefaults() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Audio Defaults", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

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
                1 -> IndividualTab(
                    viewModel = viewModel,
                    onRecordClick = requestRecording,
                    onMashupRecordClick = requestMashupRecording
                )
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
                bearerToken = viewModel.rawToken,
                mimeType = viewModel.nowPlayingMimeType,
                playbackVolume = viewModel.audioDefaults.playback_volume,
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
                onStop = { viewModel.stopRecording() },
                levelPercent = viewModel.recordingLevelPercent
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
    onRecordClick: (Int) -> Unit,
    onMashupRecordClick: () -> Unit
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
                    onStop = { viewModel.stopRecording() },
                    levelPercent = viewModel.recordingLevelPercent
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

        // Mashups section
        item {
            Spacer(Modifier.height(8.dp))
            MashupsSection(
                viewModel = viewModel,
                onMashupRecordClick = onMashupRecordClick
            )
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
    onStop: () -> Unit,
    levelPercent: Int = 0
) {
    when {
        recordingState == RecordingState.RECORDING && isThisTab -> {
            Column(horizontalAlignment = Alignment.End) {
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
                LinearProgressIndicator(
                    progress = levelPercent / 100f,
                    modifier = Modifier
                        .width(120.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer
                )
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

// Resample mono 16-bit PCM from srcRate to dstRate using linear interpolation.
private fun resample16MonoPcm(src: ByteArray, srcRate: Int, dstRate: Int): ByteArray {
    val srcSamples = src.size / 2
    val dstSamples = (srcSamples.toLong() * dstRate / srcRate).toInt()
    val dst = ByteArray(dstSamples * 2)
    for (i in 0 until dstSamples) {
        val p = i.toDouble() * srcRate / dstRate
        val i0 = p.toInt().coerceAtMost(srcSamples - 1)
        val i1 = (i0 + 1).coerceAtMost(srcSamples - 1)
        val f = p - i0
        val s0 = ((src[i0*2+1].toInt() shl 8) or (src[i0*2].toInt() and 0xFF)).toShort().toInt()
        val s1 = ((src[i1*2+1].toInt() shl 8) or (src[i1*2].toInt() and 0xFF)).toShort().toInt()
        val out = (s0 + (s1 - s0) * f).toInt().toShort()
        dst[i*2] = (out.toInt() and 0xFF).toByte()
        dst[i*2+1] = ((out.toInt() shr 8) and 0xFF).toByte()
    }
    return dst
}

@Composable
private fun NowPlayingBar(
    name: String,
    streamUrl: String?,
    bearerToken: String?,
    mimeType: String?,
    playbackVolume: Float = 0.75f,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (mimeType?.lowercase()?.contains("wav") == true) {
        NowPlayingBarWav(name, streamUrl, bearerToken, playbackVolume, onClose, modifier)
    } else {
        NowPlayingBarExo(name, streamUrl, bearerToken, onClose, modifier)
    }
}

// WAV player using AudioTrack at a fixed 44100 Hz rate.
// AudioTrack-based player for WAV/PCM audio. Downloads via OkHttp with Bearer auth,
// resamples to 44100 Hz, normalizes peak amplitude, then streams via AudioTrack.
@Composable
private fun NowPlayingBarWav(
    name: String,
    url: String?,
    bearerToken: String?,
    playbackVolume: Float = 0.75f,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val pcmRef = remember { arrayOf<ByteArray?>(null) }
    val trackRef = remember { arrayOf<AudioTrack?>(null) }
    val jobRef = remember { arrayOf<kotlinx.coroutines.Job?>(null) }
    val offsetRef = remember { intArrayOf(0) }
    val totalDurRef = remember { longArrayOf(0L) }

    fun stopPlayback() {
        jobRef[0]?.cancel(); jobRef[0] = null
        try { trackRef[0]?.pause() } catch (_: Exception) {}
    }

    fun startPlayback() {
        val track = trackRef[0] ?: return
        val pcm = pcmRef[0] ?: return
        try { track.play() } catch (_: Exception) { return }
        jobRef[0] = scope.launch(Dispatchers.IO) {
            val chunk = 8192
            while (isActive && offsetRef[0] < pcm.size) {
                val toWrite = minOf(chunk, pcm.size - offsetRef[0])
                val result = track.write(pcm, offsetRef[0], toWrite)
                if (result > 0) {
                    offsetRef[0] += result
                    withContext(Dispatchers.Main) {
                        position = offsetRef[0].toLong() * totalDurRef[0] / pcm.size
                    }
                } else if (result < 0) break
            }
            if (isActive) withContext(Dispatchers.Main) {
                isPlaying = false; offsetRef[0] = 0; position = 0L
            }
        }
    }

    DisposableEffect(url) {
        isPrepared = false; isPlaying = false; hasError = false
        position = 0L; duration = 0L; isPreparing = url != null
        pcmRef[0] = null
        trackRef[0]?.release(); trackRef[0] = null
        offsetRef[0] = 0; totalDurRef[0] = 0L

        val dlJob = if (url != null) scope.launch(Dispatchers.IO) {
            try {
                val targetRate = 44100

                // Download real WAV via OkHttp with Bearer auth
                val req = Request.Builder().url(url)
                    .apply { if (bearerToken != null) addHeader("Authorization", "Bearer $bearerToken") }
                    .build()
                val bytes = OkHttpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                    resp.body?.bytes() ?: throw Exception("Empty body")
                }

                // Parse WAV header
                val srcRate = (bytes[24].toInt() and 0xFF) or ((bytes[25].toInt() and 0xFF) shl 8) or
                    ((bytes[26].toInt() and 0xFF) shl 16) or ((bytes[27].toInt() and 0xFF) shl 24)
                val channels = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)

                // Locate "data" chunk
                var dataStart = 44
                run {
                    var i = 12
                    while (i < bytes.size - 8) {
                        if (bytes[i]=='d'.code.toByte() && bytes[i+1]=='a'.code.toByte() &&
                            bytes[i+2]=='t'.code.toByte() && bytes[i+3]=='a'.code.toByte()) {
                            dataStart = i + 8; break
                        }
                        val sz = (bytes[i+4].toInt() and 0xFF) or ((bytes[i+5].toInt() and 0xFF) shl 8) or
                            ((bytes[i+6].toInt() and 0xFF) shl 16) or ((bytes[i+7].toInt() and 0xFF) shl 24)
                        i += 8 + sz
                    }
                }

                // Validate RIFF header — if wrong, the downloaded bytes aren't a WAV
                val header0 = bytes[0].toInt() and 0xFF
                val header1 = bytes[1].toInt() and 0xFF
                val header2 = bytes[2].toInt() and 0xFF
                val header3 = bytes[3].toInt() and 0xFF
                if (header0 != 'R'.code || header1 != 'I'.code || header2 != 'F'.code || header3 != 'F'.code) {
                    throw Exception("Not RIFF: bytes=${header0},${header1},${header2},${header3} size=${bytes.size}")
                }

                val rawPcm = bytes.copyOfRange(dataStart, bytes.size)
                val origByteRate = srcRate.toLong() * channels * 2
                val wavDurMs = if (origByteRate > 0) rawPcm.size.toLong() * 1000L / origByteRate else 0L

                // Resample to 44100 Hz if needed (fallback for pre-existing non-normalized files)
                val wavPcm = if (srcRate != targetRate && channels == 1) {
                    resample16MonoPcm(rawPcm, srcRate, targetRate)
                } else rawPcm

                // Android playback gain: AudioTrack plays raw PCM at face value while browsers
                // apply their own loudness management, making Android sound ~6 dB quieter.
                // 0.75 default maps to 2.5x gain (3.33 * 0.75). User can adjust via Audio Defaults.
                val PLAYBACK_GAIN = 3.33f * playbackVolume
                for (i in 0 until wavPcm.size - 1 step 2) {
                    val s = ((wavPcm[i+1].toInt() shl 8) or (wavPcm[i].toInt() and 0xFF)).toShort().toInt()
                    val boosted = (s * PLAYBACK_GAIN).toInt().coerceIn(-32768, 32767)
                    wavPcm[i]   = (boosted and 0xFF).toByte()
                    wavPcm[i+1] = ((boosted shr 8) and 0xFF).toByte()
                }

                // AudioTrack at 44100 Hz (native rate — no hardware resampling needed)
                val channelMask = if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                val minBuf = AudioTrack.getMinBufferSize(targetRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(targetRate)
                        .setChannelMask(channelMask)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                    maxOf(minBuf, 65536),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )

                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    track.release()
                    throw Exception("AudioTrack init failed (state=${track.state})")
                }

                withContext(Dispatchers.Main) {
                    if (!isActive) { track.release(); return@withContext }
                    trackRef[0] = track
                    pcmRef[0] = wavPcm
                    totalDurRef[0] = wavDurMs
                    duration = wavDurMs
                    isPreparing = false; isPrepared = true; isPlaying = true
                    startPlayback()
                }
            } catch (e: Exception) {
                if (isActive) withContext(Dispatchers.Main) {
                    isPreparing = false; hasError = true; errorMsg = e.message ?: "Unknown error"
                }
            }
        } else null

        onDispose {
            dlJob?.cancel()
            jobRef[0]?.cancel(); jobRef[0] = null
            trackRef[0]?.release(); trackRef[0] = null
        }
    }

    NowPlayingBarUi(
        name = name,
        isPlaying = isPlaying,
        isPrepared = isPrepared,
        hasError = hasError,
        errorMsg = errorMsg,
        isPreparing = isPreparing,
        position = position,
        duration = duration,
        onPlayPause = {
            if (isPlaying) { stopPlayback(); isPlaying = false }
            else { isPlaying = true; startPlayback() }
        },
        onSeek = { frac ->
            val pcm = pcmRef[0] ?: return@NowPlayingBarUi
            val seekMs = (frac * duration).toLong()
            val newOff = (seekMs * pcm.size / duration).toInt().coerceIn(0, pcm.size - 1)
            val wasPlaying = isPlaying
            if (wasPlaying) stopPlayback()
            trackRef[0]?.flush()
            offsetRef[0] = newOff; position = seekMs
            if (wasPlaying) startPlayback()
        },
        onClose = onClose,
        modifier = modifier
    )
}

// ExoPlayer-based player for compressed formats (M4A, MP3, etc.)
@Composable
private fun NowPlayingBarExo(
    name: String,
    streamUrl: String?,
    bearerToken: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var isPreparing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    val playerRef = remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(streamUrl) {
        isPrepared = false; isPlaying = false; hasError = false
        position = 0L; duration = 0L; isPreparing = streamUrl != null

        var exo: ExoPlayer? = null

        if (streamUrl != null) {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val req = if (bearerToken != null)
                        chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer $bearerToken")
                            .build()
                    else chain.request()
                    chain.proceed(req)
                }
                .build()

            val mediaSourceFactory = DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttpClient))

            exo = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            isPreparing = false; isPrepared = true
                            duration = exo!!.duration.coerceAtLeast(0L)
                        }
                        Player.STATE_ENDED -> { isPlaying = false; isPrepared = false; position = 0L }
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayerError(error: PlaybackException) {
                    isPreparing = false; hasError = true; isPlaying = false; isPrepared = false
                    errorMsg = "err=${error.errorCode} cause=${error.cause?.javaClass?.simpleName}"
                }
            })

            exo.setMediaItem(MediaItem.fromUri(streamUrl))
            exo.prepare()
            exo.playWhenReady = true
        }

        playerRef.value = exo

        onDispose { playerRef.value = null; exo?.release() }
    }

    val player = playerRef.value
    LaunchedEffect(player, isPlaying) {
        while (player != null && isPlaying) {
            position = player.currentPosition.coerceAtLeast(0L)
            delay(500)
        }
    }

    NowPlayingBarUi(
        name = name,
        isPlaying = isPlaying,
        isPrepared = isPrepared,
        hasError = hasError,
        errorMsg = errorMsg,
        isPreparing = isPreparing,
        position = position,
        duration = duration,
        onPlayPause = { player?.let { if (it.isPlaying) it.pause() else it.play() } },
        onSeek = { frac ->
            player?.let { p ->
                val seekMs = (frac * duration).toLong()
                p.seekTo(seekMs); position = seekMs
            }
        },
        onClose = onClose,
        modifier = modifier
    )
}

@Composable
private fun NowPlayingBarUi(
    name: String,
    isPlaying: Boolean,
    isPrepared: Boolean,
    hasError: Boolean,
    errorMsg: String,
    isPreparing: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                IconButton(onClick = onPlayPause, enabled = isPrepared && !hasError) {
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
                    text = "Error: $errorMsg",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 30.dp, bottom = 6.dp)
                )
                isPreparing -> LinearProgressIndicator(
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
                            onValueChange = onSeek,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
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

// ===================== Audio Defaults Dialog =====================

@Composable
private fun AudioDefaultsDialog(viewModel: SessionsViewModel) {
    Dialog(onDismissRequest = { viewModel.closeAudioDefaults() }) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Audio Defaults", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Echo Cancellation", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.audioDefaults.echo_cancellation,
                        onCheckedChange = { viewModel.setAudioDefault(echoCancellation = it) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Noise Suppression", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.audioDefaults.noise_suppression,
                        onCheckedChange = { viewModel.setAudioDefault(noiseSuppression = it) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Gain Control", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = viewModel.audioDefaults.auto_gain_control,
                        onCheckedChange = { viewModel.setAudioDefault(autoGainControl = it) }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Volume", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${(viewModel.audioDefaults.playback_volume * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(36.dp)
                    )
                }
                Slider(
                    value = viewModel.audioDefaults.playback_volume,
                    onValueChange = { viewModel.setAudioDefault(playbackVolume = it) },
                    modifier = Modifier.fillMaxWidth(),
                    valueRange = 0f..1f
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewModel.audioDefaultsSaved) {
                        Text("Saved!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { viewModel.closeAudioDefaults() }) { Text("Close") }
                    Button(
                        onClick = { viewModel.saveAudioDefaults() },
                        enabled = !viewModel.audioDefaultsSaving
                    ) {
                        if (viewModel.audioDefaultsSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

// ===================== Mashups Section =====================

@Composable
private fun MashupsSection(
    viewModel: SessionsViewModel,
    onMashupRecordClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Backing ExoPlayer (for playback during recording and "Play Both" preview)
    val backingExoRef = remember { arrayOf<ExoPlayer?>(null) }
    var isBackingPlaying by remember { mutableStateOf(false) }

    // AudioTrack for playing local new recording during "Play Both" preview
    val newTrackRef = remember { arrayOf<AudioTrack?>(null) }
    val newJobRef = remember { arrayOf<kotlinx.coroutines.Job?>(null) }
    var isNewPlaying by remember { mutableStateOf(false) }

    var sourceDropdownExpanded by remember { mutableStateOf(false) }

    val isMashupRecording = viewModel.recordingState == RecordingState.RECORDING && viewModel.pendingForTab == 3

    // Band options for source selector
    val bandOptions = viewModel.activeBands.filter { band ->
        viewModel.bandMemberSessions.any { it.band_id == band.id }
    }

    val filteredSessions = viewModel.filteredMashupSessions()

    DisposableEffect(Unit) {
        onDispose {
            backingExoRef[0]?.release(); backingExoRef[0] = null
            newJobRef[0]?.cancel(); newJobRef[0] = null
            newTrackRef[0]?.release(); newTrackRef[0] = null
        }
    }

    fun stopBackingPlayer() {
        backingExoRef[0]?.stop()
        backingExoRef[0]?.release()
        backingExoRef[0] = null
        isBackingPlaying = false
    }

    fun stopNewPlayer() {
        newJobRef[0]?.cancel(); newJobRef[0] = null
        try { newTrackRef[0]?.pause() } catch (_: Exception) {}
        newTrackRef[0]?.release(); newTrackRef[0] = null
        isNewPlaying = false
    }

    fun stopBothPlayers() { stopBackingPlayer(); stopNewPlayer() }

    fun startBackingPlayer(url: String, token: String?) {
        stopBackingPlayer()
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = if (token != null)
                    chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
                else chain.request()
                chain.proceed(req)
            }
            .build()
        val exo = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(okHttp)))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isBackingPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { isBackingPlaying = false }
            }
        })
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        backingExoRef[0] = exo
        isBackingPlaying = true
    }

    fun playBoth() {
        val backingUrl = viewModel.mashupBackingUrl ?: return
        val mashupFile = viewModel.mashupFile ?: return
        stopBothPlayers()

        // Play backing via ExoPlayer
        startBackingPlayer(backingUrl, viewModel.rawToken)

        // Play new recording via AudioTrack
        scope.launch(Dispatchers.IO) {
            try {
                val bytes = mashupFile.readBytes()
                // Find data chunk
                var dataStart = 44; var i = 12
                while (i < bytes.size - 8) {
                    if (bytes[i] == 'd'.code.toByte() && bytes[i+1] == 'a'.code.toByte() &&
                        bytes[i+2] == 't'.code.toByte() && bytes[i+3] == 'a'.code.toByte()) {
                        dataStart = i + 8; break
                    }
                    val sz = (bytes[i+4].toInt() and 0xFF) or ((bytes[i+5].toInt() and 0xFF) shl 8) or
                        ((bytes[i+6].toInt() and 0xFF) shl 16) or ((bytes[i+7].toInt() and 0xFF) shl 24)
                    i += 8 + sz.coerceAtLeast(0)
                }
                val pcm = bytes.copyOfRange(dataStart.coerceIn(0, bytes.size), bytes.size)
                val minBuf = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                    maxOf(minBuf, 65536),
                    AudioTrack.MODE_STREAM,
                    android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    newTrackRef[0] = track
                    withContext(Dispatchers.Main) { isNewPlaying = true }
                    track.play()
                    var off = 0
                    val job = scope.launch(Dispatchers.IO) {
                        while (isActive && off < pcm.size) {
                            val toWrite = minOf(8192, pcm.size - off)
                            val written = track.write(pcm, off, toWrite)
                            if (written > 0) off += written else break
                        }
                        withContext(Dispatchers.Main) { isNewPlaying = false }
                    }
                    newJobRef[0] = job
                } else {
                    track.release()
                }
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Section header
        Text(
            text = "Mashups",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        Divider()
        Spacer(Modifier.height(8.dp))

        // Source selector
        Text("Backing Track Source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { sourceDropdownExpanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = when {
                    viewModel.mashupSource == "own" -> "My Sessions"
                    viewModel.mashupSource.startsWith("band-") -> {
                        val bandId = viewModel.mashupSource.removePrefix("band-").toIntOrNull()
                        viewModel.activeBands.find { it.id == bandId }?.name ?: "Band"
                    }
                    else -> "My Sessions"
                }
                Text(label, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }) {
                DropdownMenuItem(text = { Text("My Sessions") }, onClick = {
                    viewModel.updateMashupSource("own"); sourceDropdownExpanded = false
                })
                bandOptions.forEach { band ->
                    DropdownMenuItem(text = { Text(band.name) }, onClick = {
                        viewModel.updateMashupSource("band-${band.id}"); sourceDropdownExpanded = false
                    })
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Search
        OutlinedTextField(
            value = viewModel.mashupSearch,
            onValueChange = { viewModel.updateMashupSearch(it) },
            placeholder = { Text("Search sessions...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (viewModel.mashupSearch.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateMashupSearch("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        // Session list for backing track selection (max 5 visible, scrollable in a fixed height box)
        if (filteredSessions.isEmpty()) {
            Text("No sessions found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                filteredSessions.take(20).forEach { session ->
                    val isSelected = viewModel.mashupBackingSession?.id == session.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectMashupBacking(session) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!session.uploader_handle.isNullOrBlank()) {
                                Text("@${session.uploader_handle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        session.recorded_at?.take(10)?.let { date ->
                            Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Record new part
        Text("Your New Recording", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))

        when {
            isMashupRecording -> {
                // Stop button with level meter
                Column(horizontalAlignment = Alignment.Start) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = formatDuration(viewModel.recordingSeconds),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = {
                                stopBackingPlayer()
                                viewModel.stopMashupRecording()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = viewModel.recordingLevelPercent / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.errorContainer
                    )
                }
            }
            viewModel.mashupFile != null -> {
                // Existing recording
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Recording ready", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            stopBothPlayers()
                            viewModel.clearMashupRecording()
                            if (viewModel.mashupBackingSession != null && viewModel.mashupBackingUrl != null) {
                                startBackingPlayer(viewModel.mashupBackingUrl!!, viewModel.rawToken)
                            }
                            onMashupRecordClick()
                        }
                    ) { Text("Re-record") }
                    IconButton(
                        onClick = { stopBothPlayers(); viewModel.clearMashupRecording() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear recording", modifier = Modifier.size(16.dp))
                    }
                }
            }
            else -> {
                // Record button
                Button(
                    onClick = {
                        if (viewModel.mashupBackingSession != null && viewModel.mashupBackingUrl != null) {
                            startBackingPlayer(viewModel.mashupBackingUrl!!, viewModel.rawToken)
                        }
                        onMashupRecordClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = viewModel.recordingState == RecordingState.IDLE
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Record")
                }
                if (viewModel.mashupBackingSession != null) {
                    Text(
                        "Backing track will play during recording",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Controls visible when both backing and recording exist
        if (viewModel.mashupBackingSession != null && viewModel.mashupFile != null && !isMashupRecording) {
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Volume sliders
            Text("Backing Volume: ${(viewModel.mashupBackingVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = viewModel.mashupBackingVolume,
                onValueChange = { viewModel.updateMashupBackingVolume(it) },
                modifier = Modifier.fillMaxWidth(),
                valueRange = 0f..1f
            )
            Text("New Recording Volume: ${(viewModel.mashupNewVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall)
            Slider(
                value = viewModel.mashupNewVolume,
                onValueChange = { viewModel.updateMashupNewVolume(it) },
                modifier = Modifier.fillMaxWidth(),
                valueRange = 0f..1f
            )

            Spacer(Modifier.height(8.dp))

            // Play Both button
            val isPlayingBoth = isBackingPlaying || isNewPlaying
            OutlinedButton(
                onClick = {
                    if (isPlayingBoth) stopBothPlayers()
                    else playBoth()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isPlayingBoth) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isPlayingBoth) "Stop Preview" else "Play Both")
            }

            Spacer(Modifier.height(8.dp))

            // Name + date
            OutlinedTextField(
                value = viewModel.mashupName,
                onValueChange = { viewModel.updateMashupName(it) },
                label = { Text("Session name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = viewModel.mashupRecordedAt,
                onValueChange = { viewModel.updateMashupRecordedAt(it) },
                label = { Text("Date (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Error messages
            viewModel.mergeError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }
            viewModel.mashupUploadError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }

            // Save Merged button
            Button(
                onClick = {
                    stopBothPlayers()
                    viewModel.saveMerged(context)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isMerging && !viewModel.mashupUploading
            ) {
                if (viewModel.isMerging || viewModel.mashupUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Icon(Icons.Default.CallMerge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Merged")
                }
            }
        }
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
