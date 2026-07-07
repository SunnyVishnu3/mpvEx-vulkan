@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package app.gyrolet.mpvrx.ui.browser.anime

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.domain.anicli.AniCliAnime
import app.gyrolet.mpvrx.domain.anicli.AniCliEpisode
import app.gyrolet.mpvrx.domain.anicli.AniCliStreamLink
import app.gyrolet.mpvrx.domain.anicli.AniCliSubtitleTrack
import app.gyrolet.mpvrx.domain.anicli.AniCliUiState
import app.gyrolet.mpvrx.domain.anicli.AnimeDownloadInfo
import app.gyrolet.mpvrx.domain.anicli.AnimeHistoryEntry
import app.gyrolet.mpvrx.domain.anicli.AnimeListContext
import app.gyrolet.mpvrx.domain.anicli.AnimeSource
import app.gyrolet.mpvrx.domain.anicli.DownloadState
import app.gyrolet.mpvrx.domain.anicli.onlyEnglishSubtitles
import app.gyrolet.mpvrx.preferences.EpisodeViewMode
import app.gyrolet.mpvrx.preferences.TrendingViewMode
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.browser.components.BrowserTopBar
import app.gyrolet.mpvrx.ui.icons.AppIcon
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.preferences.getSimplifiedStoragePath
import app.gyrolet.mpvrx.utils.media.MediaUtils
import app.gyrolet.mpvrx.utils.media.PlaybackSubtitleTrack
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ScoreGold = Color(0xFFFFC107)
private val SubAccent = Color(0xFF4FC3F7)
private val DownloadAccent = Color(0xFF43A047)
private val HoldAccent = Color(0xFFF9A825)

private enum class AnimeHomeTab(val label: String, val icon: AppIcon) {
    TRENDING("Trending", Icons.Default.AutoAwesome),
    SEARCH("Search", Icons.Default.Search),
    BOOKMARKS("Bookmarks", Icons.Default.Bookmarks),
    HISTORY("History", Icons.Default.History),
    DOWNLOADS("Downloads", Icons.Default.Download),
}

internal fun animeSearchPlaceholder(selectedSource: AnimeSource): String =
    when (selectedSource) {
        AnimeSource.MOVIEBOX -> "Search movies and TV shows"
    }

internal fun canSubmitAnimeSearch(query: String, isSearching: Boolean): Boolean =
    query.isNotBlank()

internal fun animeSearchKey(selectedSource: AnimeSource, query: String): String =
    "${selectedSource.name}:${query.trim().lowercase(Locale.ROOT)}"

internal fun isInfiniteAnimeGridMode(
    selectedTabIsTrending: Boolean,
    selectedSource: AnimeSource,
    trendingViewMode: TrendingViewMode,
): Boolean =
    selectedTabIsTrending &&
        selectedSource == AnimeSource.MOVIEBOX &&
        trendingViewMode == TrendingViewMode.Grid

