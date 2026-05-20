---
name: libxposed
description: >
  Verified reference for building Android Xposed modules with the modern libxposed API (API 101+).
  Contains raw-scraped Javadoc signatures and cloned official source code — the only reliable ground
  truth for correct method names, class hierarchies, and implementation patterns. Without this skill,
  agents hallucinate legacy XposedBridge names (XC_MethodHook, beforeHookedMethod, onServiceConnected)
  that compile but silently fail at runtime. Use this skill whenever the user mentions Xposed module
  development: hooking methods, XposedModule, XposedInterface, Hooker, Chain, Invoker, lifecycle
  callbacks (onModuleLoaded, onPackageLoaded, onPackageReady, onSystemServerStarting), scope management,
  java_init.list, module.prop, XposedService, RemotePreferences, deoptimize, hookClassInitializer,
  helper or helper-ktx, or migrating from legacy XposedBridge. Even simple questions like hook a method
  MUST trigger this skill — the API has critical differences from legacy Xposed that cause silent
  runtime failures.
---

# libxposed — Modern Xposed API Skill

> **Why this skill exists:** The libxposed API (API 101+) has completely different class names,
> method signatures, and patterns from legacy XposedBridge. LLMs frequently hallucinate legacy
> names like `onServiceConnected`, `onScopeRequestGranted`, `XC_MethodHook`, or `beforeHookedMethod`.
> Every one of those names is WRONG. This skill contains raw-scraped, audit-verified ground truth
> from the official Javadoc and source code. Reading the reference files before writing code is the
> ONLY way to guarantee correct API usage.

---

## ⚡ MANDATORY: Before Writing ANY Code

**Stop. Read the required files first.** The libxposed API has many names that are subtly different
from what you might guess. If you skip this step, you WILL produce code with wrong method names.
Past audits found 12+ critical hallucination errors when this protocol was not followed.

### Step 1 — Always read first (every invocation)

Read `references/javadoc/INDEX.md` — this is the master index containing confirmed-correct method
names, class hierarchies, and key discoveries from the raw Javadoc scrape. It takes 68 lines and
prevents the most common hallucination errors.

### Step 2 — Read the files relevant to your task

Determine which category your task falls into and read the required files:

| Task Category | MUST Read Before Writing Code |
|---------------|-------------------------------|
| **Writing a new module** | `references/github/example/app/src/main/java/io/github/libxposed/example/ModuleMain.java` (Java module entry pattern) AND `references/github/example/app/build.gradle.kts` (build config) AND `references/github/example/app/src/main/resources/META-INF/xposed/` folder (java_init.list, module.prop, scope.list) |
| **Hooking methods** | `references/javadoc/api-javadoc/01-package-and-XposedInterface.md` (hook API + constants) AND `references/javadoc/api-javadoc/02-Chain-HookBuilder-HookHandle-Hooker.md` (Chain methods, proceed variants) |
| **Using the Invoker system** | `references/javadoc/api-javadoc/03-Invoker-ExceptionMode-Module-Interface.md` (Invoker, Invoker.Type, setType, Origin, Chain) |
| **Using XposedService** (service binding, remote prefs, scope requests) | `references/javadoc/service-javadoc/01-service-complete.md` (full service API) AND `references/github/example/app/src/main/java/io/github/libxposed/example/App.kt` (OnServiceListener pattern) AND `references/github/example/app/src/main/java/io/github/libxposed/example/MainActivity.kt` (OnScopeEventListener pattern) |
| **Lifecycle callbacks** | `references/javadoc/api-javadoc/03-Invoker-ExceptionMode-Module-Interface.md` (all Param interfaces + lifecycle methods) |
| **Kotlin module** | `references/github/example/app/src/main/java/io/github/libxposed/example/ModuleMainKt.kt` (Kotlin entry) |
| **Helper library** | Browse `references/github/helper/helper/src/main/java/io/github/libxposed/helper/` |
| **Helper-ktx DSL** | `references/github/helper/helper-ktx/src/main/kotlin/io/github/libxposed/helper/ktx/HookBuilderKt.kt` |
| **Legacy migration** | `references/github/LSPosed-wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API.md` |
| **Scope configuration** | `references/github/LSPosed-wiki/Module-Scope.md` AND scope section below |
| **Error handling** | `references/javadoc/api-javadoc/04-error-package.md` |

