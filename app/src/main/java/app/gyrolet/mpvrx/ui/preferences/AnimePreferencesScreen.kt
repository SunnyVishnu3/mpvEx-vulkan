package app.gyrolet.mpvrx.ui.preferences

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.preferences.components.SwitchPreference
import app.gyrolet.mpvrx.ui.theme.LocalEmphasizedTypography
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import org.koin.compose.koinInject

@Serializable
object AnimePreferencesScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val appearancePreferences = koinInject<AppearancePreferences>()
        val backstack = LocalBackStack.current
        val emphasizedTypography = LocalEmphasizedTypography.current

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Anime", style = emphasizedTypography.headlineSmall) },
                    navigationIcon = {
                        IconButton(onClick = { backstack.popSafely() }) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    },
                )
            },
        ) { padding ->
            ProvidePreferenceLocals {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 28.dp)) {
                    item {
                        PreferenceSectionHeader(title = "Anime Mode")
                    }
                    item {
                        PreferenceCard {
                            val showAnimeTab by appearancePreferences.showAnimeTab.collectAsState()
                            SwitchPreference(
                                value = showAnimeTab,
                                onValueChange = appearancePreferences.showAnimeTab::set,
                                title = { Text("Show Anime Tab") },
                                summary = { Text("Show Anime tab in the bottom navigation bar", color = MaterialTheme.colorScheme.outline) },
                            )
                        }
                    }
                    item {
                        PreferenceSectionHeader(title = "About")
                    }
                    item {
                        PreferenceCard {
                            Text(
                                "Anime mode uses MovieBox provider to browse and stream anime content directly from the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
