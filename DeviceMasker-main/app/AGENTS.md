# :app Module Guide

Compose UI, ViewModels, config persistence, RemotePreferences writer, diagnostics, and root evidence collection. This is the user-facing Android application.

## Module Structure

```
app/src/main/kotlin/com/astrixforge/devicemasker/
├── data/                     RemotePreferences writer, config sync, settings, repositories
│   ├── models/               InstalledApp, TypeAliases
│   └── repository/           SpoofRepository, AppScopeRepository + interfaces
│
├── service/                  Config persistence, logging, support bundles
│   └── diagnostics/          Root access, root capture, support bundles, JSONL store
│
├── ui/                       All Compose UI
│   ├── navigation/           NavDestination, navigation state, deep links, bottom bar
│   ├── screens/
│   │   ├── home/             Dashboard: module toggle, stats, group selector
│   │   ├── groups/           Group list, CRUD, import/export
│   │   ├── groupspoofing/    Spoof config + app assignment
│   │   │   ├── categories/   SIM, DeviceHardware, Location category cards
│   │   │   ├── items/        Independent/Correlated spoof items
│   │   │   ├── tabs/         Spoof tab + Apps tab
│   │   │   └── model/        UIDisplayCategory
│   │   ├── settings/         Theme, root status, export, diagnostics link
│   │   └── diagnostics/      Module status, anti-detection tests, hook logs
│   ├── theme/                Color, Theme, Typography, Shapes, Motion (M3E)
│   └── components/           Reusable UI components
│       ├── expressive/       M3E expressive components (cards, switches, indicators)
│       └── dialog/           Timezone, country pickers, standard dialogs
│
└── utils/                    Image utilities
```

## Manual DI — No Hilt

All wiring is manual. `DeviceMaskerApp.onCreate()` creates singletons. `MainActivity` creates repositories via `remember`. Navigation entries pass dependencies to screen routes, and screens use explicit `viewModelFactory` helpers from `DeviceMaskerViewModelFactories.kt` as default ViewModel parameters. Service/repository contracts are split into narrow workflow interfaces with compatibility facades (`IConfigManager`, `ISpoofRepository`) for existing callers and test fakes.

**Singletons (objects):** `ConfigManager`, `XposedPrefs`, `ConfigSync`, `LogManager`, `RootAccessManager`, `RootCaptureStore`

**Instance singletons (in DeviceMaskerApp companion):** `appLogStore`

## Key Files

| File | Role |
|------|------|
| `DeviceMaskerApp.kt` | Application entry — plants Timber trees, inits ConfigManager/XposedPrefs/RootAccessManager, registers config sync on LSPosed bind |
| `data/XposedPrefs.kt` | Writes to RemotePreferences via `XposedService.getRemotePreferences()`, and exposes LSPosed scope via `scopedPackages`/`refreshScope()`. All keys delegate to `SharedPrefsKeys` in `:common`. Uses `commit()` not `apply()`. |
| `data/ConfigSync.kt` | Flattens `JsonConfig` -> flat per-app SharedPreferences keys. `syncFromConfig()` for full sync, `syncPackages()` for dirty package writes, `syncApp()` compatibility for single app. Full sync clears stale keys. |
| `data/ConfigSyncHelpers.kt` | App sync state and SharedPreferences editor helpers used by `ConfigSync`. |
| `data/repository/AppScopeRepository.kt` | Full installed-app metadata for the Apps tab plus scoped package metadata fast path for Home. `loadScopedApps()` must not full-scan on startup. |
| `service/ConfigManager.kt` | JSON config CRUD. Backs `AtomicFile` at `filesDir/config.json`. Exposes `config: StateFlow<JsonConfig>`. Uses `Mutex` for thread-safe saves. |
| `data/repository/SpoofRepository.kt` | Main repo for ViewModels. Correlation-aware value generation with `AtomicReference` caches for SIM/Location/DeviceHardware configs. Singleton via `getInstance()`. |
| `ui/components/AppIconCache.kt` | Bounded app-icon cache used by Home scoped rows and app lists. Keep icon decode off repeated composition paths. |
| `ui/navigation/DeviceMaskerViewModelFactories.kt` | Manual ViewModel factories used by screen default parameters. Production factories create lifecycle `SavedStateHandle` instances with `createSavedStateHandle()`. |

## Screen → ViewModel → Repository Mapping

| Screen | ViewModel | Key Repository Methods |
|--------|-----------|----------------------|
| Home | `HomeViewModel(ISpoofRepository, isXposedActiveFlow, xposedScopeStateFlow, SavedStateHandle)` | `setModuleEnabled()`, `setActiveGroup()`, `refreshScopedApps()`, `setAppEnabled()`, `regenerateAllValues()` |
| Groups | `GroupsViewModel(ISpoofRepository)` | `createGroup()`, `deleteGroup()`, `setDefaultGroup()`, `exportGroups()`, `importGroups()` |
| GroupSpoofing | `GroupSpoofingViewModel(ISpoofRepository, groupId)` | `generateValue()`, `updateGroupWithCarrier()`, `updateGroupWithDeviceProfile()`, `addAppToGroup()`, `removeAppFromGroup()` |
| Settings | `SettingsViewModel(Application, ISettingsDataStore, ILogManager, ioDispatcher)` | `setThemeMode()`, `exportLogsToUri()`, `createShareableLogs()` |
| Diagnostics | `DiagnosticsViewModel(Application, ISpoofRepository)` | `refresh()`, `runDiagnosticTests()`, `runAntiDetectionTests()` |

