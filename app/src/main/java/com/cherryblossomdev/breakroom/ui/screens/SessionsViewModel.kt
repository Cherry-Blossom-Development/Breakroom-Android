package com.cherryblossomdev.breakroom.ui.screens

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.SessionsRepository
import com.cherryblossomdev.breakroom.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecordingState { IDLE, RECORDING, SAVING }

class SessionsViewModel(private val repository: SessionsRepository) : ViewModel() {

    // ===== Tab =====
    var selectedTab by mutableStateOf(0)
        private set

    // ===== My sessions (all, split by session_type in getters) =====
    var sessions by mutableStateOf<List<Session>>(emptyList())
        private set

    // ===== Band member sessions =====
    var bandMemberSessions by mutableStateOf<List<Session>>(emptyList())
        private set

    // ===== Reference data =====
    var bands by mutableStateOf<List<BandListEntry>>(emptyList())
        private set
    var bandDetails by mutableStateOf<Map<Int, BandDetail>>(emptyMap())
        private set
    var instruments by mutableStateOf<List<Instrument>>(emptyList())
        private set

    // ===== Year tabs per area =====
    var bandYear by mutableStateOf<Int?>(null)
        private set
    var indivYear by mutableStateOf<Int?>(null)
        private set
    var bmYear by mutableStateOf<Int?>(null)
        private set

    // ===== Band filters =====
    var bandPracticeBandFilter by mutableStateOf<Int?>(null)
        private set
    var bmBandFilter by mutableStateOf<Int?>(null)
        private set

    // ===== Recording =====
    var recordingState by mutableStateOf(RecordingState.IDLE)
        private set
    var recordingSeconds by mutableStateOf(0)
        private set
    var pendingRecordingFile by mutableStateOf<File?>(null)
        private set
    var pendingForTab by mutableStateOf(0)
        private set

    // ===== Now playing =====
    var nowPlayingId by mutableStateOf<Int?>(null)
        private set
    var nowPlayingName by mutableStateOf<String?>(null)
        private set
    var nowPlayingUrl by mutableStateOf<String?>(null)
        private set
    var nowPlayingMimeType by mutableStateOf<String?>(null)
        private set

    // ===== Rating popups =====
    var ratingPopupSessionId by mutableStateOf<Int?>(null)
        private set
    var bmRatingPopupSessionId by mutableStateOf<Int?>(null)
        private set

    // ===== Loading / error =====
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // ===== Band management UI =====
    var expandedBandId by mutableStateOf<Int?>(null)
        private set
    var creatingBand by mutableStateOf(false)
        private set
    var invitingToBandId by mutableStateOf<Int?>(null)
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var recordingFile: File? = null

    val myHandle: String? get() = repository.getMyHandle()
    val authCookie: String? get() = repository.getAuthCookie()
    val rawToken: String? get() = repository.getRawToken()

    // ===== Derived =====
    val bandSessions get() = sessions.filter { it.session_type == "band" }
    val individualSessions get() = sessions.filter { it.session_type != "band" }
    val activeBands get() = bands.filter { it.status == "active" }
    val pendingInvites get() = bands.filter { it.status == "invited" }

    // ===== Load =====

    fun loadAll() {
        loadSessions()
        loadBandMemberSessions()
        loadBands()
        loadInstruments()
    }

