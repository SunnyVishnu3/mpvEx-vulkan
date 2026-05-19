package com.astrixforge.devicemasker.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.astrixforge.devicemasker.DeviceMaskerApp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.SettingsDataStore
import com.astrixforge.devicemasker.data.models.ThemeMode
import com.astrixforge.devicemasker.data.repository.SpoofRepository
import com.astrixforge.devicemasker.service.ShareableLogResult
import com.astrixforge.devicemasker.service.diagnostics.RootAccessManager
import com.astrixforge.devicemasker.service.diagnostics.RootAccessState
import com.astrixforge.devicemasker.ui.navigation.DeviceMaskerNavigator
import com.astrixforge.devicemasker.ui.navigation.NavDestination
import com.astrixforge.devicemasker.ui.navigation.rememberDeviceMaskerNavigationState
import com.astrixforge.devicemasker.ui.navigation.settingsViewModelFactory
import com.astrixforge.devicemasker.ui.screens.settings.SettingsScreen
import com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModel
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import com.astrixforge.devicemasker.ui.theme.LocalMotionPolicy
import com.astrixforge.devicemasker.ui.theme.rememberMotionPolicy
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Main Activity for DeviceMasker.
 *
 * Uses Jetpack Compose with Material 3 for the entire UI with edge-to-edge display. Features:
 * - Bottom navigation with animated transitions
 * - Theme settings persistence (AMOLED, Dynamic Colors)
 * - Spring-based animations for smooth navigation
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    private var pendingDeepLinkIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDeepLinkIntent = intent

        enableEdgeToEdge()
        Timber.d("MainActivity created, module active: ${DeviceMaskerApp.isXposedModuleActive}")

        setContent {
            MainActivityContent(
                activity = this,
                appContext = applicationContext,
                deepLinkIntent = pendingDeepLinkIntent,
                deepLinkIntentHandled = { pendingDeepLinkIntent = null },
                onExitApp = { finish() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLinkIntent = intent
    }
}

@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun MainActivityContent(
    activity: ComponentActivity,
    appContext: Context,
    deepLinkIntent: Intent?,
    deepLinkIntentHandled: () -> Unit,
    onExitApp: () -> Unit,
) {
    val windowSizeClass = calculateWindowSizeClass(activity = activity)
    val settingsStore = remember { SettingsDataStore(appContext) }
    val repository = remember { SpoofRepository.getInstance(appContext) }
    val themeMode by
        settingsStore.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val amoledMode by settingsStore.amoledMode.collectAsStateWithLifecycle(initialValue = true)
    val dynamicColors by
        settingsStore.dynamicColors.collectAsStateWithLifecycle(initialValue = true)
    val rootAccessState by RootAccessManager.state.collectAsStateWithLifecycle()
    val motionPolicy = rememberMotionPolicy()
    var showRootWarning by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    RequestStartupRootCapture(appContext)
    ShowRootWarningWhenUnavailable(rootAccessState) { showRootWarning = true }

    CompositionLocalProvider(LocalMotionPolicy provides motionPolicy) {
        ApplyEdgeToEdgeStyle(activity = activity, themeMode = themeMode)
        DeviceMaskerTheme(
            themeMode = themeMode,
            amoledBlack = amoledMode,
            dynamicColor = dynamicColors,
        ) {
            DeviceMaskerMainApp(
                repository = repository,
                settingsStore = settingsStore,
                themeMode = themeMode,
                amoledMode = amoledMode,
                dynamicColors = dynamicColors,
                rootAccessState = rootAccessState,
                windowSizeClass = windowSizeClass,
                deepLinkIntent = deepLinkIntent,
                deepLinkIntentHandled = deepLinkIntentHandled,
                onExitApp = onExitApp,
            )

            if (showRootWarning) {
                RootAccessWarningDialog(
                    rootAccessState = rootAccessState,
                    onRetry = {
                        coroutineScope.launch {
                            if (requestRootAndCaptureStartup(appContext, force = true)) {
                                showRootWarning = false
                            }
                        }
                    },
                    onDismiss = { showRootWarning = false },
                )
            }
        }
    }
}

/** Main app composable with navigation. */
@Composable
fun DeviceMaskerMainApp(
    repository: SpoofRepository,
    settingsStore: SettingsDataStore,
    themeMode: ThemeMode,
    amoledMode: Boolean,
    dynamicColors: Boolean,
    rootAccessState: RootAccessState,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    deepLinkIntent: Intent? = null,
    deepLinkIntentHandled: () -> Unit = {},
    onExitApp: () -> Unit = {},
) {
    val navigationState = rememberDeviceMaskerNavigationState()
    val navigator = remember(navigationState) { DeviceMaskerNavigator(navigationState) }
    val context = LocalContext.current
    val reduceMotion = AppMotion.shouldReduceMotion()
    val sceneStrategies = rememberMainSceneStrategies(windowSizeClass)
    val entryDecorators =
        persistentListOf(
            rememberSaveableStateHolderNavEntryDecorator<NavDestination>(),
            rememberViewModelStoreNavEntryDecorator<NavDestination>(),
        )

    val showNavRail =
        windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact &&
            !navigationState.isFocusScreen
    val showBottomBar =
        windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact &&
            !navigationState.isFocusScreen
    val navigationBackHandler = {
        if (navigator.goBack()) {
            onExitApp()
        }
    }

    HandleMainDeepLinkIntent(deepLinkIntent, navigator, deepLinkIntentHandled)

    MainNavigationScaffold(
        navigationState = navigationState,
        navigator = navigator,
        showBottomBar = showBottomBar,
        showNavRail = showNavRail,
        modifier = modifier,
    ) { innerModifier ->
        DeviceMaskerNavDisplay(
            navigationState = navigationState,
            navigator = navigator,
            repository = repository,
            settingsStore = settingsStore,
            themeMode = themeMode,
            amoledMode = amoledMode,
            dynamicColors = dynamicColors,
            rootAccessState = rootAccessState,
            context = context,
            modifier = innerModifier,
            navigationBackHandler = navigationBackHandler,
            entryDecorators = entryDecorators,
            sceneStrategies = sceneStrategies,
            reduceMotion = reduceMotion,
        )
    }
}

@Composable
internal fun SettingsEntry(
    application: android.app.Application,
    settingsStore: SettingsDataStore,
    themeMode: ThemeMode,
    amoledMode: Boolean,
    dynamicColors: Boolean,
    rootAccessState: RootAccessState,
    navigator: DeviceMaskerNavigator,
    viewModel: SettingsViewModel =
        viewModel(
            factory =
                remember(application, settingsStore) {
                    settingsViewModelFactory(application, settingsStore)
                }
        ),
) {
    val settingsState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareLogsChooserTitle = stringResource(R.string.settings_export_logs_share_chooser)

    SettingsScreen(
        themeMode = themeMode,
        amoledDarkMode = amoledMode,
        dynamicColors = dynamicColors,
        isExportingLogs = settingsState.isExportingLogs,
        rootAccessState = rootAccessState,
        exportResult = settingsState.exportResult,
        onThemeModeChange = { mode ->
            Timber.d("Theme mode changed: $mode")
            viewModel.setThemeMode(mode)
        },
        onAmoledDarkModeChange = { enabled ->
            Timber.d("AMOLED mode changed: $enabled")
            viewModel.setAmoledMode(enabled)
        },
        onDynamicColorChange = { enabled ->
            Timber.d("Dynamic colors changed: $enabled")
            viewModel.setDynamicColors(enabled)
        },
        onExportLogsToUri = { uri ->
            Timber.d("Exporting logs to: $uri")
            viewModel.exportLogsToUri(uri)
        },
        onShareLogs = {
            Timber.d("Sharing logs...")
            viewModel.createShareableLogs { result ->
                handleShareLogsResult(context, shareLogsChooserTitle, result)
            }
        },
        onClearExportResult = { viewModel.clearExportResult() },
        onNavigateToDiagnostics = dropUnlessResumed(block = navigator::navigateToDiagnostics),
        onNavigateToLogsMonitor = dropUnlessResumed(block = navigator::navigateToLogsMonitor),
        generateLogFileName = { viewModel.generateLogFileName() },
    )
}

private fun handleShareLogsResult(
    context: android.content.Context,
    shareLogsChooserTitle: String,
    result: ShareableLogResult,
) {
    when (result) {
        is ShareableLogResult.Success -> {
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, result.uri)
                    putExtra(Intent.EXTRA_SUBJECT, shareLogsChooserTitle)
                    clipData = ClipData.newUri(context.contentResolver, result.fileName, result.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(shareIntent, shareLogsChooserTitle))
        }
        is ShareableLogResult.Error -> Timber.e("Failed to share logs: ${result.message}")
    }
}

@Composable
private fun RootAccessWarningDialog(
    rootAccessState: RootAccessState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.root_access_warning_title)) },
        text = {
            Text(
                text =
                    if (rootAccessState == RootAccessState.UNAVAILABLE) {
                        stringResource(R.string.root_access_unavailable_message)
                    } else {
                        stringResource(R.string.root_access_denied_message)
                    }
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.root_access_retry)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
        },
    )
}
