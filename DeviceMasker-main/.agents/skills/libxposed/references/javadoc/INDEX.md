# Raw Scraped Data Index — libxposed + LSPosed
# Scraped: 2025-05-03
# All content is verbatim from official sources.
# This data is used as ground truth for the skill files.

## What's Here

### api-javadoc/
Raw content from https://libxposed.github.io/api/
Every class and interface page scraped individually.

| File | Content |
|---|---|
| `01-package-and-XposedInterface.md` | Package summary + XposedInterface full (fields + methods) |
| `02-Chain-HookBuilder-HookHandle-Hooker.md` | Chain, HookBuilder, HookHandle, Hooker |
| `03-Invoker-ExceptionMode-Module-Interface.md` | Invoker, Invoker.Type, Invoker.Type.Chain, Invoker.Type.Origin, ExceptionMode, XposedModule, XposedInterfaceWrapper, XposedModuleInterface, all Param interfaces |
| `04-error-package.md` | io.github.libxposed.api.error: XposedFrameworkError, HookFailedError |

### service-javadoc/
Raw content from https://libxposed.github.io/service/
Every class and interface page scraped individually.

| File | Content |
|---|---|
| `01-service-complete.md` | Package summary, XposedService, XposedServiceHelper, OnServiceListener, OnScopeEventListener, XposedProvider, RemotePreferences, RemotePreferences.Editor |

### LSPosed wiki (located at `../github/LSPosed-wiki/`)
Raw content from https://github.com/LSPosed/LSPosed/wiki
All 5 developer-facing pages. **Note:** This data is NOT under `javadoc/` — it lives at
`references/github/LSPosed-wiki/` as individual markdown files.

| File (in `../github/LSPosed-wiki/`) | Content |
|---|---|
| `Develop-Xposed-Modules-Using-Modern-Xposed-API.md` | Migration guide from legacy to modern API |
| `Module-Scope.md` | Scope declaration, manifest-based scope |
| `Native-Hook.md` | Native hook support |
| `New-XSharedPreferences.md` | Legacy XSharedPreferences (deprecated) |


## Pages NOT Available

The following wiki pages returned JS-disabled errors with no content:
- How to use it (user guide, not developer-relevant)
- 如何使用 (Chinese user guide)

## Key Discoveries from Raw Data

### OnServiceListener method names
Official names: `onServiceBind(XposedService)` and `onServiceDied(XposedService)`
NOT `onServiceConnected` / `onServiceDisconnected` (those are Android's ServiceConnection callbacks)

### OnScopeEventListener method names
Official names: `onScopeRequestApproved(List<String>)` and `onScopeRequestFailed(String)`
NOT `onScopeRequestGranted` / `onScopeRequestDenied` / `onScopeRequestError`

### XposedModule constructor
No-arg: `public XposedModule()` — confirmed by Javadoc

### HookFailedError hierarchy
Extends Error (not RuntimeException) — this is intentional per design note

### LSPosed archive status
The LSPosed/LSPosed repo was archived on Mar 27, 2026 (read-only).
The libxposed/* repos (api, service, helper, example) are still active.

### Remote file storage location (from wiki)
Remote Files stored at: `/data/adb/lspd/modules/<user>/<module>`
Remote Preferences stored at: LSPosed database

### exceptionMode in module.prop
Not listed in the official wiki API Changes table — only minApiVersion, targetApiVersion, staticScope.
It IS listed in the package-summary Javadoc as optional property.
