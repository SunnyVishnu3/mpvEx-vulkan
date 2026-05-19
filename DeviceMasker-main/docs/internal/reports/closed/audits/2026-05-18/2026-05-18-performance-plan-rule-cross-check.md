# Performance Plan Rule Cross-Check

Date: 2026-05-18

## Findings

### Medium 1. `xposed/AGENTS.md` documents a `PrefsHelper.buildHookConfigSnapshot()` API that does not exist

Evidence:
- `xposed/AGENTS.md:99` says `PrefsHelper` is the low-level RemotePreferences reader and snapshot builder.
- `xposed/AGENTS.md:106` lists `buildHookConfigSnapshot(prefs, pkg)`.
- Actual code builds snapshots with `HookConfigSnapshot.fromPrefs(prefs, hookPackage)` in `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/XposedEntry.kt:144`.
- Actual snapshot API is `HookConfigSnapshot.fromPrefs(...)` in `xposed/src/main/kotlin/com/astrixforge/devicemasker/xposed/HookConfigSnapshot.kt:24`.
- `rg` found no `buildHookConfigSnapshot` implementation under `xposed/src/main/kotlin` or `xposed/src/test/kotlin`.

Problem:
The rule text is slightly stale. It points future agents toward a helper that is not present. This is a brain-damaged documentation API: the concept is correct, but the named call path is wrong.

Root cause:
The implementation plan originally expected snapshot construction helpers in `PrefsHelper`, but final code kept the builder on `HookConfigSnapshot`.

Fix direction:
Update `xposed/AGENTS.md` to say:

```text
`PrefsHelper` is the low-level RemotePreferences reader. `HookConfigSnapshot.fromPrefs(prefs, pkg)` builds the per-package value/persona snapshot. Hot value callbacks should use `HookConfigSnapshot`, not repeat preference reads.
```

Remove the `buildHookConfigSnapshot(prefs, pkg)` table row unless that function is actually added.

Verification:
Run `rg -n "buildHookConfigSnapshot|HookConfigSnapshot.fromPrefs" xposed/AGENTS.md xposed/src/main/kotlin xposed/src/test/kotlin` and confirm docs name the real API.

### Low 1. The implementation plan tech stack lists stale kotlinx serialization version

Evidence:
- Plan line `docs/superpowers/plans/2026-05-18-performance-optimization-and-warning-cleanup.md:9` lists `kotlinx.serialization 1.10.0`.
- Current catalog uses `serializationJson = "1.11.0"` in `gradle/libs.versions.toml:27`.

Problem:
This is not a runtime rule violation, but it is stale plan metadata. It can mislead later dependency/debugging work.

Root cause:
The plan header was not updated after the version catalog changed.

Fix direction:
Update the plan header from `kotlinx.serialization 1.10.0` to `kotlinx.serialization 1.11.0`, or remove exact dependency versions from old implementation-plan prose if the catalog is the real source of truth.

Verification:
Run `rg -n "serialization 1.10.0|serializationJson" docs/superpowers/plans/2026-05-18-performance-optimization-and-warning-cleanup.md gradle/libs.versions.toml`.

## Executive Summary

The important architecture/rule updates are mostly correct and internally consistent. The plan, root `AGENTS.md`, `docs/AGENTS_PROJECT_RULES.md`, public architecture, Memory Bank, and module guides agree on the big data-shape changes:

- Home scoped-app UI must use LSPosed scope plus scoped package metadata, not spoof groups, `appConfigs`, or a full installed-app scan.
- Dirty RemotePreferences sync must still publish current `enabled_apps`, config version, and explicit `commit()` result checks.
- Runtime hook eligibility stays tied to canonical `JsonConfig.appConfigs`, explicit group assignment, app enablement, enabled group, and enabled values.
- Xposed value hookers should read a per-package `HookConfigSnapshot` instead of repeatedly reading RemotePreferences in hot callbacks.
- Hook success is still not claimed from this cleanup; Memory Bank correctly limits proof to local unit/static/build/R8 evidence.

The only concrete correction needed is the stale `PrefsHelper.buildHookConfigSnapshot()` wording. That is not a code bug, but it is a documentation landmine for future agents.

## Scope

Reviewed:
- `docs/superpowers/plans/2026-05-18-performance-optimization-and-warning-cleanup.md`
- `AGENTS.md`
- `docs/AGENTS_PROJECT_RULES.md`
- `docs/public/ARCHITECTURE.md`
- `app/AGENTS.md`
- `common/AGENTS.md`
- `xposed/AGENTS.md`
- `verifier/AGENTS.md`
- Memory Bank files under `memory-bank/`
- Relevant current code references found with `rg`

