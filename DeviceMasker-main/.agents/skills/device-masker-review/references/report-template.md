# Review/Audit Report Template

Use this template for the single Device Masker review/audit report file written by the skill.

```markdown
# YYYY-MM-DD Topic Review

Date: YYYY-MM-DD
Mode: Review/audit report
Report path: docs/internal/reports/active/audits/YYYY-MM-DD/YYYY-MM-DD-topic-kebab-case.md

## Findings

### Critical

No findings, or list findings in this shape:

#### [C-1] Short Finding Title

- Evidence: file/report/log/source links with line numbers where possible.
- Problem: what is wrong and why it matters.
- Root cause: the deeper design, data, lifecycle, API, or workflow cause.
- Fix direction: practical remediation aligned with project patterns.
- Verification: command, runtime check, or document comparison that would prove the fix.

### High

### Medium

### Low

### Info

## Executive Summary

Summarize the review result after the findings. Do not hide important findings here.

## Scope

State what was reviewed, affected modules, and what was out of scope.

## Source Inventory

### Project Files

| Source | Relevance |
| --- | --- |
|  |  |

### Reports, Docs, And Memory Bank

| Source | Relevance |
| --- | --- |
|  |  |

### Existing Logs And Runtime Evidence

| Source | Relevance |
| --- | --- |
|  |  |

### External Docs Or GitHub Sources

| Source | Date Checked | Relevance |
| --- | --- | --- |
|  |  |  |

## Project Rule Violations

List concrete violations of `AGENTS.md`, `docs/AGENTS_PROJECT_RULES.md`, module guides, or Memory Bank rules.

## AGENTS.md And Rule Drift Audit

Compare every relevant `AGENTS.md` and module guide against current code, Memory Bank, reports, architecture, and validation evidence. List stale, contradictory, missing, or over-specific rules. Explain why each rule is wrong or incomplete now, especially when frequent development or breaking changes made old guidance unsafe.

## Root Cause Analysis

Summarize repeated causes across findings, such as wrong data shape, lifecycle mismatch, unsafe hook strategy, stale docs, or weak validation.

## Recommended Fixes

Rank required fixes. Include file/module ownership where useful.

## Architecture Improvement Opportunities

List architecture improvements only when they remove proven complexity, stale assumptions, unsafe boundaries, duplicated workflow, or repeated failure modes. Include the current pain, the better shape, and the migration/verification path.

## UI/UX Review Audit

Include this section for UI, workflow, navigation, accessibility, diagnostics, settings/export, public validation, group/app assignment, or target configuration reviews.

Cover:
- Workflow fit and primary actions.
- Information hierarchy and density.
- Loading, empty, disabled, error, partial-success, unavailable, and unknown states.
- Navigation, back behavior, dialogs, sheets, deep links, and state restoration.
- Accessibility: touch targets, content descriptions, TalkBack order, focus, text scaling, contrast, reduced motion, and IME behavior.
- Layout resilience across compact/medium/expanded widths, landscape, edge-to-edge insets, long labels, and dense tables.
- Domain-trust copy: no overclaiming hook success, Android readiness, real-device proof, or anti-detection coverage.
- Visual consistency with Material 3 Expressive and existing Device Masker patterns.
- Verification evidence such as screenshots, Mobile MCP checks, previews, accessibility notes, or UI tests.

## Best Solution Direction

Describe the safest durable direction if multiple fixes are possible.

## Optional Improvements

List improvements that are useful but not required to close the review.

## Proposed APIs, Interfaces, Dependencies, Or Tools

Only include proposals that directly reduce proven complexity or risk. State why existing project patterns are insufficient.

## Rejected Or Risky Approaches

List approaches that should not be taken and why.

## Verification Plan

Use concrete checks. For code changes, include Gradle/test/static/runtime commands where relevant.

## Residual Risks And Unknowns

List what remains unproven and what evidence would close it.

## Suggested Next Tasks

Use actionable tasks with verification steps.

## Write Boundary Confirmation

State that this report was the only file written and no other files were edited, created, moved, committed, or pushed.
```
