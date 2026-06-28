package app.gyrolet.mpvrx.domain.anicli

enum class AnimeSource(val displayName: String, val supportsMode: Boolean, val isAdult: Boolean = false) {
    MOVIEBOX("MovieBox", false),
}

val AnimeSource.isStreamingEmbedSource: Boolean get() = false

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

data class AniCliEpisode(
    val id: String,
    val number: String,
    val title: String? = null,
    val isFiller: Boolean = false,
    val thumbnail: String? = null,
    val isPreview: Boolean = false,
    val duration: String? = null,
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
    val isLoadingEpisodes: Boolean = false,
    val episodes: List<AniCliEpisode> = emptyList(),
    val selectedEpisode: String? = null,
    val isLoadingStreams: Boolean = false,
    val streamLinks: List<AniCliStreamLink> = emptyList(),
    val showStreamSheet: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val trendingAnime: List<AniCliAnime> = emptyList(),
    val isLoadingTrending: Boolean = false,
    val animeHistory: List<AnimeHistoryEntry> = emptyList(),
    val selectedSource: AnimeSource = AnimeSource.MOVIEBOX,
)
