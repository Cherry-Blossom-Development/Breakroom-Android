package com.cherryblossomdev.breakroom.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import com.cherryblossomdev.breakroom.ui.theme.isLargeTextScale
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
import com.cherryblossomdev.breakroom.data.models.DiscoverBlog
import com.cherryblossomdev.breakroom.data.models.DiscoverGallery
import com.cherryblossomdev.breakroom.data.models.DiscoverShowcase
import com.cherryblossomdev.breakroom.network.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ==================== ViewModel ====================

// Each Discover section shows at most PAGE_SIZE items at a time and pages
// in more via "Load more" instead of fetching every public gallery/
// showcase/blog on the site at once -- matches PAGE_SIZE in the web
// client's DiscoverPage.vue.
private const val DISCOVER_PAGE_SIZE = 8
private const val SEARCH_DEBOUNCE_MS = 350L

data class DiscoverSectionState<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val loadingMore: Boolean = false
)

data class DiscoverUiState(
    val showcases: DiscoverSectionState<DiscoverShowcase> = DiscoverSectionState(),
    val galleries: DiscoverSectionState<DiscoverGallery> = DiscoverSectionState(),
    val blogs: DiscoverSectionState<DiscoverBlog> = DiscoverSectionState(),
    val isLoading: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

class DiscoverViewModel(
    private val repository: DiscoverRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    // Fetches page 1 of all three sections, replacing whatever they held.
    // Only shows the full-page spinner on the very first load -- a
    // debounced search re-runs this in the background so typing doesn't
    // blank the page each keystroke.
    fun loadAll() {
        viewModelScope.launch {
            val showFullSpinner = !_uiState.value.hasLoadedOnce
            _uiState.value = _uiState.value.copy(isLoading = showFullSpinner, error = null)

            val q = _uiState.value.searchQuery.trim()
            val galleriesResult = repository.getGalleries(DISCOVER_PAGE_SIZE, 0, q)
            val showcasesResult = repository.getShowcases(DISCOVER_PAGE_SIZE, 0, q)
            val blogsResult = repository.getBlogs(DISCOVER_PAGE_SIZE, 0, q)

            val galleries = (galleriesResult as? BreakroomResult.Success)?.data
            val showcases = (showcasesResult as? BreakroomResult.Success)?.data
            val blogs = (blogsResult as? BreakroomResult.Success)?.data

            if (showFullSpinner && (galleries == null || showcases == null || blogs == null)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load Discover content"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasLoadedOnce = true,
                    galleries = galleries?.let { DiscoverSectionState(it.items, it.total) }
                        ?: _uiState.value.galleries,
                    showcases = showcases?.let { DiscoverSectionState(it.items, it.total) }
                        ?: _uiState.value.showcases,
                    blogs = blogs?.let { DiscoverSectionState(it.items, it.total) }
                        ?: _uiState.value.blogs
                )
            }
        }
    }

    fun setSearchQuery(value: String) {
        _uiState.value = _uiState.value.copy(searchQuery = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadAll()
        }
    }

    fun loadMoreGalleries() {
        loadMoreSection(
            section = _uiState.value.galleries,
            update = { _uiState.value = _uiState.value.copy(galleries = it) },
            fetch = { limit, offset, q -> repository.getGalleries(limit, offset, q) }
        )
    }

    fun loadMoreShowcases() {
        loadMoreSection(
            section = _uiState.value.showcases,
            update = { _uiState.value = _uiState.value.copy(showcases = it) },
            fetch = { limit, offset, q -> repository.getShowcases(limit, offset, q) }
        )
    }

    fun loadMoreBlogs() {
        loadMoreSection(
            section = _uiState.value.blogs,
            update = { _uiState.value = _uiState.value.copy(blogs = it) },
            fetch = { limit, offset, q -> repository.getBlogs(limit, offset, q) }
        )
    }

    private fun <T> loadMoreSection(
        section: DiscoverSectionState<T>,
        update: (DiscoverSectionState<T>) -> Unit,
        fetch: suspend (Int, Int, String?) -> BreakroomResult<com.cherryblossomdev.breakroom.data.DiscoverPage<T>>
    ) {
        if (section.loadingMore || section.items.size >= section.total) return
        update(section.copy(loadingMore = true))
        viewModelScope.launch {
            val q = _uiState.value.searchQuery.trim()
            val result = fetch(DISCOVER_PAGE_SIZE, section.items.size, q)
            val page = (result as? BreakroomResult.Success)?.data
            update(
                if (page != null) {
                    section.copy(items = section.items + page.items, total = page.total, loadingMore = false)
                } else {
                    section.copy(loadingMore = false)
                }
            )
        }
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

private fun openInBrowser(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val query = state.searchQuery.trim()

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
                            text = "Browse artist showcases, galleries, and blogs people have made discoverable.",
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

                    discoverSection(
                        title = "Showcases",
                        section = state.showcases,
                        emptyMessage = if (query.isEmpty()) "Nothing to discover yet." else "No showcases match your search.",
                        onLoadMore = viewModel::loadMoreShowcases
                    ) { showcase ->
                        DiscoverShowcaseCard(
                            showcase = showcase,
                            onClick = { openInBrowser(context, "https://www.prosaurus.com/store/${showcase.store_url}") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    discoverSection(
                        title = "Galleries",
                        section = state.galleries,
                        emptyMessage = if (query.isEmpty()) "Nothing to discover yet." else "No galleries match your search.",
                        onLoadMore = viewModel::loadMoreGalleries
                    ) { gallery ->
                        DiscoverGalleryCard(
                            gallery = gallery,
                            onClick = { openInBrowser(context, "https://www.prosaurus.com/g/${gallery.gallery_url}") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    discoverSection(
                        title = "Blogs",
                        section = state.blogs,
                        emptyMessage = if (query.isEmpty()) "Nothing to discover yet." else "No blogs match your search.",
                        onLoadMore = viewModel::loadMoreBlogs
                    ) { blogEntry ->
                        DiscoverBlogCard(
                            blogEntry = blogEntry,
                            onClick = { openInBrowser(context, "https://www.prosaurus.com/b/${blogEntry.blog_url}") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// Renders one Discover section: a heading, an empty state or a 2-column
// grid of cards, and a "Load more" button while more results remain on
// the server (see DiscoverSectionState.total vs items.size).
private fun <T> LazyListScope.discoverSection(
    title: String,
    section: DiscoverSectionState<T>,
    emptyMessage: String,
    onLoadMore: () -> Unit,
    card: @Composable RowScope.(T) -> Unit
) {
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    if (section.items.isEmpty()) {
        item {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        items(section.items.chunked(2)) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { entry -> card(entry) }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (section.items.size < section.total) {
            item {
                val remaining = section.total - section.items.size
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onLoadMore, enabled = !section.loadingMore) {
                        Text(if (section.loadingMore) "Loading..." else "Load more ($remaining more)")
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
private fun DiscoverBlogCard(
    blogEntry: DiscoverBlog,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    DiscoverCard(
        modifier = modifier,
        coverImagePath = null,
        coverTitle = blogEntry.latest_post_title,
        coverExcerpt = blogEntry.latest_post_excerpt,
        name = blogEntry.blog_name,
        artist = blogEntry.artist,
        countLabel = "${blogEntry.post_count} post${if (blogEntry.post_count == 1) "" else "s"}",
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
    modifier: Modifier = Modifier,
    coverTitle: String? = null,
    coverExcerpt: String? = null
) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$name by ${artistDisplayName(artist)}, $countLabel" +
                    if (coverTitle != null) ", latest post $coverTitle" else ""
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
                } else if (coverTitle != null) {
                    // Blogs have no cover image, so their preview slot shows a
                    // teaser of the most recent post instead (title + excerpt).
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "LATEST POST",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = coverTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!coverExcerpt.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = coverExcerpt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            val stackArtistRow = isLargeTextScale()
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (stackArtistRow) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val artistAvatar = @Composable {
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
                }
                val artistName = @Composable {
                    Text(
                        text = artistDisplayName(artist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (stackArtistRow) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (stackArtistRow) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        artistAvatar()
                        artistName()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        artistAvatar()
                        artistName()
                    }
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
