package app.gyrolet.mpvrx.ui.browser.anime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.gyrolet.mpvrx.domain.anicli.AniCliAnime
import app.gyrolet.mpvrx.domain.anicli.AniCliEpisode
import app.gyrolet.mpvrx.domain.anicli.AniCliStreamLink
import app.gyrolet.mpvrx.domain.anicli.AniCliSubtitleTrack
import app.gyrolet.mpvrx.domain.anicli.AniCliUiState
import app.gyrolet.mpvrx.domain.anicli.AnimeHistoryEntry
import app.gyrolet.mpvrx.domain.anicli.AnimeSource
import app.gyrolet.mpvrx.domain.anicli.provider.AnimeParams
import app.gyrolet.mpvrx.domain.anicli.provider.BaseAnimeProvider
import app.gyrolet.mpvrx.domain.anicli.provider.EpisodeStreamsParams
import app.gyrolet.mpvrx.domain.anicli.provider.SearchParams
import app.gyrolet.mpvrx.domain.anicli.provider.SearchResult
import app.gyrolet.mpvrx.domain.anicli.provider.SourceRegistry
import app.gyrolet.mpvrx.preferences.BrowserPreferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class AnimeViewModel(application: Application) : AndroidViewModel(application), KoinComponent {

    private val sourceRegistry: SourceRegistry by inject()
    private val browserPreferences: BrowserPreferences by inject()
    private val okHttpClient: OkHttpClient by inject()
    private val gson = Gson()

    private val provider: BaseAnimeProvider = sourceRegistry.get(AnimeSource.MOVIEBOX)

    private val _uiState = MutableStateFlow(AniCliUiState(selectedSource = AnimeSource.MOVIEBOX))
    val uiState: StateFlow<AniCliUiState> = _uiState.asStateFlow()

    private val _trendingCache = mutableListOf<AniCliAnime>()
    private var trendingLoaded = false

    init {
        loadHistory()
        loadTrending()
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _uiState.update { it.copy(searchQuery = query, isSearching = true, searchResults = emptyList(), searchPage = 1, searchHasMore = true, hasSearched = true) }
        performSearch(query, 1)
    }

    fun loadMoreSearch() {
        val state = _uiState.value
        if (!state.searchHasMore || state.isLoadingMoreSearch) return
        _uiState.update { it.copy(isLoadingMoreSearch = true) }
        performSearch(state.searchQuery, state.searchPage + 1, append = true)
    }

    private fun performSearch(query: String, page: Int, append: Boolean = false) {
        viewModelScope.launch {
            val results = runCatching {
                provider.search(SearchParams(query = query, currentPage = page, pageLimit = 20))
            }.getOrNull()
            val animeList = results?.results?.map { it.toAniCliAnime() }.orEmpty()
            _uiState.update { state ->
                state.copy(
                    isSearching = false,
                    isLoadingMoreSearch = false,
                    searchResults = if (append) state.searchResults + animeList else animeList,
                    searchPage = page,
                    searchHasMore = animeList.size >= 20,
                )
            }
        }
    }

    fun loadTrending() {
        if (trendingLoaded) return
        _uiState.update { it.copy(isLoadingTrending = true) }
        viewModelScope.launch {
            val results = runCatching {
                provider.search(SearchParams(query = "", currentPage = 1, pageLimit = 30))
            }.getOrNull()
            val animeList = results?.results?.map { it.toAniCliAnime() }.orEmpty()
            trendingLoaded = true
            _trendingCache.addAll(animeList)
            _uiState.update { it.copy(trendingAnime = animeList, isLoadingTrending = false) }
        }
    }

    fun loadAnimeEpisodes(animeId: String, animeName: String) {
        _uiState.update {
            it.copy(
                selectedAnime = AniCliAnime(id = animeId, name = animeName, subEpisodes = 0, dubEpisodes = 0),
                episodes = emptyList(), isLoadingEpisodes = true, selectedEpisode = null, streamLinks = emptyList(), showStreamSheet = false,
            )
        }
        viewModelScope.launch {
            val detail = runCatching {
                provider.get(AnimeParams(id = animeId, query = animeName))
            }.getOrNull()
            val episodes = detail?.episodesInfo?.map { epInfo ->
                AniCliEpisode(id = epInfo.id, number = epInfo.episode, title = epInfo.title, poster = epInfo.poster, duration = epInfo.duration)
            }.orEmpty()
            _uiState.update { state ->
                val updatedAnime = state.selectedAnime?.copy(
                    subEpisodes = episodes.size,
                    description = detail?.description ?: animeName,
                    type = detail?.type,
                )
                state.copy(selectedAnime = updatedAnime, episodes = episodes, isLoadingEpisodes = false)
            }
        }
    }

    fun selectEpisode(episodeId: String, episodeNumber: String) {
        val anime = _uiState.value.selectedAnime ?: return
        _uiState.update { it.copy(selectedEpisode = episodeId, selectedEpisodeNumber = episodeNumber, isLoadingStreams = true, streamLinks = emptyList(), showStreamSheet = true) }
        viewModelScope.launch {
            val servers = runCatching {
                provider.episodeStreams(EpisodeStreamsParams(query = anime.name, animeId = anime.id, episode = episodeNumber, episodeId = episodeId))
            }.getOrNull()
            val links = servers.orEmpty().flatMap { server ->
                server.links.map { stream ->
                    AniCliStreamLink(
                        quality = stream.quality, url = stream.link, isM3u8 = stream.isHls == true,
                        referer = stream.referer, userAgent = provider.defaultUserAgent,
                        requestHeaders = stream.requestHeaders,
                        subtitles = server.subtitles.map { AniCliSubtitleTrack(url = it.url, label = it.language ?: "Sub") },
                        audioLanguages = server.audio, title = server.name,
                    )
                }
            }
            _uiState.update { it.copy(streamLinks = links, isLoadingStreams = false) }
        }
    }

    fun dismissStreamSheet() {
        _uiState.update { it.copy(showStreamSheet = false, streamLinks = emptyList(), selectedEpisode = null) }
    }

    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun clearInfo() { _uiState.update { it.copy(infoMessage = null) } }

    fun addToHistory(anime: AniCliAnime, episodeNumber: String) {
        viewModelScope.launch {
            val history = _uiState.value.animeHistory.toMutableList()
            history.removeAll { it.anime.id == anime.id }
            history.add(0, AnimeHistoryEntry(anime = anime, lastEpisode = episodeNumber, watchedAt = System.currentTimeMillis()))
            _uiState.update { it.copy(animeHistory = history) }
            saveHistory()
        }
    }

    fun downloadStream(link: AniCliStreamLink, animeName: String, episodeNumber: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val dir = File(app.cacheDir, "anime/${animeName.replace(" ", "_")}")
            dir.mkdirs()
            val ext = link.url.substringAfterLast(".").substringBefore("?").takeIf { it.length in 2..4 } ?: "mp4"
            val file = File(dir, "Episode_$episodeNumber.$ext")

            _uiState.update { it.copy(infoMessage = "Downloading E$episodeNumber...") }

            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(link.url)
                        .header("Referer", link.referer ?: "https://moviebox.ph/")
                        .header("User-Agent", link.userAgent ?: "Mozilla/5.0")
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
                        response.body?.byteStream()?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }.onSuccess {
                    _uiState.update { it.copy(infoMessage = "Downloaded: ${file.absolutePath}") }
                }.onFailure { e ->
                    _uiState.update { it.copy(infoMessage = "Download failed: ${e.message}") }
                }
            }
        }
    }

    private fun loadHistory() {
        val json = browserPreferences.animeEnabledSources.get()
    }

    private fun saveHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val json = gson.toJson(_uiState.value.animeHistory)
                browserPreferences.animeEnabledSources.set(setOf(json))
            }
        }
    }

    private fun SearchResult.toAniCliAnime() = AniCliAnime(
        id = id, name = title,
        subEpisodes = episodes.sub.size, dubEpisodes = episodes.dub.size,
        thumbnail = poster ?: "", bannerImage = bannerImage,
        description = description, score = score,
        genres = genres, type = mediaType, status = status,
    )
}
