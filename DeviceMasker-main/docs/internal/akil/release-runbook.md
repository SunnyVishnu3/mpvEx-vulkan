# Akila Personal Release Runbook

This file is only for Akila. It is an internal checklist for releasing Device Masker.

## Golden Rule

Do not change the app version for every commit.

Change version only when you are ready to make a release.

Normal work:

```text
code -> commit -> push -> CI runs
```

Release work:

```text
version bump -> commit -> push -> tag -> push tag -> Manual Release workflow
```

## Version Files

App version lives in `gradle.properties`.

Current example:

```properties
VERSION_NAME=0.1.1
VERSION_CODE=2
```

Meaning:

- `VERSION_NAME` is the user-facing version, like `0.1.1`.
- `VERSION_CODE` is Android's internal upgrade number.
- `VERSION_CODE` must always increase for every release.

Example sequence:

```text
0.1.1 -> VERSION_CODE=2
0.1.2 -> VERSION_CODE=3
0.2.0 -> VERSION_CODE=4
1.0.0 -> VERSION_CODE=5
```

## Before A Release

Pick the next version.

Example next release:

```properties
VERSION_NAME=0.1.2
VERSION_CODE=3
```

Update `gradle.properties`, then verify:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat spotlessCheck detekt :app:assembleCiRelease --no-daemon
```

If these fail, fix the problem before tagging.

## Commit Version Bump

Commit the version change:

```powershell
git add gradle.properties
git commit -m "chore: bump version to 0.1.2"
git push
```

If other release workflow/docs changes are part of the same release prep, commit them too.

## Create And Push Tag

Tag name must match `VERSION_NAME` with `v` in front.

For `VERSION_NAME=0.1.2`, tag must be:

```text
v0.1.2
```

Commands:

```powershell
git tag v0.1.2
git push origin v0.1.2
```

Do not use a mismatched tag like `v0.1.3` when `VERSION_NAME=0.1.2`.

The release workflow will fail if tag and version do not match.

## Run Manual Release Workflow

Go to GitHub:

```text
Actions -> Manual Release -> Run workflow
```

Inputs:

```text
tag_name = v0.1.2
draft = true
```

Keep `draft=true` so you can review the release before publishing.

## What The Workflow Does

The manual release workflow will:

```text
1. Checkout the tag
2. Check tag_name matches VERSION_NAME
3. Check signing secrets exist
4. Run Spotless, Detekt, lint, and tests
5. Build debug APK
6. Build signed release APK
7. Build ciRelease for R8 validation
8. Split public release assets from Actions-only artifacts
9. Upload Actions artifacts
10. Create a GitHub Release draft
11. Generate changelog automatically
```

Expected public GitHub Release asset:

```text
DeviceMasker-v0.1.2-code3-release-signed.apk
```

Expected Actions-only artifacts:

```text
DeviceMasker-v0.1.2-code3-debug.apk
DeviceMasker-v0.1.2-code3-release-signed.apk
DeviceMasker-v0.1.2-code3-mapping-release.zip
DeviceMasker-v0.1.2-code3-source-<shortSha>.zip
app/common/xposed build reports
```

GitHub Release also automatically shows source ZIP/TAR for the tag.

Public release should stay simple: keep only the signed release APK in the GitHub Release assets.
Mapping, custom source zip, debug APK, and reports are for developer/debugging use and should stay in
Actions artifacts.

## After Workflow Finishes

Open the draft GitHub Release.

Check:

```text
1. APK name has correct version
2. Changelog looks okay
3. Public assets contain only the signed release APK
4. Version/tag is correct
```

Then click:

```text
Publish release
```

## Signing Secrets Required

The release workflow needs these GitHub Actions secrets:

```text
KEYSTORE_BASE64
KEYSTORE_PASS
KEY_ALIAS
KEY_PASS
```

If any secret is missing, the release workflow fails.

That is correct. Do not make unsigned public releases by accident.

## What CI Does On Normal Pushes

CI does not publish releases.

CI does:

```text
spotlessCheck
detekt
compile
lint
test
:app:assembleCiRelease
debug APK build
debug APK artifact upload
quality report upload
```

CI debug artifact name format:

```text
DeviceMasker-v0.1.2-code3-debug-<shortSha>.apk
```

## What Not To Do

Do not bump version on every commit.

Do not create a release without a tag.

Do not push a tag before committing the version bump.

Do not publish release if workflow failed.

Do not publish release if LSPosed/runtime validation is still needed for the claim you are making.

Do not upload keystore files to Git.

Do not zip the whole working directory as source. Use GitHub's automatic source archive or `git archive`.

## Quick Example For Next Release

For release `0.1.2`:

```powershell
# 1. Edit gradle.properties manually:
# VERSION_NAME=0.1.2
# VERSION_CODE=3

.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat spotlessCheck detekt :app:assembleCiRelease --no-daemon

git add gradle.properties
git commit -m "chore: bump version to 0.1.2"
git push

git tag v0.1.2
git push origin v0.1.2
```

Then run GitHub Actions:

```text
Manual Release
tag_name = v0.1.2
draft = true
```

Review the draft release, then publish.
