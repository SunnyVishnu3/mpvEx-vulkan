# CI/CD, Release, And Versioning Report - 2026-05-07

## Scope

Analyze the current GitHub Actions, Android release build, version management, artifact naming, and source upload gaps. No workflow or Gradle changes were made in this report.

## Research Confirmation

The proposed approach was checked against current official documentation on 2026-05-07:

- GitHub Actions artifacts are meant to persist workflow outputs such as build files, test results, logs, binaries, and reports after a job finishes. This confirms that uploading APKs/reports from CI is appropriate, but Actions artifacts are not the best permanent public release surface.
- GitHub Releases are the right permanent distribution surface for manually published APKs. Release assets can be uploaded with GitHub CLI, and GitHub automatically exposes source ZIP/TAR archives for the release tag.
- `workflow_dispatch` is the correct GitHub Actions trigger for a manual solo-dev release workflow with inputs.
- `gh release create` supports uploading `dist/*`, generated notes, draft releases, and `--verify-tag`, which prevents accidentally creating a release from an unpushed or wrong tag.
- Android versioning is expected to be defined in Gradle build configuration. `versionCode` must monotonically increase for app upgrades, while `versionName` is the user-facing version string.
- Android release builds should be signed and optimized before distribution. This matches the current release build type using signing env vars, R8 minification, and resource shrinking.
- GitHub artifact attestations are available for provenance, but they are optional here. Add them later only if users need binary provenance verification; do not complicate the first release workflow.

Verdict: the original recommendation is sound. The only refinement is to make the manual release workflow verify the tag against `VERSION_NAME` before building.

## Current State

Implementation update on 2026-05-08:

- `gradle.properties` now owns app version:
  - `VERSION_NAME=0.1.1`
  - `VERSION_CODE=2`
- `app/build.gradle.kts` reads `VERSION_NAME` and `VERSION_CODE` through Gradle providers.
- CI now runs `:app:assembleCiRelease` as an R8 validation build.
- CI debug artifacts are copied to `dist/` with a `DeviceMasker` versioned file name before upload.
- CI no longer builds signed release artifacts on every main push.
- `release.yml` is manual-only through `workflow_dispatch`.
- Manual release verifies `tag_name == v${VERSION_NAME}` before building.
- Manual release requires signing secrets, builds signed release + `ciRelease`, prepares APK/mapping/source artifacts in `dist/`, uploads Actions artifacts, and creates a GitHub Release using generated notes.

- CI exists in `.github/workflows/ci.yml`.
- Release artifact build exists in `.github/workflows/release.yml`.
- Gradle wrapper validation and dependency submission are already split into small workflows.
- App version is now centralized in `gradle.properties`.
- Signing is already environment-based:
  - `KEYSTORE_PATH`
  - `KEYSTORE_PASS`
  - `KEY_ALIAS`
  - `KEY_PASS`
- CI/release upload raw Gradle outputs from:
  - `app/build/outputs/apk/debug/*.apk`
  - `app/build/outputs/apk/release/*.apk`
  - `app/build/outputs/mapping/release/**`

## Problems

### 1. Artifact Names Were Generic

Before the implementation update, the workflow artifact names were readable enough:

- `debug-apks-${{ github.run_id }}`
- `signed-release-artifacts-${{ github.run_id }}`

But the files inside those artifacts were still Gradle default names like:

- `app-debug.apk`
- `app-release.apk`
- `app-release-unsigned.apk`

That is why the artifact did not clearly say `DeviceMasker`, version, variant, signing state, or commit.

Status: fixed for CI debug artifacts and manual release artifacts by copying outputs into `dist/`.

### 2. Version Had No Single Simple Release Knob

`versionCode` and `versionName` used to live directly inside `app/build.gradle.kts`. This worked, but for releases it was easy to forget what changed and hard for workflows to read without fragile parsing.

Android requires `versionCode` to increase for updates, while `versionName` is the user-facing version string. This should be obvious and centralized.

Status: fixed by `VERSION_NAME` and `VERSION_CODE` in `gradle.properties`.

### 3. Release Workflow Did Not Create A GitHub Release

`.github/workflows/release.yml` used to build signed artifacts, but it did not publish a GitHub Release with assets. Users only got short-lived Actions artifacts.

GitHub Releases automatically include source ZIP/TAR archives for the tag, but Actions artifacts do not include source unless the workflow uploads source explicitly.

Status: fixed. Manual release now creates a GitHub Release with `gh release create --generate-notes`.

### 4. CI Built Signed Release On Main Push

