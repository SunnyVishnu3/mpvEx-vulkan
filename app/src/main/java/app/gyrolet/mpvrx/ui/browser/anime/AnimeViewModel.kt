package app.gyrolet.mpvrx.ui.browser.anime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.domain.anicli.AniCliAnime
import app.gyrolet.mpvrx.domain.anicli.AniCliEpisode
import app.gyrolet.mpvrx.domain.anicli.AniCliStreamLink
import app.gyrolet.mpvrx.domain.anicli.AniCliSubtitleTrack
import app.gyrolet.mpvrx.domain.anicli.AniCliUiState
import app.gyrolet.mpvrx.domain.anicli.AnimeDownloadQualityMode
import app.gyrolet.mpvrx.domain.anicli.AnimeDownloadRequest
import app.gyrolet.mpvrx.domain.anicli.AnimeHistoryEntry
import app.gyrolet.mpvrx.domain.anicli.AnimeListContext
import app.gyrolet.mpvrx.domain.anicli.AnimeSource
import app.gyrolet.mpvrx.domain.anicli.DownloadState
import app.gyrolet.mpvrx.domain.anicli.provider.AnimeParams
import app.gyrolet.mpvrx.domain.anicli.provider.EpisodeStreamsParams
import app.gyrolet.mpvrx.domain.anicli.provider.SearchParams
import app.gyrolet.mpvrx.domain.anicli.provider.SearchResult
import app.gyrolet.mpvrx.domain.anicli.provider.Server
import app.gyrolet.mpvrx.domain.anicli.provider.SourceRegistry
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import app.gyrolet.mpvrx.preferences.EpisodeViewMode
import app.gyrolet.mpvrx.preferences.FoldersPreferences
import app.gyrolet.mpvrx.preferences.TrendingViewMode
import app.gyrolet.mpvrx.repository.AnimeDownloadRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val SEP = "\u001F"
private const val MAX_HISTORY = 20
private const val SEARCH_PAGE_SIZE = 20
private const val EXPLORE_PAGE_SIZE = 24

class AnimeViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val sourceRegistry: SourceRegistry by inject()
    private val browserPreferences: BrowserPreferences by inject()
    private val foldersPreferences: FoldersPreferences by inject()
    private val downloadRepository: AnimeDownloadRepository by inject()
    private val gson = Gson()

    private val provider by lazy { sourceRegistry.get(AnimeSource.MOVIEBOX) }

    private val _uiState = MutableStateFlow(
        AniCliUiState(
            selectedSource = AnimeSource.MOVIEBOX,
            mode = "sub",
        )
    )
    val uiState: StateFlow<AniCliUiState> = _uiState.asStateFlow()

    val downloads = downloadRepository.downloads

    val episodeViewMode: StateFlow<EpisodeViewMode> =
        browserPreferences.animeEpisodeViewMode.stateIn(viewModelScope)

    val trendingViewMode: StateFlow<TrendingViewMode> =
        browserPreferences.animeTrendingViewMode.stateIn(viewModelScope)

    val animeFolderUri: StateFlow<String> =
        foldersPreferences.animeFolder.changes()
            .stateIn(viewModelScope, SharingStarted.Eagerly, foldersPreferences.animeFolder.get())

    private val _episodeSortAscending = MutableStateFlow(false)
    val episodeSortAscending: StateFlow<Boolean> = _episodeSortAscending.asStateFlow()

    fun toggleEpisodeSort() {
        _episodeSortAscending.update { !it }
    }

    private val _bookmarks = MutableStateFlow<List<AniCliAnime>>(emptyList())
    val bookmarks: StateFlow<List<AniCliAnime>> = _bookmarks.asStateFlow()

    private var exploreRequestId = 0L
    private var searchRequestId = 0L
    private var episodesRequestId = 0L
    private var streamsRequestId = 0L

    init {
        migrateLegacyAnimeFolder()
        restoreBookmarks()
        restoreHistory()
        loadExplore()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                hasSearched = if (query.isBlank()) false else state.hasSearched,
                searchResults = if (query.isBlank()) emptyList() else state.searchResults,
                searchPage = if (query.isBlank()) 1 else state.searchPage,
                searchHasMore = if (query.isBlank()) true else state.searchHasMore,
            )
        }
    }

    fun search() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isBlank()) return
        val requestId = ++searchRequestId
        _uiState.update {
            it.copy(
                isSearching = true,
                isLoadingMoreSearch = false,
                hasSearched = true,
                searchResults = emptyList(),
                searchPage = 1,
                searchHasMore = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                provider.search(
                    SearchParams(
                        query = query,
                        currentPage = 1,
                        pageLimit = SEARCH_PAGE_SIZE,
                    )
                )?.results.orEmpty().map { it.toAniCliAnime() }
            }.onSuccess { results ->
                if (!isActiveSearch(query, requestId)) return@onSuccess
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = results,
                        searchPage = 1,
                        searchHasMore = results.size >= SEARCH_PAGE_SIZE,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                if (!isActiveSearch(query, requestId)) return@onFailure
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = error.message ?: "Search failed",
                    )
                }
            }
        }
    }

    fun loadMoreSearchResults() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        if (query.isBlank() || state.isLoadingMoreSearch || !state.searchHasMore) return
        val nextPage = state.searchPage + 1
        val requestId = ++searchRequestId
        _uiState.update { it.copy(isLoadingMoreSearch = true) }
        viewModelScope.launch {
            val results = runCatching {
                provider.search(
                    SearchParams(
                        query = query,
                        currentPage = nextPage,
                        pageLimit = SEARCH_PAGE_SIZE,
                    )
                )?.results.orEmpty().map { it.toAniCliAnime() }
            }.getOrDefault(emptyList())

            if (!isActiveSearch(query, requestId)) return@launch
            _uiState.update {
                it.copy(
                    isLoadingMoreSearch = false,
                    searchResults = it.searchResults + results,
                    searchPage = nextPage,
                    searchHasMore = results.size >= SEARCH_PAGE_SIZE,
                )
            }
        }
    }

    fun clearSearch() {
        searchRequestId++
        _uiState.update {
            it.copy(
                searchQuery = "",
                hasSearched = false,
                isSearching = false,
                isLoadingMoreSearch = false,
                searchResults = emptyList(),
                searchPage = 1,
                searchHasMore = true,
            )
        }
    }

    fun loadExplore() {
        val requestId = ++exploreRequestId
        _uiState.update {
            it.copy(
                isLoadingTrending = true,
                animeProviderPage = 1,
                animeProviderHasMore = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                provider.latest(
                    SearchParams(
                        query = "",
                        currentPage = 1,
                        pageLimit = EXPLORE_PAGE_SIZE,
                    )
                )?.results
                    ?: provider.search(
                        SearchParams(
                            query = "",
                            currentPage = 1,
                            pageLimit = EXPLORE_PAGE_SIZE,
                        )
                    )?.results.orEmpty()
            }.map { results ->
                results.map { it.toAniCliAnime() }
            }.onSuccess { results ->
                if (exploreRequestId != requestId) return@onSuccess
                _uiState.update {
                    it.copy(
                        isLoadingTrending = false,
                        trendingAnime = results,
                        animeProviderPage = 1,
                        animeProviderHasMore = results.size >= EXPLORE_PAGE_SIZE,
                    )
                }
            }.onFailure { error ->
                if (exploreRequestId != requestId) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoadingTrending = false,
                        errorMessage = error.message ?: "Could not load MovieBox",
                    )
                }
            }
        }
    }

    fun loadMoreAnime() {
        val state = _uiState.value
        if (state.isLoadingTrending || !state.animeProviderHasMore) return
        val nextPage = state.animeProviderPage + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTrending = true) }
            val results = runCatching {
                provider.latest(
                    SearchParams(
                        query = "",
                        currentPage = nextPage,
                        pageLimit = EXPLORE_PAGE_SIZE,
                    )
                )?.results
                    ?: provider.search(
                        SearchParams(
                            query = "",
                            currentPage = nextPage,
                            pageLimit = EXPLORE_PAGE_SIZE,
                        )
                    )?.results.orEmpty()
            }.getOrDefault(emptyList()).map { it.toAniCliAnime() }

            _uiState.update {
                it.copy(
                    isLoadingTrending = false,
                    trendingAnime = it.trendingAnime + results,
                    animeProviderPage = nextPage,
                    animeProviderHasMore = results.size >= EXPLORE_PAGE_SIZE,
                )
            }
        }
    }

    fun selectAnime(
        anime: AniCliAnime,
        list: List<AniCliAnime>,
        context: AnimeListContext,
    ) {
        val current = _uiState.value.selectedAnime
        if (current?.id == anime.id && _uiState.value.selectedListContext == context) {
            _uiState.update {
                it.copy(
                    selectedAnime = null,
                    selectedAnimeIndex = null,
                    selectedListContext = null,
                    episodes = emptyList(),
                    selectedEpisode = null,
                    selectedEpisodeNumber = null,
                    streamLinks = emptyList(),
                    showStreamSheet = false,
                    isLoadingEpisodes = false,
                    isLoadingStreams = false,
                    errorMessage = null,
                )
            }
            return
        }
        val selectedIndex = list.indexOfFirst { it.id == anime.id }.takeIf { it >= 0 }
        episodesRequestId++
        streamsRequestId++
        _uiState.update {
            it.copy(
                selectedAnime = anime,
                selectedAnimeIndex = selectedIndex,
                selectedListContext = context,
                episodes = emptyList(),
                selectedEpisode = null,
                selectedEpisodeNumber = null,
                streamLinks = emptyList(),
                showStreamSheet = false,
                isLoadingEpisodes = true,
                isLoadingStreams = false,
                errorMessage = null,
            )
        }
        loadSelectedAnimeEpisodes(anime)
    }

    fun selectAdjacentAnime(direction: AnimeNavigationDirection) {
        val state = _uiState.value
        val list = selectedContextList(state)
        val currentIndex = state.selectedAnimeIndex ?: list.indexOfFirst { it.id == state.selectedAnime?.id }
        if (currentIndex < 0) return
        val nextIndex = when (direction) {
            AnimeNavigationDirection.PREVIOUS -> currentIndex - 1
            AnimeNavigationDirection.NEXT -> currentIndex + 1
        }
        val next = list.getOrNull(nextIndex) ?: return
        selectAnime(next, list, state.selectedListContext ?: AnimeListContext.TRENDING)
    }

    private fun loadSelectedAnimeEpisodes(anime: AniCliAnime) {
        val requestId = ++episodesRequestId
        viewModelScope.launch {
            runCatching {
                provider.get(AnimeParams(id = anime.id, query = anime.name))
            }.onSuccess { detail ->
                if (!isActiveEpisodeRequest(anime.id, requestId)) return@onSuccess
                val episodes = detail?.episodesInfo
                    ?.map { epInfo ->
                        AniCliEpisode(
                            id = epInfo.id,
                            number = epInfo.episode,
                            title = epInfo.title,
                            poster = epInfo.poster,
                            duration = epInfo.duration,
                        )
                    }.orEmpty()
                _uiState.update { state ->
                    val updatedAnime = state.selectedAnime?.copy(
                        name = detail?.title ?: anime.name,
                        subEpisodes = episodes.size.takeIf { it > 0 } ?: anime.subEpisodes,
                        description = detail?.description ?: anime.description,
                        type = detail?.type ?: anime.type,
                        status = detail?.year ?: anime.status,
                        thumbnail = anime.thumbnail.ifBlank { detail?.poster.orEmpty() },
                    )
                    state.copy(
                        selectedAnime = updatedAnime,
                        episodes = episodes,
                        isLoadingEpisodes = false,
                    )
                }
            }.onFailure { error ->
                if (!isActiveEpisodeRequest(anime.id, requestId)) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoadingEpisodes = false,
                        errorMessage = error.message ?: "Could not load episodes",
                    )
                }
            }
        }
    }

    fun selectEpisode(episode: AniCliEpisode) {
        val anime = _uiState.value.selectedAnime ?: return
        val requestId = ++streamsRequestId
        _uiState.update {
            it.copy(
                selectedEpisode = episode.id,
                selectedEpisodeNumber = episode.number,
                isLoadingStreams = true,
                streamLinks = emptyList(),
                showStreamSheet = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                provider.episodeStreams(
                    EpisodeStreamsParams(
                        query = anime.name,
                        animeId = anime.id,
                        episode = episode.number,
                        episodeId = episode.id,
                        translationType = "sub",
                    )
                ).orEmpty().toStreamLinks()
            }.onSuccess { links ->
                if (!isActiveStreamRequest(anime.id, episode.id, requestId)) return@onSuccess
                _uiState.update {
                    it.copy(
                        isLoadingStreams = false,
                        streamLinks = links,
                        errorMessage = if (links.isEmpty()) "No playable streams found" else null,
                    )
                }
            }.onFailure { error ->
                if (!isActiveStreamRequest(anime.id, episode.id, requestId)) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoadingStreams = false,
                        errorMessage = error.message ?: "Could not load streams",
                    )
                }
            }
        }
    }

    fun dismissStreamSheet() {
        _uiState.update {
            it.copy(
                showStreamSheet = false,
                streamLinks = emptyList(),
                selectedEpisode = null,
                selectedEpisodeNumber = null,
                isLoadingStreams = false,
            )
        }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun toggleEpisodeViewMode() {
        val next = when (episodeViewMode.value) {
            EpisodeViewMode.Grid -> EpisodeViewMode.List
            EpisodeViewMode.List -> EpisodeViewMode.Grid
        }
        browserPreferences.animeEpisodeViewMode.set(next)
    }

    fun toggleTrendingViewMode() {
        val next = when (trendingViewMode.value) {
            TrendingViewMode.Grid -> TrendingViewMode.List
            TrendingViewMode.List -> TrendingViewMode.Grid
        }
        browserPreferences.animeTrendingViewMode.set(next)
    }

    fun toggleBookmark(anime: AniCliAnime) {
        val current = _bookmarks.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == anime.id }
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
        } else {
            current.add(0, anime)
        }
        _bookmarks.value = current
        foldersPreferences.bookmarkedAnime.set(current.map { it.serializeAnime() }.toSet())
        _uiState.update { it.copy(bookmarkedIds = current.map { anime -> anime.id }.toSet()) }
    }

    fun isBookmarked(animeId: String): Boolean =
        _bookmarks.value.any { it.id == animeId }

    fun addToHistory(anime: AniCliAnime, episodeNumber: String) {
        val updated = listOf(
            AnimeHistoryEntry(
                anime = anime,
                lastEpisode = episodeNumber,
                watchedAt = System.currentTimeMillis(),
            )
        ) + _uiState.value.animeHistory.filterNot { it.anime.id == anime.id }
        val trimmed = updated.take(MAX_HISTORY)
        _uiState.update { it.copy(animeHistory = trimmed) }
        foldersPreferences.animeHistory.set(trimmed.map { it.serializeHistory() }.toSet())
    }

    fun removeHistory(animeId: String) {
        val updated = _uiState.value.animeHistory.filterNot { it.anime.id == animeId }
        _uiState.update { it.copy(animeHistory = updated) }
        foldersPreferences.animeHistory.set(updated.map { it.serializeHistory() }.toSet())
    }

    fun clearHistory() {
        _uiState.update { it.copy(animeHistory = emptyList()) }
        foldersPreferences.animeHistory.set(emptySet())
    }

    fun hasAnimeFolder(): Boolean = foldersPreferences.animeFolder.get().isNotBlank()

    fun ensureAnimeFolderConfigured(): Boolean {
        if (hasAnimeFolder()) return true
        _uiState.update {
            it.copy(infoMessage = "Set Anime download folder in Advanced settings first")
        }
        return false
    }

    fun canDownloadSelectedSource(): Boolean = true

    fun getDownloadState(animeName: String, epNo: String): DownloadState =
        downloads.value[downloadKey(animeName, epNo)]?.state ?: DownloadState.Idle

    fun onEpisodeDownloadAction(anime: AniCliAnime, episode: AniCliEpisode) {
        handleDownloadAction(anime.name, episode.number) {
            queueDownload(
                AnimeDownloadRequest(
                    source = AnimeSource.MOVIEBOX,
                    animeId = anime.id,
                    animeName = anime.name,
                    episodeId = episode.id,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                    translationType = "sub",
                    qualityLabel = "Auto",
                    qualityMode = AnimeDownloadQualityMode.HIGHEST,
                )
            )
        }
    }

    fun onStreamDownloadAction(anime: AniCliAnime, episode: AniCliEpisode, link: AniCliStreamLink) {
        handleDownloadAction(anime.name, episode.number) {
            queueDownload(
                AnimeDownloadRequest(
                    source = AnimeSource.MOVIEBOX,
                    animeId = anime.id,
                    animeName = anime.name,
                    episodeId = episode.id,
                    episodeNumber = episode.number,
                    episodeTitle = episode.title,
                    subtitleTracks = link.subtitles,
                    translationType = link.audioLanguages.firstOrNull() ?: link.title ?: "sub",
                    qualityLabel = link.quality,
                    qualityMode = AnimeDownloadQualityMode.EXACT,
                    directUrl = link.url,
                    referer = link.referer,
                    userAgent = link.userAgent,
                    requestHeaders = link.requestHeaders,
                )
            )
        }
    }

    fun downloadAllEpisodes(anime: AniCliAnime) {
        if (!ensureAnimeFolderConfigured()) return
        val episodes = _uiState.value.episodes
        if (episodes.isEmpty()) return
        episodes.forEach { episode ->
            val state = getDownloadState(anime.name, episode.number)
            if (state == DownloadState.Idle || state is DownloadState.Failed) {
                queueDownload(
                    AnimeDownloadRequest(
                        source = AnimeSource.MOVIEBOX,
                        animeId = anime.id,
                        animeName = anime.name,
                        episodeId = episode.id,
                        episodeNumber = episode.number,
                        episodeTitle = episode.title,
                        translationType = "sub",
                        qualityLabel = "Auto",
                        qualityMode = AnimeDownloadQualityMode.HIGHEST,
                    )
                )
            }
        }
        _uiState.update { it.copy(infoMessage = "Queued ${episodes.size} episodes") }
    }

    fun pauseDownload(animeName: String, epNo: String) {
        downloadRepository.pauseDownload(animeName, epNo)
    }

    fun resumeDownload(animeName: String, epNo: String) {
        downloadRepository.resumeDownload(animeName, epNo)
    }

    fun dismissDownload(animeName: String, epNo: String) {
        downloadRepository.removeDownload(animeName, epNo)
    }

    fun pauseAllDownloads() {
        downloadRepository.pauseAllDownloads()
    }

    fun resumeAllDownloads() {
        downloadRepository.resumeAllDownloads()
    }

    fun hasActiveDownloads(): Boolean = downloadRepository.hasActiveDownloads()

    fun hasPausedDownloads(): Boolean = downloadRepository.hasPausedDownloads()

    private fun handleDownloadAction(
        animeName: String,
        epNo: String,
        queue: () -> Unit,
    ) {
        when (getDownloadState(animeName, epNo)) {
            DownloadState.Preparing,
            is DownloadState.InProgress -> pauseDownload(animeName, epNo)

            is DownloadState.Paused,
            is DownloadState.Failed -> resumeDownload(animeName, epNo)

            DownloadState.Completed -> _uiState.update {
                it.copy(infoMessage = "Episode $epNo is already downloaded")
            }

            DownloadState.Idle -> {
                if (!ensureAnimeFolderConfigured()) return
                queue()
            }
        }
    }

    private fun queueDownload(request: AnimeDownloadRequest) {
        runCatching {
            downloadRepository.queueDownload(request)
        }.onFailure { error ->
            _uiState.update {
                it.copy(infoMessage = error.message ?: "Could not queue download")
            }
        }
    }

    private fun List<Server>.toStreamLinks(): List<AniCliStreamLink> =
        flatMap { server ->
            server.links.map { stream ->
                AniCliStreamLink(
                    quality = stream.quality,
                    url = stream.link,
                    isM3u8 = stream.isHls == true,
                    isEmbedUrl = stream.format.equals("embed", ignoreCase = true),
                    referer = stream.referer ?: server.headers["Referer"] ?: provider.defaultReferer,
                    userAgent = provider.defaultUserAgent,
                    requestHeaders = buildMap {
                        putAll(server.headers.filterValues { it.isNotBlank() })
                        putAll(stream.requestHeaders.filterValues { it.isNotBlank() })
                    },
                    subtitles = server.subtitles.map { subtitle ->
                        AniCliSubtitleTrack(
                            url = subtitle.url,
                            label = subtitle.language ?: "Subtitle",
                            languageCode = subtitle.language,
                        )
                    },
                    audioLanguages = stream.audioLanguage?.let(::listOf)
                        ?: server.audio,
                    title = server.name,
                )
            }
        }.distinctBy { it.url }

    private fun restoreBookmarks() {
        val serialized = foldersPreferences.bookmarkedAnime.get()
        val restored = serialized.mapNotNull { it.deserializeAnime() }
        if (restored.isNotEmpty() || serialized.isNotEmpty()) {
            _bookmarks.value = restored
            _uiState.update { it.copy(bookmarkedIds = restored.map { anime -> anime.id }.toSet()) }
            return
        }

        val legacyJson = browserPreferences.animeBookmarksJson.get()
        val legacy = runCatching {
            val type = object : TypeToken<List<AniCliAnime>>() {}.type
            gson.fromJson<List<AniCliAnime>>(legacyJson, type)
        }.getOrNull().orEmpty()
        if (legacy.isNotEmpty()) {
            _bookmarks.value = legacy
            foldersPreferences.bookmarkedAnime.set(legacy.map { it.serializeAnime() }.toSet())
            _uiState.update { it.copy(bookmarkedIds = legacy.map { anime -> anime.id }.toSet()) }
        }
    }

    private fun restoreHistory() {
        val history = foldersPreferences.animeHistory.get()
            .mapNotNull { it.deserializeHistory() }
            .sortedByDescending { it.watchedAt }
        _uiState.update { it.copy(animeHistory = history) }
    }

    private fun migrateLegacyAnimeFolder() {
        if (foldersPreferences.animeFolder.get().isNotBlank()) return
        val legacy = browserPreferences.animeFolderUri.get()
        if (legacy.isNotBlank()) {
            foldersPreferences.animeFolder.set(legacy)
        }
    }

    private fun selectedContextList(state: AniCliUiState): List<AniCliAnime> =
        when (state.selectedListContext) {
            AnimeListContext.SEARCH -> state.searchResults
            AnimeListContext.BOOKMARKS -> _bookmarks.value
            AnimeListContext.HISTORY -> state.animeHistory.map { it.anime }
            AnimeListContext.TRENDING,
            null -> state.trendingAnime
        }

    private fun isActiveSearch(query: String, requestId: Long): Boolean =
        _uiState.value.searchQuery.trim() == query && searchRequestId == requestId

    private fun isActiveEpisodeRequest(animeId: String, requestId: Long): Boolean =
        _uiState.value.selectedAnime?.id == animeId && episodesRequestId == requestId

    private fun isActiveStreamRequest(animeId: String, episodeId: String, requestId: Long): Boolean =
        _uiState.value.selectedAnime?.id == animeId &&
            _uiState.value.selectedEpisode == episodeId &&
            streamsRequestId == requestId

    internal fun SearchResult.toAniCliAnime() = AniCliAnime(
        id = id,
        name = title,
        subEpisodes = episodes.sub.size,
        dubEpisodes = episodes.dub.size,
        thumbnail = poster ?: "",
        bannerImage = bannerImage,
        description = description,
        score = score,
        genres = genres,
        type = mediaType,
        status = status ?: year,
        season = season ?: year,
    )

    companion object {
        private fun downloadKey(animeName: String, epNo: String): String = "${animeName}_ep$epNo"
    }
}