    fun loadSessions() {
        viewModelScope.launch {
            isLoading = true
            when (val result = withContext(Dispatchers.IO) { repository.getSessions() }) {
                is BreakroomResult.Success -> {
                    sessions = result.data
                    if (bandYear == null) bandYear = defaultYear(bandSessions)
                    if (indivYear == null) indivYear = defaultYear(individualSessions)
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
            isLoading = false
        }
    }

    fun loadBandMemberSessions() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getBandMemberSessions() }) {
                is BreakroomResult.Success -> {
                    bandMemberSessions = result.data
                    if (bmYear == null) bmYear = defaultYear(result.data)
                }
                is BreakroomResult.Error -> { /* not critical */ }
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    fun loadBands() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getBands() }) {
                is BreakroomResult.Success -> bands = result.data
                is BreakroomResult.Error -> { /* not critical */ }
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    fun loadInstruments() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getInstruments() }) {
                is BreakroomResult.Success -> instruments = result.data
                else -> { /* not critical */ }
            }
        }
    }

    fun loadBandDetail(bandId: Int) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getBandDetail(bandId) }) {
                is BreakroomResult.Success -> bandDetails = bandDetails + (bandId to result.data)
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    // ===== Tab / filter selection =====

    fun selectTab(tab: Int) { selectedTab = tab }
    fun selectBandYear(year: Int?) { bandYear = year }
    fun selectIndivYear(year: Int?) { indivYear = year }
    fun selectBmYear(year: Int?) { bmYear = year }
    fun selectBandPracticeBandFilter(bandId: Int?) { bandPracticeBandFilter = bandId }
    fun selectBmBandFilter(bandId: Int?) { bmBandFilter = bandId }

    // ===== Filtered + grouped session helpers =====

    fun filteredBandSessions(): List<Session> {
        var list = bandSessions
        if (bandPracticeBandFilter != null) list = list.filter { it.band_id == bandPracticeBandFilter }
        if (bandYear != null) list = list.filter { yearFromDate(it.recorded_at ?: it.uploaded_at) == bandYear }
        return list
    }

    fun filteredIndivSessions(): List<Session> {
        var list = individualSessions
        if (indivYear != null) list = list.filter { yearFromDate(it.recorded_at ?: it.uploaded_at) == indivYear }
        return list
    }

    fun filteredBmSessions(): List<Session> {
        var list = bandMemberSessions
        if (bmBandFilter != null) list = list.filter { it.band_id == bmBandFilter }
        if (bmYear != null) list = list.filter { yearFromDate(it.recorded_at ?: it.uploaded_at) == bmYear }
        return list
    }

    fun groupedBandSessions() = groupSessions(filteredBandSessions())
    fun groupedIndivSessions() = groupSessions(filteredIndivSessions())
    fun groupedBmSessions() = groupSessions(filteredBmSessions())

    fun availableBandYears() = availableYears(bandSessions)
    fun availableIndivYears() = availableYears(individualSessions)
    fun availableBmYears() = availableYears(bandMemberSessions)

    fun availableBmBands(): List<BandListEntry> {
        val bandIds = bandMemberSessions.mapNotNull { it.band_id }.toSet()
        return activeBands.filter { it.id in bandIds }
    }

    // ===== Recording =====

    fun startRecording(context: Context, forTab: Int) {
        pendingForTab = forTab
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "session_$timestamp.m4a")
        recordingFile = file

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        mediaRecorder = recorder
        recordingSeconds = 0
        recordingState = RecordingState.RECORDING

        timerJob = viewModelScope.launch {
            while (true) { delay(1000); recordingSeconds++ }
        }
    }

    fun stopRecording() {
        timerJob?.cancel(); timerJob = null
        try { mediaRecorder?.stop() } catch (e: Exception) { }
        mediaRecorder?.release(); mediaRecorder = null
        pendingRecordingFile = recordingFile
        recordingState = RecordingState.SAVING
    }

    fun discardPendingRecording() {
        pendingRecordingFile?.delete()
        pendingRecordingFile = null
        recordingFile = null
        recordingState = RecordingState.IDLE
    }

    fun saveRecording(name: String, recordedAt: String?, bandId: Int?, instrumentId: Int?) {
        val file = pendingRecordingFile ?: return
        val sessionType = if (pendingForTab == 0) "band" else "individual"
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                repository.uploadSession(file, name.ifBlank { "Session" }, recordedAt, sessionType, bandId, instrumentId)
            }
            when (result) {
                is BreakroomResult.Success -> {
                    sessions = listOf(result.data) + sessions
                    val year = yearFromDate(result.data.recorded_at ?: result.data.uploaded_at)
                    if (pendingForTab == 0 && year != null && bandYear != null && bandYear != year) bandYear = year
                    if (pendingForTab == 1 && year != null && indivYear != null && indivYear != year) indivYear = year
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
            file.delete()
            pendingRecordingFile = null
            recordingFile = null
            recordingState = RecordingState.IDLE
            isLoading = false
        }
    }

    // ===== Playback =====

    fun playSession(session: Session) {
        if (nowPlayingId == session.id) { stopPlayback(); return }
        nowPlayingId = session.id
        nowPlayingName = session.name
        nowPlayingUrl = repository.buildStreamUrl(session.id)
        nowPlayingMimeType = session.mime_type
    }

    fun stopPlayback() {
        nowPlayingId = null; nowPlayingName = null; nowPlayingUrl = null; nowPlayingMimeType = null
    }

    // ===== Rating (my sessions) =====

    fun openRatingPopup(sessionId: Int) { ratingPopupSessionId = sessionId }
    fun closeRatingPopup() { ratingPopupSessionId = null }

    fun submitRating(sessionId: Int, rating: Int?) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.rateSession(sessionId, rating) }) {
                is BreakroomResult.Success -> {
                    sessions = sessions.map { s ->
                        if (s.id == sessionId) s.copy(
                            avg_rating = result.data.avg_rating,
                            rating_count = result.data.rating_count,
                            my_rating = result.data.my_rating
                        ) else s
                    }
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
            ratingPopupSessionId = null
        }
    }

    // ===== Rating (band member sessions) =====

    fun openBmRatingPopup(sessionId: Int) { bmRatingPopupSessionId = sessionId }
    fun closeBmRatingPopup() { bmRatingPopupSessionId = null }

    fun submitBmRating(sessionId: Int, rating: Int?) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.rateSession(sessionId, rating) }) {
                is BreakroomResult.Success -> {
                    bandMemberSessions = bandMemberSessions.map { s ->
                        if (s.id == sessionId) s.copy(
                            avg_rating = result.data.avg_rating,
                            rating_count = result.data.rating_count,
                            my_rating = result.data.my_rating
                        ) else s
                    }
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
            bmRatingPopupSessionId = null
        }
    }

    // ===== Edit (my sessions) =====

    fun updateSessionName(sessionId: Int, name: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.updateSession(sessionId, name = name) }) {
                is BreakroomResult.Success -> sessions = sessions.map { if (it.id == sessionId) result.data else it }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    fun updateSessionDate(sessionId: Int, recordedAt: String?) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) {
                repository.updateSession(sessionId, recordedAt = recordedAt ?: "")
            }) {
                is BreakroomResult.Success -> sessions = sessions.map { if (it.id == sessionId) result.data else it }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.deleteSession(sessionId) }) {
                is BreakroomResult.Success -> {
                    sessions = sessions.filter { it.id != sessionId }
                    if (nowPlayingId == sessionId) stopPlayback()
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    // ===== Band management =====

    fun toggleExpandBand(bandId: Int) {
        expandedBandId = if (expandedBandId == bandId) null else bandId
        if (expandedBandId == bandId && !bandDetails.containsKey(bandId)) {
            loadBandDetail(bandId)
        }
    }

    fun startCreatingBand() { creatingBand = true }
    fun cancelCreatingBand() { creatingBand = false }

    fun createBand(name: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.createBand(name) }) {
                is BreakroomResult.Success -> {
                    val newEntry = BandListEntry(
                        id = result.data.id,
                        name = result.data.name,
                        description = result.data.description,
                        role = "owner",
                        status = "active",
                        member_count = 1
                    )
                    bands = bands + newEntry
                    bandDetails = bandDetails + (result.data.id to result.data)
                    expandedBandId = result.data.id
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
            creatingBand = false
        }
    }

    fun startInviting(bandId: Int) { invitingToBandId = bandId }
    fun cancelInviting() { invitingToBandId = null }

    fun inviteMember(bandId: Int, handle: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.inviteBandMember(bandId, handle) }) {
                is BreakroomResult.Success -> loadBandDetail(bandId)
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
            invitingToBandId = null
        }
    }

    fun respondToInvite(bandId: Int, accept: Boolean) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.respondBandInvite(bandId, accept) }) {
                is BreakroomResult.Success -> {
                    if (accept) {
                        bands = bands.map { b -> if (b.id == bandId) b.copy(status = "active") else b }
                        loadBandDetail(bandId)
                    } else {
                        bands = bands.filter { it.id != bandId }
                    }
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    fun removeBandMember(bandId: Int, userId: Int, isSelf: Boolean) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.removeBandMember(bandId, userId) }) {
                is BreakroomResult.Success -> {
                    if (isSelf) {
                        bands = bands.filter { it.id != bandId }
                        if (expandedBandId == bandId) expandedBandId = null
                    } else {
                        loadBandDetail(bandId)
                        bands = bands.map { b ->
                            if (b.id == bandId) b.copy(member_count = (b.member_count - 1).coerceAtLeast(0)) else b
                        }
                    }
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
        }
    }

    fun clearError() { errorMessage = null }

    // ===== Helpers =====

    private fun defaultYear(sessions: List<Session>): Int? =
        sessions.mapNotNull { yearFromDate(it.recorded_at ?: it.uploaded_at) }.maxOrNull()

    private fun groupSessions(sessions: List<Session>): Map<Int, Map<Int, List<Session>>> =
        sessions.groupBy { yearFromDate(it.recorded_at ?: it.uploaded_at) ?: 0 }
            .mapValues { (_, s) ->
                s.groupBy { monthFromDate(it.recorded_at ?: it.uploaded_at) ?: 0 }
                    .toSortedMap(reverseOrder())
            }
            .toSortedMap(reverseOrder())

    private fun availableYears(sessions: List<Session>): List<Int> =
        sessions.mapNotNull { yearFromDate(it.recorded_at ?: it.uploaded_at) }.distinct().sortedDescending()

    private fun yearFromDate(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        return try { dateStr.substring(0, 4).toInt() } catch (e: Exception) { null }
    }

    private fun monthFromDate(dateStr: String?): Int? {
        if (dateStr.isNullOrBlank()) return null
        return try { dateStr.substring(5, 7).toInt() } catch (e: Exception) { null }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        try { mediaRecorder?.stop() } catch (e: Exception) { }
        mediaRecorder?.release()
        mediaRecorder = null
    }
}
