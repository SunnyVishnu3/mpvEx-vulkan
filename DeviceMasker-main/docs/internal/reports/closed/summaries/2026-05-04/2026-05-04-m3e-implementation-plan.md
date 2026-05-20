# Material 3 Expressive (M3E) Implementation Plan

**Date:** 2026-05-04  
**Target Version:** androidx.compose.material3:material3:1.5.0-alpha18  
**Current Version:** 1.4.0  
**Project:** Device Masker  
**Intensity Level:** Foundational → Excellent (gradual)  

---

## Executive Summary

This plan details migrating Device Masker's UI from standard Material 3 to **Material 3 Expressive (M3E)** using `compose-material3` `1.5.0-alpha18`. The app currently scores **38/100** on M3E compliance. Key gaps: missing emphasized typography, incomplete shape scale, hardcoded colors in schemes, no MotionScheme integration, and custom components that M3E now provides natively.

**Approach:** Incremental, non-breaking migration across 4 phases. No build file changes without explicit user confirmation.

---

## 1. M3E Availability Summary (1.5.0-alpha18)

### New Composables (require `@ExperimentalMaterial3ExpressiveApi`)
| Component | Purpose | Replaces Custom |
|-----------|---------|-----------------|
| `ButtonGroup` | Horizontal button groups with press-expansion | `QuickActionGroup` |
| `SplitButtonLayout` | Primary action + dropdown menu | N/A (new pattern) |
| `FloatingActionButtonMenu` | Expandable FAB with 2-6 actions | N/A (new pattern) |
| `HorizontalFloatingToolbar` / `FloatingToolbar` | Rich action toolbars | N/A (new pattern) |
| `LoadingIndicator` | Expressive loading spinner | `ExpressiveLoadingIndicator` |

### Stable APIs (no opt-in required)
| API | Purpose |
|-----|---------|
| `MotionScheme.standard()` / `MotionScheme.expressive()` | Theme-level animation specs |
| `MaterialExpressiveTheme` | Expressive theme wrapper |
| `expressiveLightColorScheme()` / `darkColorScheme()` | Expressive color schemes |
| `Typography` with 15 emphasized styles | `displayLargeEmphasized` through `labelSmallEmphasized` |
| `MaterialShapes` (30+ `RoundedPolygon`s) | Shape morphing library |
| `WavyProgressIndicator` | Decorative progress indicators |

### Key Breaking Changes (1.4.0 → 1.5.0-alpha18)
- `Scrim()` renamed to `LevitatedPaneScrim()`
- `rememberWithGapSearchBarState` → `rememberSearchBarWithGapState`
- PullToRefreshDefaults renames: `shape` → `indicatorShape`, `containerColor` → `indicatorContainerColor`
- Deprecated experimental `ModalBottomSheet` APIs removed

---

## 2. Theme Compliance Audit (Score: 38/100)

### Color (30/100) — Critical
| Violation | Severity | File | Fix |
|-----------|----------|------|-----|
| Hardcoded `Color(0xFF...)` inside ColorScheme constructors | Critical | `Theme.kt` lines 32, 38, 44, 57, 64-65, 83, 89, 95, 188, 193, 198, 200-211 | Extract to named constants in `Color.kt` |
| `LightColorScheme` missing surface container roles | Critical | `Theme.kt` lines 71-96 | Add `background`, `surface`, `surfaceVariant`, all `surfaceContainer*` roles, `outline`, `inverse*` |
| AMOLED black suppresses tonal elevation | High | `Theme.kt` lines 47-50 | Document as intentional deviation or use near-black tones |

### Shape (35/100) — High
| Violation | Severity | File | Fix |
|-----------|----------|------|-----|
| Only 5 of 10 M3E corner steps defined | High | `Shapes.kt` lines 8-24 | Add `none`, `largeIncreased` (20dp), `extraLargeIncreased` (32dp), `extraExtraLarge` (48dp), `full` |
| No asymmetric / inner-corner shapes | Medium | `Shapes.kt` | Add `extraSmallTop`, `largeStart`, `largeEnd`, etc. |

### Typography (25/100) — Critical
| Violation | Severity | File | Fix |
|-----------|----------|------|-----|
| Missing 15 emphasized type styles | Critical | `Typography.kt` line 150 | Add `displayLargeEmphasized` through `labelSmallEmphasized` with higher weights |
| No variable font axes | Medium | `Typography.kt` | Evaluate Roboto Flex for hero moments (optional) |

### Motion (45/100) — High
| Violation | Severity | File | Fix |
|-----------|----------|------|-----|
| Custom springs don't match M3E token values | High | `Motion.kt` lines 53-59 | Adopt `MotionScheme.expressive()` or match exact damping/stiffness |
| No spatial/effects speed variants | Medium | `Motion.kt` | Organize into `default/fast/slow` × `spatial/effects` hierarchy |
| **Positive:** Honors system animation scale | — | `Motion.kt` line 30 | Keep `rememberMotionPolicy()` |