**Why this matters:** The official example files at `references/github/example/` are the gold-standard
reference implementation. They contain the correct method names confirmed by the Javadoc audit.
Reading them before writing code ensures you use `onServiceBind` (not `onServiceConnected`),
`onScopeRequestApproved` (not `onScopeRequestGranted`), and other correct names.

---

## Official Resources

| Resource            | URL                                                                                    | Status     |
| ------------------- | -------------------------------------------------------------------------------------- | ---------- |
| API Javadoc         | https://libxposed.github.io/api/                                                       | ✅ Active  |
| Service Javadoc     | https://libxposed.github.io/service/                                                   | ✅ Active  |
| API GitHub repo     | https://github.com/libxposed/api                                                       | ✅ Active  |
| Service GitHub repo | https://github.com/libxposed/service                                                   | ✅ Active  |
| Helper GitHub repo  | https://github.com/libxposed/helper                                                    | ✅ Active  |
| Example module      | https://github.com/libxposed/example                                                   | ✅ Active  |
| LSPosed wiki guide  | https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API | ⚠️ Archived |
| Maven Central — api | https://central.sonatype.com/artifact/io.github.libxposed/api                          | ✅ Active  |
| MvnRepository — all | https://mvnrepository.com/artifact/io.github.libxposed                                 | ✅ Active  |

> **Note:** The LSPosed/LSPosed repository was **archived on Mar 27, 2026** (read-only).
> The `libxposed/*` repos (api, service, helper, example) are the actively maintained successors.

---

## Reference Files — Complete Map

### Tier 1: Raw Scraped Javadoc (ground truth for API signatures)

These files were scraped from the official Javadoc and verified against live pages on 2026-05-03.
They are the definitive source for correct method names and signatures.

| File | Content | Lines |
|------|---------|-------|
| `references/javadoc/INDEX.md` | Master index + key discoveries (correct method names, hierarchy notes) | ~70 |
| `references/javadoc/api-javadoc/01-package-and-XposedInterface.md` | Package summary + full `XposedInterface` (fields, methods, constants) | ~300 |
| `references/javadoc/api-javadoc/02-Chain-HookBuilder-HookHandle-Hooker.md` | `Chain`, `HookBuilder`, `HookHandle`, `Hooker` interfaces | ~200 |
| `references/javadoc/api-javadoc/03-Invoker-ExceptionMode-Module-Interface.md` | `Invoker`, `Invoker.Type`, `ExceptionMode`, `XposedModule`, `XposedModuleInterface`, all Param interfaces | ~380 |
| `references/javadoc/api-javadoc/04-error-package.md` | `XposedFrameworkError`, `HookFailedError` — both extend `Error` (NOT RuntimeException) | ~60 |
| `references/javadoc/service-javadoc/01-service-complete.md` | Full service library: `XposedService`, `XposedServiceHelper`, `OnServiceListener`, `OnScopeEventListener`, `XposedProvider`, `RemotePreferences` | ~295 |

### Tier 2: Cloned GitHub Repos (source code + build files + META-INF)

These are complete clones of the official repositories. Use them for build configurations,
manifest examples, and seeing the actual source code when Javadoc descriptions are insufficient.

| Directory | Content | Key files |
|-----------|---------|-----------|
| `references/github/api/` | Full `libxposed/api` repo | `api/src/main/java/io/github/libxposed/api/XposedInterface.java`; `XposedModule.java`; `XposedModuleInterface.java`; `README.md` |
| `references/github/service/` | Full `libxposed/service` repo | `service/src/main/java/io/github/libxposed/service/XposedService.java`; `XposedServiceHelper.java`; `RemotePreferences.java`; `XposedProvider.java` |
| `references/github/helper/` | Full `libxposed/helper` repo | `helper/src/main/java/io/github/libxposed/helper/HookBuilder.java`; `Reflector.java`; `helper-ktx/.../HookBuilderKt.kt` |
| `references/github/example/` | **Official working example** | `ModuleMain.java`; `ModuleMainKt.kt`; `App.kt`; `MainActivity.kt`; `build.gradle.kts`; `AndroidManifest.xml`; `META-INF/xposed/` files |
| `references/github/LSPosed-wiki/` | Archived wiki pages | `Develop-Xposed-Modules-Using-Modern-Xposed-API.md`; `Module-Scope.md`; `Native-Hook.md`; `New-XSharedPreferences.md` |

