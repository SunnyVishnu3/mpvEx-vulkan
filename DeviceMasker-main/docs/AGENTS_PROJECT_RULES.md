# Agent Project Rules

These rules are mandatory for agents working in Device Masker. They cover code changes, runtime validation, evidence handling, report organization, and device UI work.

## First Checks

- Read the relevant `AGENTS.md` file before editing a module.
- For Xposed work, load `.agents/skills/libxposed/SKILL.md` first.
- Keep the change scoped to the user request.
- Do not add baseline debt casually. Detekt baselines should stay empty.

## Code Shape

- Keep functions, composables, interfaces, and files small enough to explain quickly.
- Split by real responsibility, not by random helper extraction.
- Use data tables, maps, sets, or small helpers for pure mapping logic.
- Avoid large `when`/`if` piles when the data shape can remove branches.
- Put public or reusable helper types in files matching their declarations.
- Build long JSON, command text, manifests, and assertion text through small helpers.
- Keep compatibility facades narrow; new workflow code should depend on smaller interfaces.

## Config Scope Correctness

- `JsonConfig.appConfigs` is the canonical source for app assignment, app enablement, group app counts, and UI checked state.
- `SpoofGroup.assignedApps` is legacy/display compatibility only. Do not use it for new runtime, sync, toggle, count, or hook-scope decisions.
- `AppConfig.isEnabled` is standalone app-level spoof enablement. Group assignment and unassignment must preserve it unless the user explicitly toggles app enablement.
- UI that claims to show LSPosed scoped apps must read LSPosed/libxposed scope data and join it with scoped package metadata. Home must use the scoped metadata fast path and must not full-scan installed apps on startup.
- LSPosed scope alone is display/selection input, not runtime hook eligibility.
- Runtime RemotePreferences sync must require an explicit enabled `AppConfig.groupId` that resolves to an enabled group. Do not let default-group fallback make an unassigned package hookable.
- Dirty RemotePreferences sync may rewrite only changed package keys, but it must still publish the current `SharedPrefsKeys.KEY_ENABLED_APPS` app-config package set/config version and preserve explicit `commit()` success checks.
- Xposed target selection must require both current `SharedPrefsKeys.KEY_ENABLED_APPS` app-config package-set membership and the per-package enabled key. A stale `app_enabled_*` boolean is not proof of current assignment.
- Hook-family policy keys should follow the enabled spoof types for ordinary value hook families, while app-wide anti-detection/package-manager policy remains explicit and conservative.

## Constants

- Name every domain number.
- Raw numbers are acceptable only for obvious zero/one math, tiny local indexes, or API calls where the meaning is already clear.

## Naming

Rules: `ParameterNaming`, `MatchingDeclarationName`, `ObjectPropertyNaming`

Write code like this:
- Callback parameters can be noun/action style: `timezoneSelected`, `groupSelected`.
- Put public data classes in files matching their declaration name.
- Use backing state names like `serviceConnectedState`, not `_isServiceConnected`.

Avoid:
- Public helper types hidden inside unrelated files.
- Leading underscore properties in Kotlin objects.
- Callback parameter names that Detekt flags repeatedly.

## Android API Levels

- New platform APIs need both runtime SDK guards and local API annotations such as `@RequiresApi`.
- Keep API-gated helpers small so Lint can prove the guarded call path.

## Error Handling

- Prefer specific exceptions for file, JSON, parsing, and serialization failures.
- Fallbacks must be logged with useful context.
- Do not write silent recovery like `catch (Exception) { false }`.
- Broad catches are allowed only at real safety boundaries and must document why recovery is safe.

## Logs And Evidence

- Put build logs in `logs/build/`.
- Put device/logcat evidence in `logs/device/`.
- Put scratch checks in `logs/tmp/`.
- Do not place temporary logs or evidence in the project root.

## Support Bundle Contract

- Support bundles are debugging evidence, not decoration. Do not add placeholder files that look authoritative but contain fake `{}` data or intentionally blank streams.
- App and Xposed event exports must remain valid JSONL after redaction. If a line is structured, redact through the structured event model and re-encode it.
- Hook-side evidence is owned by LSPosed/logcat. The app may parse copied LSPosed/logcat lines into `xposed/xposed_events.jsonl`, but must not add a custom Binder/AIDL hook-evidence path.
- LSPosed root capture must handle `/data/adb/lspd/log` and `/data/adb/lspd/log.old` as either files or directories. Missing paths must be explicit in the copied artifact and manifest.
- Root/logcat capture must include sidecar manifests with command, status, exit code, timeout, byte counts, and root availability. Blank artifacts need an explanation in the artifact, not silent emptiness.
- `diagnostics/hook_health.json` must be either real derived evidence or absent by design. Do not ship `{}` as a health result.
- When root/emulator access is available, validate logs/export work against a real generated ZIP, not only unit tests.
- Support bundle hook evidence proves module/backend behavior only. It does not prove final target-observed spoof values unless verifier or target-app value evidence is included.

## Device UI Work