### Elevation (40/100) — Medium
| Violation | Severity | File | Fix |
|-----------|----------|------|-----|
| No `surfaceColorAtElevation()` usage | Medium | Theme/components | Replace hardcoded dark grays with `surfaceColorAtElevation()` |
| Raw `tonalElevation` dp values | Low | Components | Use elevation level tokens (Level 0=0dp, 1=1dp, 2=3dp, 3=6dp, 4=8dp, 5=12dp) |
| AMOLED black breaks elevation model | High | `Theme.kt` | Document as intentional deviation |

---

## 3. Component Migration Plan

### High Priority (Replace with M3E Built-ins)

#### 3.1 `ExpressiveLoadingIndicator` → `LoadingIndicator`
**Rationale:** M3E provides `LoadingIndicator` natively with contained/uncontained variants.

**Migration steps:**
1. Replace `CircularProgressIndicator` calls with `LoadingIndicator`
2. Use `contained` variant inside surfaces, `uncontained` for inline
3. Size: 48dp default
4. Color: inherits `primary` from theme

**Estimated effort:** 1-2 hours  
**Files to touch:** `ExpressiveLoadingIndicator.kt`, `ExpressivePullToRefresh.kt`

#### 3.2 `QuickActionGroup` → `ButtonGroup`
**Rationale:** M3E `ButtonGroup` implements press-expansion and overflow natively.

**Migration steps:**
1. Replace custom `Row` of `FilledTonalButton`s with `ButtonGroup`
2. Use `ButtonGroupScope` for items
3. Configure `overflowIndicator` for collapsed items
4. Spacing: 8dp between items (M3E default)

**Estimated effort:** 2-3 hours  
**Files to touch:** `QuickActionGroup.kt`, callers in screens

### Medium Priority (Align Tokens, Keep Custom)

#### 3.3 `ExpressiveSwitch`
**Decision:** Keep custom. M3E does not provide a new Switch.  
**Alignment:** Update spring spec to match `md.sys.motion.spring.fast.spatial` (damping 0.9, stiffness 1400).

#### 3.4 `ToggleButton`
**Decision:** Deprecate and remove. Duplicate of `ExpressiveSwitch`.  
**Migration:** Move callers to `ExpressiveSwitch` or standard M3 `Switch`.  
**Estimated effort:** 1 hour

#### 3.5 `ExpressiveIconButton`
**Decision:** Keep as wrapper.  
**Alignment:** Ensure default `buttonSize` is 48dp (M3E touch target minimum). Use `md.sys.motion.spring.fast.spatial` for press scale.

#### 3.6 `ExpressiveCard`
**Decision:** Keep as wrapper.  
**Alignment:** Map press-scale to M3E fast spatial spring. Container color `surfaceContainerHigh`, shape `corner.large` already align.

### Low Priority (Keep, Minor Token Updates)

| Component | Action | Token Updates |
|-----------|--------|---------------|
| `ExpressivePullToRefresh` | Keep | Migrate inner indicator to `LoadingIndicator`; use `primary`/`primaryContainer` for indicator colors |
| `MorphingShape` | Keep utility | Use `md.sys.shape.corner` + `md.sys.motion.spring` for transitions |
| `StatusIndicator` | Keep | Pulse: `md.sys.motion.spring.default.spatial`; semantic colors → `error`/`primary`/`tertiary` |
| `SectionHeader` | Keep | Typography already aligned; chevron rotation uses M3E spring |
| `AnimatedSection` | Keep | Expand/collapse uses M3E spring |
| `ActionBottomSheet` | Keep | Already uses standard M3 `ModalBottomSheet`; no changes |
| `SpoofValueCard`, `StatCard` | Keep | Inherit `ExpressiveCard` tokens |
| `ScreenHeader` | Keep or migrate to `CenterAlignedTopAppBar` | `headlineMedium` is correct |

---

## 4. Phase-by-Phase Implementation

### Phase 1: Theme Foundation (No dependency change needed)
**Goal:** Fix critical theme violations without upgrading material3.

1. **Extract hardcoded colors** from `Theme.kt` into named constants in `Color.kt`
2. **Add missing surface container roles** to `LightColorScheme`
3. **Add 15 emphasized typography styles** to `Typography.kt`
4. **Expand shape scale** to all 10 symmetric steps + asymmetric variants
5. **Document AMOLED theme** as intentional M3E deviation

**Estimated effort:** 4-6 hours  
**Risk:** Low — no dependency changes, purely additive/refactoring.