---

## Quick Mental Model

```
Module APK
├── META-INF/xposed/
│   ├── java_init.list      ← "com.example.MyModule" (one FQN per line)
│   ├── native_init.list    ← native entries (optional)
│   ├── module.prop         ← minApiVersion=101, targetApiVersion=101
│   └── scope.list          ← target package names (one per line)
└── src/main/java/
    └── MyModule.java       ← extends XposedModule (no-arg constructor)
```

The framework instantiates `MyModule` exactly **once per process** via its **no-arg constructor**, then calls:

1. `attachFramework(XposedInterface)` — automatic; **modules must NOT call this**
2. `onModuleLoaded(ModuleLoadedParam)` — first safe place to initialize
3. `onPackageLoaded(PackageLoadedParam)` — default classloader ready, before `AppComponentFactory` (`@RequiresApi(29)`)
4. `onPackageReady(PackageReadyParam)` — full app classloader ready → **best place for most hooks**
5. `onSystemServerStarting(SystemServerStartingParam)` — system server scope only

---

## Maven Central — Artifacts

| Artifact ID  | Scope for modules | Description                                             |
| ------------ | ----------------- | ------------------------------------------------------- |
| `api`        | **`compileOnly`** | Core hook/lifecycle API — framework provides at runtime |
| `service`    | `implementation`  | Module app ↔ framework IPC (XposedService)              |
| `helper`     | `implementation`  | XposedHelpers replacement utility library               |
| `helper-ktx` | `implementation`  | Kotlin extensions for helper                            |

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("io.github.libxposed:api:101.0.1")
    implementation("io.github.libxposed:service:<version>")   // optional
    implementation("io.github.libxposed:helper:<version>")    // optional
    implementation("io.github.libxposed:helper-ktx:<version>") // optional
}
```

> **Important:** `api` **must** be `compileOnly` for modules. The framework injects the real
> implementation at runtime. Using `implementation` bloats the APK with dead stubs.

---

## Core Hook Pattern

```java
HookHandle handle = hook(method)
    .setPriority(PRIORITY_DEFAULT)
    .setExceptionMode(ExceptionMode.DEFAULT)
    .intercept(chain -> {
        Object arg0 = chain.getArg(0);         // by index
        List<Object> args = chain.getArgs();   // immutable list
        Object thiz = chain.getThisObject();   // null for static AND constructors

        Object result = chain.proceed();                     // same args + same this
        // Object result = chain.proceed(new Object[]{…});  // different args (Object[] NOT List)
        // Object result = chain.proceedWith(otherObj);     // different this
        // Object result = chain.proceedWith(obj, args);    // different both
        return result;
    });

