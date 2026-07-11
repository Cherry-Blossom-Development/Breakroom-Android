package com.cherryblossomdev.breakroom.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RequiresPermission
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecordingState { IDLE, RECORDING, SAVING }

data class PendingUpload(val originalFileName: String, val mimeType: String)

class SessionsViewModel(
    private val repository: SessionsRepository
) : ViewModel() {

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
    var setlists by mutableStateOf<Map<Int, List<BandSetlist>>>(emptyMap())
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
    var pendingUploadInfo by mutableStateOf<PendingUpload?>(null)
        private set

    // ===== Practice suggestions (default band + name autocomplete, scoped per sessionType) =====
    var practiceDefaultBandId by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    var practiceCommonNames by mutableStateOf<Map<Pair<String, Int>, List<String>>>(emptyMap())
        private set

    // ===== Level meter =====
    var recordingLevelPercent by mutableStateOf(0)
        private set
    private var levelPollerJob: Job? = null

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

    // ===== Paywall =====
    var showPaywall by mutableStateOf(false)
        private set

    // ===== Band management UI =====
    var expandedBandId by mutableStateOf<Int?>(null)
        private set
    var creatingBand by mutableStateOf(false)
        private set
    var invitingToBandId by mutableStateOf<Int?>(null)
        private set

    // ===== Audio Defaults =====
    var audioDefaults by mutableStateOf(AudioDefaults())
        private set
    var showAudioDefaults by mutableStateOf(false)
        private set
    var audioDefaultsSaving by mutableStateOf(false)
        private set
    var audioDefaultsSaved by mutableStateOf(false)
        private set

    // ===== Device =====
    var currentDevice by mutableStateOf<UserDevice?>(null)
        private set
    var deviceEditing by mutableStateOf(false)
        private set
    var deviceNameInput by mutableStateOf("")
        private set
    var deviceNameSaving by mutableStateOf(false)
        private set

    // ===== Mashup =====
    var mashupSource by mutableStateOf("own")
        private set
    var mashupSearch by mutableStateOf("")
        private set
    var mashupBackingSession by mutableStateOf<Session?>(null)
        private set
    var mashupFile by mutableStateOf<File?>(null)
        private set
    var mashupName by mutableStateOf("")
        private set
    var mashupRecordedAt by mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        private set
    var mashupBackingVolume by mutableStateOf(1.0f)
        private set
    var mashupNewVolume by mutableStateOf(1.0f)
        private set
    var isMerging by mutableStateOf(false)
        private set
    var mergeError by mutableStateOf<String?>(null)
        private set
    var saveAsIndividual by mutableStateOf(false)
        private set
    var mashupUploading by mutableStateOf(false)
        private set
    var mashupUploadError by mutableStateOf<String?>(null)
        private set
    var pendingMashupRecord by mutableStateOf(false)
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var recordingFile: File? = null

    // AudioRecord for mashup recording (gives raw PCM for mixing)
    private var audioRecord: AudioRecord? = null
    private val activeAudioEffects = mutableListOf<android.media.audiofx.AudioEffect>()
    private val mashupPcmChunks = mutableListOf<ByteArray>()
    private var mashupRecordingFile: File? = null
    private var mashupLevelJob: Job? = null
    private val MASHUP_SAMPLE_RATE = 44100

    // Silence (ms) to prepend to new recording when mixing, derived from exo position at stop
    var mashupSilencePadMs: Long = 0L

    // PCM chunks for emulator regular recording (AudioRecord path)
    private val recordingPcmChunks = mutableListOf<ByteArray>()
    private var usingAudioRecordForRegular = false

    val myHandle: String? get() = repository.getMyHandle()
    val authCookie: String? get() = repository.getAuthCookie()
    val rawToken: String? get() = repository.getRawToken()
    val mashupBackingUrl: String? get() = mashupBackingSession?.let { repository.buildStreamUrl(it.id) }

    // ===== Derived =====
    val bandSessions get() = sessions.filter { it.session_type == "band" }
    val individualSessions get() = sessions.filter { it.session_type == "individual" }
    val mashupSessions get() = sessions.filter { it.session_type == "mashup" }
    val activeBands get() = bands.filter { it.status == "active" }
    val pendingInvites get() = bands.filter { it.status == "invited" }

    // ===== Load =====

    fun loadAll() {
        loadSessions()
        loadBandMemberSessions()
        loadBands()
        loadInstruments()
        loadAudioDefaults()
        loadDevice()
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
                 else -> { }
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
                 else -> { }
            }
        }
    }

    fun loadBands() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getBands() }) {
                is BreakroomResult.Success -> bands = result.data
                is BreakroomResult.Error -> { /* not critical */ }
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                 else -> { }
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
                 else -> { }
            }
        }
        loadSetlists(bandId)
    }

    // ===== Set Lists =====

    fun loadSetlists(bandId: Int) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getSetlists(bandId) }) {
                is BreakroomResult.Success -> setlists = setlists + (bandId to result.data)
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                 else -> { }
            }
        }
    }

    fun createSetlist(bandId: Int, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.createSetlist(bandId, name.trim()) }) {
                is BreakroomResult.Success -> {
                    setlists = setlists + (bandId to (listOf(result.data) + (setlists[bandId] ?: emptyList())))
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                 else -> { }
            }
        }
    }

    fun renameSetlist(bandId: Int, setlistId: Int, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.renameSetlist(bandId, setlistId, name.trim()) }) {
                is BreakroomResult.Success -> {
                    setlists = setlists + (bandId to (setlists[bandId] ?: emptyList()).map {
                        if (it.id == setlistId) result.data else it
                    })
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                 else -> { }
            }
        }
    }

    fun deleteSetlist(bandId: Int, setlistId: Int) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.deleteSetlist(bandId, setlistId) }) {
                is BreakroomResult.Success -> {
                    setlists = setlists + (bandId to (setlists[bandId] ?: emptyList()).filter { it.id != setlistId })
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                 else -> { }
            }
        }
    }

    fun addSong(bandId: Int, setlistId: Int, song: String) {
        if (song.isBlank()) return
        val setlist = setlists[bandId]?.find { it.id == setlistId } ?: return
        saveSetlistSongs(bandId, setlistId, setlist.songs + song.trim())
    }

    fun removeSong(bandId: Int, setlistId: Int, index: Int) {
        val setlist = setlists[bandId]?.find { it.id == setlistId } ?: return
        saveSetlistSongs(bandId, setlistId, setlist.songs.filterIndexed { i, _ -> i != index })
    }

    fun moveSong(bandId: Int, setlistId: Int, index: Int, direction: Int) {
        val setlist = setlists[bandId]?.find { it.id == setlistId } ?: return
        val swapIndex = index + direction
        if (index < 0 || index >= setlist.songs.size || swapIndex < 0 || swapIndex >= setlist.songs.size) return
        val newSongs = setlist.songs.toMutableList()
        val tmp = newSongs[index]
        newSongs[index] = newSongs[swapIndex]
        newSongs[swapIndex] = tmp
        saveSetlistSongs(bandId, setlistId, newSongs)
    }

    private fun saveSetlistSongs(bandId: Int, setlistId: Int, songs: List<String>) {
        setlists = setlists + (bandId to (setlists[bandId] ?: emptyList()).map {
            if (it.id == setlistId) it.copy(songs = songs) else it
        })
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.setSetlistSongs(bandId, setlistId, songs) }) {
                is BreakroomResult.Success -> {
                    setlists = setlists + (bandId to (setlists[bandId] ?: emptyList()).map {
                        if (it.id == setlistId) it.copy(songs = result.data) else it
                    })
                }
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                 else -> { }
            }
        }
    }

    // ===== Audio Defaults =====

    fun loadAudioDefaults() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getAudioDefaults() }) {
                is BreakroomResult.Success -> audioDefaults = result.data
                else -> { /* keep defaults */ }
            }
        }
    }

    fun openAudioDefaults() { showAudioDefaults = true }
    fun closeAudioDefaults() { showAudioDefaults = false }

    fun setAudioDefault(
        echoCancellation: Boolean? = null,
        noiseSuppression: Boolean? = null,
        autoGainControl: Boolean? = null,
        softLimiter: Boolean? = null,
        playbackVolume: Float? = null,
        wavPlaybackBoost: Float? = null,
        recordingNormalization: Float? = null,
        bitrate: Int? = null,
        mashupBackingVolume: Float? = null,
        mashupNewVolume: Float? = null
    ) {
        audioDefaults = audioDefaults.copy(
            echo_cancellation = echoCancellation ?: audioDefaults.echo_cancellation,
            noise_suppression = noiseSuppression ?: audioDefaults.noise_suppression,
            auto_gain_control = autoGainControl ?: audioDefaults.auto_gain_control,
            soft_limiter = softLimiter ?: audioDefaults.soft_limiter,
            playback_volume = playbackVolume ?: audioDefaults.playback_volume,
            wav_playback_boost = wavPlaybackBoost ?: audioDefaults.wav_playback_boost,
            recording_normalization = recordingNormalization ?: audioDefaults.recording_normalization,
            bitrate = bitrate ?: audioDefaults.bitrate,
            mashup_backing_volume = mashupBackingVolume ?: audioDefaults.mashup_backing_volume,
            mashup_new_volume = mashupNewVolume ?: audioDefaults.mashup_new_volume
        )
    }

    fun saveAudioDefaults() {
        viewModelScope.launch {
            audioDefaultsSaving = true
            when (withContext(Dispatchers.IO) { repository.saveAudioDefaults(audioDefaults) }) {
                is BreakroomResult.Success -> {
                    audioDefaultsSaved = true
                    viewModelScope.launch { delay(2500); audioDefaultsSaved = false }
                }
                else -> { }
            }
            audioDefaultsSaving = false
        }
    }

    // ===== Device =====

    fun loadDevice() {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.registerDevice() }) {
                is BreakroomResult.Success -> {
                    android.util.Log.d("SessionsVM", "loadDevice success: ${result.data}")
                    currentDevice = result.data
                }
                is BreakroomResult.Error -> android.util.Log.e("SessionsVM", "loadDevice error: ${result.message}")
                is BreakroomResult.AuthenticationError -> android.util.Log.e("SessionsVM", "loadDevice: auth error")
                else -> android.util.Log.e("SessionsVM", "loadDevice: unknown result")
            }
        }
    }

    fun startRenameDevice() {
        deviceNameInput = currentDevice?.user_name ?: ""
        deviceEditing = true
    }

    fun cancelRenameDevice() {
        deviceEditing = false
        deviceNameInput = ""
    }

    fun onDeviceNameChanged(name: String) { deviceNameInput = name }

    fun saveDeviceName() {
        val device = currentDevice ?: return
        viewModelScope.launch {
            deviceNameSaving = true
            val name = deviceNameInput.trim().ifEmpty { null }
            when (withContext(Dispatchers.IO) { repository.saveDeviceName(device.device_token, name) }) {
                is BreakroomResult.Success -> {
                    currentDevice = device.copy(user_name = name)
                    deviceEditing = false
                    deviceNameInput = ""
                }
                else -> { }
            }
            deviceNameSaving = false
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

    // ===== Mashup helpers =====

    fun updateMashupSource(source: String) { mashupSource = source; mashupBackingSession = null }
    fun updateMashupSearch(q: String) { mashupSearch = q }
    fun selectMashupBacking(session: Session) { mashupBackingSession = session }
    fun updateMashupBackingVolume(v: Float) { mashupBackingVolume = v }
    fun updateMashupNewVolume(v: Float) { mashupNewVolume = v }
    fun updateSaveAsIndividual(v: Boolean) { saveAsIndividual = v }
    fun updateMashupName(n: String) { mashupName = n }
    fun updateMashupRecordedAt(d: String) { mashupRecordedAt = d }
    fun clearMashupRecording() {
        mashupFile?.delete(); mashupFile = null; mashupName = ""
        mashupSilencePadMs = 0L
        mashupBackingVolume = audioDefaults.mashup_backing_volume
        mashupNewVolume = audioDefaults.mashup_new_volume
    }
    fun clearMergeError() { mergeError = null }
    fun clearMashupUploadError() { mashupUploadError = null }
    fun requestMashupRecord() { pendingMashupRecord = true }
    fun clearPendingMashupRecord() { pendingMashupRecord = false }

    fun mashupSourceSessions(): List<Session> = when {
        mashupSource == "own" -> sessions
        mashupSource.startsWith("band-") -> {
            val bandId = mashupSource.removePrefix("band-").toIntOrNull()
            if (bandId != null) bandMemberSessions.filter { it.band_id == bandId } else sessions
        }
        else -> sessions
    }

    fun filteredMashupSessions(): List<Session> {
        val q = mashupSearch.trim().lowercase()
        val src = mashupSourceSessions()
        return if (q.isEmpty()) src
        else src.filter {
            it.name.lowercase().contains(q) || it.uploader_handle?.lowercase()?.contains(q) == true
        }
    }

    // ===== Recording (MediaRecorder — Band Practice & Individual) =====

    // UNPROCESSED audio source is not routed to the host mic on the Android emulator —
    // it captures silence or noise instead. MIC is the only source the emulator's QEMU
    // audio layer actually passes through.
    private val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.FINGERPRINT.contains("sdk_gphone") ||   // modern Google emulator (API 33+)
        Build.MODEL.startsWith("sdk_gphone") ||        // e.g. sdk_gphone64_x86_64
        Build.MODEL.contains("google_sdk", ignoreCase = true) ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
        Build.DEVICE.startsWith("emu") ||              // e.g. emu64xa
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
        (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context, forTab: Int) {
        pendingForTab = forTab
        sessionTypeForTab(forTab)?.let { loadPracticeDefaultBand(it) }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        if (isEmulator) {
            // On the emulator the MIC source captures at extremely low gain (~5% of full scale).
            // MediaRecorder writes directly to a compressed file with no PCM access, so there is
            // no way to normalize it. AudioRecord gives raw PCM that we can peak-normalize before
            // saving, producing an audible recording regardless of emulator input level.
            val file = File(context.cacheDir, "session_$timestamp.wav")
            recordingFile = file
            recordingPcmChunks.clear()
            usingAudioRecordForRegular = true

            val bufSize = maxOf(
                AudioRecord.getMinBufferSize(MASHUP_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                8192
            )
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, MASHUP_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                    .also { if (it.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException() }
            } catch (e: Exception) { return }

            activeAudioEffects.forEach { it.release() }
            activeAudioEffects.clear()
            val sessionId = rec.audioSessionId
            if (audioDefaults.auto_gain_control && AutomaticGainControl.isAvailable())
                AutomaticGainControl.create(sessionId)?.also { it.enabled = true; activeAudioEffects.add(it) }
            if (audioDefaults.noise_suppression && NoiseSuppressor.isAvailable())
                NoiseSuppressor.create(sessionId)?.also { it.enabled = true; activeAudioEffects.add(it) }
            if (audioDefaults.echo_cancellation && AcousticEchoCanceler.isAvailable())
                AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true; activeAudioEffects.add(it) }

            audioRecord = rec
            rec.startRecording()
            recordingSeconds = 0
            recordingState = RecordingState.RECORDING

            levelPollerJob?.cancel()
            levelPollerJob = viewModelScope.launch(Dispatchers.IO) {
                val buf = ShortArray(bufSize / 2)
                while (isActive && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = rec.read(buf, 0, buf.size)
                    if (read > 0) {
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xFF).toByte()
                        }
                        synchronized(recordingPcmChunks) { recordingPcmChunks.add(bytes) }
                        var maxAmp = 0
                        for (s in buf.slice(0 until read)) {
                            val abs = kotlin.math.abs(s.toInt())
                            if (abs > maxAmp) maxAmp = abs
                        }
                        withContext(Dispatchers.Main) {
                            recordingLevelPercent = (maxAmp * 100) / 32767
                        }
                    }
                }
            }

            timerJob = viewModelScope.launch {
                while (true) { delay(1000); recordingSeconds++ }
            }
            return
        }

        // Real device path: MediaRecorder with AudioSource selected by toggle state.
        // Map toggle combination to the closest matching AudioSource:
        //   all off           → UNPROCESSED (raw ADC, no voice pipeline — best for instruments)
        //   noise only        → VOICE_RECOGNITION (light noise reduction, no AGC)
        //   echo/AGC, or mix  → MIC (full voice call processing stack)
        usingAudioRecordForRegular = false
        val file = File(context.cacheDir, "session_$timestamp.m4a")
        recordingFile = file

        val preferredSource = when {
            !audioDefaults.echo_cancellation && !audioDefaults.noise_suppression && !audioDefaults.auto_gain_control ->
                MediaRecorder.AudioSource.UNPROCESSED
            audioDefaults.noise_suppression && !audioDefaults.echo_cancellation && !audioDefaults.auto_gain_control ->
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            else ->
                MediaRecorder.AudioSource.MIC
        }

        fun buildRecorder(source: Int): MediaRecorder {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                    else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(audioDefaults.bitrate)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(file.absolutePath)
            return r
        }

        val recorder = try {
            buildRecorder(preferredSource).also { it.prepare() }
        } catch (e: Exception) {
            // UNPROCESSED / VOICE_RECOGNITION not supported on this hardware — fall back to MIC
            if (preferredSource != MediaRecorder.AudioSource.MIC) {
                try { buildRecorder(MediaRecorder.AudioSource.MIC).also { it.prepare() } }
                catch (e2: Exception) { return }
            } else return
        }
        recorder.start()
        mediaRecorder = recorder
        recordingSeconds = 0
        recordingState = RecordingState.RECORDING

        // Level meter via getMaxAmplitude()
        levelPollerJob?.cancel()
        levelPollerJob = viewModelScope.launch {
            while (true) {
                delay(80)
                val amp = mediaRecorder?.maxAmplitude ?: 0
                recordingLevelPercent = (amp * 100) / 32767
            }
        }

        timerJob = viewModelScope.launch {
            while (true) { delay(1000); recordingSeconds++ }
        }
    }

    fun stopRecording() {
        levelPollerJob?.cancel(); levelPollerJob = null
        recordingLevelPercent = 0
        timerJob?.cancel(); timerJob = null
        activeAudioEffects.forEach { it.release() }
        activeAudioEffects.clear()

        if (usingAudioRecordForRegular) {
            usingAudioRecordForRegular = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            // Flatten PCM chunks and peak-normalize to 0.7 (same as mashup path).
            // This compensates for the emulator's extremely low capture gain.
            val chunks = synchronized(recordingPcmChunks) { recordingPcmChunks.toList().also { recordingPcmChunks.clear() } }
            val totalBytes = chunks.sumOf { it.size }
            val combined = ByteArray(totalBytes)
            var off = 0
            for (chunk in chunks) { chunk.copyInto(combined, off); off += chunk.size }

            applyNormalizationAndLimiter(combined)

            val wavBytes = encodeWav(combined, MASHUP_SAMPLE_RATE)
            recordingFile?.writeBytes(wavBytes)
            pendingRecordingFile = recordingFile
            recordingState = RecordingState.SAVING
            return
        }

        // Real device MediaRecorder path
        try { mediaRecorder?.stop() } catch (e: Exception) { }
        mediaRecorder?.release(); mediaRecorder = null
        pendingRecordingFile = recordingFile
        recordingState = RecordingState.SAVING
    }

    fun discardPendingRecording() {
        pendingRecordingFile?.delete()
        pendingRecordingFile = null
        pendingUploadInfo = null
        recordingFile = null
        recordingState = RecordingState.IDLE
    }

    /** Maps a Band Practice (0) / Individual (1) tab index to its practice-suggestions sessionType. Other tabs (e.g. mashup) have no scoped suggestions. */
    private fun sessionTypeForTab(forTab: Int): String? = when (forTab) {
        0 -> "band"
        1 -> "individual"
        else -> null
    }

    /**
     * Copies a picked content [uri] (e.g. from a system file picker) into a cache file and
     * routes it through the same save flow as a live recording, for Band Practice or Individual.
     */
    fun pickUploadFile(context: Context, uri: Uri, forTab: Int) {
        pendingForTab = forTab
        sessionTypeForTab(forTab)?.let { loadPracticeDefaultBand(it) }
        viewModelScope.launch(Dispatchers.IO) {
            val displayName = queryDisplayName(context, uri) ?: "recording"
            val ext = displayName.substringAfterLast('.', "").lowercase(Locale.US)
            val mimeType = mimeTypeForExtension(ext)
            val tempFile = File(context.cacheDir, "upload_${System.currentTimeMillis()}${if (ext.isNotEmpty()) ".$ext" else ""}")
            val copied = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                true
            } catch (e: Exception) { false }

            withContext(Dispatchers.Main) {
                if (!copied || tempFile.length() == 0L) {
                    tempFile.delete()
                    errorMessage = "Could not read the selected file"
                    return@withContext
                }
                pendingRecordingFile = tempFile
                pendingUploadInfo = PendingUpload(displayName, mimeType)
                recordingState = RecordingState.SAVING
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun mimeTypeForExtension(ext: String): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/m4a"
        "webm" -> "audio/webm"
        "opus" -> "audio/opus"
        else -> "audio/mpeg"
    }

    fun loadPracticeDefaultBand(sessionType: String) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getPracticeSuggestions(sessionType, null) }) {
                is BreakroomResult.Success -> {
                    val bandId = result.data.defaultBandId
                    practiceDefaultBandId = if (bandId != null) practiceDefaultBandId + (sessionType to bandId)
                        else practiceDefaultBandId - sessionType
                }
                else -> { /* not critical */ }
            }
        }
    }

    fun loadPracticeSuggestionsForBand(sessionType: String, bandId: Int) {
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { repository.getPracticeSuggestions(sessionType, bandId) }) {
                is BreakroomResult.Success -> practiceCommonNames = practiceCommonNames + ((sessionType to bandId) to result.data.commonNames)
                else -> { /* not critical */ }
            }
        }
    }

    fun practiceSongOptions(sessionType: String, bandId: Int?): List<String> {
        if (bandId == null) return emptyList()
        val ordered = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        (practiceCommonNames[sessionType to bandId] ?: emptyList()).forEach { if (seen.add(it)) ordered += it }
        val setlistNames = (setlists[bandId] ?: emptyList()).flatMap { it.songs }.toSortedSet()
        setlistNames.forEach { if (seen.add(it)) ordered += it }
        return ordered
    }

    fun saveRecording(name: String, recordedAt: String?, bandId: Int?, instrumentId: Int?) {
        val file = pendingRecordingFile ?: return
        val sessionType = if (pendingForTab == 0) "band" else "individual"
        val mimeType = pendingUploadInfo?.mimeType ?: "audio/m4a"
        viewModelScope.launch {
            isLoading = true
            val result = withContext(Dispatchers.IO) {
                repository.uploadSession(file, name.ifBlank { "Session" }, recordedAt, sessionType, bandId, instrumentId, mimeType)
            }
            when (result) {
                is BreakroomResult.Success -> {
                    sessions = listOf(result.data) + sessions
                    val year = yearFromDate(result.data.recorded_at ?: result.data.uploaded_at)
                    if (pendingForTab == 0 && year != null && bandYear != null && bandYear != year) bandYear = year
                    if (pendingForTab == 1 && year != null && indivYear != null && indivYear != year) indivYear = year
                    file.delete()
                    pendingRecordingFile = null
                    pendingUploadInfo = null
                    recordingFile = null
                    recordingState = RecordingState.IDLE
                }
                is BreakroomResult.SubscriptionRequired -> {
                    showPaywall = true
                }
                is BreakroomResult.Error -> {
                    errorMessage = result.message
                    file.delete()
                    pendingRecordingFile = null
                    pendingUploadInfo = null
                    recordingFile = null
                    recordingState = RecordingState.IDLE
                }
                is BreakroomResult.AuthenticationError -> {
                    errorMessage = "Session expired"
                    file.delete()
                    pendingRecordingFile = null
                    pendingUploadInfo = null
                    recordingFile = null
                    recordingState = RecordingState.IDLE
                }
                else -> { }
            }
            isLoading = false
        }
    }

    // ===== Recording (AudioRecord — Mashup, gives raw PCM for mixing) =====

    @SuppressLint("MissingPermission")
    fun startMashupRecording(context: Context) {
        if (recordingState != RecordingState.IDLE) return
        pendingForTab = 3
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        mashupRecordingFile = File(context.cacheDir, "mashup_$timestamp.wav")
        mashupPcmChunks.clear()

        val bufSize = maxOf(
            AudioRecord.getMinBufferSize(MASHUP_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            8192
        )
        // Start with UNPROCESSED so we control each effect individually below.
        // On the emulator UNPROCESSED isn't routed to the host mic — force MIC there.
        val mashupSource = if (isEmulator) MediaRecorder.AudioSource.MIC
                           else MediaRecorder.AudioSource.UNPROCESSED
        val rec = try {
            AudioRecord(mashupSource, MASHUP_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
                .also { if (it.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException() }
        } catch (e: Exception) {
            AudioRecord(MediaRecorder.AudioSource.MIC, MASHUP_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        }

        // Apply individual software effects based on audioDefaults
        activeAudioEffects.forEach { it.release() }
        activeAudioEffects.clear()
        val sessionId = rec.audioSessionId
        if (audioDefaults.noise_suppression && NoiseSuppressor.isAvailable())
            NoiseSuppressor.create(sessionId)?.also { it.enabled = true; activeAudioEffects.add(it) }
        if (audioDefaults.echo_cancellation && AcousticEchoCanceler.isAvailable())
            AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true; activeAudioEffects.add(it) }
        if (audioDefaults.auto_gain_control && AutomaticGainControl.isAvailable())
            AutomaticGainControl.create(sessionId)?.also { it.enabled = true; activeAudioEffects.add(it) }

        audioRecord = rec
        rec.startRecording()
        recordingSeconds = 0
        recordingState = RecordingState.RECORDING

        timerJob = viewModelScope.launch {
            while (true) { delay(1000); recordingSeconds++ }
        }

        mashupLevelJob = viewModelScope.launch(Dispatchers.IO) {
            val buf = ShortArray(bufSize / 2)
            while (isActive && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = rec.read(buf, 0, buf.size)
                if (read > 0) {
                    val bytes = ByteArray(read * 2)
                    for (i in 0 until read) {
                        bytes[i * 2] = (buf[i].toInt() and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((buf[i].toInt() shr 8) and 0xFF).toByte()
                    }
                    synchronized(mashupPcmChunks) { mashupPcmChunks.add(bytes) }
                    var maxAmp = 0
                    for (s in buf.slice(0 until read)) {
                        val abs = kotlin.math.abs(s.toInt())
                        if (abs > maxAmp) maxAmp = abs
                    }
                    withContext(Dispatchers.Main) {
                        recordingLevelPercent = (maxAmp * 100) / 32767
                    }
                }
            }
        }
    }

    fun stopMashupRecording(backingPositionMs: Long = 0L) {
        timerJob?.cancel(); timerJob = null
        mashupLevelJob?.cancel(); mashupLevelJob = null
        recordingLevelPercent = 0
        recordingSeconds = 0

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        activeAudioEffects.forEach { it.release() }
        activeAudioEffects.clear()

        // Flatten PCM chunks
        val chunks = synchronized(mashupPcmChunks) { mashupPcmChunks.toList().also { mashupPcmChunks.clear() } }
        val totalBytes = chunks.sumOf { it.size }
        val combined = ByteArray(totalBytes)
        var off = 0
        for (chunk in chunks) { chunk.copyInto(combined, off); off += chunk.size }

        // Compute silence to prepend in mix: backing position at stop minus recording duration.
        // exo.currentPosition tracks media timeline directly, so this is more accurate than nanoTime.
        val recordingDurationMs = totalBytes.toLong() * 1000L / (MASHUP_SAMPLE_RATE * 2)
        mashupSilencePadMs = maxOf(0L, backingPositionMs - recordingDurationMs)

        applyNormalizationAndLimiter(combined)

        val wavBytes = encodeWav(combined, MASHUP_SAMPLE_RATE)
        mashupRecordingFile?.writeBytes(wavBytes)
        mashupFile = mashupRecordingFile
        mashupRecordingFile = null

        if (mashupName.isBlank()) {
            mashupName = "Mashup - ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
        }
        recordingState = RecordingState.IDLE
    }

    fun saveMerged(context: Context) {
        val backingSession = mashupBackingSession ?: return
        val newFile = mashupFile ?: return
        val capturedSaveAsIndividual = saveAsIndividual
        val capturedSilencePadMs = mashupSilencePadMs

        viewModelScope.launch {
            isMerging = true
            mergeError = null

            data class MergeResult(
                val mashupSession: Session,
                val individualSession: Session?
            )

            val result = withContext(Dispatchers.IO) {
                try {
                    // Optionally save the new recording as a standalone individual session
                    val individualSession: Session? = if (capturedSaveAsIndividual) {
                        val indivName = mashupName.ifBlank { backingSession.name }
                        val indivResult = repository.uploadSession(
                            newFile, indivName,
                            mashupRecordedAt.takeIf { it.isNotBlank() },
                            "individual", null, null
                        )
                        if (indivResult is BreakroomResult.Success) indivResult.data else null
                    } else null

                    // Download backing WAV with Bearer auth
                    val backingUrl = repository.buildStreamUrl(backingSession.id)
                    val token = repository.getRawToken()
                    val req = Request.Builder().url(backingUrl)
                        .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                        .build()
                    val backingBytes = OkHttpClient().newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception("Failed to download backing track")
                        resp.body?.bytes() ?: throw Exception("Empty backing track")
                    }

                    // Extract raw PCM from both WAV files, normalize format, then mix
                    val backingPcm = extractWavPcm(backingBytes)
                    val newPcm = extractWavPcm(newFile.readBytes())

                    // Pad start of new recording with silence computed from exo position at stop
                    val silenceBytes = (capturedSilencePadMs * MASHUP_SAMPLE_RATE / 1000L).toInt() * 2
                    val alignedNew = if (silenceBytes > 0) {
                        val padded = ByteArray(newPcm.size + silenceBytes)
                        newPcm.copyInto(padded, silenceBytes)
                        padded
                    } else newPcm

                    val mixed = mixPcm(backingPcm, alignedNew, mashupBackingVolume, mashupNewVolume)

                    // Encode mixed PCM as WAV and upload as mashup session
                    val mergedWav = encodeWav(mixed, MASHUP_SAMPLE_RATE)
                    val mergedFile = File(context.cacheDir, "merged_${System.currentTimeMillis()}.wav")
                    mergedFile.writeBytes(mergedWav)

                    val name = mashupName.ifBlank { "Merged – ${backingSession.name}" }
                    val uploadResult = repository.uploadSession(
                        mergedFile, name,
                        mashupRecordedAt.takeIf { it.isNotBlank() },
                        "mashup", null, null
                    )
                    mergedFile.delete()

                    when (uploadResult) {
                        is BreakroomResult.Success -> BreakroomResult.Success(
                            MergeResult(uploadResult.data, individualSession)
                        )
                        else -> uploadResult as BreakroomResult<MergeResult>
                    }
                } catch (e: Exception) {
                    BreakroomResult.Error(e.message ?: "Merge failed")
                }
            }

            when (result) {
                is BreakroomResult.Success -> {
                    val mashupSession = result.data.mashupSession
                    val individualSession = result.data.individualSession

                    // Record which source sessions went into this mashup (best-effort)
                    viewModelScope.launch {
                        val sources = mutableListOf(
                            MashupSourceEntry(backingSession.id, mashupBackingVolume)
                        )
                        if (individualSession != null) {
                            sources.add(MashupSourceEntry(individualSession.id, mashupNewVolume))
                        }
                        repository.recordMashupSources(mashupSession.id, sources)
                    }

                    val newSessions = mutableListOf(mashupSession)
                    if (individualSession != null) newSessions.add(individualSession)
                    sessions = newSessions + sessions

                    val year = yearFromDate(mashupSession.recorded_at ?: mashupSession.uploaded_at)
                    if (year != null && indivYear != null && indivYear != year) indivYear = year
                    mashupFile?.delete(); mashupFile = null
                    mashupName = ""
                    mashupBackingSession = null
                    saveAsIndividual = false
                    mergeError = null
                }
                is BreakroomResult.SubscriptionRequired -> showPaywall = true
                is BreakroomResult.Error -> mergeError = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                else -> { }
            }

            isMerging = false
        }
    }

    fun dismissPaywall() { showPaywall = false }

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
                 else -> { }
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
                 else -> { }
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
                 else -> { }
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
                 else -> { }
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
                 else -> { }
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
                is BreakroomResult.SubscriptionRequired -> showPaywall = true
                is BreakroomResult.Error -> errorMessage = result.message
                is BreakroomResult.AuthenticationError -> errorMessage = "Session expired"
                 else -> { }
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
                 else -> { }
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
                 else -> { }
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
                 else -> { }
            }
        }
    }

    fun clearError() { errorMessage = null }

    // ===== PCM / WAV helpers =====

    private fun extractWavPcm(bytes: ByteArray): ByteArray {
        if (bytes.size < 44) return bytes

        // Read fmt chunk: channels @ 22, sample rate @ 24
        val channels = (bytes[22].toInt() and 0xFF) or ((bytes[23].toInt() and 0xFF) shl 8)
        val sampleRate = (bytes[24].toInt() and 0xFF) or
            ((bytes[25].toInt() and 0xFF) shl 8) or
            ((bytes[26].toInt() and 0xFF) shl 16) or
            ((bytes[27].toInt() and 0xFF) shl 24)

        // Find data chunk
        var dataStart = 44
        var i = 12
        while (i < bytes.size - 8) {
            if (bytes[i] == 'd'.code.toByte() && bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() && bytes[i + 3] == 'a'.code.toByte()
            ) { dataStart = i + 8; break }
            val sz = (bytes[i + 4].toInt() and 0xFF) or ((bytes[i + 5].toInt() and 0xFF) shl 8) or
                ((bytes[i + 6].toInt() and 0xFF) shl 16) or ((bytes[i + 7].toInt() and 0xFF) shl 24)
            i += 8 + sz.coerceAtLeast(0)
        }

        var pcm = bytes.copyOfRange(dataStart.coerceIn(0, bytes.size), bytes.size)

        // Stereo → mono: average left and right channels
        if (channels == 2 && pcm.size >= 4) {
            val mono = ByteArray(pcm.size / 2)
            var src = 0; var dst = 0
            while (src + 3 < pcm.size) {
                val left  = ((pcm[src + 1].toInt() shl 8) or (pcm[src].toInt()     and 0xFF)).toShort().toInt()
                val right = ((pcm[src + 3].toInt() shl 8) or (pcm[src + 2].toInt() and 0xFF)).toShort().toInt()
                val avg = ((left + right) / 2).coerceIn(-32768, 32767)
                mono[dst]     = (avg and 0xFF).toByte()
                mono[dst + 1] = ((avg shr 8) and 0xFF).toByte()
                src += 4; dst += 2
            }
            pcm = mono
        }

        // Resample to MASHUP_SAMPLE_RATE if needed
        if (sampleRate > 0 && sampleRate != MASHUP_SAMPLE_RATE) {
            pcm = resampleMono16(pcm, sampleRate, MASHUP_SAMPLE_RATE)
        }

        return pcm
    }

    private fun resampleMono16(pcm: ByteArray, fromRate: Int, toRate: Int): ByteArray {
        if (fromRate == toRate) return pcm
        val inSamples = pcm.size / 2
        val outSamples = (inSamples.toLong() * toRate / fromRate).toInt()
        val out = ByteArray(outSamples * 2)
        for (i in 0 until outSamples) {
            val srcPos = i.toDouble() * fromRate / toRate
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx
            val s0 = if (srcIdx < inSamples)
                ((pcm[srcIdx * 2 + 1].toInt() shl 8) or (pcm[srcIdx * 2].toInt() and 0xFF)).toShort().toInt()
                else 0
            val s1 = if (srcIdx + 1 < inSamples)
                ((pcm[(srcIdx + 1) * 2 + 1].toInt() shl 8) or (pcm[(srcIdx + 1) * 2].toInt() and 0xFF)).toShort().toInt()
                else s0
            val interp = (s0 + (s1 - s0) * frac).toInt().coerceIn(-32768, 32767)
            out[i * 2]     = (interp and 0xFF).toByte()
            out[i * 2 + 1] = ((interp shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun applyNormalizationAndLimiter(pcm: ByteArray) {
        val peakTarget = (audioDefaults.recording_normalization * 32767f).toInt().coerceIn(0, 32767)
        if (peakTarget == 0) return
        var maxAmp = 0
        for (i in 0 until pcm.size - 1 step 2) {
            val s = kotlin.math.abs(((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt())
            if (s > maxAmp) maxAmp = s
        }
        if (maxAmp in 1 until peakTarget) {
            val boost = peakTarget.toFloat() / maxAmp
            for (i in 0 until pcm.size - 1 step 2) {
                val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
                val boosted = (s * boost).toInt().coerceIn(-32768, 32767)
                pcm[i] = (boosted and 0xFF).toByte()
                pcm[i + 1] = ((boosted shr 8) and 0xFF).toByte()
            }
        }
        if (audioDefaults.soft_limiter) {
            val knee = 0.75f * 32767f
            for (i in 0 until pcm.size - 1 step 2) {
                val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toFloat()
                val abs = kotlin.math.abs(s)
                if (abs > knee) {
                    val sign = if (s >= 0f) 1f else -1f
                    val over = abs - knee
                    val limited = knee + over / (1f + over / (32767f - knee))
                    val result = (sign * limited).toInt().coerceIn(-32768, 32767)
                    pcm[i] = (result and 0xFF).toByte()
                    pcm[i + 1] = ((result shr 8) and 0xFF).toByte()
                }
            }
        }
    }

    private fun mixPcm(a: ByteArray, b: ByteArray, volA: Float, volB: Float): ByteArray {
        val len = maxOf(a.size, b.size).let { if (it % 2 == 0) it else it + 1 }
        val out = ByteArray(len)
        // First pass: find peak of the weighted sum to detect clipping headroom needed
        var peakSum = 0f
        var i = 0
        while (i < len - 1) {
            val sa = if (i + 1 < a.size) ((a[i + 1].toInt() shl 8) or (a[i].toInt() and 0xFF)).toShort().toFloat() else 0f
            val sb = if (i + 1 < b.size) ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toFloat() else 0f
            val s = kotlin.math.abs(sa * volA + sb * volB)
            if (s > peakSum) peakSum = s
            i += 2
        }
        // Scale down only if the sum would clip — preserves slider levels as-is otherwise
        val scale = if (peakSum > 32767f) 32767f / peakSum else 1f
        // Second pass: write scaled mix
        i = 0
        while (i < len - 1) {
            val sa = if (i + 1 < a.size) ((a[i + 1].toInt() shl 8) or (a[i].toInt() and 0xFF)).toShort().toFloat() else 0f
            val sb = if (i + 1 < b.size) ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toFloat() else 0f
            val mixed = ((sa * volA + sb * volB) * scale).toInt().coerceIn(-32768, 32767)
            out[i] = (mixed and 0xFF).toByte()
            out[i + 1] = ((mixed shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    private fun encodeWav(pcm: ByteArray, sampleRate: Int = 44100, channels: Int = 1): ByteArray {
        val dataSize = pcm.size
        val buf = ByteArray(44 + dataSize)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII)); bb.putInt(36 + dataSize)
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII)); bb.putInt(16)
        bb.putShort(1); bb.putShort(channels.toShort())
        bb.putInt(sampleRate); bb.putInt(sampleRate * channels * 2)
        bb.putShort((channels * 2).toShort()); bb.putShort(16)
        bb.put("data".toByteArray(Charsets.US_ASCII)); bb.putInt(dataSize)
        bb.put(pcm)
        return buf
    }

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
        levelPollerJob?.cancel()
        mashupLevelJob?.cancel()
        try { mediaRecorder?.stop() } catch (e: Exception) { }
        mediaRecorder?.release(); mediaRecorder = null
        audioRecord?.stop(); audioRecord?.release(); audioRecord = null
    }
}
