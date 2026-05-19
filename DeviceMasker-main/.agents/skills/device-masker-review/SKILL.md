---
name: device-masker-review
description: Evidence-backed review, audit, and code-review workflow for the Device Masker Android LSPosed/libxposed project that may write exactly one internal audit report file and must not modify any other project files. Use when reviewing or auditing current code, architecture, architecture-improvement opportunities, UI/UX, accessibility, folder structure, AGENTS.md/module-guide correctness, docs, reports, tests, validation evidence, release readiness, hook safety, R8 safety, Android compatibility, stale Memory Bank content, or project-rule compliance.
---

# Device Masker Review

Use this skill to inspect the actual repository state and write a findings-first review/audit report. This is local-first: source files, tests, docs, reports, logs, and project rules are the primary evidence. External docs are supporting evidence only when current platform/API behavior matters.

## Write Boundary

Write exactly one Markdown report file only. Use this path shape by default:

```text
docs/internal/reports/active/audits/YYYY-MM-DD/YYYY-MM-DD-topic-kebab-case.md
```

Use `closed/` only when the user explicitly asks for a final/closed/historical report or the review/audit is clearly superseded/completed.

Do not create, edit, move, rename, or delete any other project file or folder. Do not write logs, raw evidence files, Memory Bank updates, docs, source changes, config changes, build artifacts, commits, branches, tags, or pull requests. If the parent date folder is missing, create only the minimum parent folder required for the single report path.

If the review finds required code, docs, AGENTS.md, Memory Bank, report, or architecture changes, record the exact recommended edits inside the report instead of applying them.

Treat all `AGENTS.md` files and module guides as review targets, not as automatically correct truth. This project is under active development and has frequent breaking changes, validation, package, hook, and documentation changes. For any review that touches rules, architecture, workflow, or module boundaries, compare every relevant `AGENTS.md` rule against current code, Memory Bank, public docs, reports, and validation evidence. Flag stale, contradictory, missing, or over-specific rules and propose exact updates in the report, and be 100% confident and sure about it.

## Review Workflow

1. Define the review scope: changed files, module, report/doc set, runtime evidence, architecture area, or release-readiness question.
2. Read required project context before judging code.
3. Inspect real files without modifying them. Prefer `rg`, `rg --files`, `git diff`, `git status`, `git show`, and targeted file reads.
4. Review tests and verification evidence before implementation details when a change claims behavior.
5. Review implementation against Device Masker rules and module boundaries.
6. Review architecture improvement opportunities: data-model shape, module ownership, dependency direction, hook lifecycle, validation flow, report lifecycle, and agent/project rules. Propose improvements only when they remove proven complexity, stale assumptions, or repeated failure modes.
7. Check all relevant `AGENTS.md` files, docs, reports, Memory Bank, and public validation claims for stale, contradictory, or overstated information.
8. Use external docs only when needed:
   - Google Developer Knowledge MCP for Android, Google APIs, Play, Firebase, Material, web.dev, Google Cloud, and official Google developer docs.
   - Context7 MCP for non-Google library/framework/API docs.
   - Web search/GitHub for current releases, changelogs, breaking changes, upstream source, issues, and missing MCP coverage.
9. Separate verified facts, inferences, risks, and unknowns. Do not mix them.
10. Write one comprehensive findings-first review/audit report file at the approved report path.
11. If project docs or Memory Bank should change, list exact recommended edits instead of applying them.

For GitHub PR review comments, use the installed `gh-address-comments` workflow or GitHub plugin skills when the task is specifically to inspect/address PR comments. Do not treat PR comments alone as a full project review unless the user asks for one.

## Review Axes

Evaluate the relevant axes for every review:

- Correctness: behavior matches requirements, edge cases, error paths, Android-version differences, target-app behavior, and tests.
- Xposed safety: stable hooker callback shape, deoptimization, pass-through fallback, no target-process random generation, no Timber/Compose/private JSON/hardcoded keys in `:xposed`, no broad risky hooks without opt-in.
- Architecture and data model: RemotePreferences-first config, `JsonConfig.appConfigs` canonical scope, `SharedPrefsKeys` key ownership, coherent identity data, narrow interfaces, module boundaries.
- Architecture improvement: identify simpler data shapes, cleaner module ownership, safer hook lifecycle boundaries, better validation/report flows, and rule/doc updates that match the current development reality.
- Verification integrity: tests, lint, Detekt, R8 guard, emulator/device evidence, LSPosed/logcat proof, verifier JSON, exact expected-vs-actual values.
- Docs, rules, and reports: report category/date rules, public validation separation, stale claims, Memory Bank accuracy, and whether root/module `AGENTS.md` files still match current code and architecture.
- Security and privacy: secrets, raw identifiers in logs, unsafe input/config handling, command construction, root/logcat capture boundaries.
- Performance and concurrency: blocking calls, hot hook callback allocations/reflection, lock contention, unbounded work, Compose recomposition hotspots.
- UI/UX/accessibility: navigation clarity, workflow efficiency, state feedback, error recovery, loading/empty states, touch targets, text scaling, contrast, TalkBack semantics, reduced motion, edge-to-edge/insets, screen-density behavior, and consistency with Material 3 Expressive/project UI rules when the review scope includes app UI or public-facing workflow.

