package app.gyrolet.mpvrx.ui.browser.anime

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.domain.anicli.AniCliEpisode
import app.gyrolet.mpvrx.domain.anicli.AniCliStreamLink
import app.gyrolet.mpvrx.domain.anicli.AniCliUiState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.player.PlayerActivity
import app.gyrolet.mpvrx.ui.theme.LocalEmphasizedTypography
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
data class AnimeEpisodeScreen(
    val animeId: String,
    val animeName: String,
) : Screen {

    @Composable
    override fun Content() {
        val vm: AnimeViewModel = koinViewModel()
        val state by vm.uiState.collectAsState()
        val backstack = LocalBackStack.current
        val emphasizedTypography = LocalEmphasizedTypography.current
        val context = LocalContext.current

        LaunchedEffect(animeId) {
            vm.loadAnimeEpisodes(animeId, animeName)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(animeName, style = emphasizedTypography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    navigationIcon = {
                        IconButton(onClick = { backstack.popSafely() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            if (state.isLoadingEpisodes) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.episodes.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No episodes found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Episodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                    }
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeCard(
                            episode = episode,
                            onClick = { vm.selectEpisode(episode.id, episode.number) },
                        )
                    }
                }
            }
        }

        if (state.showStreamSheet && state.streamLinks.isNotEmpty()) {
            StreamSheet(state = state, vm = vm, animeName = animeName)
        }

        state.infoMessage?.let { msg ->
            LaunchedEffect(msg) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                vm.clearInfo()
            }
        }
    }

    @Composable
    private fun EpisodeCard(episode: AniCliEpisode, onClick: () -> Unit) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Episode ${episode.number}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    episode.title?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (episode.isFiller) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("Filler", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StreamSheet(state: AniCliUiState, vm: AnimeViewModel, animeName: String) {
        val context = LocalContext.current
        ModalBottomSheet(onDismissRequest = vm::dismissStreamSheet) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Select Stream", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (state.isLoadingStreams) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 400.dp)) {
                        items(state.streamLinks, key = { "${it.quality}_${it.url}" }) { link ->
                            StreamCard(
                                link = link,
                                onPlay = {
                                    vm.dismissStreamSheet()
                                    val intent = Intent(context, PlayerActivity::class.java).apply {
                                        putExtra("uri", link.url)
                                        putExtra("title", "$animeName E${state.selectedEpisodeNumber ?: ""}")
                                        putExtra("headers", arrayOf("Referer", link.referer ?: "https://moviebox.ph/", "User-Agent", link.userAgent ?: "Mozilla/5.0"))
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                    state.selectedAnime?.let { anime ->
                                        vm.addToHistory(anime, state.selectedEpisodeNumber ?: "")
                                    }
                                },
                                onDownload = {
                                    vm.downloadStream(link, animeName, state.selectedEpisodeNumber ?: "")
                                },
                            )
                        }
                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }

    @Composable
    private fun StreamCard(link: AniCliStreamLink, onPlay: () -> Unit, onDownload: () -> Unit) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(link.title ?: link.quality, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(link.quality, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (link.isM3u8) {
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("HLS", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
