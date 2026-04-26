package com.cherryblossomdev.breakroom.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.FeaturesRepository
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

class SessionsViewModel(
    private val repository: SessionsRepository,
    private val featuresRepository: FeaturesRepository
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

    // ===== Features =====
    var myFeatures by mutableStateOf<List<String>>(emptyList())
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
    private val mashupPcmChunks = mutableListOf<ByteArray>()
    private var mashupRecordingFile: File? = null
    private var mashupLevelJob: Job? = null
    private val MASHUP_SAMPLE_RATE = 44100

    val myHandle: String? get() = repository.getMyHandle()
    val authCookie: String? get() = repository.getAuthCookie()
    val rawToken: String? get() = repository.getRawToken()
    val mashupBackingUrl: String? get() = mashupBackingSession?.let { repository.buildStreamUrl(it.id) }

    // ===== Derived =====
    val bandSessions get() = sessions.filter { it.session_type == "band" }
    val individualSessions get() = sessions.filter { it.session_type != "band" }
    val activeBands get() = bands.filter { it.status == "active" }
    val pendingInvites get() = bands.filter { it.status == "invited" }

    fun hasFeature(key: String) = featuresRepository.hasFeature(myFeatures, key)

    // ===== Load =====

    fun loadAll() {
        loadSessions()
        loadBandMemberSessions()
        loadBands()
        loadInstruments()
        loadFeatures()
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

    fun loadFeatures() {
        viewModelScope.launch {
            myFeatures = withContext(Dispatchers.IO) { featuresRepository.getMyFeatures() }
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
        playbackVolume: Float? = null
    ) {
        audioDefaults = audioDefaults.copy(
            echo_cancellation = echoCancellation ?: audioDefaults.echo_cancellation,
            noise_suppression = noiseSuppression ?: audioDefaults.noise_suppression,
            auto_gain_control = autoGainControl ?: audioDefaults.auto_gain_control,
            playback_volume = playbackVolume ?: audioDefaults.playback_volume
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
                is BreakroomResult.Success -> currentDevice = result.data
                else -> { /* non-critical */ }
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
    fun updateMashupName(n: String) { mashupName = n }
    fun updateMashupRecordedAt(d: String) { mashupRecordedAt = d }
    fun clearMashupRecording() { mashupFile?.delete(); mashupFile = null; mashupName = "" }
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
                    file.delete()
                    pendingRecordingFile = null
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
                    recordingFile = null
                    recordingState = RecordingState.IDLE
                }
                is BreakroomResult.AuthenticationError -> {
                    errorMessage = "Session expired"
                    file.delete()
                    pendingRecordingFile = null
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
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            MASHUP_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )
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

    fun stopMashupRecording() {
        timerJob?.cancel(); timerJob = null
        mashupLevelJob?.cancel(); mashupLevelJob = null
        recordingLevelPercent = 0
        recordingSeconds = 0

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        // Flatten PCM chunks
        val chunks = synchronized(mashupPcmChunks) { mashupPcmChunks.toList().also { mashupPcmChunks.clear() } }
        val totalBytes = chunks.sumOf { it.size }
        val combined = ByteArray(totalBytes)
        var off = 0
        for (chunk in chunks) { chunk.copyInto(combined, off); off += chunk.size }

        // Peak normalize to 0.7 (same as web — compensates for OS echo suppression attenuation)
        val PEAK_TARGET = 22938 // floor(0.7 * 32767)
        var maxAmp = 0
        for (i in 0 until combined.size - 1 step 2) {
            val s = kotlin.math.abs(((combined[i + 1].toInt() shl 8) or (combined[i].toInt() and 0xFF)).toShort().toInt())
            if (s > maxAmp) maxAmp = s
        }
        if (maxAmp in 1 until PEAK_TARGET) {
            val boost = PEAK_TARGET.toFloat() / maxAmp
            for (i in 0 until combined.size - 1 step 2) {
                val s = ((combined[i + 1].toInt() shl 8) or (combined[i].toInt() and 0xFF)).toShort().toInt()
                val boosted = (s * boost).toInt().coerceIn(-32768, 32767)
                combined[i] = (boosted and 0xFF).toByte()
                combined[i + 1] = ((boosted shr 8) and 0xFF).toByte()
            }
        }

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

        viewModelScope.launch {
            isMerging = true
            mergeError = null

            val result = withContext(Dispatchers.IO) {
                try {
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

                    // Extract raw PCM from both WAV files and mix
                    val backingPcm = extractWavPcm(backingBytes)
                    val newPcm = extractWavPcm(newFile.readBytes())
                    val mixed = mixPcm(backingPcm, newPcm, mashupBackingVolume, mashupNewVolume)

                    // Encode mixed PCM as WAV and upload as a new session
                    val mergedWav = encodeWav(mixed, MASHUP_SAMPLE_RATE)
                    val mergedFile = File(context.cacheDir, "merged_${System.currentTimeMillis()}.wav")
                    mergedFile.writeBytes(mergedWav)

                    val name = mashupName.ifBlank { "Merged – ${backingSession.name}" }
                    val uploadResult = repository.uploadSession(
                        mergedFile, name,
                        mashupRecordedAt.takeIf { it.isNotBlank() },
                        "individual", null, null
                    )
                    mergedFile.delete()
                    uploadResult
                } catch (e: Exception) {
                    BreakroomResult.Error(e.message ?: "Merge failed")
                }
            }

            when (result) {
                is BreakroomResult.Success -> {
                    sessions = listOf(result.data) + sessions
                    val year = yearFromDate(result.data.recorded_at ?: result.data.uploaded_at)
                    if (year != null && indivYear != null && indivYear != year) indivYear = year
                    mashupFile?.delete(); mashupFile = null
                    mashupName = ""
                    mashupBackingSession = null
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
        return bytes.copyOfRange(dataStart.coerceIn(0, bytes.size), bytes.size)
    }

    private fun mixPcm(a: ByteArray, b: ByteArray, volA: Float, volB: Float): ByteArray {
        val len = maxOf(a.size, b.size).let { if (it % 2 == 0) it else it + 1 }
        val out = ByteArray(len)
        var i = 0
        while (i < len - 1) {
            val sa = if (i + 1 < a.size) ((a[i + 1].toInt() shl 8) or (a[i].toInt() and 0xFF)).toShort().toInt() else 0
            val sb = if (i + 1 < b.size) ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt() else 0
            val mixed = ((sa * volA) + (sb * volB)).toInt().coerceIn(-32768, 32767)
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