## UI/UX Review Audit

Include this section when the user asks for UI/UX review, visual audit, accessibility review, screen-flow review, or when the reviewed change touches Compose UI, navigation, public validation UX, diagnostics UX, settings/export UX, group/app assignment, or target-app configuration workflows.

Check:

- Workflow fit: the primary user action is visible, reversible, and does not require hidden state knowledge.
- Information hierarchy: screen titles, section headings, status labels, and density fit operational Android tooling instead of marketing-style layout.
- State clarity: loading, empty, disabled, error, partial-success, root unavailable, module unavailable, target unscoped, and validation unknown states are explicit.
- Navigation and back behavior: top-level stacks, detail screens, deep links, dialogs, bottom sheets, and back navigation preserve user context.
- Accessibility: touch targets, content descriptions, TalkBack order, focus behavior, text scaling, high contrast, dynamic color, reduced motion, and keyboard/IME behavior.
- Layout resilience: compact/medium/expanded widths, landscape, edge-to-edge insets, navigation bar/status bar overlap, long labels, translated strings, and dense data tables do not overlap or truncate critical content.
- Domain trust: UI copy must not overclaim hook success, Android 16 readiness, real-device proof, or anti-detection coverage from weak evidence.
- Visual consistency: Material 3 Expressive tokens, spacing, shape, typography, icon usage, and component choices match existing Device Masker patterns.
- Verification: prefer screenshot evidence, Mobile MCP visual checks, Compose previews, accessibility scanner/TalkBack notes, and targeted UI tests when available.

## Report Shape

Use this section order unless the user asks for a smaller answer:

- Findings, severity ordered: `Critical`, `High`, `Medium`, `Low`, `Info`
- Executive summary
- Scope
- Source inventory
- Project rule violations
- AGENTS.md and rule drift audit
- Root cause analysis
- Recommended fixes
- Architecture improvement opportunities
- UI/UX review audit
- Best solution direction
- Optional improvements
- Proposed APIs, interfaces, dependencies, or tools
- Rejected or risky approaches
- Verification plan
- Residual risks and unknowns
- Suggested next tasks
- Report file path
- Write boundary confirmation

## Findings Standard

Each finding should include:

- Severity: `Critical`, `High`, `Medium`, `Low`, or `Info`.
- Evidence: file/line links, report paths, log paths, command output already present on disk, or source links.
- Problem: what is wrong and why it matters.
- Root cause: the data shape, API misuse, lifecycle mismatch, validation gap, or stale documentation pattern behind it.
- Fix direction: practical remediation that matches existing project patterns.
- Verification: the command, runtime check, or document comparison that would prove the fix.

Findings should lead the report. Summaries and broad context come after findings, not before them.

## Hard Rules

- Do not claim hook success from app launch, app-side service connection, or config presence alone.
- Do not claim physical-device stability from emulator-only evidence.
- Do not accept hand-wavy performance, safety, or correctness claims.
- Do not recommend broad rewrites or new dependencies unless they directly reduce a proven risk.
- Do not create Detekt baseline debt as a review "fix".
- Do not move reports or docs during this workflow.
- Do not treat existing `AGENTS.md` rules as immutable when current code, Memory Bank, and evidence prove they are stale. Report the mismatch and propose precise rule changes.

## Combined Research And Review Requests

If the user asks for research and review/audit together:

- Still write exactly one report file.
- Classify by the primary output: use `audits/` when repository findings drive the answer; use the research workflow when external source facts drive the answer.
- Include clear `Research Inputs` and `Review Findings` sections when both outputs are substantial.

Use `.agents/skills/device-masker-research/SKILL.md` when external research is substantial or is the primary output.

## Final Response

End with:

- Highest-severity findings or "no findings".
- Verification checks performed and anything not run.
- Report file path.
- Confirmation that only the report file was written and no other files were edited, created, moved, committed, or pushed by the skill workflow.
