package app.gyrolet.mpvrx.domain.anicli

import kotlinx.serialization.Serializable

@Serializable
enum class AnimeSource(val displayName: String, val supportsMode: Boolean, val isAdult: Boolean = false) {
    MOVIEBOX("MovieBox", false),
}

val AnimeSource.isStreamingEmbedSource: Boolean get() = false

enum class AnimeTab { LATEST, SEARCH, BOOKMARKS, DOWNLOADS }

enum class AnimeListContext {
    TRENDING,
    SEARCH,
    BOOKMARKS,
    HISTORY,
}

data class AniCliAnime(
    val id: String,
    val name: String,
    val subEpisodes: Int,
    val dubEpisodes: Int,
    val thumbnail: String = "",
    val bannerImage: String? = null,
    val description: String? = null,
    val score: Float? = null,
    val genres: List<String> = emptyList(),
    val type: String? = null,
    val status: String? = null,
    val format: String? = null,
    val season: String? = null,
    val studios: List<String> = emptyList(),
    val tags: List<AnilistTag> = emptyList(),
    val isTagsLoaded: Boolean = false,
)

data class AnilistTag(
    val name: String,
    val rank: Int = 0,
    val isAdult: Boolean = false,
)

data class AnimeHistoryEntry(
    val anime: AniCliAnime,
    val lastEpisode: String,
    val watchedAt: Long,
)

@Serializable
data class AniCliEpisode(
    val id: String,
    val number: String,
    val title: String? = null,
    val isFiller: Boolean = false,
    val thumbnail: String? = null,
    val isPreview: Boolean = false,
    val duration: String? = null,
    val poster: String? = null,
)

data class AniCliSubtitleTrack(
    val url: String,
    val label: String,
    val languageCode: String? = null,
)

data class AniCliStreamLink(
    val quality: String,
    val url: String,
    val isM3u8: Boolean = false,
    val isEmbedUrl: Boolean = false,
    val referer: String? = null,
    val userAgent: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val subtitles: List<AniCliSubtitleTrack> = emptyList(),
    val audioLanguages: List<String> = emptyList(),
    val title: String? = null,
)

@Serializable
enum class AnimeDownloadQualityMode {
    HIGHEST,
    LOWEST,
    EXACT,
}

data class AnimeDownloadRequest(
    val source: AnimeSource,
    val animeId: String,
    val animeName: String,
    val episodeId: String? = null,
    val episodeNumber: String,
    val episodeTitle: String? = null,
    val subtitleTracks: List<AniCliSubtitleTrack> = emptyList(),
    val translationType: String,
    val qualityLabel: String = "Auto",
    val qualityMode: AnimeDownloadQualityMode = AnimeDownloadQualityMode.HIGHEST,
    val directUrl: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data object Preparing : DownloadState()
    data class InProgress(val progress: Float) : DownloadState()
    data class Paused(val progress: Float) : DownloadState()
    data object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

data class AnimeDownloadInfo(
    val key: String,
    val animeId: String,
    val animeName: String,
    val episodeId: String? = null,
    val epNo: String,
    val episodeTitle: String? = null,
    val quality: String,
    val state: DownloadState = DownloadState.Idle,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val transferRateBytesPerSecond: Long? = null,
    val fileUri: String? = null,
    val subtitleFileUri: String? = null,
    val subtitleLabel: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class AniCliUiState(
    val searchQuery: String = "",
    val mode: String = "sub",
    val isSearching: Boolean = false,
    val isLoadingMoreSearch: Boolean = false,
    val searchResults: List<AniCliAnime> = emptyList(),
    val searchPage: Int = 1,
    val searchHasMore: Boolean = true,
    val hasSearched: Boolean = false,
    val selectedAnime: AniCliAnime? = null,
    val selectedAnimeIndex: Int? = null,
    val selectedListContext: AnimeListContext? = null,
    val isLoadingEpisodes: Boolean = false,
    val episodes: List<AniCliEpisode> = emptyList(),
    val selectedEpisode: String? = null,
    val selectedEpisodeNumber: String? = null,
    val isLoadingStreams: Boolean = false,
    val streamLinks: List<AniCliStreamLink> = emptyList(),
    val showStreamSheet: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val trendingAnime: List<AniCliAnime> = emptyList(),
    val isLoadingTrending: Boolean = false,
    val animeHistory: List<AnimeHistoryEntry> = emptyList(),
    val selectedSource: AnimeSource = AnimeSource.MOVIEBOX,
    val bookmarkedIds: Set<String> = emptySet(),
    val isSortAscending: Boolean = true,
    val animeProviderPage: Int = 1,
    val animeProviderHasMore: Boolean = true,
    val selectedTab: AnimeTab = AnimeTab.LATEST,
    val latestAnime: List<AniCliAnime> = emptyList(),
    val latestPage: Int = 1,
    val latestHasMore: Boolean = true,
    val isLoadingLatest: Boolean = false,
    val isLoadingMoreLatest: Boolean = false,
    val animeBookmarks: List<AniCliAnime> = emptyList(),
    val downloadedAnime: List<AniCliAnime> = emptyList(),
    val downloadedEpisodes: Map<String, List<String>> = emptyMap(),
    val isDownloadingAll: Boolean = false,
)
