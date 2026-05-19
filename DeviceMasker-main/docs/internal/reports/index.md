# Internal Reports Index

Internal reports are organized by lifecycle, category, and date:

```text
docs/internal/reports/<active|closed>/<category>/YYYY-MM-DD/YYYY-MM-DD-topic.md
```

## Lifecycles

- `active`: pending decisions, open remediation, unfinished validation, or live investigation state.
- `closed`: decision recorded, remediation complete, validation superseded, or retained historical report.

## Categories

- `audits`: code reviews, architecture audits, UI audits, Detekt audits, CI/CD audits, performance audits, and libxposed audits.
- `validation`: emulator/device runtime tests, verifier matrices, Android-version evidence, R8 smoke tests, build gates, crash validation, and value checks.
- `research`: Android behavior research, Xposed/libxposed research, native hook research, proc-maps research, tool evaluation, and decision-heavy investigations.
- `summaries`: project-wide summaries, implementation completion reports, wrap-up reports, lifecycle recommendations, and final state summaries.

Classify by primary output, not by title wording. Existing-project findings go in `audits`; external facts, feasibility, options, and decision input go in `research`. If one request asks for both research and audit, create separate reports when both outputs are substantial; otherwise classify the combined report by its primary output.

The canonical rules live in `docs/AGENTS_PROJECT_RULES.md`.