## Source Inventory

Key alignment evidence:
- Root scoped-app and dirty-sync rules: `AGENTS.md:11` through `AGENTS.md:14`.
- Project rules for scoped metadata, explicit assignment, dirty sync, and allowlist checks: `docs/AGENTS_PROJECT_RULES.md:24` through `docs/AGENTS_PROJECT_RULES.md:31`.
- Architecture Home scoped-app flow: `docs/public/ARCHITECTURE.md:146` through `docs/public/ARCHITECTURE.md:168`.
- Architecture snapshot/logging flow: `docs/public/ARCHITECTURE.md:174` through `docs/public/ARCHITECTURE.md:220`.
- App module guide for `syncPackages()`, `loadScopedApps()`, `AppIconCache`, and lifecycle `SavedStateHandle`: `app/AGENTS.md:50` through `app/AGENTS.md:57`, `app/AGENTS.md:104` through `app/AGENTS.md:109`, and `app/AGENTS.md:123`.
- Common module guide still preserves canonical `appConfigs`, `SharedPrefsKeys`, config-time generation, and no default-group fallback: `common/AGENTS.md:20` through `common/AGENTS.md:25`.
- Verifier guide correctly documents narrow private API reflection and lint-clean manifest expectations: `verifier/AGENTS.md:29` through `verifier/AGENTS.md:34`.
- Memory Bank records the new performance work and evidence boundary: `memory-bank/projectbrief.md:49` through `memory-bank/projectbrief.md:56`, `memory-bank/progress.md:30` through `memory-bank/progress.md:37`, and `memory-bank/activeContext.md:18` through `memory-bank/activeContext.md:27`.

## Project Rule Violations

No high-severity project rule violation found in the reviewed rule set.

The new rules are not random churn. They describe real implementation changes and close previous data-shape problems:
- Home scoped-app loading now has the correct input shape.
- Dirty sync avoids broad rewrites while preserving allowlist/version correctness.
- Xposed hot paths have a snapshot boundary.

The stale `PrefsHelper` API wording should be fixed before this report is closed.

## AGENTS.md And Rule Drift Audit

Correct:
- Root `AGENTS.md` now has concise permanent guardrails instead of duplicating all architecture detail.
- `docs/AGENTS_PROJECT_RULES.md` carries the deeper config-scope correctness rules.
- `app/AGENTS.md` matches the app-side implementation shape for scoped metadata, dirty sync hints, icon cache, and factory-owned `SavedStateHandle`.
- `common/AGENTS.md` remains consistent with source-of-truth model boundaries.
- `verifier/AGENTS.md` correctly scopes verifier private API reflection to evidence collection.

Needs correction:
- `xposed/AGENTS.md` should not document nonexistent `PrefsHelper.buildHookConfigSnapshot()`.

## Best Solution Direction

Patch only `xposed/AGENTS.md` and the plan tech-stack line. Do not refactor code to satisfy stale docs. The current code shape is simpler: `HookConfigSnapshot.fromPrefs()` owns snapshot construction directly.

## Verification Plan

1. Fix `xposed/AGENTS.md` wording -> verify: `rg -n "buildHookConfigSnapshot" xposed/AGENTS.md` returns nothing.
2. Fix plan serialization version -> verify: plan line matches `gradle/libs.versions.toml`.
3. Optional docs-only check -> verify: `rg -n "full-scan|scoped metadata|syncPackages|HookConfigSnapshot|enabled_apps" AGENTS.md docs/AGENTS_PROJECT_RULES.md docs/public/ARCHITECTURE.md app/AGENTS.md xposed/AGENTS.md memory-bank`.

No Gradle build is required for these docs-only fixes.

## Residual Risks And Unknowns

- I did not rerun Gradle gates; this was a documentation/rule cross-check.
- I did not validate target-device LSPosed behavior. The existing Memory Bank correctly says the 2026-05-18 work has local unit/static/build/R8 proof only.
- The working tree is broad and dirty, so rule correctness does not imply the whole implementation is ready to commit.

## Suggested Next Tasks

1. Patch the two documentation mismatches above.
2. Re-run `gitnexus detect_changes(scope=all)` before any commit, because the project rules require it and the current change set is broad.
3. Run fresh target-device LSPosed validation before making any new hook-success claim.

## Report File Path

`docs/internal/reports/active/audits/2026-05-18/2026-05-18-performance-plan-rule-cross-check.md`

## Write Boundary Confirmation

Only this report file was written for the audit. No source files, existing docs, Memory Bank files, commits, pushes, or build artifacts were modified by this review workflow.