### Phase 2: Motion Token Alignment (No dependency change needed)
**Goal:** Align custom motion specs to M3E token values.

1. **Refactor `AppMotion`** to match M3E spring token hierarchy:
   - `Expressive.spatialDefault` (damping 0.9, stiffness 700)
   - `Expressive.spatialFast` (damping 0.9, stiffness 1400)
   - `Expressive.spatialSlow` (damping 0.9, stiffness 300)
   - `Expressive.effectsDefault` (damping 1.0, stiffness 1600)
   - `Standard.*` equivalents
2. **Update `ExpressiveSwitch`, `ExpressiveIconButton`, `ExpressiveCard`** to use aligned springs
3. **Add elevation level constants** (Level 0-5)

**Estimated effort:** 3-4 hours  
**Risk:** Low — numeric changes only, behavior should feel similar.

### Phase 3: Dependency Upgrade + Component Migration
**Goal:** Upgrade to 1.5.0-alpha18 and migrate to M3E built-ins.

**Prerequisite:** User confirmation to modify `gradle/libs.versions.toml`.

1. **Bump `material3` to `1.5.0-alpha18`** (or add expressive artifact)
2. **Add `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`** where needed
3. **Migrate `ExpressiveLoadingIndicator`** → `LoadingIndicator`
4. **Migrate `QuickActionGroup`** → `ButtonGroup`
5. **Remove `ToggleButton`** and migrate callers
6. **Adopt `MotionScheme.expressive()`** in theme if stable API is available
7. **Test all modified components** across light/dark/AMOLED themes

**Estimated effort:** 6-8 hours  
**Risk:** Medium — alpha dependency, experimental APIs may change.

### Phase 4: Polish & Advanced Features
**Goal:** Leverage advanced M3E features.

1. **Evaluate `SplitButtonLayout`** for export/settings actions
2. **Evaluate `FloatingActionButtonMenu`** for main screen quick actions
3. **Evaluate `HorizontalFloatingToolbar`** for rich editing contexts
4. **Add `MaterialShapes`** for decorative moments (e.g., avatar masks, hero graphics)
5. **Consider Roboto Flex** for editorial/hero type moments
6. **Window size class adaptation** for tablets/foldables

**Estimated effort:** 4-6 hours  
**Risk:** Low — purely additive features.

---

## 5. Token Reference Quick Guide

### Motion Tokens (M3E Spec)
| Token | Damping | Stiffness | Use Case |
|-------|---------|-----------|----------|
| `expressive.default.spatial` | 0.9 | 700 | Default position/size animations |
| `expressive.fast.spatial` | 0.9 | 1400 | Small components (buttons, switches) |
| `expressive.slow.spatial` | 0.9 | 300 | Full-screen animations |
| `expressive.default.effects` | 1.0 | 1600 | Default color/alpha animations |
| `expressive.fast.effects` | 1.0 | 3800 | Quick color/alpha changes |
| `standard.default.spatial` | 1.0 | 380 | Utilitarian position/size |
| `standard.fast.spatial` | 1.0 | 380 | Same as default (standard has less variation) |

### Shape Scale (M3E Spec)
| Token | Value | Use Case |
|-------|-------|----------|
| `none` | 0dp | Full-bleed images, edge-to-edge lists |
| `extra-small` | 4dp | Chips, small buttons |
| `small` | 8dp | Text fields, list items |
| `medium` | 12dp | Cards, dialogs |
| `large` | 16dp | Bottom sheets, navigation drawers |
| `large-increased` | 20dp | Extended components |
| `extra-large` | 28dp | Full-screen dialogs |
| `extra-large-increased` | 32dp | Large containers |
| `extra-extra-large` | 48dp | Hero cards, featured content |
| `full` | 50% | Buttons, chips, pills |

### Elevation Levels (M3E Spec)
| Level | dp | Use Case |
|-------|-----|----------|
| 0 | 0dp | Base surface |
| 1 | 1dp | Elevated buttons, cards |
| 2 | 3dp | Bottom sheets, floating toolbars |
| 3 | 6dp | Dialogs, menus |
| 4 | 8dp | Navigation drawers |
| 5 | 12dp | Modal side sheets |

---

## 6. Testing Checklist

### Theme
- [ ] Light theme renders correctly with new surface container roles
- [ ] Dark theme renders correctly with extracted named colors
- [ ] AMOLED theme still uses pure black (documented deviation)
- [ ] All 30 typography styles (15 baseline + 15 emphasized) accessible
- [ ] Shape scale covers all 10 symmetric steps

