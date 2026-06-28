package app.gyrolet.mpvrx.ui.browser.anime

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.domain.anicli.AniCliAnime
import app.gyrolet.mpvrx.domain.anicli.AniCliUiState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.theme.LocalEmphasizedTypography
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import coil3.compose.AsyncImage
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
object AnimeScreen : Screen {

    @Composable
    override fun Content() {
        val vm: AnimeViewModel = koinViewModel()
        val state by vm.uiState.collectAsState()
        var searchText by remember(state.searchQuery) { mutableStateOf(state.searchQuery) }
        val emphasizedTypography = LocalEmphasizedTypography.current

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Anime", style = emphasizedTypography.headlineSmall, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search anime...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = ""; vm.search("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search(searchText) }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
                Spacer(Modifier.height(12.dp))

                when {
                    state.hasSearched && state.searchResults.isEmpty() && !state.isSearching -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.hasSearched -> SearchResultsGrid(state = state, vm = vm)
                    else -> TrendingGrid(state = state, vm = vm)
                }
            }
        }
    }

    @Composable
    private fun TrendingGrid(state: AniCliUiState, vm: AnimeViewModel) {
        val backstack = LocalBackStack.current

        if (state.isLoadingTrending && state.trendingAnime.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.trendingAnime, key = { it.id }) { anime ->
                AnimeCard(anime = anime, onClick = {
                    backstack.add(AnimeEpisodeScreen(animeId = anime.id, animeName = anime.name))
                })
            }
        }
    }

    @Composable
    private fun SearchResultsGrid(state: AniCliUiState, vm: AnimeViewModel) {
        val backstack = LocalBackStack.current
        val listState = rememberLazyGridState()

        LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
            val totalItems = listState.layoutInfo.totalItemsCount
            if (lastVisible >= totalItems - 4 && !state.isLoadingMoreSearch && state.searchHasMore) {
                vm.loadMoreSearch()
            }
        }

        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.searchResults, key = { it.id }) { anime ->
                AnimeCard(anime = anime, onClick = {
                    backstack.add(AnimeEpisodeScreen(animeId = anime.id, animeName = anime.name))
                })
            }
            if (state.isLoadingMoreSearch) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun AnimeCard(anime: AniCliAnime, onClick: () -> Unit) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)) {
                    AsyncImage(
                        model = anime.thumbnail.ifEmpty { anime.bannerImage },
                        contentDescription = anime.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        anime.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        anime.type?.let { InfoChip(it) }
                        if (anime.subEpisodes > 0) InfoChip("${anime.subEpisodes} EP")
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoChip(text: String) {
        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(text, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}
