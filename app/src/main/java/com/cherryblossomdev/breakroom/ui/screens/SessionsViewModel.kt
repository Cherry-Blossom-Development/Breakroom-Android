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
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Session
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

class SessionsViewModel(
    private val repository: SessionsRepository
) : ViewModel() {

    var sessions by mutableStateOf<List<Session>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Year tab selection — null means "All"
    var selectedYear by mutableStateOf<Int?>(null)
        private set

    // Recording state machine
    var recordingState by mutableStateOf(RecordingState.IDLE)
        private set
    var recordingSeconds by mutableStateOf(0)
        private set

    // After recording stops, file is held here until saved or discarded
    var pendingRecordingFile by mutableStateOf<File?>(null)
        private set

    // Now-playing
    var nowPlayingId by mutableStateOf<Int?>(null)
        private set
    var nowPlayingUrl by mutableStateOf<String?>(null)
        private set
    var nowPlayingName by mutableStateOf<String?>(null)
        private set

    // Rating popup
    var ratingPopupSessionId by mutableStateOf<Int?>(null)
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var recordingFile: File? = null

    fun loadSessions() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            when (val result = withContext(Dispatchers.IO) { repository.getSessions() }) {
                is BreakroomResult.Success -> {
                    sessions = result.data
                    // Default to the most recent year if not yet set
                    if (selectedYear == null) {
                        selectedYear = result.data
                            .mapNotNull { yearFromDate(it.recorded_at ?: it.uploaded_at) }
                            .maxOrNull()
                    }
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
            }
            isLoading = false
        }
    }

    fun selectYear(year: Int?) {
        selectedYear = year
    }

    // Sessions filtered by selected year, grouped by year then month
    fun groupedSessions(): Map<Int, Map<Int, List<Session>>> {
        val filtered = if (selectedYear == null) {
            sessions
        } else {
            sessions.filter { yearFromDate(it.recorded_at ?: it.uploaded_at) == selectedYear }
        }
        return filtered.groupBy { yearFromDate(it.recorded_at ?: it.uploaded_at) ?: 0 }
            .mapValues { (_, sessionsInYear) ->
                sessionsInYear.groupBy { monthFromDate(it.recorded_at ?: it.uploaded_at) ?: 0 }
                    .toSortedMap(reverseOrder())
            }
            .toSortedMap(reverseOrder())
    }

    fun availableYears(): List<Int> {
        return sessions
            .mapNotNull { yearFromDate(it.recorded_at ?: it.uploaded_at) }
            .distinct()
            .sortedDescending()
    }

    // ==================== Recording ====================

    fun startRecording(context: Context) {
        val cacheDir = context.cacheDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(cacheDir, "session_$timestamp.m4a")
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
            while (true) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        timerJob = null
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // recording too short — ignore
        }
        mediaRecorder?.release()
        mediaRecorder = null
        pendingRecordingFile = recordingFile
        recordingState = RecordingState.SAVING
    }

    fun discardPendingRecording() {
        pendingRecordingFile?.delete()
        pendingRecordingFile = null
        recordingFile = null
        recordingState = RecordingState.IDLE
    }

    fun saveRecording(name: String, recordedAt: String?) {
        val file = pendingRecordingFile ?: return
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                repository.uploadSession(file, name.ifBlank { "Session" }, recordedAt)
            }
            when (result) {
                is BreakroomResult.Success -> {
                    sessions = listOf(result.data) + sessions
                    // Ensure the newly uploaded session's year is selected
                    val year = yearFromDate(result.data.recorded_at ?: result.data.uploaded_at)
                    if (year != null && selectedYear != null && selectedYear != year) {
                        selectedYear = year
                    }
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

    // ==================== Playback ====================

    fun playSession(session: Session) {
        if (nowPlayingId == session.id) {
            // Toggle: clicking the same session again clears it
            nowPlayingId = null
            nowPlayingUrl = null
            nowPlayingName = null
            return
        }
        nowPlayingId = session.id
        nowPlayingName = session.name
        nowPlayingUrl = null  // will be populated asynchronously
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getStreamUrl(session.id) }) {
                is BreakroomResult.Success -> nowPlayingUrl = result.data
                is BreakroomResult.Error -> {
                    errorMessage = result.message
                    nowPlayingId = null
                    nowPlayingName = null
                }
                is BreakroomResult.AuthenticationError -> {
                    errorMessage = "Session expired"
                    nowPlayingId = null
                    nowPlayingName = null
                }
            }
        }
    }

    fun stopPlayback() {
        nowPlayingId = null
        nowPlayingUrl = null
        nowPlayingName = null
    }

    // ==================== Rating ====================

    fun openRatingPopup(sessionId: Int) {
        ratingPopupSessionId = sessionId
    }

    fun closeRatingPopup() {
        ratingPopupSessionId = null
    }

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

    // ==================== Edit ====================

    fun updateSessionName(sessionId: Int, name: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) {
                repository.updateSession(sessionId, name = name)
            }) {
                is BreakroomResult.Success -> {
                    sessions = sessions.map { if (it.id == sessionId) result.data else it }
                }
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
                is BreakroomResult.Success -> {
                    sessions = sessions.map { if (it.id == sessionId) result.data else it }
                }
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

    fun clearError() { errorMessage = null }

    // ==================== Helpers ====================

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
