package com.cherryblossomdev.breakroom.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.cherryblossomdev.breakroom.ui.theme.scaledDp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cherryblossomdev.breakroom.data.DiscoverRepository
import com.cherryblossomdev.breakroom.data.models.BreakroomResult
import com.cherryblossomdev.breakroom.data.models.DiscoverArtist
import com.cherryblossomdev.breakroom.data.models.DiscoverGallery
import com.cherryblossomdev.breakroom.data.models.DiscoverShowcase
import com.cherryblossomdev.breakroom.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

data class DiscoverUiState(
    val showcases: List<DiscoverShowcase> = emptyList(),
    val galleries: List<DiscoverGallery> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

class DiscoverViewModel(
    private val repository: DiscoverRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    fun loadAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val galleriesResult = repository.getGalleries()
            val showcasesResult = repository.getShowcases()

            val galleries = (galleriesResult as? BreakroomResult.Success)?.data
            val showcases = (showcasesResult as? BreakroomResult.Success)?.data

            if (galleries == null || showcases == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load Discover content"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    galleries = galleries,
                    showcases = showcases
                )
            }
        }
    }

    fun setSearchQuery(value: String) {
        _uiState.value = _uiState.value.copy(searchQuery = value)
    }
}

// ==================== Screen ====================

private fun artistDisplayName(artist: DiscoverArtist): String {
    val name = "${artist.first_name.orEmpty()} ${artist.last_name.orEmpty()}".trim()
    return name.ifEmpty { artist.handle }
}

private fun artistInitial(artist: DiscoverArtist): String {
    return (artist.first_name?.firstOrNull() ?: artist.handle.firstOrNull() ?: '?').toString()
}

private fun matchesDiscoverQuery(name: String, artist: DiscoverArtist, query: String): Boolean {
    return name.lowercase().contains(query) ||
        artistDisplayName(artist).lowercase().contains(query) ||
        artist.handle.lowercase().contains(query)
}

private fun openInBrowser(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val query = state.searchQuery.trim().lowercase()
    val filteredShowcases = remember(state.showcases, query) {
        if (query.isEmpty()) state.showcases
        else state.showcases.filter { matchesDiscoverQuery(it.page_title ?: it.store_url, it.artist, query) }
    }
    val filteredGalleries = remember(state.galleries, query) {
        if (query.isEmpty()) state.galleries
        else state.galleries.filter { matchesDiscoverQuery(it.gallery_name, it.artist, query) }
    }

    Scaffold(contentWindowInsets = WindowInsets(0)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.loadAll() }) { Text("Retry") }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Browse art galleries and artist showcases people have made discoverable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text("Search by name or artist...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(
                            text = "Showcases",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (filteredShowcases.isEmpty()) {
                        item {
                            Text(
                                text = if (state.showcases.isEmpty()) "Nothing to discover yet." else "No showcases match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filteredShowcases.chunked(2)) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { showcase ->
                                    DiscoverShowcaseCard(
                                        showcase = showcase,
                                        onClick = { openInBrowser(context, "https://www.prosaurus.com/store/${showcase.store_url}") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Galleries",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (filteredGalleries.isEmpty()) {
                        item {
                            Text(
                                text = if (state.galleries.isEmpty()) "Nothing to discover yet." else "No galleries match your search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filteredGalleries.chunked(2)) { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { gallery ->
                                    DiscoverGalleryCard(
                                        gallery = gallery,
                                        onClick = { openInBrowser(context, "https://www.prosaurus.com/g/${gallery.gallery_url}") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverShowcaseCard(
    showcase: DiscoverShowcase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DiscoverCard(
        modifier = modifier,
        coverImagePath = showcase.cover_image_path,
        name = showcase.page_title?.takeIf { it.isNotBlank() } ?: showcase.store_url,
        artist = showcase.artist,
        countLabel = "${showcase.item_count} item${if (showcase.item_count == 1) "" else "s"}",
        onClick = onClick
    )
}

@Composable
private fun DiscoverGalleryCard(
    gallery: DiscoverGallery,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DiscoverCard(
        modifier = modifier,
        coverImagePath = gallery.cover_image_path,
        name = gallery.gallery_name,
        artist = gallery.artist,
        countLabel = "${gallery.artwork_count} artwork${if (gallery.artwork_count == 1) "" else "s"}",
        onClick = onClick
    )
}

@Composable
private fun DiscoverCard(
    coverImagePath: String?,
    name: String,
    artist: DiscoverArtist,
    countLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$name by ${artistDisplayName(artist)}, $countLabel"
            },
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverImagePath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("${RetrofitClient.BASE_URL}uploads/$coverImagePath")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "No preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(scaledDp(18))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artist.photo_path != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("${RetrofitClient.BASE_URL}uploads/${artist.photo_path}")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = artistInitial(artist),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = artistDisplayName(artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
