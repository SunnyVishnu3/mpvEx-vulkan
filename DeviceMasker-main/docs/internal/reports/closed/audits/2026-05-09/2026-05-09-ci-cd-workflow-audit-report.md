# CI/CD Workflow Audit Report

Date: 2026-05-09
Branch audited: `release/0.1.5`
Status: active

## 2026-05-09 Implementation Update

Applied after this audit:

- Kept 16 KB APK verification as a local developer check only after CI showed hosted runners do not expose `zipalign` through the script's expected Android SDK paths.
- Added `build-metadata.json` generation and upload.
- Split manual release Actions artifacts into signed release APK, debug APK, source archive, mapping archive, build metadata, and quality reports.
- Kept public GitHub Release assets limited to the signed release APK only.
- Added build provenance attestations for signed release APKs and branch debug APKs.
- Added an `actionlint` workflow for `.github/workflows/**` changes.
- Kept signed release artifacts enabled for all branch CI runs when signing secrets are available.
- Skipped emulator/runtime CI by decision; LSPosed and Android 16 DevCheck proof remain manual/device evidence work.
- Changed Gradle setup cache policy to allow cache writes from branch/tag runs for maximum solo-dev build caching.

## Executive Verdict

The current CI/CD setup is good for a solo Android/Xposed project: it runs quality checks, compiles all modules, validates R8, builds branch artifacts, keeps release publishing manual, and avoids automatic dependency PR noise after Dependabot removal.

The biggest gaps are not complexity gaps. They are evidence and supply-chain gaps:

1. CI builds artifacts but intentionally does not run the project's 16 KB APK verifier; this remains local/dev-only.
2. CI does not generate artifact attestations for APK/source artifacts.
3. Release workflow does not upload mapping/source/debug artifacts separately; it uploads one combined Actions artifact.
4. There is no workflow-level YAML validation/static CI lint.
5. There is no Android runtime/device test workflow, which is acceptable for now but should be explicit.
6. Permissions are mostly good, but release can be tightened at job level if artifact attestations are added.

For simplicity-first solo development, keep the current three-workflow model:

- `ci.yml`: all branch pushes + PRs to `main`.
- `release.yml`: manual only.
- `dependency-submission.yml`: main only.
- `wrapper-validation.yml`: wrapper-file changes only.

Do not add Play Store deployment, Firebase App Distribution, matrix emulator farms, or complicated promotion environments yet.

## Current Workflow Inventory

| Workflow | Trigger | Purpose | Current status |
| --- | --- | --- | --- |
| `.github/workflows/ci.yml` | push to any branch, PR to `main`, manual | quality checks, R8 validation, debug/release/source artifacts | working |
| `.github/workflows/release.yml` | manual only | tag-verified signed release build and GitHub Release creation | good model, needs small hardening |
| `.github/workflows/dependency-submission.yml` | push to `main`, manual | GitHub dependency graph submission | keep main-only |
| `.github/workflows/wrapper-validation.yml` | wrapper path changes on push/PR, manual | Gradle wrapper checksum validation | good |

Latest GitHub Actions evidence:

| Run | Branch | Result | Artifacts |
| --- | --- | --- | --- |
| `25600065460` | `release/0.1.5` | success | `DeviceMasker-debug-*`, `DeviceMasker-release-signed-*`, `DeviceMasker-source-*`, `quality-reports-*` |

Latest run timing:

| Job | Duration | Notes |
| --- | --- | --- |
| `Quality` | about 6m 35s | Spotless, Detekt, compile, lint, test, R8 validation |
| `Assemble Build Artifacts` | about 5m 30s | debug and signed release artifacts produced |

## What Is Already Good

| Area | Assessment |
| --- | --- |
| Branch CI | Fixed: CI now runs on every branch push. |
| PR CI | PRs targeting `main` still run CI. Good. |
| Release workflow | Manual-only is correct for a solo dev and avoids accidental releases. |
| Gradle setup | Uses `gradle/actions/setup-gradle@v5`, which is the right family of action for Gradle caching and wrapper support. |
| Wrapper validation | Present and path-scoped. Good safety check. |
| Permissions | CI uses `contents: read`; dependency submission uses `contents: write`; release uses `contents: write`. Reasonable. |
| Artifacts | Debug APK, signed release APK, source archive, and reports are uploaded. |
| Concurrency | Uses workflow/ref concurrency and cancels old branch CI runs. Correct for branch pushes. |
| Dependabot | Removed from repo config and old PR branches closed. Good because dependency changes are now deliberate. |

## Issues And Improvements

### 1. Keep 16 KB APK Verification Local-Only