handle.unhook();            // remove hook (idempotent)
handle.getExecutable();     // the hooked Method or Constructor
```

---

## Critical API Facts

Every fact below is verified against the raw Javadoc scrapes in `references/javadoc/`
and the official example source in `references/github/example/`.

| Topic | Correct Fact |
|-------|-------------|
| `chain.proceed(args)` | Takes `Object[]` — **NOT** `List<Object>` |
| `chain.getArgs()` | Returns **immutable** `List<Object>` |
| `chain.getThisObject()` | `null` for **both** static methods **and** constructors |
| `HookHandle` methods | Only `unhook()` and `getExecutable()` — nothing else |
| `Invoker` type mutation | `setType(Invoker.Type)` — **NOT** `toType()` |
| `Invoker.Type.ORIGIN` | Static constant on `Type` interface |
| `Invoker.Type.Chain.FULL` | Static field on `Chain` record — full hook chain |
| `ExceptionMode.DEFAULT` | Third enum constant — follows `module.prop`; defaults to `PROTECTIVE` |
| `XposedModule` constructor | **No-arg** — `public XposedModule()` |
| `ModuleLoadedParam` | `getProcessName()` + `isSystemServer()` — NO `isFirstLoad()` |
| `PackageLoadedParam` | `isFirstPackage()` — NOT `isFirstLoad()` |
| `PackageReadyParam` | Extends `PackageLoadedParam`; adds `getClassLoader()` + `getAppComponentFactory()` |
| `SystemServerStartingParam` | Only `getClassLoader()` — no `getStartParam()` |
| `onPackageLoaded` | `@RequiresApi(29)` |
| Scope filter | Modules MUST filter by `param.getPackageName()` — may receive extra callbacks |
| `HookFailedError` | Extends `Error` via `XposedFrameworkError` — NOT `RuntimeException`. Do not catch it. |

### Service Library — Correct Method Names

Verified against raw Javadoc (`references/javadoc/service-javadoc/01-service-complete.md`)
and official example (`references/github/example/app/src/main/java/io/github/libxposed/example/App.kt`):

| Interface | Correct Methods | Common Wrong Names |
|-----------|----------------|-----|
| `XposedServiceHelper.OnServiceListener` | **`onServiceBind(XposedService)`** — can fire multiple times; **`onServiceDied(XposedService)`** | ~~onServiceConnected~~, ~~onServiceDisconnected~~ |
| `XposedService.OnScopeEventListener` | **`onScopeRequestApproved(List<String>)`**; **`onScopeRequestFailed(String)`** — only 2 methods | ~~onScopeRequestGranted~~, ~~onScopeRequestDenied~~, ~~onScopeRequestError~~ |

---

## Correct Service Listener Pattern

From official example at `references/github/example/app/src/main/java/io/github/libxposed/example/App.kt`:

```kotlin
class App : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        @Volatile var mService: XposedService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)  // call only ONCE
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
    }
}
```

## Correct Scope Request Pattern

From official example at `references/github/example/app/src/main/java/io/github/libxposed/example/MainActivity.kt`:

```kotlin
val callback = object : XposedService.OnScopeEventListener {
    override fun onScopeRequestApproved(approved: List<String>) {
        // scope was granted
    }
    override fun onScopeRequestFailed(message: String) {
        // scope request failed
    }
}
service.requestScope(listOf("com.example.app"), callback)
```

---

## ExceptionMode — Three Values

| Value | Behavior |
|-------|----------|
| `DEFAULT` | Follows `exceptionMode` in `module.prop`; defaults to `PROTECTIVE` if unset |
| `PROTECTIVE` | Exception before `proceed()` → chain continues without hook. After → returns proceeded value. Exceptions FROM `proceed()` always propagate. |
| `PASSTHROUGH` | All hooker exceptions propagate to caller. Recommended for debugging. |

---

## Invoker.Type Hierarchy

```
Invoker.Type  (sealed interface)
 ├── static final ORIGIN         → shortcut for Origin record
 ├── Origin (record)             → skips ALL hooks, calls original directly
 └── Chain (record, int maxPriority)
     ├── Chain.FULL              → full chain (default from getInvoker())
     └── new Chain(priority)     → skips hooks with priority > maxPriority
```

---

## Scope System

- `scope.list` — one package name per line in `META-INF/xposed/scope.list`
- `system` → special virtual name for system server process
- `android` → targets android framework `:ui` process
- Packages whose components ALL run in system server → use `system`, not the package name
- `staticScope=true` in `module.prop` → users cannot add apps outside the list
- Legacy manifest-based scope (`xposedscope` meta-data) also supported — see `references/github/LSPosed-wiki/Module-Scope.md`

---

## module.prop Keys

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `minApiVersion` | int | Yes | Minimum Xposed API version required |
| `targetApiVersion` | int | Yes | Target Xposed API version |
| `staticScope` | boolean | No | Lock scope to scope.list only |
| `exceptionMode` | string | No | `protective` (default) or `passthrough` |

---

## Verification Checklist (use before finalizing code)

Before returning code to the user, verify each item applies correctly:

- [ ] Module class extends `XposedModule` with a **no-arg constructor**
- [ ] `api` dependency uses `compileOnly` (not `implementation`)
- [ ] `java_init.list` contains the fully-qualified class name
- [ ] `module.prop` has both `minApiVersion` and `targetApiVersion`
- [ ] Hook uses `chain.proceed()` or `chain.proceed(Object[])` — NOT `List<Object>`
- [ ] Lifecycle callbacks match correct param types (e.g., `PackageLoadedParam` not `LoadedParam`)
- [ ] Service listener uses `onServiceBind`/`onServiceDied` — NOT `onServiceConnected`/`Disconnected`
- [ ] Scope listener uses `onScopeRequestApproved`/`onScopeRequestFailed` — NOT `Granted`/`Denied`
- [ ] `onPackageLoaded` usage has `@RequiresApi(29)` consideration
- [ ] Module filters `param.getPackageName()` inside callbacks