enum class AnimeNavigationDirection {
    PREVIOUS,
    NEXT,
}

internal fun buildAnimeNavigationDirections(
    hasPrevious: Boolean,
    hasNext: Boolean,
): List<AnimeNavigationDirection> = buildList {
    if (hasPrevious) add(AnimeNavigationDirection.PREVIOUS)
    if (hasNext) add(AnimeNavigationDirection.NEXT)
}

internal fun shouldShowBulkEpisodeDownload(
    hasAnimeFolder: Boolean,
    canDownloadSelectedSource: Boolean,
    hasEpisodes: Boolean,
): Boolean = hasAnimeFolder && canDownloadSelectedSource && hasEpisodes

private fun String.sanitizeUrl(): String = when {
    startsWith("//") -> "https:$this"
    else -> this
}

private fun AniCliAnime.serializeAnime(): String =
    listOf(
        id,
        name,
        subEpisodes.toString(),
        dubEpisodes.toString(),
        thumbnail.sanitizeUrl(),
        bannerImage?.sanitizeUrl().orEmpty(),
        description.orEmpty(),
        type.orEmpty(),
        status.orEmpty(),
    ).joinToString(SEP)

private fun String.deserializeAnime(): AniCliAnime? {
    val parts = split(SEP)
    if (parts.size < 4) return null
    return AniCliAnime(
        id = parts[0],
        name = parts[1],
        subEpisodes = parts[2].toIntOrNull() ?: 0,
        dubEpisodes = parts[3].toIntOrNull() ?: 0,
        thumbnail = parts.getOrNull(4).orEmpty(),
        bannerImage = parts.getOrNull(5)?.takeIf { it.isNotBlank() },
        description = parts.getOrNull(6)?.takeIf { it.isNotBlank() },
        type = parts.getOrNull(7)?.takeIf { it.isNotBlank() },
        status = parts.getOrNull(8)?.takeIf { it.isNotBlank() },
    )
}

private fun AnimeHistoryEntry.serializeHistory(): String =
    listOf(
        anime.id,
        anime.name,
        anime.subEpisodes.toString(),
        anime.dubEpisodes.toString(),
        anime.thumbnail.sanitizeUrl(),
        lastEpisode,
        watchedAt.toString(),
        anime.bannerImage?.sanitizeUrl().orEmpty(),
    ).joinToString(SEP)

private fun String.deserializeHistory(): AnimeHistoryEntry? {
    val parts = split(SEP)
    if (parts.size < 7) return null
    return AnimeHistoryEntry(
        anime = AniCliAnime(
            id = parts[0],
            name = parts[1],
            subEpisodes = parts[2].toIntOrNull() ?: 0,
            dubEpisodes = parts[3].toIntOrNull() ?: 0,
            thumbnail = parts[4],
            bannerImage = parts.getOrNull(7)?.takeIf { it.isNotBlank() },
        ),
        lastEpisode = parts[5],
        watchedAt = parts[6].toLongOrNull() ?: 0L,
    )
}