Severity: Medium

The repo now has `scripts/verify-16kb-page-support.ps1`, and local debug/release/ciRelease APKs passed. CI does not run this script yet.

Why it matters:

- Android docs say apps with uncompressed shared libraries need 16 KB zip alignment for 16 KB devices.
- This project ships transitive `.so` files.
- Android 16 compatibility is an active project goal.

Decision:

Keep this check local-only for now. A CI attempt failed because the hosted runner did not expose `zipalign` through the script's expected Android SDK paths. Do not block branch artifacts on this check until the script has a Linux runner path that is proven stable.

### 2. Add Artifact Attestations For Release/Branch APKs

Severity: Medium-high

GitHub supports artifact attestations for build provenance. This would let you prove an APK came from a specific workflow run and commit.

Why it matters:

- You distribute APKs through GitHub artifacts/releases.
- Attestation improves trust without adding complex release infrastructure.

Recommended first step:

```yaml
permissions:
  contents: read
  attestations: write
  id-token: write

- name: Attest debug APK
  uses: actions/attest@v4
  with:
    subject-path: dist/debug/*.apk
```

For release workflow, attest `dist/public/*.apk` before `gh release create`.

Do not block releases on attestation at first. Add it, verify it works, then make it required later.

### 3. Split Release Workflow Artifacts More Clearly

Severity: Medium

`release.yml` uploads one combined Actions artifact named `DeviceMasker-release-${tag}-${run_id}` containing APKs, mapping, source, and reports.

This works, but it is less beginner-friendly than separate artifacts.

Recommended:

- `DeviceMasker-release-signed-${tag}`
- `DeviceMasker-debug-${tag}`
- `DeviceMasker-source-${tag}`
- `DeviceMasker-mapping-${tag}`
- `DeviceMasker-quality-reports-${tag}`

Keep public GitHub Release assets simple:

- Public release asset: signed release APK only.
- Actions artifacts: debug APK, signed release APK, source zip, mapping zip, reports.

This matches your current preference: simple public release, complete Actions archive.

### 4. Add A Workflow YAML Lint Step

Severity: Medium

There is no static validation for workflow YAML. A tiny workflow syntax mistake can break all CI.

Recommended simple option:

- Add `rhysd/actionlint` or install `actionlint` in a small workflow.

Example:

```yaml
name: Workflow Lint

on:
  pull_request:
    paths:
      - ".github/workflows/**"
  push:
    paths:
      - ".github/workflows/**"
  workflow_dispatch:

permissions:
  contents: read

jobs:
  actionlint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: rhysd/actionlint@v1
```

Solo-dev recommendation: add this only if workflow editing continues often. It is useful but not urgent.

### 5. CI Builds Signed Release On Branch Push When Secrets Exist

Severity: Medium

Current branch CI can build signed release APKs when signing secrets are configured. This is convenient, but it means every branch push with secrets available can produce a signed release APK artifact.

This is not automatically public, but it is still a signed production-capable artifact.

Safer options:

| Option | Behavior | Recommendation |
| --- | --- | --- |
| Keep current | Signed release artifacts on branch CI when secrets exist | convenient, acceptable if repo is private/trusted |
| Only sign on `main` | Branch CI gets debug/source only | safer |
| Only sign on manual release | No signed branch artifacts | safest |

For your solo workflow, I recommend:

- Branch CI: debug APK + source zip + reports.
- Main CI: debug APK + signed release APK if secrets exist.
- Manual release: signed public release APK.

That avoids accidental sharing of signed release artifacts from experimental branches.

### 6. Add Build Metadata Manifest To Artifacts

Severity: Medium

Artifacts are named well, but each artifact zip should include a small metadata file:

```json
{
  "versionName": "0.1.1",
  "versionCode": 2,
  "gitSha": "...",
  "branch": "release/0.1.5",
  "workflowRunId": "...",
  "buildType": "debug",
  "signed": false
}
```

Why it matters:

- Beginner-friendly.
- You can inspect an old artifact without guessing which commit/version produced it.
- Helps when debugging Android 16 reports from a downloaded APK.

Recommended file path:

```text
dist/metadata/build-metadata.json
```

Upload it with every artifact group.

### 7. Release Workflow Should Verify Tag Points To Intended Commit

Severity: Medium

`release.yml` checks out `ref: tag_name` and verifies `tag == v${VERSION_NAME}`. Good.

Potential gap:

- It does not clearly report the tag commit, current branch, or whether the tag is reachable from `main`.

For a solo dev, do not require tag reachable from `main` yet because you may release from `release/*`.

Recommended:

