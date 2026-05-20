# Device Masker Review Skill Summary

Date: 2026-05-11

Status: Implemented as a project-local skill on 2026-05-11.

Skill path:

```text
.agents/skills/device-masker-review/
```

Implemented files:

```text
.agents/skills/device-masker-review/SKILL.md
.agents/skills/device-masker-review/agents/openai.yaml
.agents/skills/device-masker-review/references/report-template.md
```

## Purpose

Create a project-local review/audit skill for deep inspection of the existing Device Masker codebase, docs, reports, architecture, tests, validation evidence, folder structure, and project rules.

The review skill is local-first. It should understand the current repo state, inspect real files, compare behavior against project rules, and use external docs only when needed to judge current Android, Kotlin, libxposed, Gradle, Compose, dependency, or platform behavior.

The skill must write exactly one comprehensive findings-first review/audit report file. It must not create or edit any other project file or folder.

## Trigger Shape

Use the skill when the user asks for:

- Audit, review, code review, architecture review, folder structure audit, docs audit, bug/risk scan, stability review, validation review, or readiness review.
- Architecture improvement analysis and proposal, especially when current design/rules no longer fit active development or breaking changes.
- A comparison between current project implementation and project rules.
- A comparison between current code/architecture and all relevant `AGENTS.md` files or module guides.
- A comparison between current code and external docs or latest platform/library behavior.
- Identification of bugs, regressions, critical issues, missing tests, stale docs, unsafe hooks, broad refactors, or weak assumptions.
- Recommended fixes, better APIs, safer architecture, useful plugins/dependencies, or validation plans.

Do not use it for pure external research where the primary output is upstream facts/options. That belongs to the research skill.

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
3. Read relevant current reports and public docs:
   - `docs/internal/reports/index.md`
   - relevant active/closed reports
   - `docs/public/ARCHITECTURE.md`
   - `docs/public/validation/DEVICE_MASKER_VALIDATION_STATUS.md` when runtime claims are involved
4. Inspect actual files. Prefer `rg`/`rg --files` for discovery.
5. Audit local code/docs first.
6. Treat all `AGENTS.md` and module guide files as audit targets, not immutable truth. Because the project is under active development with frequent breaking changes, compare those rules to current code, Memory Bank, reports, public docs, and validation evidence.
7. Audit architecture improvement opportunities when relevant: data model shape, module ownership, dependency direction, hook lifecycle, validation flow, report lifecycle, and project/agent rules.
8. Fetch external docs only when needed for current API/platform/library comparison:
   - Google Developer Knowledge for Android, Google APIs, Material, Firebase, Play, web.dev, Google Cloud.
   - Context7 for non-Google libraries/frameworks/API docs.
   - Web search for recent releases, changelogs, issues, breaking changes, and upstream source.
9. Write one comprehensive review/audit report file.
10. If files, docs, Memory Bank, or reports should change, list exact recommended edits instead of applying them.

## Output Boundary

Default report location:

```text
docs/internal/reports/active/audits/YYYY-MM-DD/YYYY-MM-DD-topic-kebab-case.md
```

Use `closed/` only when the user explicitly asks for a final/closed/historical report or the audit is clearly completed/superseded.

The report file must be findings-first and severity-ordered.

Required sections:

- Executive summary
- Scope
- Findings
- Root cause analysis
- Evidence with file/line references
- Project rule violations
- AGENTS.md and rule drift audit
- External docs comparison, when used
- Architecture improvement opportunities
- Recommended fixes
- Best possible solution direction
- Optional improvements
- Proposed APIs/interfaces, if justified
- Dependency/plugin/tool recommendations, if useful
- Rejected or risky approaches
- Verification plan
- Residual risks and unknowns
- Next tasks
- Write boundary confirmation

## Review Standard

The review/audit must be blunt but technical:

- Call out bugs, regressions, missing validation, stale docs, unsafe assumptions, broad unrelated changes, and weak evidence.
- Do not treat app launch or service connection as hook proof.
- Do not claim Android 16 real-device readiness from emulator evidence.
- Do not accept hand-wavy performance, safety, or correctness claims.
- Prefer concrete file/line evidence over general statements.
- Separate verified facts from inference.

## Recommendations And Fix Proposals

The audit should not stop at listing problems. It should propose:

- safer fixes
- implementation direction
- compatibility constraints
- tests and validation gates
- report/docs updates
- possible new APIs/interfaces only when they remove real complexity
- dependency/plugin/tool recommendations only when they directly reduce risk or improve capability

Avoid speculative architecture and enterprise-style layers unless the current problem needs them.

## Evidence Handling

Write only the single review/audit report file. Do not create scattered notes files.

Use existing logs and evidence already present in the repo. Do not generate raw evidence files during this skill workflow.

## Memory Bank Rule

Do not update Memory Bank during this skill workflow.

Recommend Memory Bank updates only when the review/audit changes:

- current project state
- architecture/system patterns
- known risks
- next tasks
- verified evidence
- project rules or workflow

## Combined Research And Review Requests

If a user asks for research and review/audit together:

- Still write exactly one report file.
- Classify by primary output: `audits/` when repository findings drive the answer, `research/` when external source facts drive the answer.
- Include clear `Research Inputs` and `Audit Findings` sections when both outputs are substantial.

## Skill Creation Notes

The future skill should be concise. Put only essential workflow in `SKILL.md`.

Resources:

- `references/report-template.md` for the default report-file skeleton.
- No scripts are required initially unless repeated repository scans become mechanical.

The skill must produce review-grade output: findings first, evidence-backed, single-report-file-only, and useful for deciding what to fix next.
