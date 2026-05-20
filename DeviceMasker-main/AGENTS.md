# Device Masker — Agent Guide

Android LSPosed/libxposed module for per-app device identity spoofing. Gradle modules: `:app` (Compose UI + config), `:common` (shared contracts + generators), `:xposed` (hooks running inside target app processes), and `:verifier` (local validation target app).

## Permanent Rules

- Config delivery is RemotePreferences-first. Do not add custom AIDL/Binder config or hook-evidence paths.
- `SharedPrefsKeys.kt` is the only source for RemotePreferences key names.
- `JsonConfig.appConfigs` is canonical for app scope; `SpoofGroup.assignedApps` is legacy/display-only.
- `AppConfig.isEnabled` is standalone app-level enablement; do not reset it during group assignment or unassignment.
- LSPosed scoped-app UI must read libxposed service scope data and use scoped package metadata; do not derive a "scoped apps" list from spoof groups or full-scan installed apps on Home startup.
- Runtime sync must use explicit app-to-group assignment from `JsonConfig.appConfigs`; do not use default-group fallback for hook eligibility.
- Dirty RemotePreferences sync must still publish the current `enabled_apps` app-config package set/config version and use explicit `commit()` result checks.
- Xposed target selection must require the current `enabled_apps` app-config package set plus the per-package enabled key; a stale `app_enabled_*` key alone must never activate hooks.
- Generated identity values are created in app/common config flows, never inside target-process hooks.
- `:xposed` must return original values for disabled, missing, blank, malformed, unsafe, or unsupported config.
- `:xposed` must not use Timber, Compose, random generation, app-private JSON reads, or hardcoded preference keys.
- Runtime hooks must not use direct Kotlin SAM `.intercept { ... }` callbacks unless release R8/runtime validation explicitly proves it safe.
- App launch and app-side Xposed service connection do not prove hooks work.
- Hook success requires LSPosed/logcat evidence and, where possible, actual spoofed values inside target apps.
- Support bundles must use real captured evidence. Do not export placeholder `{}` snapshots, blank Xposed JSONL by design, or malformed redacted JSONL.
- LSPosed support capture must handle `/data/adb/lspd/log*` as either files or directories and must preserve command manifests for missing paths.
- Target app safety is more important than broad spoof coverage.
- Before any project work, read `docs/AGENTS_PROJECT_RULES.md` and apply those non-negotiable project rules.

## Module Boundaries

| Module | What lives here | Key constraint |
|--------|----------------|----------------|
| `:app` | Compose UI, ViewModels, ConfigManager, ConfigSync, XposedPrefs, diagnostics, root capture | No hook logic. Timber OK here. |
| `:common` | JsonConfig, SpoofType, SharedPrefsKeys, generators, DevicePersona | No Android UI deps. Generators run at config time only. |
| `:xposed` | XposedEntry, all hookers, PrefsHelper, DualLog | **No Timber. No Compose. No random generation.** `libxposed:api` is `compileOnly`. |
| `:verifier` | Local target app for emulator/device validation evidence | Not production app. Keep it simple and machine-readable. |

## Project Architecture

Non-Negotiable: For full app architecture, config flow, diagnostics flow, and module interaction details, Must read `docs/public/ARCHITECTURE.md` befor doing anything.

Root `AGENTS.md` only keeps permanent guardrails. Do not duplicate detailed architecture here; update the architecture doc and Memory Bank when architecture changes.

## Project Structure

