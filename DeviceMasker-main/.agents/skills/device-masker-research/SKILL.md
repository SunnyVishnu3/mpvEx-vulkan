---
name: device-masker-research
description: Source-backed research for the Device Masker Android LSPosed/libxposed project that may write exactly one internal research report file and must not modify any other project files. Use when researching Android platform behavior, Android 16 compatibility, 16 KB page-size support, proc maps hardening, Kotlin, Gradle, AGP, Compose, Material, R8, LSPosed/libxposed/Xposed APIs, target-app detection behavior, release notes, breaking changes, upstream GitHub issues, new APIs, or any source-backed investigation that needs a cited report.
---

# Device Masker Research

Use this skill to turn a research question into a current, cited, project-aware research report.

## Write Boundary

Write exactly one Markdown report file only. Use this path shape by default:

```text
docs/internal/reports/active/research/YYYY-MM-DD/YYYY-MM-DD-topic-kebab-case.md
```

Use `closed/` only when the user explicitly asks for a final/closed/historical report or the report is clearly superseded/completed.

Do not create, edit, move, rename, or delete any other project file or folder. Do not write logs, raw evidence files, Memory Bank updates, docs, source changes, config changes, build artifacts, commits, branches, tags, or pull requests. If the parent date folder is missing, create only the minimum parent folder required for the single report path.

If research implies project changes, record the recommended edits inside the report instead of applying them.

## Source Selection

Use sources by authority and topic:

- Google Developer Knowledge MCP: Android, Google APIs, Play, Firebase, Material, web.dev, Google Cloud, and official Google developer docs.
- Context7 MCP: non-Google library/framework/API docs such as Kotlin, Gradle-adjacent APIs, Compose libraries not covered by Google, and other current API references. Resolve the library ID first.
- Web search/browser: latest release notes, changelogs, breaking changes, upstream GitHub repositories, source code, issues, pull requests, and docs missing from MCP coverage.
- Project-local sources to inspect only: code, Memory Bank, docs, existing internal reports, existing build logs, existing device logs, existing verifier evidence, and public validation artifacts.

Prefer primary sources. Use secondary sources only for orientation, then verify against official docs, upstream source, release notes, or existing project evidence.

## Research Workflow

1. Define the exact research question, affected modules, and decision the report should support.
2. Read required project context before external research.
3. Gather current external sources with the source-selection rules above.
4. Compare upstream facts against Device Masker constraints and existing project evidence.
5. Separate verified facts, inferences, risks, and unknowns. Do not mix them.
6. Write one comprehensive report file at the approved report path.
7. If project docs or Memory Bank should change, list exact recommended edits instead of applying them.

## Report Shape

Use this section order unless the user asks for a smaller answer:

- Executive summary
- Research question and scope
- Source inventory with links or local paths
- Verified facts
- Source-backed findings
- Inferences
- Project impact
- Compatibility risks and edge cases
- Unknowns and gaps
- Recommendations
- Suggested next tasks
- Report file path
- Write boundary confirmation

## Quality Bar

Every non-trivial report must include:

- Clear distinction between official docs, library docs, release notes/changelogs, GitHub/source/issues, and project-local evidence.
- Verified facts with citations.
- Inferences explicitly labeled as inferences.
- Project impact for `:app`, `:common`, `:xposed`, `:verifier`, validation, release/R8, or docs as relevant.
- Compatibility risks, edge cases, unknowns, and recommended next tasks.

Do not claim Android physical-device stability from emulator-only evidence. Do not claim hook success from app launch, app-side service connection, or configuration presence alone.

## Combined Research And Review Requests

If the user asks for research and review/audit together:

- Still write exactly one report file.
- Classify by the primary output: use `research/` when external source facts drive the answer; use the review/audit workflow when repository findings drive the answer.
- Include clear `Research Inputs` and `Review Findings` sections when both outputs are substantial.

For purely local code review with no external research, use the review/audit workflow instead of this skill.

## Final Response

End with:

- Key sources used, especially web/GitHub sources.
- The report file path.
- Confirmation that only the report file was written and no other files were edited, created, moved, committed, or pushed.
- Any remaining unknowns or validation gaps.
