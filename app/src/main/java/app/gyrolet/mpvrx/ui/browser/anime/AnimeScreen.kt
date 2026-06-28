package app.gyrolet.mpvrx.ui.browser.anime

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.domain.anicli.AniCliAnime
import app.gyrolet.mpvrx.domain.anicli.AniCliStreamLink
import app.gyrolet.mpvrx.domain.anicli.AniCliUiState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.player.PlayerActivity
import app.gyrolet.mpvrx.ui.theme.LocalEmphasizedTypography
import app.gyrolet.mpvrx.ui.theme.spacing
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
                    title = {
                        Text("Anime", style = emphasizedTypography.headlineSmall, fontWeight = FontWeight.Bold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            ) {
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
                    state.isLoadingTrending && state.trendingAnime.isEmpty() -> LoadingIndicator()
                    state.hasSearched && state.isSearching && state.searchResults.isEmpty() -> LoadingIndicator()
                    state.hasSearched && state.searchResults.isEmpty() && !state.isSearching -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.selectedAnime != null -> EpisodeView(state = state, vm = vm)
                    state.hasSearched -> SearchResultsView(state = state, vm = vm)
                    else -> TrendingView(state = state, vm = vm)
                }
            }
        }

        if (state.showStreamSheet && state.streamLinks.isNotEmpty()) {
            StreamSheet(state = state, vm = vm)
        }

        state.errorMessage?.let {
            AlertDialog(onDismissRequest = vm::clearError, title = { Text("Error") }, text = { Text(it) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } })
        }
    }

    @Composable
    private fun LoadingIndicator() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }

    @Composable
    private fun TrendingView(state: AniCliUiState, vm: AnimeViewModel) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Explore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            items(state.trendingAnime, key = { it.id }) { anime ->
                TrendingCard(anime = anime, onClick = { vm.selectAnime(anime) })
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    @Composable
    private fun TrendingCard(anime: AniCliAnime, onClick: () -> Unit) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(modifier = Modifier.height(140.dp)) {
                AsyncImage(
                    model = anime.thumbnail.ifEmpty { anime.bannerImage },
                    contentDescription = anime.name,
                    modifier = Modifier.width(100.dp).fillMaxHeight(),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                    Text(anime.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        anime.type?.let { InfoChip(it) }
                        if (anime.subEpisodes > 0) InfoChip("${anime.subEpisodes} EP")
                        anime.score?.let { InfoChip("${it}/10") }
                    }
                    anime.genres.take(3).forEach { genre ->
                        Text(genre, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    @Composable
    private fun InfoChip(text: String) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
        }
    }

    @Composable
    private fun SearchResultsView(state: AniCliUiState, vm: AnimeViewModel) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.searchResults, key = { it.id }) { anime ->
                TrendingCard(anime = anime, onClick = { vm.selectAnime(anime) })
            }
            if (state.isLoadingMoreSearch) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(32.dp)) } }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun EpisodeView(state: AniCliUiState, vm: AnimeViewModel) {
        val anime = state.selectedAnime ?: return
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.selectAnime(anime); /* deselect by reset */ }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(anime.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        anime.type?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            anime.description?.let { desc ->
                item {
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }

            if (anime.genres.isNotEmpty()) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        anime.genres.forEach { genre -> InfoChip(genre) }
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Episodes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }

            if (state.isLoadingEpisodes) {
                item { Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeCard(episode = episode, isSelected = episode.id == state.selectedEpisode, onClick = { vm.selectEpisode(episode.id, episode.number) })
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    @Composable
    private fun EpisodeCard(episode: app.gyrolet.mpvrx.domain.anicli.AniCliEpisode, isSelected: Boolean, onClick: () -> Unit) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Episode ${episode.number}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    episode.title?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (episode.isFiller) InfoChip("Filler")
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StreamSheet(state: AniCliUiState, vm: AnimeViewModel) {
        val context = LocalContext.current
        ModalBottomSheet(onDismissRequest = vm::dismissStreamSheet) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Select Stream", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (state.isLoadingStreams) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                        items(state.streamLinks, key = { "${it.quality}_${it.url}" }) { link ->
                            StreamCard(link = link, onClick = {
                                vm.dismissStreamSheet()
                                val intent = Intent(context, PlayerActivity::class.java).apply {
                                    putExtra("uri", link.url)
                                    putExtra("title", state.selectedAnime?.name ?: "Anime")
                                    putExtra("headers", arrayOf(
                                        "Referer", link.referer ?: "https://moviebox.ph/",
                                        "User-Agent", link.userAgent ?: "Mozilla/5.0",
                                    ))
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                                vm.addToHistory(state.selectedAnime!!, state.selectedEpisodeNumber ?: "")
                            })
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }

    @Composable
    private fun StreamCard(link: AniCliStreamLink, onClick: () -> Unit) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.title ?: link.quality, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(link.quality, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (link.isM3u8) InfoChip("HLS")
            }
        }
    }
}
