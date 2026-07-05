package com.cherryblossomdev.breakroom.ui.screens.bandpage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cherryblossomdev.breakroom.data.SessionsRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.Instrument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BandPageMemberUi(
    val id: Int,
    val handle: String,
    val firstName: String?,
    val lastName: String?,
    val photoUrl: String?,
    val role: String,
    val instrumentIds: Set<Int>,
    val isSaving: Boolean = false
)

data class BandPageSongUi(
    val id: Int,
    val name: String?,
    val recordedAt: String?,
    val uploaderHandle: String,
    val instrumentName: String?,
    val onPage: Boolean,
    val displayOrder: Int
)

data class BandPageUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val bandName: String = "",
    val bandUrl: String = "",
    val story: String = "",
    val backgroundColor: String = "",
    val backgroundPhotoUrl: String? = null,
    val isPublished: Boolean = false,
    val isSavingSettings: Boolean = false,
    val isUploadingBackground: Boolean = false,
    val saveMessage: String? = null,
    val members: List<BandPageMemberUi> = emptyList(),
    val instruments: List<Instrument> = emptyList(),
    val songs: List<BandPageSongUi> = emptyList()
)

class BandPageSetupViewModel(
    private val repository: SessionsRepository,
    private val bandId: Int
) : ViewModel() {

    private val _state = MutableStateFlow(BandPageUiState())
    val state: StateFlow<BandPageUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = repository.getBandPage(bandId)) {
                is BreakroomResult.Success -> {
                    val data = result.data
                    _state.value = _state.value.copy(
                        isLoading = false,
                        bandName = data.band_name,
                        bandUrl = data.band_url ?: "",
                        story = data.story ?: "",
                        backgroundColor = data.background_color ?: "",
                        backgroundPhotoUrl = data.background_photo_url,
                        isPublished = data.is_published,
                        members = data.members.map { m ->
                            BandPageMemberUi(
                                id = m.id,
                                handle = m.handle,
                                firstName = m.first_name,
                                lastName = m.last_name,
                                photoUrl = m.photo_url,
                                role = m.role,
                                instrumentIds = m.instrument_ids.toSet()
                            )
                        },
                        instruments = data.instruments,
                        songs = data.sessions.map { s ->
                            BandPageSongUi(
                                id = s.id,
                                name = s.name,
                                recordedAt = s.recorded_at,
                                uploaderHandle = s.uploader_handle,
                                instrumentName = s.instrument_name,
                                onPage = s.on_page == 1,
                                displayOrder = s.display_order
                            )
                        }
                    )
                }
                is BreakroomResult.Error -> {
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                else -> {
                    _state.value = _state.value.copy(isLoading = false, error = "Failed to load band page")
                }
            }
        }
    }

    fun updateBandUrl(value: String) {
        val sanitized = value.lowercase().filter { it.isLetterOrDigit() || it == '-' }
        _state.value = _state.value.copy(bandUrl = sanitized)
    }

    fun updateStory(value: String) {
        _state.value = _state.value.copy(story = value)
    }

    fun updateBackgroundColor(value: String) {
        _state.value = _state.value.copy(backgroundColor = value)
    }

    fun updatePublished(value: Boolean) {
        _state.value = _state.value.copy(isPublished = value)
        saveSettings()
    }

    fun saveSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSavingSettings = true, error = null, saveMessage = null)
            val current = _state.value
            when (val result = repository.updateBandPage(
                bandId,
                current.bandUrl.trim().ifEmpty { null },
                current.story.trim().ifEmpty { null },
                current.backgroundColor.ifEmpty { null },
                current.isPublished
            )) {
                is BreakroomResult.Success -> {
                    _state.value = _state.value.copy(isSavingSettings = false, saveMessage = "Settings saved")
                }
                is BreakroomResult.Error -> {
                    _state.value = _state.value.copy(isSavingSettings = false, error = result.message)
                }
                else -> {
                    _state.value = _state.value.copy(isSavingSettings = false, error = "Failed to save settings")
                }
            }
        }
    }

    fun uploadBackground(uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploadingBackground = true, error = null)
            when (val result = repository.uploadBandPageBackground(bandId, uri)) {
                is BreakroomResult.Success -> {
                    _state.value = _state.value.copy(
                        isUploadingBackground = false,
                        backgroundPhotoUrl = result.data.background_photo_url
                    )
                }
                is BreakroomResult.Error -> {
                    _state.value = _state.value.copy(isUploadingBackground = false, error = result.message)
                }
                else -> {
                    _state.value = _state.value.copy(isUploadingBackground = false, error = "Failed to upload background")
                }
            }
        }
    }

    fun removeBackground() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploadingBackground = true, error = null)
            when (val result = repository.deleteBandPageBackground(bandId)) {
                is BreakroomResult.Success -> {
                    _state.value = _state.value.copy(isUploadingBackground = false, backgroundPhotoUrl = null)
                }
                is BreakroomResult.Error -> {
                    _state.value = _state.value.copy(isUploadingBackground = false, error = result.message)
                }
                else -> {
                    _state.value = _state.value.copy(isUploadingBackground = false, error = "Failed to remove background")
                }
            }
        }
    }

    fun toggleInstrument(member: BandPageMemberUi, instrumentId: Int) {
        val newIds = if (member.instrumentIds.contains(instrumentId))
            member.instrumentIds - instrumentId
        else
            member.instrumentIds + instrumentId

        _state.value = _state.value.copy(
            members = _state.value.members.map {
                if (it.id == member.id) it.copy(instrumentIds = newIds, isSaving = true) else it
            }
        )

        viewModelScope.launch {
            val result = repository.setBandPageMemberInstruments(bandId, member.id, newIds.toList())
            _state.value = _state.value.copy(
                members = _state.value.members.map {
                    if (it.id == member.id) it.copy(isSaving = false) else it
                },
                error = (result as? BreakroomResult.Error)?.message ?: _state.value.error
            )
        }
    }

    fun toggleSong(song: BandPageSongUi) {
        _state.value = _state.value.copy(
            songs = _state.value.songs.map {
                if (it.id == song.id) it.copy(onPage = !it.onPage) else it
            }
        )
        saveSongs()
    }

    fun moveSong(song: BandPageSongUi, direction: Int) {
        val featured = _state.value.songs.filter { it.onPage }.sortedBy { it.displayOrder }
        val idx = featured.indexOfFirst { it.id == song.id }
        val swapIdx = idx + direction
        if (idx == -1 || swapIdx < 0 || swapIdx >= featured.size) return

        val a = featured[idx]
        val b = featured[swapIdx]
        val updatedSongs = _state.value.songs.map {
            when (it.id) {
                a.id -> it.copy(displayOrder = b.displayOrder)
                b.id -> it.copy(displayOrder = a.displayOrder)
                else -> it
            }
        }
        _state.value = _state.value.copy(songs = updatedSongs)
        saveSongs()
    }

    private fun saveSongs() {
        viewModelScope.launch {
            val ordered = _state.value.songs.filter { it.onPage }.sortedBy { it.displayOrder }.map { it.id }
            val result = repository.setBandPageSongs(bandId, ordered)
            if (result is BreakroomResult.Error) {
                _state.value = _state.value.copy(error = result.message)
            }
        }
    }

    fun clearSaveMessage() {
        _state.value = _state.value.copy(saveMessage = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