`ci.yml` had an `assemble-signed-release` job for non-PR runs. For a solo developer, this was unnecessary churn. A signed release should happen only in the manual release workflow.

Status: fixed. Signed release was removed from CI.

### 5. Release Checks Were Close But Not Project-Complete

Release workflow used to run:

- `spotlessCheck`
- `lint`
- `test`
- `assembleRelease`

It should also run `detekt`, and preferably the known release/R8 validation build:

- `:app:assembleCiRelease`

This project has Xposed/R8-specific risk. Do not claim runtime hook success from CI alone.

Status: fixed for static/build validation. Runtime LSPosed proof is still manual/device-owned.

## Recommended Simple Design

Keep four workflows:

| Workflow | Purpose | Keep/Change |
| --- | --- | --- |
| `ci.yml` | PR/main quality + debug artifact | Keep, simplify signed release out |
| `release.yml` | Manual signed release + GitHub Release | Improve |
| `wrapper-validation.yml` | Gradle wrapper integrity | Keep |
| `dependency-submission.yml` | GitHub dependency graph | Keep |

Avoid semantic-release, release drafter, Play Store deployment, multi-environment promotion, or custom Gradle publishing logic for now. That would be enterprise sludge for a solo LSPosed module.

Best-practice fit:

- Keep CI and release separate. CI proves the branch is healthy; release publishes a signed artifact.
- Use least privilege: CI can keep `contents: read`; release needs `contents: write` only because it creates a GitHub Release.
- Build into `dist/` before upload. This makes artifact naming explicit and avoids depending on Android Gradle Plugin internal output names.
- Prefer draft releases at first. This gives one final manual review before publishing a binary that users install.

## Version Management Recommendation

App version now lives in `gradle.properties`:

```properties
VERSION_NAME=0.1.1
VERSION_CODE=2
```

Then read it from `app/build.gradle.kts`:

```kotlin
val appVersionName = providers.gradleProperty("VERSION_NAME").get()
val appVersionCode = providers.gradleProperty("VERSION_CODE").get().toInt()

android {
    defaultConfig {
        versionCode = appVersionCode
        versionName = appVersionName
    }
}
```

Rules:

- Bump both values manually before release.
- Tag releases as `v${VERSION_NAME}`.
- Never decrease `VERSION_CODE`.
- Do not derive release `versionCode` from GitHub run number; that makes local and CI release builds disagree.
- In `release.yml`, fail early if `tag_name` does not equal `v${VERSION_NAME}`.

Recommended tag check:

```bash
VERSION_NAME=$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)
EXPECTED_TAG="v${VERSION_NAME}"

if [ "$TAG_NAME" != "$EXPECTED_TAG" ]; then
  echo "Tag $TAG_NAME does not match gradle.properties VERSION_NAME=$VERSION_NAME"
  exit 1
fi
```

## Artifact Naming Recommendation

Do not fight Android Gradle Plugin output internals unless needed. The boring fix is a workflow `dist/` step after build:

```bash
VERSION_NAME=$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)
VERSION_CODE=$(grep '^VERSION_CODE=' gradle.properties | cut -d= -f2)
SHORT_SHA="${GITHUB_SHA::7}"

mkdir -p dist
cp app/build/outputs/apk/debug/*.apk \
  "dist/DeviceMasker-v${VERSION_NAME}-code${VERSION_CODE}-debug-${SHORT_SHA}.apk"
```

For release:

```bash
cp app/build/outputs/apk/release/*.apk \
  "dist/DeviceMasker-v${VERSION_NAME}-code${VERSION_CODE}-release-signed.apk"

cd app/build/outputs/mapping/release
zip -r "$GITHUB_WORKSPACE/dist/DeviceMasker-v${VERSION_NAME}-code${VERSION_CODE}-mapping-release.zip" .
```

Suggested final names:

- `DeviceMasker-v0.1.1-code2-debug-abcdef1.apk`
- `DeviceMasker-v0.1.1-code2-release-signed.apk`
- `DeviceMasker-v0.1.1-code2-mapping-release.zip`
- `DeviceMasker-v0.1.1-code2-source-abcdef1.zip`

## Source Upload Recommendation

For GitHub Releases:

- No extra source asset is required.
- GitHub automatically adds source ZIP and TAR archives for the release tag.

For Actions artifacts:

- Add an explicit source archive only if you want source inside the workflow artifacts page.
- Use `git archive`, not a raw workspace ZIP, to avoid uploading `.gradle`, `build/`, secrets, or temporary logs.