### Motion
- [ ] `ExpressiveSwitch` spring feels natural (damping 0.9, stiffness 1400)
- [ ] `ExpressiveIconButton` press scale uses fast spatial spring
- [ ] `ExpressiveCard` press scale uses fast spatial spring
- [ ] Reduced motion fallback works (system animation scale = 0)
- [ ] No janky animations after spring value changes

### Components (Post-Phase 3)
- [ ] `LoadingIndicator` renders in contained and uncontained variants
- [ ] `ButtonGroup` press-expansion works with 2-5 items
- [ ] `ButtonGroup` overflow menu appears when items exceed width
- [ ] `QuickActionGroup` callers work after migration
- [ ] `ToggleButton` callers work after migration to `ExpressiveSwitch`
- [ ] No regression in `ExpressivePullToRefresh`
- [ ] All components work across light/dark/AMOLED themes

### Accessibility
- [ ] Touch targets remain ≥ 48dp
- [ ] Color contrast ≥ 4.5:1 for text, ≥ 3:1 for non-text
- [ ] Reduced motion alternatives functional
- [ ] Screen reader labels preserved

---

## 7. Files to Modify

### Phase 1
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Color.kt` — Add named constants for all hardcoded colors
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Theme.kt` — Extract colors, add surface roles to LightColorScheme
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Typography.kt` — Add 15 emphasized styles
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Shapes.kt` — Expand to 10-step scale + asymmetric variants

### Phase 2
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Motion.kt` — Refactor to M3E token hierarchy
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/ExpressiveSwitch.kt` — Update spring specs
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/ExpressiveIconButton.kt` — Update spring specs, ensure 48dp touch target
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/ExpressiveCard.kt` — Update spring specs
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/ToggleButton.kt` — Deprecate/remove

### Phase 3
- `gradle/libs.versions.toml` — Bump material3 to 1.5.0-alpha18 (requires user confirmation)
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/ExpressiveLoadingIndicator.kt` — Migrate to `LoadingIndicator`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/QuickActionGroup.kt` — Migrate to `ButtonGroup`
- `app/src/main/kotlin/com/astrixforge/devicemasker/ui/components/expressive/ExpressivePullToRefresh.kt` — Update indicator reference
- Various screen files — Update imports for removed/renamed components

### Phase 4
- New files for `SplitButtonLayout`, `FloatingActionButtonMenu`, or `HorizontalFloatingToolbar` usage
- Optional: `app/src/main/kotlin/com/astrixforge/devicemasker/ui/theme/Type.kt` — Roboto Flex integration

---

## 8. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 1.5.0-alpha18 APIs change in future alpha | High | Medium | Isolate experimental API usage behind wrappers; easy to update |
| | | | |
| AMOLED users reject near-black surfaces | Low | Medium | Keep AMOLED as documented deviation; do not force tonal surfaces |
| | | | |
| Spring spec changes affect perceived performance | Medium | Low | A/B test on device; keep old specs as fallback if needed |
| | | | |
| `ButtonGroup` overflow doesn't fit app's use cases | Medium | Medium | Keep custom `QuickActionGroup` as fallback implementation |
| | | | |
| Build issues with alpha dependency | Medium | High | Pin exact version; do not use dynamic alpha versions |

---

## 9. Success Criteria

- Theme compliance score improves from **38/100** to **≥ 75/100**
- All critical and high violations resolved
- No visual regressions in light/dark/AMOLED themes
- `LoadingIndicator` and `ButtonGroup` successfully replace custom equivalents
- All touch targets ≥ 48dp
- Reduced motion fallback functional across all animated components
- Build passes: `spotlessCheck`, `lint`, `test`, `assembleDebug`

---

## Appendix A: M3E Intensity Level Decision

**Selected:** Foundational → Excellent (gradual)

**Rationale:**
- Device Masker is a privacy/security tool, not a content/entertainment app
- Clarity and usability are paramount
- Max 1-2 hero moments per flow (e.g., export success, root grant confirmation)
- Standard navigation patterns must be preserved
- AMOLED-first design limits color expression opportunities

**Hero Moment Candidates:**
1. Root grant success animation (excellent motion + emphasized type)
2. Support bundle export completion (shape morph + color emphasis)

---

## Appendix B: Current vs Target Dependency Versions

| Artifact | Current | Target (Stable) | Target (M3E Alpha) |
|----------|---------|-----------------|-------------------|
| compose-material3 | 1.4.0 | 1.4.0 | **1.5.0-alpha18** |
| compose-bom | 2026.02.01 | 2026.04.01 | 2026.04.01 |
| material3-window-size-class | 1.4.0 | 1.4.0 | 1.5.0-alpha18 |
| material3-adaptive-navigation-suite | — | — | 1.5.0-alpha18 |

**Note:** Upgrading to `1.5.0-alpha18` requires user confirmation and may require BOM update.
