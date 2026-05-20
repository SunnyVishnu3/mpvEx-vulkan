# libxposed Helper Evaluation Report

Date started: 2026-05-09
Status: active

## Decision Rule

Adopt `libxposed/helper` only if all of these pass:

1. The artifact resolves from the configured repositories without adding a custom repository.
2. The dependency does not package `io.github.libxposed:api` into the APK.
3. One small hooker becomes simpler without changing runtime behavior.
4. Hook registration still uses `intercept(stableHooker { ... })`.
5. R8 release smoke still passes.

If any item fails, do not adopt the helper in production. Keep project-owned reflection plus static guards.

## Candidate Hooker

`SystemFeatureHooker` is the only candidate for this experiment because it has a small PackageManager method-discovery surface and useful Android/OEM variance.

## Research Finding

Context7 shows `helper-ktx` includes hook-building DSLs. That is useful, but it also creates callback-shape risk for this project because release R8 safety depends on Device Masker's concrete `StableHooker` adapter.

## Result

Not adopted. The current project-owned reflection path remains because Maven search did not show a current `io.github.libxposed:helper` Central artifact alongside `api`, `interface`, and `service`, and the helper DSL would need extra callback-shape review before production use.
