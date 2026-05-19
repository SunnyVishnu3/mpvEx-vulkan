# Device Masker - UI Folder Structure Audit Report

**Generated:** May 6, 2026  
**Auditor:** AI Agent (Claude + 4 subagents)  
**Scope:** `app/src/main/kotlin/com/astrixforge/devicemasker/`

---

## Executive Summary

This audit analyzes the entire UI layer (`app/` module) of the Device Masker Android application. The codebase demonstrates **strong architecture** with proper separation of concerns, modern patterns, and good organization. However, several improvements can enhance maintainability, reduce code duplication, and better align with Android/Kotlin best practices.

### Key Findings

| Category | Status | Rating |
|----------|--------|--------|
| Folder Organization | Good | ⭐⭐⭐⭐☆ |
| Layer Separation | Excellent | ⭐⭐⭐⭐⭐ |
| UI Components | Good (with room for improvement) | ⭐⭐⭐☆☆ |
| State Management | Good | ⭐⭐⭐⭐☆ |
| Navigation Architecture | Good (Navigation 3) | ⭐⭐⭐⭐☆ |
| Code Duplication | Moderate | ⭐⭐⭐☆☆ |

### Priority Actions

1. **High Priority:** Move `ThemeMode.kt` from `ui/screens/` to `ui/theme/`
2. **High Priority:** Extract dialog composables to `ui/components/dialog/`
3. **Medium Priority:** Extract inline screen composables to reusable components
4. **Medium Priority:** Consolidate similar card patterns
5. **Low Priority:** Add navigation argument validation

---

## Table of Contents