```bash
echo "TAG_COMMIT=$(git rev-parse HEAD)" >> "$GITHUB_ENV"
git log -1 --oneline
```

Optional later:

- Require release tags to be reachable from `main` after you decide releases must only come from merged code.

### 8. Add Manual "Validate APK" Workflow For Uploaded/Existing APKs

Severity: Low-medium

Useful for your Android 16 work:

- Manual workflow input: APK artifact path or build variant.
- Runs 16 KB check.
- Extracts APK metadata.
- Maybe runs static checks on mapping/source availability.

Not urgent. The current CI can cover most of it.

### 9. Add Emulator Tests Only When They Are Stable

Severity: Low for now

This repo’s real proof depends on LSPosed and target apps. GitHub-hosted Linux runners will not easily reproduce that.

Do not add heavy emulator tests yet unless they are simple non-Xposed app/verifier launches.

Potential future workflow:

- Build verifier APK.
- Run basic instrumentation or managed-device tests.
- Do not claim Xposed hook success from GitHub CI.

For target-hook validation, keep using real device/emulator + LSPosed evidence outside GitHub Actions.

### 10. Consider CodeQL Later, Not Now

Severity: Low

CodeQL can help for Java/Kotlin security scanning, but for this project:

- Detekt is already strict.
- Kotlin/Android CodeQL signal may be limited.
- It will add runtime and maintenance.

Recommendation: defer until after Android 16 release stabilization.

## Recommended Target State

### Keep

- `ci.yml` on all branch pushes.
- `release.yml` manual-only.
- `dependency-submission.yml` main-only.
- `wrapper-validation.yml` path-scoped.
- Dependabot disabled.
- Release public asset limited to signed release APK.

### Add Soon

1. 16 KB APK verification in CI. Reverted; local-only by decision.
2. Build metadata JSON uploaded with artifacts. Applied.
3. Separate release workflow artifacts. Applied.
4. Artifact attestation for signed release APK. Applied.

### Add Later

1. `actionlint` workflow. Applied.
2. Optional unsigned branch `ciRelease` artifact for R8 inspection.
3. Optional Android emulator/verifier smoke that does not pretend to prove LSPosed hooks.
4. Optional CodeQL.

## Suggested Minimal Patch Plan

```text
1. Add 16 KB verification to ci.yml -> verify: branch CI passes and logs show 16 KB pass. Reverted; local-only by decision.
2. Add build-metadata.json in ci.yml and release.yml -> verify: artifact zip includes metadata. Applied.
3. Split release workflow artifact upload steps -> verify: Actions page shows separate debug/release/source/mapping/report artifacts. Applied.
4. Add artifact attestation only for release signed APK -> verify: gh attestation verify works for the release APK. Applied.
```

This is the best next patch because it improves trust and Android 16 readiness without turning CI into enterprise sludge.

## Current Open Questions

| Question | Why it matters | Suggested answer |
| --- | --- | --- |
| Should branch CI upload signed release APKs? | Signed artifacts from experimental branches are convenient but riskier. | User decision: keep signed release artifacts on all branch CI runs. |
| Should release tags come only from `main`? | Prevents releasing unmerged branch code. | Not yet; allow release branches until workflow is stable. |
| Should CI run emulator tests? | Could increase confidence but may be flaky and not prove LSPosed hooks. | User decision: skip. |
| Should dependency submission remain after Dependabot removal? | Dependency graph still helps alerts without update PR spam. | Keep it. |

## Source Notes

- GitHub workflow permissions can be set at workflow or job level, and GitHub recommends explicit permissions for `GITHUB_TOKEN`: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax
- GitHub artifact attestations can establish provenance for binaries, requiring `id-token: write`, `contents: read`, and `attestations: write`: https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations
- Gradle `setup-gradle` handles Gradle User Home caching and defaults to write cache only from default branches, while other branches read cache: https://github.com/gradle/actions/blob/main/docs/setup-gradle.md
- Gradle recommends using the Gradle Wrapper with GitHub Actions setup-gradle: https://github.com/gradle/actions
- Android 16 KB page-size docs require 16 KB zip alignment for apps shipping uncompressed shared libraries and recommend AGP 8.5.1+ / current tooling: https://developer.android.com/guide/practices/page-sizes
- `gh release create` supports `--generate-notes`, `--verify-tag`, `--draft`, `--notes-file`, and `--fail-on-no-commits`: https://cli.github.com/manual/gh_release_create
- Reference observations from large Android repos: Signal Android separates Android CI, APK diff, and reproducible build checks; Now in Android uses CI-backed screenshot/benchmark style validation. These are useful patterns, but too heavy for Device Masker right now.