```
devicemasker/
├── app/                  :app module — user-facing app, config, diagnostics, root capture
│   └── src/main/kotlin/com/astrixforge/devicemasker/
│       ├── data/             App data access, repositories, RemotePreferences bridge
│       ├── service/          App services, config persistence, logs, diagnostics
│       ├── ui/               Compose UI, navigation, theme, reusable components
│       └── utils/            App utility helpers
│
├── common/               :common module — shared contracts, models, generators
│   └── src/main/
│       ├── kotlin/.../common/
│       │   ├── models/       Shared data models and value objects
│       │   ├── generators/   Config-time identity generators
│       │   ├── diagnostics/  Shared diagnostics schema and redaction
│       │   └── (root)        Shared config contracts, key builders, constants
│
├── xposed/               :xposed module — hooks in target app processes
│   └── src/main/
│       ├── kotlin/.../xposed/
│       │   ├── hooker/       Target-process hook implementations and shared hook helpers
│       │   ├── diagnostics/  Hook-side diagnostics and health tracking
│       │   └── (root)        Module entry, preference reader, logging, deoptimization support
│       └── resources/META-INF/xposed/   libxposed module metadata
│
├── verifier/             :verifier module — local validation target app
│   └── src/main/kotlin/.../verifier/    Runtime evidence reader, writes latest.json
│
├── gradle/               Gradle version catalog and build dependency metadata
├── docs/                 Public docs, internal audits/reports, implementation plans
├── logs/                 Build logs, device evidence, temporary agent/user artifacts, temp build logs file
├── build.gradle.kts      Root Gradle conventions
├── settings.gradle.kts   Gradle module/repository settings
├── lint.xml              Android lint configuration
└── gradle.properties     Gradle/Android build properties
```

### File & Boundaries

| Type | Correct Location | Wrong |
| --- | --- | --- |
| User-facing docs | `docs/public/` | `docs/internal/` |
| Active internal reports/audits | `docs/internal/reports/active/<category>/YYYY-MM-DD/` | project root or `docs/internal/reports/` root |
| Closed internal reports/audits | `docs/internal/reports/closed/<category>/YYYY-MM-DD/` | project root or `docs/internal/reports/` root |
| Implementation plans | `docs/superpowers/plans/` | reports folders or project root |
| Build logs and command output | `logs/build/` | project root, docs, module dirs |
| Device testing logs, logcat, screenshots, captures, exported evidence | `logs/device/` | project root or docs |
| Agent/user temporary check artifacts | `logs/tmp/` | project root or source folders |

All agent-created and user-created build logs, device logs, temporary captures, smoke-test exports, and scratch evidence must stay under `logs/` using the closest matching subfolder. Do not scatter temporary evidence files in the project root.

Report lifecycle:
- Put reports with pending decisions, open remediation, or active analysis in `docs/internal/reports/active/<category>/YYYY-MM-DD/`.
- Move reports to `docs/internal/reports/closed/<category>/YYYY-MM-DD/` only after the decision is recorded or the remediation is complete.
- Public docs should be curated summaries, not raw internal report moves. Keep raw evidence in `docs/internal/reports/` and link/summarize it from `docs/public/` when useful.
- Do not leave report files directly under `docs/internal/reports/`.

## Commands And Rules

Use Windows Gradle wrapper commands from repo root.

```powershell
.\gradlew.bat spotlessApply spotlessCheck detekt --no-daemon
.\gradlew.bat :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest --no-daemon
.\gradlew.bat spotlessCheck detekt lint test assembleDebug --no-daemon
.\gradlew.bat spotlessCheck detekt :common:testDebugUnitTest :app:testDebugUnitTest :xposed:testDebugUnitTest lint assembleRelease :app:assembleCiRelease :verifier:assembleDebug --no-daemon
.\gradlew.bat detektBaseline --no-daemon
```

Rules:
- Format with Spotless; do not hand-format unrelated code.
- Run Spotless and Detekt together after Kotlin/Compose changes.
- Run module tests for touched modules.
- Run the release/R8 gate before release or hook-safety claims.
- Run `detektBaseline` separately from `detekt`; update baselines only for accepted, documented existing debt.
- Do not claim hook success without LSPosed/logcat evidence and target-app value checks where possible.
- After logs/export changes, inspect a real support ZIP when root/emulator access is available. Required checks: app JSONL parseable, Xposed JSONL parseable/non-empty after hook reproduction, LSPosed logs copied or missing status explained, root command manifests present, and `hook_health.json` meaningful.
- On build failures, find the root cause first; use relevant skills, official docs, Google-developer-knowledge MCP for Android/Google APIs, and web search when current docs are needed.