## Navigation (Navigation 3)

- Framework: `navigation3-runtime/ui` 1.1.1, NOT Navigation Compose 2.x
- Destinations: `NavDestination` sealed interface with `@Serializable` `NavKey` types
- State: `DeviceMaskerNavigationState` — per-tab back stacks (Home, Groups, Settings)
- `DeviceMaskerNavigator` — imperative API for screens (`navigateToGroup()`, `navigateToDiagnostics()`, `goBack()`)
- Focus screens (`GroupSpoofing`, `Diagnostics`) hide bottom bar/nav rail
- Adaptive: `NavigationRail` for medium/expanded, bottom bar for compact
- Deep links: `devicemasker://open/{path}` — supports `home`, `groups`, `groups/{id}`, `settings`, `diagnostics`
- Scene strategies: `ListDetailSceneStrategy` for Groups→GroupSpoofing on wider screens

## Theme (Material 3 Expressive)

- `DeviceMaskerTheme` composable: supports SYSTEM/LIGHT/DARK, AMOLED black, dynamic colors
- Uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`
- Named colors in `Color.kt` — no hardcoded `Color(0x...)` outside that file
- `LocalEmphasizedTypography` for bolder expressive weights
- `AppMotion` object: spring-based specs (Expressive, Standard, Snappy, ReducedMotion)
- `ElevationTokens` Level0–Level5

## Compose Rules

- Reusable composables must accept `modifier: Modifier = Modifier`.
- State-backed collection parameters should use immutable collection types.
- Wrap callbacks captured by `LaunchedEffect` or restartable effects with `rememberUpdatedState`.
- Do not build ViewModels inside composable bodies. Use factories and default parameters at the screen boundary.
- Helpers that emit multiple independent children need a parent layout.
- Keep scope-specific modifiers, such as dropdown menu anchors, at the scope call site and pass them down as `modifier`.
- Split large screens by state, section, row, and action boundaries.
- Do not add wrapper composables that only forward a ViewModel.
- Create small stable UI-state models before rendering category-heavy screens.

## App Boundary Rules

- Generate and persist identity values in app/common config flows, not in target-process hooks.
- Prefer narrow workflow interfaces for new code. Compatibility facades should not keep growing.
- `JsonConfig.appConfigs` is canonical for group app counts, app checked state, and RemotePreferences sync inputs.
- Do not use `SpoofGroup.assignedApps` for new active toggle/count/sync decisions; it is legacy/display compatibility only.
- Home Scoped Apps must use `XposedPrefs.scopedPackages` joined with scoped metadata from `AppScopeRepository.loadScopedApps()`. Do not derive LSPosed scoped-app lists from spoof groups or `appConfigs`, and do not full-scan installed apps on Home startup.
- `AppConfig.isEnabled` is standalone app-level user control. Preserve it during group assignment and unassignment unless the user explicitly toggles app enablement.
- Runtime sync must require explicit app-to-group assignment. Do not let default-group fallback make an unassigned package hookable.
- App and group mutations that affect only known packages should use dirty sync hints so `ConfigSync.syncPackages()` can avoid full RemotePreferences rewrites. Global/default/import/repair flows may still request full sync.

## Diagnostics & Root

- `AppLogStore`: JSONL events in `filesDir/logs/sessions/`
- `LogManager`: single `Export Logs` ZIP path that always builds the maximum available support bundle
- `StrictModeGuard`: debug-only app-process StrictMode policy. Never install StrictMode from `:xposed`.
- `RootAccessManager`: libsu root grant state, startup request
- `RootLogCaptureService`: foreground service for bounded root capture
- `BootCaptureReceiver`: `BOOT_COMPLETED` → starts root capture

## Testing

- `MainDispatcherRule` — swaps `Dispatchers.Main` with `UnconfinedTestDispatcher`
- Hand-written fakes for interfaces: `FakeSpoofRepository`, `FakeConfigManager`, `FakeSettingsDataStore`, `FakeLogManager`, `FakeAppScopeRepository`, `FakeSharedPreferences`
- Turbine for Flow emissions
- MockK only for Navigation 3 framework types
- Production ViewModel factories own lifecycle `SavedStateHandle` creation. Unit tests pass explicit `SavedStateHandle()` values.
- `advanceUntilIdle()` required after async ops in `runTest`

## Build

- `compileSdk 37`, `targetSdk 36`, `minSdk 26`, JVM 17
- Release minification/resource shrinking is enabled. Runtime hook callbacks must stay R8-safe through the `:xposed` StableHooker/named `XposedInterface.Hooker` pattern.
- `aidl = false`, `buildConfig = true`, `compose = true`
- `useLegacyPackaging = true` for primary dex Xposed class loading
- Compose compiler reports/metrics are opt-in with `enableComposeCompilerReports` and `enableComposeCompilerMetrics`
- Signing from env vars: `KEYSTORE_PATH`, `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`

## Manifest

- Permissions: `QUERY_ALL_PACKAGES`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`
- `XposedProvider` at `${applicationId}.XposedService` — required for RemotePreferences bridge
- `MainActivity`: `singleTop`, `adjustResize`, deep link filter `devicemasker://open`
- `allowBackup = false`