1. [Current Folder Structure Analysis](#1-current-folder-structure-analysis)
2. [UI Components Audit](#2-ui-components-audit)
3. [State Management Analysis](#3-state-management-analysis)
4. [Navigation Architecture Analysis](#4-navigation-architecture-analysis)
5. [Duplicate Code Patterns](#5-duplicate-code-patterns)
6. [Recommendations & Action Items](#6-recommendations--action-items)
7. [Proposed Target Structure](#7-proposed-target-structure)

---

## 1. Current Folder Structure Analysis

### Current Tree

```
app/src/main/kotlin/com/astrixforge/devicemasker/
├── DeviceMaskerApp.kt                    (Application entry - root)
├── data/
│   ├── ConfigSync.kt                     (Config sync)
│   ├── ISettingsDataStore.kt             (Interface)
│   ├── SettingsDataStore.kt              (DataStore wrapper)
│   ├── XposedPrefs.kt                    (RemotePreferences bridge)
│   ├── models/
│   │   ├── InstalledApp.kt
│   │   └── TypeAliases.kt
│   └── repository/
│       ├── AppScopeRepository.kt
│       ├── IAppScopeRepository.kt
│       ├── ISpoofRepository.kt
│       └── SpoofRepository.kt
├── service/
│   ├── ConfigManager.kt                  (Config CRUD - singleton)
│   ├── IConfigManager.kt                 (Interface)
│   ├── IDiagnosticsProvider.kt           (Interface)
│   ├── ILogManager.kt                    (Interface)
│   ├── IServiceClient.kt                 (Interface)
│   ├── LogManager.kt                     (Log export - singleton)
│   ├── ServiceClient.kt                  (AIDL client)
│   ├── DefaultDiagnosticsProvider.kt
│   └── diagnostics/
│       ├── BootCaptureReceiver.kt
│       ├── DiagnosticSessionManager.kt
│       ├── DiagnosticSnapshotBuilder.kt
│       ├── JsonlDiagnosticStore.kt
│       ├── RootAccessManager.kt
│       ├── RootCaptureStore.kt
│       ├── RootLogCaptureService.kt
│       ├── RootLogCollector.kt
│       ├── RootShell.kt
│       ├── StrictModeGuard.kt
│       └── SupportBundleBuilder.kt
├── ui/
│   ├── MainActivity.kt                   (UI entry point)
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   ├── Typography.kt
│   │   ├── Shapes.kt
│   │   └── Motion.kt
│   ├── navigation/
│   │   ├── DeviceMaskerNavigationState.kt
│   │   ├── DeviceMaskerNavigator.kt
│   │   ├── NavDestination.kt
│   │   ├── DeviceMaskerDeepLinks.kt
│   │   └── BottomNavBar.kt
│   ├── components/
│   │   ├── GroupCard.kt
│   │   ├── AppListItem.kt
│   │   ├── ActionBottomSheet.kt
│   │   ├── EmptyState.kt
│   │   ├── IconCircle.kt
│   │   ├── ScreenHeader.kt
│   │   ├── SettingsItem.kt
│   │   ├── StatCard.kt
│   │   ├── SpoofValueCard.kt
│   │   ├── ValueRow.kt
│   │   ├── dialog/
│   │   │   ├── CountryPickerDialog.kt
│   │   │   ├── StandardDialogs.kt
│   │   │   └── TimezonePickerDialog.kt
│   │   └── expressive/
│   │       ├── ExpressiveCard.kt
│   │       ├── ExpressiveSwitch.kt
│   │       ├── ExpressiveIconButton.kt
│   │       ├── ExpressiveLoadingIndicator.kt
   │   │       ├── ExpressivePullToRefresh.kt
│   │       ├── StatusIndicator.kt
│   │       ├── QuickActionGroup.kt
│   │       ├── AnimatedSection.kt
│   │       ├── SectionHeader.kt
│   │       └── MorphingShape.kt
│   ├── screens/
│   │   ├── ThemeMode.kt                  (⚠️ OUT OF PLACE)
│   │   ├── home/
│   │   │   ├── HomeScreen.kt
│   │   │   ├── HomeState.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── groups/
│   │   │   ├── GroupsScreen.kt
│   │   │   ├── GroupsState.kt
│   │   │   └── GroupsViewModel.kt
│   │   ├── groupspoofing/
│   │   │   ├── GroupSpoofingScreen.kt    (592 lines - large)
│   │   │   ├── GroupSpoofingState.kt
│   │   │   ├── GroupSpoofingViewModel.kt
│   │   │   ├── categories/
│   │   │   │   ├── CategorySection.kt
│   │   │   │   ├── SIMCardContent.kt
│   │   │   │   ├── LocationContent.kt
│   │   │   │   └── DeviceHardwareContent.kt
│   │   │   ├── items/
│   │   │   │   ├── IndependentSpoofItem.kt
│   │   │   │   ├── CorrelatedSpoofItem.kt
│   │   │   │   └── ReadOnlyValueRow.kt
│   │   │   └── model/
│   │   │       └── UIDisplayCategory.kt
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt
│   │   │   ├── SettingsState.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── diagnostics/
│   │       ├── DiagnosticsScreen.kt
│   │       ├── DiagnosticsState.kt
│   │       └── DiagnosticsViewModel.kt
│   └── utils/
│       └── ImageUtils.kt
```

### Layer Analysis

#### Data Layer (`data/`) ✅
- **Status:** Excellent
- Clear interface/implementation separation
- Models properly in `data/models/`
- Repositories properly in `data/repository/`
- RemotePreferences bridge correctly placed

#### Service Layer (`service/`) ✅
- **Status:** Good
- Config management, logging, AIDL client correctly placed
- Diagnostics subdirectory groups tightly-coupled functionality
- `StrictModeGuard.kt` could be at root but current placement is acceptable

#### UI Layer (`ui/`) ⚠️
- **Status:** Good with one issue
- Theme properly in `ui/theme/`
- Navigation properly in `ui/navigation/`
- Components properly categorized with `expressive/` subdirectory
- **Issue:** `ThemeMode.kt` misplaced (should be in `ui/theme/`)

---

## 2. UI Components Audit

### Reusable Components Found

#### Shared Components (`ui/components/`)

| File | Composable Functions | Purpose | Status |
|------|---------------------|---------|--------|
| `GroupCard.kt` | `GroupCard`, `CompactGroupCard`, `DefaultBadge` | Group display cards | ✅ Public |
| `AppListItem.kt` | `AppListItem`, `AppIcon`, `AppIconFallback` | App list items | ⚠️ AppIcon private |
| `SpoofValueCard.kt` | `SpoofValueCard`, `CompactSpoofValueCard` | Spoof value display | ✅ Public |
| `ValueRow.kt` | `ValueRow`, `LabeledValue` | Key-value patterns | ✅ Public |
| `SettingsItem.kt` | `SettingsSection`, `SettingsSwitchItem`, `SettingsClickableItem`, `SettingsInfoItem` | Settings UI patterns | ✅ Public |
| `EmptyState.kt` | `EmptyState` | Empty placeholder | ✅ Public |
| `ScreenHeader.kt` | `ScreenHeader` | Screen headers | ✅ Public |
| `IconCircle.kt` | `IconCircle` | Icon container | ✅ Public |
| `StatCard.kt` | `StatCard` | Stats display | ✅ Public |
| `ActionBottomSheet.kt` | `ActionBottomSheet`, `ActionItem` | Modal actions | ✅ Public |

#### Expressive Components (`ui/components/expressive/`)

| File | Composable Functions | Purpose |
|------|---------------------|---------|
| `ExpressiveCard.kt` | `ExpressiveCard`, `ExpressiveOutlinedCard` | Animated M3 cards |
| `ExpressiveSwitch.kt` | `ExpressiveSwitch` | Animated toggle |
| `ExpressiveIconButton.kt` | `ExpressiveIconButton`, `CompactExpressiveIconButton` | Animated buttons |
| `ExpressiveLoadingIndicator.kt` | `ExpressiveLoadingIndicator` | Animated loading |
| `ExpressivePullToRefresh.kt` | `ExpressivePullToRefresh` | Pull-to-refresh |
| `StatusIndicator.kt` | `StatusIndicator` | Status display |
| `QuickActionGroup.kt` | `QuickActionGroup`, `QuickAction` | Action groups |
| `AnimatedSection.kt` | `AnimatedSection` | Animated sections |
| `SectionHeader.kt` | `SectionHeader` | Section headers |
| `MorphingShape.kt` | `animatedRoundedCornerShape` | Shape morphing |

### Components Needing Extraction

#### High Priority (Should Move to `ui/components/`)

| Component | Current Location | Recommendation |
|-----------|-----------------|----------------|
| `StatusCard` | `HomeScreen.kt` (inline) | Extract to `ui/components/` |
| `GroupSelectorCard` | `HomeScreen.kt` (inline) | Extract to `ui/components/` |
| `LoadingIndicator` | `SettingsScreen.kt` (inline) | Extract or reuse existing |
| `CorrelatedSpoofItem` | `screens/groupspoofing/items/` | Move to `ui/components/` |
| `IndependentSpoofItem` | `screens/groupspoofing/items/` | Move to `ui/components/` |

#### High Priority (Should Move to `ui/components/dialog/`)

| Component | Current Location | Recommendation |
|-----------|-----------------|----------------|
| `CreateGroupDialog` | `GroupsScreen.kt` | Extract to `dialog/` |
| `EditGroupDialog` | `GroupsScreen.kt` | Extract to `dialog/` |
| `DeleteGroupDialog` | `GroupsScreen.kt` | Extract to `dialog/` |
| `ThemeModeDialog` | `SettingsScreen.kt` | Extract to `dialog/` |
| `ExportActionsBottomSheet` | `SettingsScreen.kt` | Refactor to use existing `ActionBottomSheet` |
| `ExportModeSplitButton` | `SettingsScreen.kt` | Extract to `dialog/` or `components/` |

### Private Components That Should Be Public

| Component | File | Reason |
|-----------|------|--------|
| `AppIcon` | `AppListItem.kt` | Already a reusable pattern |
| `DefaultBadge` | `GroupCard.kt` | Could be used in selection lists |

---

## 3. State Management Analysis

### ViewModels

| ViewModel | Location | Base Class | State Class |
|-----------|----------|------------|-------------|
| `HomeViewModel` | `ui/screens/home/` | `ViewModel` | `HomeState` |
| `GroupsViewModel` | `ui/screens/groups/` | `ViewModel` | `GroupsState` |
| `GroupSpoofingViewModel` | `ui/screens/groupspoofing/` | `ViewModel` | `GroupSpoofingState` |
| `SettingsViewModel` | `ui/screens/settings/` | `AndroidViewModel` | `SettingsState` |
| `DiagnosticsViewModel` | `ui/screens/diagnostics/` | `AndroidViewModel` | `DiagnosticsState` |

### State Pattern Analysis

**Pattern Used:** Private MutableStateFlow + Public StateFlow

```kotlin
// Private mutable state
private val _state = MutableStateFlow(HomeState())

// Public immutable state
val state: StateFlow<HomeState> = _state.asStateFlow()
```

✅ **Good Practices Observed:**
- State is exposed as immutable `StateFlow`
- State classes are data classes with immutable properties
- ViewModels properly handle DI via Hilt

**Issues Found:**
- Inconsistent use of `AndroidViewModel` vs `ViewModel` (only settings and diagnostics use AndroidViewModel)
- Some state classes have complex nested types that could benefit from sealed classes

### State Classes

| State | Properties | Complexity |
|-------|-----------|------------|
| `HomeState` | 7 properties (loading, module status, groups, etc.) | Medium |
| `GroupsState` | 3 properties | Low |
| `GroupSpoofingState` | 6 properties + complex nested types | High |
| `SettingsState` | 7 properties (theme, export, etc.) | Medium |
| `DiagnosticsState` | 7 properties + nested diagnostic types | High |

---

## 4. Navigation Architecture Analysis

### Framework
- **Navigation 3** (latest - not legacy Navigation Compose)
- Adaptive layouts with `NavigationRail` + `BottomNav`
- Deep linking support with custom URI scheme

### Destinations

| Destination | Type | Description |
|-------------|------|-------------|
| `NavDestination.Home` | Object | Dashboard |
| `NavDestination.Groups` | Object | Group list |
| `NavDestination.Settings` | Object | Settings |
| `NavDestination.Diagnostics` | Object | Diagnostics |
| `NavDestination.GroupSpoofing(groupId)` | Data class | Per-group config |

### Bottom Navigation
- 3 tabs: Home, Groups, Settings
- Diagnostics accessible via Settings screen

### Issues Found

| Issue | Severity | Description |
|-------|----------|-------------|
| Missing Diagnostics from bottom nav | Low | Only accessible via Settings |
| No argument validation for GroupSpoofing | Medium | groupId not validated |
| Navigation state not fully serializable | Low | Only top-level persisted |
| No navigation result handling | Medium | Can't pass data back from screens |

### Assessment
✅ Good architecture using modern Navigation 3 patterns  
⚠️ Medium: Argument validation needed  
⚠️ Medium: Navigation result handling missing

---

## 5. Duplicate Code Patterns

### Pattern A: Settings Item Components
**Files:** `SettingsClickableItem`, `SettingsSwitchItem`, `SettingsInfoItem`

All follow similar structure:
```
IconCircle → Column(Title, Description) → Trailing Widget
```

**Recommendation:** Create a generic settings item builder or consolidate into a single flexible composable.

### Pattern B: Card Patterns
**Files:** `GroupCard`, `SpoofValueCard`, `StatCard`

All use:
- `ExpressiveCard` base
- Similar Row/Column layouts
- Similar header + content structure

**Recommendation:** Create a base card composable with configurable content slots.

### Pattern C: Dialog Patterns
**Files:** `CreateGroupDialog`, `EditGroupDialog`, `DeleteGroupDialog`, `ThemeModeDialog`

All use `AlertDialog` with:
- Title
- Content (form fields)
- Actions (confirm/cancel)

**Recommendation:** Extract to `ui/components/dialog/` as reusable dialog components.

### Pattern D: List Item Patterns
**Files:** `AppListItem`, `GroupCard`, `CompactGroupCard`

All use:
- `ExpressiveCard` with Row layout
- Selection state support
- Icon + Text + Trailing arrangement

**Recommendation:** These are appropriately different - no consolidation needed.

---

## 6. Recommendations & Action Items

### Critical (Do First)

| # | Action | File | Target Location | Reason |
|---|--------|------|-----------------|--------|
| 1 | Move | `ui/screens/ThemeMode.kt` | `ui/theme/ThemeMode.kt` | ThemeMode is theme config, not a screen |
| 2 | Extract | `CreateGroupDialog` | `ui/components/dialog/GroupsDialog.kt` | Reusable dialog |
| 3 | Extract | `EditGroupDialog` | `ui/components/dialog/GroupsDialog.kt` | Consolidate with Create |
| 4 | Extract | `DeleteGroupDialog` | `ui/components/dialog/GroupsDialog.kt` | Consolidate |
| 5 | Extract | `ThemeModeDialog` | `ui/components/dialog/ThemeModeDialog.kt` | Reusable dialog |

### High Priority

| # | Action | File | Target Location | Reason |
|---|--------|------|-----------------|--------|
| 6 | Extract | `StatusCard` | `ui/components/StatusCard.kt` | Reusable dashboard component |
| 7 | Extract | `GroupSelectorCard` | `ui/components/GroupSelectorCard.kt` | Reusable selector |
| 8 | Extract | `ExportActionsBottomSheet` | Refactor to use `ActionBottomSheet` | Reduce duplication |
| 9 | Make Public | `AppIcon` | `AppListItem.kt` | Already reusable pattern |
| 10 | Make Public | `DefaultBadge` | `GroupCard.kt` | Could be reused |

### Medium Priority

| # | Action | Reason |
|---|--------|--------|
| 11 | Add navigation argument validation in `GroupSpoofingViewModel` | Validate groupId exists |
| 12 | Consolidate `LabeledValue` and `ReadOnlyValueRow` | Both do similar things |
| 13 | Consider adding Diagnostics as 4th bottom nav tab | If usage increases |
| 14 | Split `GroupSpoofingScreen.kt` (592 lines) | Single Responsibility |

### Low Priority

| # | Action | Reason |
|---|--------|--------|
| 15 | Add navigation result handling mechanism | For passing data back |
| 16 | Persist full navigation state | Across process death |
| 17 | Add navigation tests | Verify deep links, back stacks |

---

## 7. Proposed Target Structure

### Recommended Folder Structure

```
app/src/main/kotlin/com/astrixforge/devicemasker/
├── DeviceMaskerApp.kt
├── data/
│   ├── ConfigSync.kt
│   ├── ISettingsDataStore.kt
│   ├── SettingsDataStore.kt
│   ├── XposedPrefs.kt
│   ├── models/
│   │   ├── InstalledApp.kt
│   │   └── TypeAliases.kt
│   └── repository/
│       ├── AppScopeRepository.kt
│       ├── IAppScopeRepository.kt
│       ├── ISpoofRepository.kt
│       └── SpoofRepository.kt
├── service/
│   ├── ConfigManager.kt
│   ├── IConfigManager.kt
│   ├── IDiagnosticsProvider.kt
│   ├── ILogManager.kt
│   ├── IServiceClient.kt
│   ├── LogManager.kt
│   ├── ServiceClient.kt
│   ├── DefaultDiagnosticsProvider.kt
│   └── diagnostics/
│       └── (all diagnostics files)
├── ui/
│   ├── MainActivity.kt
│   ├── theme/
│   │   ├── Theme.kt
│   │   ├── Color.kt
│   │   ├── Typography.kt
│   │   ├── Shapes.kt
│   │   ├── Motion.kt
│   │   └── ThemeMode.kt           ← MOVED HERE from screens/
│   ├── navigation/
│   │   └── (all navigation files)
│   ├── components/
│   │   ├── GroupCard.kt
│   │   ├── AppListItem.kt
│   │   ├── ActionBottomSheet.kt
│   │   ├── EmptyState.kt
│   │   ├── IconCircle.kt
│   │   ├── ScreenHeader.kt
│   │   ├── SettingsItem.kt
│   │   ├── StatCard.kt
│   │   ├── SpoofValueCard.kt
│   │   ├── ValueRow.kt
│   │   ├── StatusCard.kt          ← NEW (extracted from HomeScreen)
│   │   ├── GroupSelectorCard.kt   ← NEW (extracted from HomeScreen)
│   │   ├── CorrelatedSpoofItem.kt ← MOVED from groupspoofing/items
│   │   ├── IndependentSpoofItem.kt ← MOVED from groupspoofing/items
│   │   └── dialog/
│   │       ├── CountryPickerDialog.kt
│   │       ├── TimezonePickerDialog.kt
│   │       ├── StandardDialogs.kt
│   │       ├── GroupsDialog.kt     ← NEW (Create/Edit/Delete)
│   │       ├── ThemeModeDialog.kt  ← NEW (extracted from SettingsScreen)
│   │       └── ExportDialog.kt    ← NEW (refactored from SettingsScreen)
│   ├── components/expressive/
│   │   └── (all expressive files - good as-is)
│   ├── screens/
│   │   ├── home/
│   │   │   ├── HomeScreen.kt      (simplified, uses extracted components)
│   │   │   ├── HomeState.kt
│   │   │   └── HomeViewModel.kt
│   │   ├── groups/
│   │   │   ├── GroupsScreen.kt     (simplified, uses dialogs from components/)
│   │   │   ├── GroupsState.kt
│   │   │   └── GroupsViewModel.kt
│   │   ├── groupspoofing/
│   │   │   ├── GroupSpoofingScreen.kt (consider splitting)
│   │   │   ├── GroupSpoofingState.kt
│   │   │   ├── GroupSpoofingViewModel.kt
│   │   │   ├── categories/
│   │   │   ├── model/
│   │   │   └── tabs/
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt   (simplified, uses extracted components)
│   │   │   ├── SettingsState.kt
│   │   │   └── SettingsViewModel.kt
│   │   └── diagnostics/
│   │       ├── DiagnosticsScreen.kt
│   │       ├── DiagnosticsState.kt
│   │       └── DiagnosticsViewModel.kt
│   └── utils/
│       └── ImageUtils.kt
```

---

## Summary Statistics

| Metric | Current | Recommended |
|--------|---------|-------------|
| Total Kotlin files | ~75 | ~85 (after extraction) |
| Files in wrong location | 1 | 0 |
| Inline composables needing extraction | 10+ | 0 |
| Duplicate patterns | 4 | 1 (consolidated) |
| ViewModels | 5 | 5 (no change) |
| Navigation destinations | 5 | 5 (no change) |

---

## Compliance Checklist

| Best Practice | Status | Notes |
|--------------|--------|-------|
| Clear layer separation (data/service/ui) | ✅ Pass | Good dependency direction |
| Interface segregation | ✅ Pass | All interfaces properly defined |
| Single Responsibility | ✅ Pass | Most classes do one thing |
| State exposed as StateFlow | ✅ Pass | Immutable state exposure |
| Navigation 3 usage | ✅ Pass | Modern Navigation 3 |
| Material 3 theming | ✅ Pass | Expressive components in use |
| Hilt DI usage | ✅ Pass | ViewModels use @HiltViewModel |
| Reusable components | ⚠️ Partial | Some inline, needs extraction |
| Code duplication | ⚠️ Moderate | 4 patterns identified |

---

## Appendix: Reference Documentation

- [Android Build Structure](https://developer.android.com/build/android-build-structure)
- [Navigation 3 Documentation](https://developer.android.com/navigation)
- [Jetpack Compose Patterns](https://developer.android.com/develop/ui/compose)
- [Material 3 Design](https://m3.material.io/)

---

*End of Report*