```bash
git archive --format=zip \
  --output "dist/DeviceMasker-v${VERSION_NAME}-code${VERSION_CODE}-source-${SHORT_SHA}.zip" \
  HEAD
```

This is safer than zipping the working directory because it archives tracked source from Git only. It avoids build output, Gradle caches, local secrets, logs, and random temporary files.

## Manual Release Workflow Shape

Use `workflow_dispatch` only for now.

Recommended inputs:

```yaml
workflow_dispatch:
  inputs:
    tag_name:
      description: "Release tag, example: v1.0.0"
      required: true
      type: string
    draft:
      description: "Create draft release"
      required: true
      default: true
      type: boolean
```

Recommended release job:

```text
1. Checkout full history -> verify tag exists and matches VERSION_NAME
2. Set up JDK + Gradle -> verify wrapper works
3. Decode signing keystore -> verify secrets exist
4. Run spotlessCheck detekt test lint -> verify quality gate
5. Run assembleRelease :app:assembleCiRelease -> verify signed/R8 build
6. Copy outputs into dist/ with DeviceMasker versioned names -> verify files exist
7. Create source archive with git archive -> verify source zip exists
8. Upload Actions artifact -> verify temporary workflow download
9. Create GitHub Release with dist/* -> verify permanent release assets
```

Use GitHub CLI because it is available on GitHub-hosted runners and keeps the workflow simple:

```bash
gh release create "$TAG_NAME" dist/* \
  --title "DeviceMasker $TAG_NAME" \
  --generate-notes \
  --verify-tag \
  --draft
```

Workflow permissions must be:

```yaml
permissions:
  contents: write
```

Use `contents: read` everywhere else unless a workflow has a concrete reason to write.

## CI Workflow Recommendation

Keep CI focused:

- `spotlessCheck`
- `detekt`
- module compile or `assembleDebug`
- `lint`
- `test`
- `:app:assembleCiRelease` if you want R8 guard on every main push
- upload reports
- upload renamed debug APK

Remove signed release assembly from `ci.yml`. Signed release belongs in the manual release workflow.

Optional but useful:

- Upload quality reports with short retention, for example 7 days.
- Upload debug APKs with medium retention, for example 14 days.
- Keep release assets in GitHub Releases, not only Actions artifacts.
- Add artifact attestations later if provenance becomes important:
  - requires `attestations: write`
  - useful for public binary trust
  - not required for the first simple workflow

## Secrets

Current names are fine:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASS`
- `KEY_ALIAS`
- `KEY_PASS`

Keep the Gradle env names as-is after decoding:

- `KEYSTORE_PATH`
- `KEYSTORE_PASS`
- `KEY_ALIAS`
- `KEY_PASS`

Do not commit keystores. Do not print secret values. Keep the decoded keystore in `$RUNNER_TEMP`.

## Proposed Implementation Order

1. Add `VERSION_NAME` and `VERSION_CODE` to `gradle.properties` -> verify `./gradlew :app:properties` or build reads them.
2. Update `app/build.gradle.kts` to read those properties -> verify `assembleDebug`.
3. Add `dist/` copy/rename steps to CI -> verify artifact file names include `DeviceMasker`.
4. Simplify CI signed release job out -> verify PR/main CI still builds.
5. Upgrade `release.yml` to manual GitHub Release creation -> verify dry run by creating draft release.
6. Add source archive to Actions artifact only if wanted -> verify archive is built from `git archive`.

## Bottom Line

Best simple setup:

- `gradle.properties` owns version.
- CI uploads renamed debug/R8 artifacts from `dist/`.
- Manual release workflow builds signed release, renames APK/mapping/source into `dist/`, uploads Actions artifacts, and creates a draft GitHub Release.
- GitHub Release source ZIP/TAR is automatic, so source upload is only needed for Actions artifacts.
- The release workflow should fail if the manual tag input does not match `VERSION_NAME`.
- Do not add heavy release automation until manual draft releases become painful.

## Sources

- GitHub workflow dispatch inputs: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onworkflow_dispatch
- GitHub Actions artifacts: https://docs.github.com/en/actions/how-tos/writing-workflows/choosing-what-your-workflow-does/storing-and-sharing-data-from-a-workflow
- `actions/upload-artifact` options: https://github.com/actions/upload-artifact
- GitHub Releases source archives: https://docs.github.com/repositories/releasing-projects-on-github/about-releases
- GitHub CLI release creation: https://cli.github.com/manual/gh_release_create
- Android app versioning: https://developer.android.com/studio/publish/versioning
- Android release builds/signing: https://developer.android.com/build/build-for-release