- Use Mobile MCP for manual emulator/device UI work: LSPosed scope changes, Device Masker UI configuration, target app selection, permission dialogs, app launches that need visual confirmation, and visual screen checks.
- Use shell/ADB for command work: builds, installs, package checks, logcat, file pulls, force-stops, and reproducible evidence capture.
- Prefer LSPosed and Device Masker UI flows for scope/config changes. Do not edit LSPosed databases or app config files directly unless the user explicitly asks and the backup/restore path is documented.
- Open LSPosed through the notification panel or another non-destructive entrypoint. Do not reinstall or modify LSPosed itself unless explicitly requested.

## Public Validation Folder

- Keep the public validation package under `docs/public/validation/`.
- The main public status file is `docs/public/validation/DEVICE_MASKER_VALIDATION_STATUS.md`.
- Put copied public evidence under `docs/public/validation/evidence/<device-kind>/<android-version>/`.
- Use `emulator/android-16`, `emulator/android-13`, `real-device/android-16`, and `real-device/android-13` subfolders unless a new platform has explicit evidence.
- Do not put raw scratch logs directly in `docs/public/validation/`; use the matching evidence subfolder.
- Public validation files must clearly separate emulator and real-device evidence. Never infer real-device status from emulator evidence.
- Empty/pending real-device sections must keep blank tables and placeholder READMEs; do not invent values.
- When copying evidence into `docs/public/validation/evidence/`, preserve the matching source under `logs/` and keep public evidence names stable, such as `latest.json`, `logcat.txt`, `build-gate.txt`, and `config.json`.
- Raw internal reports stay under `docs/internal/reports/active/` or `docs/internal/reports/closed/`; public docs should summarize or link to them, not replace them.

## Internal Report Organization

- All internal report, audit, research, validation, and summary files must live under `docs/internal/reports/`.
- Use this exact path shape for every internal report:

```text
docs/internal/reports/<active|closed>/<category>/YYYY-MM-DD/YYYY-MM-DD-short-topic-kebab-case.md
```

- `active` is for reports with pending decisions, open remediation, unfinished validation, or still-useful investigation state.
- `closed` is for reports whose decision is recorded, remediation is complete, validation is superseded, or the report is retained only as history.
- `active` and `closed` must have the same category names. Do not invent a category in only one lifecycle folder.
- Approved categories are:
  - `audits`: code reviews, architecture audits, UI audits, Detekt audits, CI/CD audits, performance audits, and libxposed audits.
  - `validation`: emulator/device runtime tests, verifier matrices, Android-version evidence, R8 smoke tests, build-gate reports, crash-validation reports, and value-check reports.
  - `research`: Android behavior research, Xposed/libxposed research, native hook research, proc-maps research, tool evaluation, and decision-heavy investigations.
  - `summaries`: project-wide summaries, implementation completion reports, wrap-up reports, lifecycle recommendations, and final state summaries.
- If a report fits more than one category, choose by primary output, not by the words in its title:
  - Defect, risk, regression, architecture, code-quality, or review findings -> `audits`.
  - Runtime proof, pass/fail result, device/emulator evidence, logs, build gate, crash result, or value matrix -> `validation`.
  - External facts, platform/API behavior, tool comparison, feasibility analysis, options, or decision input -> `research`.
  - Final state, implementation completion, lifecycle result, release note, or wrap-up -> `summaries`.
- Keep `audits` and `research` separate. Do not merge research into audits:
  - Use `audits` when the report judges current project code, design, docs, tests, or workflow.
  - Use `research` when the report investigates what is possible or what approach/tool/platform behavior should guide future work.
- If one user request asks for both research and audit:
  - Create one report when the user asks for one report or when a project-local report skill is executing.
  - Classify the report by primary output: `research` when external facts drive the answer, `audits` when project findings drive the answer.
  - Include clear internal sections such as `Research Inputs` and `Audit Findings` when both sides are substantial.
  - Create separate reports only when the user explicitly asks for separate files or the work is split into separate tasks.
- Report filenames must follow the Superpowers-style date-first kebab-case pattern:

```text
YYYY-MM-DD-short-topic-kebab-case.md
```

- Do not use uppercase report filenames, spaces, underscores, date-at-end names, or punctuation-heavy titles for new report files.
- Date folders and filename dates must match the report creation date or the evidence date being reported. If those differ, prefer the evidence date and state the creation date inside the report.
- Do not leave report files directly under `docs/internal/reports/active/`, `docs/internal/reports/closed/`, or `docs/internal/reports/`.
- When moving an active report to closed, preserve its category and date folder unless the report was categorized incorrectly. Move, do not duplicate.
- Public validation files remain under `docs/public/validation/`; do not move public status matrices into internal reports.
- Build logs, logcat, screenshots, raw captures, and scratch evidence remain under `logs/`; reports may reference them but must not scatter raw evidence through report folders.

## Verification

- Run Spotless for formatting; do not hand-format unrelated code.
- Run Detekt after Kotlin or Compose changes.
- After `detektBaseline`, run `detekt` and count non-empty baseline IDs.
- Run tests for touched modules.
- Before release or R8 claims, run the full gate and the Xposed R8 ABI guard.
- Do not claim hook success without LSPosed/logcat evidence and target-app value checks where possible.
- Do not mark WebView UA coverage complete unless both static default UA and instance `WebSettings.getUserAgentString()` are spoofed in a target app.

## Hard Stop

If a change requires adding Detekt baseline entries, broad Xposed behavior, a new config delivery path, or a custom Binder/AIDL hook-evidence path, stop and document the reason before implementing.