## Xposed Safety

**Every hook must:**
- Return original value when config is disabled, missing, blank, malformed, or unsafe
- Register callbacks with `intercept(stableHooker { ... })` or explicit named `XposedInterface.Hooker` implementations
- Call `xi.deoptimize(m)` after hooking (prevents ART/JIT inlining from bypassing hook)
- Use `safeHook()` isolation — one failed method never blocks other hooks
- Skip abstract methods before calling `xi.hook(m)`

**Never do in `:xposed`:**
- Generate random/fallback identifiers
- Use Timber (use `DualLog` instead)
- Read app-private JSON files
- Hardcode RemotePreferences key strings
- Mutate `chain.args` in place — it's immutable. Copy and call `chain.proceed(Object[])`
- Use direct Kotlin SAM intercept callbacks; release R8 caused `AbstractMethodError` from that callback shape
- Re-enable global `Class.forName` / `ClassLoader.loadClass` hooks without per-app kill switch

**libxposed API 101 specifics:**
- Lifecycle: `onModuleLoaded` → `onPackageReady` (app hooks) / `onSystemServerStarting` (system_server setup)
- `chain.thisObject` can be `null` for static methods
- `HookFailedError` extends `XposedFrameworkError` extends `Error` — rethrow before generic `Throwable` catches
- Legacy XposedBridge patterns compile but fail silently at runtime — always load the libxposed skill first (`.agents/skills/libxposed/SKILL.md`)

## Runtime Validation

Before claiming hooks work:
- Run the relevant unit/static tests and build/install the APK variant being validated.
- For release/R8 claims, validate the minified release APK with R8 enabled.
- LSPosed scope must include `android`, `system`, and the target app.
- Force-stop and relaunch target apps after any scope/module/config change.
- Check LSPosed/logcat for module load, target selection, hook registration, and spoof events.
- Verify actual spoofed values inside target apps when possible, not only app launch or service connection.
- Treat support bundle hook health as parsed log evidence, not target-value proof. It helps debug backend/hook state but does not replace verifier or target-app value checks.
- After hook safety or R8 callback changes, run the R8 hook ABI guard and validate more than one target app before stability claims.

## Module Guides

Non-negotiable rule: read the per-module rules befor read/write there files/folder:

- `app/AGENTS.md` — Compose UI, ViewModels, navigation, diagnostics, build config
- `common/AGENTS.md` — data models, generators, SharedPrefsKeys, config contracts
- `xposed/AGENTS.md` — hookers, hook patterns, anti-detection, ProGuard, metadata
- `verifier/AGENTS.md` — local Android target-app value checks and evidence capture

## Skills

Non-Negotiable rule: Load the `libxposed` skill before any Xposed work: `.agents/skills/libxposed/SKILL.md` becouse all you Know about lsposed/xposed/libxposed framework/api are out-dated/old, so this skill have the latest official docs and raw github clone repos and javadoc, full imformation so befor any xposed work load/read the skill first.

Other available skills: `claude-android-ninja`, `edge-to-edge`, `material-3-expressive`, `navigation-3`, `r8-analyzer`, `android-cli`, `compose`, `jetpack-compose`

Must use all of these skills accourding to there job never skip. 

## MCP Usage

- Use `Google-developer-knowledge` MCP for Android, Google, Material 3 Expressive, Firebase, Play, Web, or Google Cloud and all the google documentations. this is best for that things, dont use context7 for these.
- Use `mobile_mcp` for manual device control, UI/visual checks, and manual changes that cannot be done with commands.
- Use `context7` for current library/framework/API documentation before changing code that depends on external APIs.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **DeviceMasker** (6723 symbols, 14442 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/DeviceMasker/context` | Codebase overview, check index freshness |
| `gitnexus://repo/DeviceMasker/clusters` | All functional areas |
| `gitnexus://repo/DeviceMasker/processes` | All execution flows |
| `gitnexus://repo/DeviceMasker/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