@Serializable
object AnimeScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: AnimeViewModel = org.koin.compose.viewmodel.koinViewModel()
        val uiState by viewModel.uiState.collectAsState()
        val downloads by viewModel.downloads.collectAsState()
        val bookmarks by viewModel.bookmarks.collectAsState()
        val animeFolderUri by viewModel.animeFolderUri.collectAsState()
        val episodeViewMode by viewModel.episodeViewMode.collectAsState()
        val episodeSortAscending by viewModel.episodeSortAscending.collectAsState()
        val trendingViewMode by viewModel.trendingViewMode.collectAsState()
        val keyboard = LocalSoftwareKeyboardController.current
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        var selectedTab by rememberSaveable { mutableStateOf(AnimeHomeTab.TRENDING) }
        var lastSubmittedSearchKey by rememberSaveable { mutableStateOf<String?>(null) }
        val collapsedDownloadGroups = rememberSaveable(
            saver = listSaver(
                save = { it.toList() },
                restore = { it.toMutableStateList() },
            )
        ) { mutableStateListOf<String>() }
        val groupedDownloads = remember(downloads) {
            downloads.values.groupBy { it.animeName }.entries.toList()
        }

        val submitSearch: () -> Unit = submitSearch@{
            val query = uiState.searchQuery.trim()
            if (!canSubmitAnimeSearch(query, uiState.isSearching)) return@submitSearch
            lastSubmittedSearchKey = animeSearchKey(uiState.selectedSource, query)
            keyboard?.hide()
            selectedTab = AnimeHomeTab.SEARCH
            viewModel.search()
        }

        LaunchedEffect(uiState.infoMessage) {
            if (uiState.infoMessage != null) viewModel.clearInfoMessage()
        }

        LaunchedEffect(uiState.errorMessage) {
            if (uiState.errorMessage != null) viewModel.clearError()
        }

        LaunchedEffect(uiState.selectedSource, uiState.searchQuery.trim()) {
            val query = uiState.searchQuery.trim()
            if (query.isBlank()) {
                lastSubmittedSearchKey = null
                return@LaunchedEffect
            }
            delay(360)
            val current = viewModel.uiState.value
            val key = animeSearchKey(current.selectedSource, current.searchQuery)
            if (current.searchQuery.trim() == query && lastSubmittedSearchKey != key) {
                lastSubmittedSearchKey = key
                selectedTab = AnimeHomeTab.SEARCH
                viewModel.search()
            }
        }

        LaunchedEffect(listState, selectedTab, uiState.trendingAnime, uiState.animeProviderHasMore, trendingViewMode) {
            snapshotFlow {
                if (
                    !isInfiniteAnimeGridMode(
                        selectedTabIsTrending = selectedTab == AnimeHomeTab.TRENDING,
                        selectedSource = uiState.selectedSource,
                        trendingViewMode = trendingViewMode,
                    ) ||
                    uiState.trendingAnime.isEmpty() ||
                    !uiState.animeProviderHasMore ||
                    uiState.isLoadingTrending
                ) {
                    false
                } else {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
                    val total = listState.layoutInfo.totalItemsCount
                    total > 0 && lastVisible >= total - 5
                }
            }.distinctUntilChanged().collectLatest { shouldLoad ->
                if (shouldLoad) viewModel.loadMoreAnime()
            }
        }

        LaunchedEffect(listState, selectedTab, uiState.hasSearched, uiState.searchResults, uiState.searchHasMore) {
            snapshotFlow {
                if (
                    selectedTab != AnimeHomeTab.SEARCH ||
                    !uiState.hasSearched ||
                    uiState.searchResults.isEmpty() ||
                    !uiState.searchHasMore ||
                    uiState.isSearching ||
                    uiState.isLoadingMoreSearch
                ) {
                    false
                } else {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
                    val total = listState.layoutInfo.totalItemsCount
                    total > 0 && lastVisible >= total - 5
                }
            }.distinctUntilChanged().collectLatest { shouldLoad ->
                if (shouldLoad) viewModel.loadMoreSearchResults()
            }
        }

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                BrowserTopBar(
                    title = "Anime Mode",
                    isInSelectionMode = false,
                    selectedCount = 0,
                    totalCount = 0,
                    onCancelSelection = { },
                )
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    AnimeSearchRow(
                        selectedSource = uiState.selectedSource,
                        searchQuery = uiState.searchQuery,
                        isSearching = uiState.isSearching,
                        onQueryChange = viewModel::setSearchQuery,
                        onSearch = submitSearch,
                        onClear = {
                            lastSubmittedSearchKey = null
                            viewModel.clearSearch()
                        },
                    )
                }

                item {
                    AnimeSectionTabs(
                        selected = selectedTab,
                        historyCount = uiState.animeHistory.size,
                        bookmarksCount = bookmarks.size,
                        downloadsCount = downloads.size,
                        onSelect = { tab ->
                            selectedTab = tab
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    )
                }


                when (selectedTab) {
                    AnimeHomeTab.TRENDING -> {
                        item {
                            SectionHeader(
                                icon = Icons.Default.AutoAwesome,
                                title = "Latest on MovieBox",
                                actionLabel = trendingViewMode.displayName,
                                onAction = viewModel::toggleTrendingViewMode,
                            )
                        }
                        if (uiState.isLoadingTrending && uiState.trendingAnime.isEmpty()) {
                            item { LoadingCard("Loading MovieBox") }
                        } else if (uiState.trendingAnime.isEmpty()) {
                            item { EmptyCard(Icons.Default.Movie, "Nothing loaded", "Try search or refresh later") }
                        } else if (trendingViewMode == TrendingViewMode.List) {
                            items(uiState.trendingAnime, key = { "trend_list_${it.id}" }) { anime ->
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    AnimeResultCard(
                                        anime = anime,
                                        isSelected = uiState.selectedAnime?.id == anime.id &&
                                            uiState.selectedListContext == AnimeListContext.TRENDING,
                                        isBookmarked = bookmarks.any { it.id == anime.id },
                                        onClick = { viewModel.selectAnime(anime, uiState.trendingAnime, AnimeListContext.TRENDING) },
                                        onBookmark = { viewModel.toggleBookmark(anime) },
                                    )
                                    if (uiState.selectedAnime?.id == anime.id && uiState.selectedListContext == AnimeListContext.TRENDING) {
                                        AnimeDetailPanel(uiState = uiState, bookmarks = bookmarks, episodeViewMode = episodeViewMode, episodeSortAscending = episodeSortAscending, viewModel = viewModel)
                                    }
                                }
                            }
                        } else {
                            items(uiState.trendingAnime.chunked(2), key = { row -> "trend_${row.firstOrNull()?.id.orEmpty()}" }) { pair ->
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    AnimeGridRow(
                                        animePair = pair,
                                        selectedAnimeId = uiState.selectedAnime?.id,
                                        onAnimeClick = { anime ->
                                            viewModel.selectAnime(anime, uiState.trendingAnime, AnimeListContext.TRENDING)
                                        },
                                    )
                                    if (uiState.selectedListContext == AnimeListContext.TRENDING && pair.any { it.id == uiState.selectedAnime?.id }) {
                                        AnimeDetailPanel(uiState = uiState, bookmarks = bookmarks, episodeViewMode = episodeViewMode, episodeSortAscending = episodeSortAscending, viewModel = viewModel)
                                    }
                                }
                            }
                        }
                        if (uiState.isLoadingTrending && uiState.trendingAnime.isNotEmpty()) {
                            item { InlineLoading() }
                        }
                    }

                    AnimeHomeTab.SEARCH -> {
                        item {
                            SectionHeader(
                                icon = Icons.Default.Search,
                                title = "Search results",
                                actionLabel = if (uiState.hasSearched) "Clear" else null,
                                onAction = if (uiState.hasSearched) viewModel::clearSearch else null,
                            )
                        }
                        when {
                            uiState.isSearching && uiState.searchResults.isEmpty() -> item { LoadingCard("Searching MovieBox") }
                            !uiState.hasSearched -> item {
                                EmptyCard(Icons.Default.Search, "Search MovieBox", "Tap a result, then pick an episode or stream")
                            }
                            uiState.searchResults.isEmpty() -> item {
                                EmptyCard(Icons.Default.Search, "No results", "Try a simpler title")
                            }
                            else -> {
                                items(uiState.searchResults, key = { "search_${it.id}" }) { anime ->
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        AnimeResultCard(
                                            anime = anime,
                                            isSelected = uiState.selectedAnime?.id == anime.id &&
                                                uiState.selectedListContext == AnimeListContext.SEARCH,
                                            isBookmarked = bookmarks.any { it.id == anime.id },
                                            onClick = { viewModel.selectAnime(anime, uiState.searchResults, AnimeListContext.SEARCH) },
                                            onBookmark = { viewModel.toggleBookmark(anime) },
                                        )
                                        if (uiState.selectedAnime?.id == anime.id && uiState.selectedListContext == AnimeListContext.SEARCH) {
                                            AnimeDetailPanel(uiState = uiState, bookmarks = bookmarks, episodeViewMode = episodeViewMode, episodeSortAscending = episodeSortAscending, viewModel = viewModel)
                                        }
                                    }
                                }
                            }
                        }
                        if (uiState.isLoadingMoreSearch) {
                            item { InlineLoading() }
                        }
                    }

                    AnimeHomeTab.BOOKMARKS -> {
                        item {
                            SectionHeader(Icons.Default.Bookmarks, "Bookmarks")
                        }
                        if (bookmarks.isEmpty()) {
                            item { EmptyCard(Icons.Default.Bookmarks, "No bookmarks", "Save MovieBox titles here") }
                        } else {
                            items(bookmarks.chunked(2), key = { row -> "book_${row.firstOrNull()?.id.orEmpty()}" }) { pair ->
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    AnimeGridRow(
                                        animePair = pair,
                                        selectedAnimeId = uiState.selectedAnime?.id,
                                        onAnimeClick = { anime ->
                                            viewModel.selectAnime(anime, bookmarks, AnimeListContext.BOOKMARKS)
                                        },
                                    )
                                    if (uiState.selectedListContext == AnimeListContext.BOOKMARKS && pair.any { it.id == uiState.selectedAnime?.id }) {
                                        AnimeDetailPanel(uiState = uiState, bookmarks = bookmarks, episodeViewMode = episodeViewMode, episodeSortAscending = episodeSortAscending, viewModel = viewModel)
                                    }
                                }
                            }
                        }
                    }

                    AnimeHomeTab.HISTORY -> {
                        item {
                            SectionHeader(
                                icon = Icons.Default.History,
                                title = "History",
                                actionLabel = if (uiState.animeHistory.isNotEmpty()) "Clear" else null,
                                onAction = if (uiState.animeHistory.isNotEmpty()) viewModel::clearHistory else null,
                            )
                        }
                        if (uiState.animeHistory.isEmpty()) {
                            item { EmptyCard(Icons.Default.History, "No watch history", "Played MovieBox titles appear here") }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(end = 4.dp),
                                    ) {
                                        items(uiState.animeHistory, key = { "hist_${it.anime.id}" }) { entry ->
                                            HistoryCard(
                                                entry = entry,
                                                isSelected = uiState.selectedAnime?.id == entry.anime.id &&
                                                    uiState.selectedListContext == AnimeListContext.HISTORY,
                                                onClick = {
                                                    viewModel.selectAnime(
                                                        entry.anime,
                                                        uiState.animeHistory.map { it.anime },
                                                        AnimeListContext.HISTORY,
                                                    )
                                                },
                                                onRemove = { viewModel.removeHistory(entry.anime.id) },
                                            )
                                        }
                                    }
                                    if (uiState.selectedAnime != null && uiState.selectedListContext == AnimeListContext.HISTORY) {
                                        AnimeDetailPanel(uiState = uiState, bookmarks = bookmarks, episodeViewMode = episodeViewMode, episodeSortAscending = episodeSortAscending, viewModel = viewModel)
                                    }
                                }
                            }
                        }
                    }

                    AnimeHomeTab.DOWNLOADS -> {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                SectionHeader(
                                    icon = Icons.Default.Download,
                                    title = "Downloads",
                                    modifier = Modifier.weight(1f),
                                )
                                val hasActive = viewModel.hasActiveDownloads()
                                val hasPaused = viewModel.hasPausedDownloads()
                                if (hasActive || hasPaused) {
                                    DownloadCardAction(
                                        label = if (hasActive) "Pause all" else "Resume all",
                                        icon = if (hasActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        onClick = {
                                            if (hasActive) viewModel.pauseAllDownloads() else viewModel.resumeAllDownloads()
                                        },
                                    )
                                }
                            }
                        }
                        if (groupedDownloads.isEmpty()) {
                            item { EmptyCard(Icons.Default.Download, "No downloads", "Pick an episode, then tap Download") }
                        } else {
                            groupedDownloads.forEach { (animeName, animeDownloads) ->
                                val isExpanded = animeName !in collapsedDownloadGroups
                                item(key = "download_group_$animeName") {
                                    DownloadGroupHeader(
                                        title = animeName,
                                        count = animeDownloads.size,
                                        expanded = isExpanded,
                                        onToggle = {
                                            if (isExpanded) collapsedDownloadGroups.add(animeName)
                                            else collapsedDownloadGroups.remove(animeName)
                                        },
                                    )
                                }
                                if (isExpanded) {
                                    items(animeDownloads, key = { "dl_${it.key}" }) { info ->
                                        val context = LocalContext.current
                                        DownloadProgressCard(
                                            info = info,
                                            onAction = {
                                                when (info.state) {
                                                    DownloadState.Preparing,
                                                    is DownloadState.InProgress -> viewModel.pauseDownload(info.animeName, info.epNo)
                                                    is DownloadState.Paused,
                                                    is DownloadState.Failed -> viewModel.resumeDownload(info.animeName, info.epNo)
                                                    else -> Unit
                                                }
                                            },
                                            onOpen = info.fileUri
                                                ?.takeIf { info.state == DownloadState.Completed }
                                                ?.let {
                                                    {
                                                        val subtitles = info.subtitleFileUri
                                                            ?.takeIf(String::isNotBlank)
                                                            ?.let { uri -> listOf(Uri.parse(uri)) }
                                                            .orEmpty()
                                                        MediaUtils.playFile(
                                                            source = Uri.parse(it),
                                                            context = context,
                                                            title = downloadPlaybackTitle(info),
                                                            subtitles = subtitles,
                                                            enabledSubtitles = subtitles,
                                                        )
                                                    }
                                                },
                                            onDismiss = { viewModel.dismissDownload(info.animeName, info.epNo) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showStreamSheet) {
            val anime = uiState.selectedAnime
            val episode = uiState.episodes.firstOrNull { it.id == uiState.selectedEpisode }
            if (anime != null && episode != null) {
                StreamSheet(
                    anime = anime,
                    episode = episode,
                    links = uiState.streamLinks,
                    isLoading = uiState.isLoadingStreams,
                    errorMessage = uiState.errorMessage,
                    hasAnimeFolder = viewModel.hasAnimeFolder(),
                    getDownloadState = { viewModel.getDownloadState(anime.name, episode.number) },
                    onDismiss = viewModel::dismissStreamSheet,
                    onPlay = { link, context ->
                        viewModel.dismissStreamSheet()
                        MediaUtils.playFile(
                            source = link.url,
                            context = context,
                            title = streamPlaybackTitle(anime, episode, link),
                            headers = playbackHeaders(link),
                            subtitleTracks = link.subtitles.map {
                                PlaybackSubtitleTrack(
                                    url = it.url,
                                    label = it.label,
                                    languageCode = it.languageCode,
                                )
                            },
                            enabledSubtitles = preferredSubtitleUris(link),
                        )
                        viewModel.addToHistory(anime, episode.number)
                    },
                    onDownload = { link ->
                        viewModel.onStreamDownloadAction(anime, episode, link)
                    },
                )
            }
        }
    }
}

@Composable
private fun AnimeSearchRow(
    selectedSource: AnimeSource,
    searchQuery: String,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    val canSearch = canSubmitAnimeSearch(searchQuery, isSearching)
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(animeSearchPlaceholder(selectedSource)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSearching) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                if (searchQuery.isNotBlank()) {
                    Surface(
                        onClick = onClear,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            modifier = Modifier.padding(7.dp).size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (canSearch) onSearch() }),
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    )
}

@Composable
private fun AnimeSectionTabs(
    selected: AnimeHomeTab,
    historyCount: Int,
    bookmarksCount: Int,
    downloadsCount: Int,
    onSelect: (AnimeHomeTab) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(AnimeHomeTab.entries) { tab ->
            val count = when (tab) {
                AnimeHomeTab.HISTORY -> historyCount
                AnimeHomeTab.BOOKMARKS -> bookmarksCount
                AnimeHomeTab.DOWNLOADS -> downloadsCount
                else -> 0
            }
            FilterChip(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        if (count > 0) "${tab.label} $count" else tab.label,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    Icon(tab.icon, null, modifier = Modifier.size(18.dp))
                },
                shape = RoundedCornerShape(999.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: AppIcon,
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun LoadingCard(label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InlineLoading() {
    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun EmptyCard(icon: AppIcon, title: String, sub: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AnimeDetailPanel(
    uiState: AniCliUiState,
    bookmarks: List<AniCliAnime>,
    episodeViewMode: EpisodeViewMode,
    episodeSortAscending: Boolean,
    viewModel: AnimeViewModel,
) {
    val anime = uiState.selectedAnime ?: return
    val list = when (uiState.selectedListContext) {
        AnimeListContext.SEARCH -> uiState.searchResults
        AnimeListContext.BOOKMARKS -> bookmarks
        AnimeListContext.HISTORY -> uiState.animeHistory.map { it.anime }
        AnimeListContext.TRENDING,
        null -> uiState.trendingAnime
    }
    val currentIndex = uiState.selectedAnimeIndex ?: list.indexOfFirst { it.id == anime.id }
    val directions = buildAnimeNavigationDirections(
        hasPrevious = currentIndex > 0,
        hasNext = currentIndex >= 0 && currentIndex < list.lastIndex,
    )
    val isBookmarked = bookmarks.any { it.id == anime.id }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
            ) {
                AnimeThumbnail(
                    thumbnail = anime.bannerImage?.takeIf { it.isNotBlank() } ?: anime.thumbnail,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Black.copy(alpha = 0.88f),
                                )
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    directions.forEach { direction ->
                        Surface(
                            onClick = { viewModel.selectAdjacentAnime(direction) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                modifier = Modifier.padding(8.dp).size(20.dp).scale(scaleX = if (direction == AnimeNavigationDirection.PREVIOUS) -1f else 1f, scaleY = 1f),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Surface(
                        onClick = { viewModel.toggleBookmark(anime) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    ) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmarks else Icons.Outlined.Bookmarks,
                            null,
                            modifier = Modifier.padding(8.dp).size(20.dp),
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        anime.type?.takeIf { it.isNotBlank() }?.let { AccentPill(it.uppercase(Locale.ROOT), SubAccent) }
                        anime.status?.takeIf { it.isNotBlank() }?.let { AccentPill(it, MaterialTheme.colorScheme.secondary) }
                        anime.score?.takeIf { it > 0f }?.let { AccentPill("${it.toInt()}%", ScoreGold) }
                    }
                    Text(
                        anime.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        animeCardDetails(anime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                anime.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (anime.genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        anime.genres.take(8).forEach { GenreChip(it) }
                    }
                }
                AnimeEpisodeGrid(
                    uiState = uiState,
                    viewMode = episodeViewMode,
                    sortAscending = episodeSortAscending,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun AnimeEpisodeGrid(
    uiState: AniCliUiState,
    viewMode: EpisodeViewMode,
    sortAscending: Boolean,
    viewModel: AnimeViewModel,
) {
    val anime = uiState.selectedAnime ?: return
    val showBulkDownload = shouldShowBulkEpisodeDownload(
        hasAnimeFolder = viewModel.hasAnimeFolder(),
        canDownloadSelectedSource = viewModel.canDownloadSelectedSource(),
        hasEpisodes = uiState.episodes.isNotEmpty(),
    )
    val sortedEpisodes = remember(uiState.episodes, sortAscending) {
        if (sortAscending) uiState.episodes else uiState.episodes.reversed()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBulkDownload) {
                Surface(
                    onClick = { viewModel.downloadAllEpisodes(anime) },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Download all", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Surface(
                onClick = viewModel::toggleEpisodeSort,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Icon(
                    if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                onClick = viewModel::toggleEpisodeViewMode,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Icon(
                    if (viewMode == EpisodeViewMode.Grid) Icons.Default.ViewList else Icons.Default.GridView,
                    null,
                    modifier = Modifier.padding(8.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when {
        uiState.isLoadingEpisodes -> LoadingCard("Loading episodes")
        uiState.episodes.isEmpty() -> EmptyCard(Icons.Default.Movie, "No episodes", "MovieBox did not return episodes")
        viewMode == EpisodeViewMode.Grid -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedEpisodes.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { episode ->
                            EpisodeChip(
                                episode = episode,
                                isSelected = episode.id == uiState.selectedEpisode,
                                dlState = viewModel.getDownloadState(anime.name, episode.number),
                                onPlay = { viewModel.selectEpisode(episode) },
                                onDownloadAction = { viewModel.onEpisodeDownloadAction(anime, episode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(2 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedEpisodes.forEach { episode ->
                    EpisodeChip(
                        episode = episode,
                        isSelected = episode.id == uiState.selectedEpisode,
                        dlState = viewModel.getDownloadState(anime.name, episode.number),
                        onPlay = { viewModel.selectEpisode(episode) },
                        onDownloadAction = { viewModel.onEpisodeDownloadAction(anime, episode) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeChip(
    episode: AniCliEpisode,
    isSelected: Boolean,
    dlState: DownloadState,
    onPlay: () -> Unit,
    onDownloadAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = when (dlState) {
        DownloadState.Completed -> DownloadAccent
        DownloadState.Preparing,
        is DownloadState.InProgress -> SubAccent
        is DownloadState.Paused -> HoldAccent
        is DownloadState.Failed -> colorScheme.error
        DownloadState.Idle -> colorScheme.primary
    }
    Surface(
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) accent.copy(alpha = 0.18f) else colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (isSelected) accent.copy(alpha = 0.35f) else colorScheme.outline.copy(alpha = 0.10f)),
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onPlay() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = accent)
                Text(
                    episodeChipLabel(episode),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxSize()
                    .clickable { onDownloadAction() },
                contentAlignment = Alignment.Center,
            ) {
                when (dlState) {
                    DownloadState.Preparing -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
                    is DownloadState.InProgress -> Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp), tint = accent)
                    is DownloadState.Paused -> Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp), tint = accent)
                    DownloadState.Completed -> Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp), tint = accent)
                    is DownloadState.Failed -> Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = accent)
                    DownloadState.Idle -> Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp), tint = colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun episodeChipLabel(episode: AniCliEpisode): String =
    when {
        episode.number.equals("movie", ignoreCase = true) -> "Movie"
        episode.title.isNullOrBlank() || episode.title == episode.number -> episode.number
        else -> episode.title
    }

@Composable
private fun StreamSheet(
    anime: AniCliAnime,
    episode: AniCliEpisode,
    links: List<AniCliStreamLink>,
    isLoading: Boolean,
    errorMessage: String?,
    hasAnimeFolder: Boolean,
    getDownloadState: () -> DownloadState,
    onDismiss: () -> Unit,
    onPlay: (AniCliStreamLink, android.content.Context) -> Unit,
    onDownload: (AniCliStreamLink) -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(Icons.Default.PlayArrow, streamPlaybackTitle(anime, episode, null))
            if (isLoading) {
                LoadingCard("Resolving streams")
            } else if (links.isEmpty()) {
                EmptyCard(Icons.Default.Movie, "Direct streams unavailable", errorMessage ?: "MovieBox did not return a playable stream")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(links, key = { "${it.quality}_${it.url}" }) { link ->
                        StreamDownloadRow(
                            link = link,
                            dlState = getDownloadState(),
                            showDownloadAction = hasAnimeFolder,
                            onPlay = { onPlay(link, context) },
                            onDownloadAction = { onDownload(link) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamDownloadRow(
    link: AniCliStreamLink,
    dlState: DownloadState,
    showDownloadAction: Boolean,
    onPlay: () -> Unit,
    onDownloadAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    link.title ?: link.quality,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccentPill(link.quality, MaterialTheme.colorScheme.primary)
                    if (link.isM3u8) AccentPill("HLS", MaterialTheme.colorScheme.tertiary)
                    if (link.subtitles.isNotEmpty()) AccentPill("Subs: ${formatSubtitleSummary(link.subtitles)}", SubAccent)
                    if (link.audioLanguages.isNotEmpty()) AccentPill(formatLanguageSummary(link.audioLanguages), MaterialTheme.colorScheme.secondary)
                }
            }
            if (showDownloadAction) {
                Surface(
                    onClick = onDownloadAction,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(Modifier.padding(10.dp).size(20.dp), contentAlignment = Alignment.Center) {
                        when (dlState) {
                            DownloadState.Preparing -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            is DownloadState.InProgress -> Icon(Icons.Default.Pause, null)
                            is DownloadState.Paused -> Icon(Icons.Default.PlayArrow, null)
                            DownloadState.Completed -> Icon(Icons.Default.Check, null, tint = DownloadAccent)
                            is DownloadState.Failed -> Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.error)
                            DownloadState.Idle -> Icon(Icons.Default.Download, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeGridRow(
    animePair: List<AniCliAnime>,
    selectedAnimeId: String?,
    onAnimeClick: (AniCliAnime) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        animePair.forEach { anime ->
            ExploreAnimeCard(
                anime = anime,
                isSelected = selectedAnimeId == anime.id,
                onClick = { onAnimeClick(anime) },
                modifier = Modifier.weight(1f),
            )
        }
        if (animePair.size == 1) {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExploreAnimeCard(
    anime: AniCliAnime,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = animeCardAccentColor(anime.score, MaterialTheme.colorScheme.primary)
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
        ),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                AnimeThumbnail(anime.thumbnail.ifBlank { anime.bannerImage.orEmpty() }, Modifier.fillMaxSize())
                anime.score?.takeIf { it > 0f }?.let { score ->
                    AnimeScoreBadge(score, Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
            Column(Modifier.padding(10.dp).height(72.dp)) {
                Text(
                    anime.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    animeCardDetails(anime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AnimeResultCard(
    anime: AniCliAnime,
    isSelected: Boolean,
    isBookmarked: Boolean,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
) {
    val accent = animeCardAccentColor(anime.score, MaterialTheme.colorScheme.primary)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) accent.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
        ),
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(82.dp, 116.dp).clip(RoundedCornerShape(16.dp))) {
                AnimeThumbnail(anime.thumbnail.ifBlank { anime.bannerImage.orEmpty() }, Modifier.fillMaxSize())
                anime.score?.takeIf { it > 0f }?.let { score ->
                    AnimeScoreBadge(score, Modifier.align(Alignment.TopEnd).padding(7.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    anime.type?.takeIf { it.isNotBlank() }?.let { AccentPill(it.uppercase(Locale.ROOT), accent) }
                    if (anime.subEpisodes > 0) AccentPill("${anime.subEpisodes} eps", SubAccent)
                }
                Text(
                    anime.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    animeCardDetails(anime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                onClick = onBookmark,
                shape = CircleShape,
                color = if (isBookmarked) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmarks else Icons.Outlined.Bookmarks,
                    null,
                    modifier = Modifier.padding(10.dp).size(18.dp),
                    tint = if (isBookmarked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: AnimeHistoryEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val dateText = remember(entry.watchedAt) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(entry.watchedAt))
    }
    Card(
        modifier = Modifier.width(208.dp).clickable { onClick() },
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isSelected) 0.26f else 0.08f)),
    ) {
        Box(Modifier.fillMaxWidth().height(286.dp)) {
            AnimeThumbnail(entry.anime.thumbnail.ifBlank { entry.anime.bannerImage.orEmpty() }, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.22f),
                                Color.Black.copy(alpha = 0.86f),
                            )
                        )
                    )
            )
            Surface(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
            ) {
                Icon(Icons.Default.Close, null, modifier = Modifier.padding(8.dp).size(18.dp))
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AccentPill("Ep ${entry.lastEpisode}", SubAccent)
                Text(
                    entry.anime.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(dateText, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.76f))
            }
        }
    }
}

@Composable
private fun DownloadLocationCard(folderUri: String) {
    val hasFolder = folderUri.isNotBlank()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hasFolder) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (hasFolder) Icons.Default.Folder else Icons.Outlined.FolderOff,
                null,
                modifier = Modifier.size(28.dp),
                tint = if (hasFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (hasFolder) "Saving to Anime folder" else "Anime download folder not set",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (hasFolder) getSimplifiedStoragePath(folderUri) else "Set it in Advanced settings before downloading",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DownloadGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count episode${if (count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DownloadCardAction(
    label: String,
    icon: AppIcon,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun DownloadProgressCard(
    info: AnimeDownloadInfo,
    onAction: () -> Unit,
    onOpen: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = when (info.state) {
        DownloadState.Preparing,
        is DownloadState.InProgress -> SubAccent
        is DownloadState.Paused -> HoldAccent
        DownloadState.Completed -> DownloadAccent
        is DownloadState.Failed -> colorScheme.error
        DownloadState.Idle -> colorScheme.primary
    }
    val title = info.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episode ${info.epNo}"
    val progressFraction = when (val state = info.state) {
        DownloadState.Preparing -> 0.12f
        is DownloadState.InProgress -> state.progress.coerceIn(0f, 1f)
        is DownloadState.Paused -> state.progress.coerceIn(0f, 1f)
        DownloadState.Completed -> 1f
        is DownloadState.Failed -> if (info.totalBytes != null && info.totalBytes > 0) {
            (info.bytesDownloaded.toFloat() / info.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        DownloadState.Idle -> 0f
    }
    val subtitleTag = when {
        info.subtitleFileUri != null && !info.subtitleLabel.isNullOrBlank() -> "${info.subtitleLabel} ready"
        info.subtitleFileUri != null -> "Subs ready"
        !info.subtitleLabel.isNullOrBlank() -> "${info.subtitleLabel} selected"
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.16f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = accent.copy(alpha = 0.14f)) {
                    Icon(downloadStateIcon(info.state), null, modifier = Modifier.padding(9.dp).size(18.dp), tint = accent)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(downloadStatusLine(info), style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onOpen != null) {
                    DownloadIconActionButton(Icons.Default.PlayArrow, onOpen)
                } else if (info.state != DownloadState.Completed && info.state != DownloadState.Idle) {
                    DownloadIconActionButton(downloadActionIcon(info.state), onAction)
                }
                DownloadIconActionButton(Icons.Default.Close, onDismiss)
            }
            if (info.state != DownloadState.Idle) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                    color = accent,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentPill(info.quality.ifBlank { "Auto" }, accent)
                subtitleTag?.let { AccentPill(it, SubAccent) }
                if (info.state == DownloadState.Completed && info.fileUri != null) {
                    AccentPill("Ready offline", DownloadAccent)
                }
            }
        }
    }
}

@Composable
private fun DownloadIconActionButton(icon: AppIcon, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Icon(icon, null, modifier = Modifier.padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnimeThumbnail(thumbnail: String, modifier: Modifier = Modifier) {
    if (thumbnail.isBlank()) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    AsyncImage(
        model = thumbnail,
        contentDescription = null,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun AnimeScoreBadge(score: Float, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(999.dp), color = Color.Black.copy(alpha = 0.58f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(12.dp), tint = ScoreGold)
            Text("${score.toInt()}%", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccentPill(text: String, accent: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = accent.copy(alpha = 0.14f), border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GenreChip(genre: String) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            genre,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun animeCardDetails(anime: AniCliAnime): String = buildList {
    anime.type?.takeIf { it.isNotBlank() }?.let(::add)
    anime.status?.takeIf { it.isNotBlank() }?.let(::add)
    if (anime.subEpisodes > 0) add("${anime.subEpisodes} episodes")
}.joinToString(" - ").ifBlank { "MovieBox" }

private fun animeCardAccentColor(score: Float?, fallback: Color): Color =
    if (score != null && score >= 80f) ScoreGold else fallback

private fun playbackHeaders(link: AniCliStreamLink): Map<String, String> = buildMap {
    putAll(link.requestHeaders.filterValues { it.isNotBlank() })
    link.referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
    link.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
}

private fun preferredSubtitleUris(link: AniCliStreamLink): List<Uri> =
    preferredSubtitleUris(link.subtitles)

private fun preferredSubtitleUris(subtitles: List<AniCliSubtitleTrack>): List<Uri> {
    val preferred = subtitles.onlyEnglishSubtitles().ifEmpty { subtitles }
    return preferred.mapNotNull { subtitle ->
        subtitle.url.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }
}

private fun formatSubtitleSummary(subtitles: List<AniCliSubtitleTrack>): String =
    subtitles.map { subtitle ->
        subtitle.label.ifBlank {
            subtitle.languageCode ?: "Subtitle"
        }
    }.distinct().take(3).joinToString(", ")

private fun formatLanguageSummary(languages: List<String>): String =
    languages.filter { it.isNotBlank() }.distinct().take(3).joinToString(", ")

private fun streamPlaybackTitle(
    anime: AniCliAnime,
    episode: AniCliEpisode,
    link: AniCliStreamLink?,
): String {
    val episodeLabel = if (episode.number.equals("movie", ignoreCase = true)) "Movie" else "Episode ${episode.number}"
    return listOfNotNull(anime.name, episodeLabel, link?.quality).joinToString(" - ")
}

private fun downloadPlaybackTitle(info: AnimeDownloadInfo): String =
    "${info.animeName} - ${info.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episode ${info.epNo}"}"

private fun downloadStateIcon(state: DownloadState): AppIcon =
    when (state) {
        DownloadState.Preparing,
        is DownloadState.InProgress -> Icons.Default.FileDownload
        is DownloadState.Paused -> Icons.Default.Pause
        DownloadState.Completed -> Icons.Default.Check
        is DownloadState.Failed -> Icons.Default.Refresh
        DownloadState.Idle -> Icons.Default.Download
    }

private fun downloadActionIcon(state: DownloadState): AppIcon =
    when (state) {
        DownloadState.Preparing,
        is DownloadState.InProgress -> Icons.Default.Pause
        is DownloadState.Paused,
        is DownloadState.Failed -> Icons.Default.PlayArrow
        else -> Icons.Default.Download
    }

private fun downloadStatusLine(info: AnimeDownloadInfo): String =
    when (val state = info.state) {
        DownloadState.Preparing -> "Resolving stream"
        is DownloadState.InProgress -> listOfNotNull(
            formatDownloadSize(info.bytesDownloaded, info.totalBytes),
            formatDownloadSpeed(info.transferRateBytesPerSecond),
            formatPercent(state.progress),
        ).joinToString(" - ")
        is DownloadState.Paused -> "Paused - ${formatDownloadSize(info.bytesDownloaded, info.totalBytes)}"
        DownloadState.Completed -> "Downloaded ${formatTotalBytes(info.totalBytes)}"
        is DownloadState.Failed -> state.error.ifBlank { "Download failed" }
        DownloadState.Idle -> "Queued"
    }

private fun formatDownloadSize(downloadedBytes: Long, totalBytes: Long?): String =
    if (totalBytes != null && totalBytes > 0L) {
        "${formatBytesCompact(downloadedBytes)} / ${formatBytesCompact(totalBytes)}"
    } else {
        formatBytesCompact(downloadedBytes)
    }

private fun formatDownloadSpeed(bytesPerSecond: Long?): String? =
    bytesPerSecond?.takeIf { it > 0L }?.let { "${formatBytesCompact(it)}/s" }

private fun formatPercent(value: Float): String =
    "${(value.coerceIn(0f, 1f) * 100f).toInt()}%"

private fun formatTotalBytes(totalBytes: Long?): String =
    totalBytes?.takeIf { it > 0L }?.let(::formatBytesCompact) ?: ""

private fun formatBytesCompact(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val value = bytes.toDouble()
    return when {
        value >= gb -> String.format(Locale.US, "%.2f GB", value / gb)
        value >= mb -> String.format(Locale.US, "%.1f MB", value / mb)
        value >= kb -> String.format(Locale.US, "%.1f KB", value / kb)
        else -> "$bytes B"
    }
}
