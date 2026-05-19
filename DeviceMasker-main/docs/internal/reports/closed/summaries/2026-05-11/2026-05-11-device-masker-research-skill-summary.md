# Device Masker Research Skill Summary

Date: 2026-05-11

Status: Implemented as a project-local skill on 2026-05-11.

Skill path:

```text
.agents/skills/device-masker-research/
```

Implemented files:

```text
.agents/skills/device-masker-research/SKILL.md
.agents/skills/device-masker-research/agents/openai.yaml
.agents/skills/device-masker-research/references/report-template.md
```

## Purpose

Create a project-local research skill for deep, current, source-backed research on Device Masker topics. The skill is for questions about Android platform behavior, LSPosed/libxposed behavior, Kotlin/Gradle/Compose/library behavior, release changes, breaking changes, compatibility, new APIs, and project-specific technical risks.

The skill must understand the Device Masker project context first, then perform current external research with the right tool mix, and finally write exactly one research report file. It must not edit any other project file or folder.

## Trigger Shape

Use the future skill when the user asks for:

- Research on Android, Kotlin, Gradle, Compose, Material, LSPosed, libxposed, Xposed, R8, AGP, 16 KB page-size support, proc maps, native hooks, WebView, telephony restrictions, or related compatibility topics.
- Latest docs, changelogs, release notes, breaking changes, new APIs, or upstream behavior.
- A source-backed investigation before changing project architecture, dependencies, hook behavior, validation strategy, or release claims.
- A deep technical report that compares upstream facts against Device Masker needs.

Do not use it for purely local code review. That belongs to the review/audit skill unless external research is the primary output.

## Core Workflow

1. Read project rules:
   - `AGENTS.md`
   - `docs/AGENTS_PROJECT_RULES.md`
   - relevant module `AGENTS.md`
2. Read Memory Bank:
   - `memory-bank/projectbrief.md`
   - `memory-bank/productContext.md`
   - `memory-bank/systemPatterns.md`
   - `memory-bank/techContext.md`
   - `memory-bank/activeContext.md`
   - `memory-bank/progress.md`
3. Understand the exact research question and expected decision/output.
4. Select sources by topic:
   - Use Google Developer Knowledge first for Android, Google APIs, Play, Firebase, Material, web.dev, Google Cloud, and official Google developer docs.
   - Use Context7 first for non-Google library/framework/API docs where it has stronger coverage.
   - Use both when useful, but categorize source types instead of blending them blindly.
   - Use web search for latest releases, changelogs, breaking changes, upstream issue trackers, source repositories, missing MCP coverage, and recent ecosystem changes.
   - Use project-local docs, reports, code, and logs for Device Masker-specific context.
5. Separate source categories in the report:
   - Official Google docs
   - Library/framework docs
   - Release notes/changelogs
   - GitHub/source/issues
   - Project-local evidence
6. Write one comprehensive report file.
7. If files, docs, Memory Bank, or reports should change, list exact recommended edits instead of applying them.

## Output Boundary

Default report location:

```text
docs/internal/reports/active/research/YYYY-MM-DD/YYYY-MM-DD-topic-kebab-case.md
```

Use `closed/` only when the user explicitly asks for a final/closed/historical report or the report is clearly superseded/completed.

The report must include:

- Executive summary
- Research question and scope
- Source inventory with links or local paths
- Verified facts
- Source-backed findings
- Inferences clearly marked as inferences
- Project impact
- Compatibility risks
- Edge cases
- Unknowns and gaps
- Recommendations
- Suggested next tasks
- Write boundary confirmation

## Evidence Handling

Write only the single research report file. Do not create scattered notes files. Use existing logs and evidence already present in the repo. Do not generate raw evidence files during this skill workflow.

## Memory Bank Rule

Do not update Memory Bank during this skill workflow.

Recommend Memory Bank updates only when the research changes:

- current project state
- architecture/system patterns
- known risks
- next tasks
- verified evidence
- project rules or workflow

## Combined Research And Audit Requests

If a user asks for research and audit together:

- Still write exactly one report file.
- Classify by primary output: `research/` when external source facts drive the answer, `audits/` when repository findings drive the answer.
- Include clear `Research Inputs` and `Audit Findings` sections when both outputs are substantial.

## Skill Creation Notes

The future skill should be concise. Put only essential workflow in `SKILL.md`.

Likely resources:

- `references/report-template.md` for the default research report skeleton.
- No scripts are required initially unless repeated source extraction/report generation becomes mechanical.

The skill must not replace project judgment with docs quotes. It should use current docs to produce practical recommendations for this repo.
