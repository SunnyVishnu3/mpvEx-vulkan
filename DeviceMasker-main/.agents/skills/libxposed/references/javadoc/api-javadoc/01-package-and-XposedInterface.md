# RAW SCRAPE — io.github.libxposed.api package-summary
# Source: https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html
# Scraped: 2025-05-03

---

## Package io.github.libxposed.api

Modern Xposed Module API.

This package provides the public API for developing Xposed modules using the modern Xposed
framework. It replaces the legacy XposedBridge API with a redesigned, type-safe interface.

### Getting Started

Module entry classes should extend `XposedModule`. The framework calls `attachFramework()`
automatically; modules **should not** perform initialization work before `onModuleLoaded()` is called.

### Entry Registration

Java entry classes are listed in `META-INF/xposed/java_init.list` (one fully-qualified class name
per line); native entries use `META-INF/xposed/native_init.list`. Place these files under
`src/main/resources/META-INF/xposed/` and Gradle will package them into the APK automatically.

### Module Configuration

Module metadata is specified via standard Android resources (`android:label` for the module name,
`android:description` for the description) and `META-INF/xposed/module.prop` (Java Properties format).

Required properties:
- `minApiVersion` – minimum Xposed API version required
- `targetApiVersion` – target Xposed API version

Optional properties:
- `staticScope` (boolean) – whether the module scope is fixed and users should not apply the module
  on apps outside the scope list
- `exceptionMode` (string) [protective|passthrough] - Default to protective

### Scope

Module scope is defined by a list of package names in `META-INF/xposed/scope.list`. The framework
injects the module into all regular processes declared by those packages. After injection, every loaded
package in that process will trigger callbacks. As a result, modules may receive callbacks beyond the
originally scoped packages, so they should always filter by process name and package name.

A special case applies to components declared with `android:process="system"` in a
`android:sharedUserId="android.uid.system"` package: they run in system server. For packages whose
components all run in system server, adding the package name itself to the scope has no effect.
Instead, system server is represented by the special virtual package name `system`, which should be
used explicitly.

Note that `android` package is still a valid scope target because some of its components declare
`android:process=":ui"` and therefore do not run in system server. Modules scoped to `android` can
still receive events for `android` package loading even though `android` package has no code. By
contrast, `com.android.providers.settings` is not a valid scope target; modules should use the
`system` scope and then wait for the `com.android.providers.settings` package loading event.

### Hook Model

The API uses an **interceptor-chain** model (similar to OkHttp interceptors). Modules implement
`Hooker` and its `intercept(Chain)` method. Hooking is performed through a builder returned by
`hook(Executable)`:

```java
HookHandle handle = hook(method)
    .setPriority(PRIORITY_DEFAULT)
    .setExceptionMode(ExceptionMode.PROTECTIVE)
    .intercept(chain -> {
        // pre-processing
        Object result = chain.proceed();
        // post-processing
        return result;
    });
```

### Invoker System

To call the original (or hooked) method bypassing access checks, obtain an `Invoker` via
`getInvoker(Method)` or `getInvoker(Constructor)`. The invoker type controls what part of the hook
chain is executed (see `Invoker.Type`).

### Module Lifecycle Callbacks

Override the following callbacks in `XposedModule`:
- `onModuleLoaded()` – called once when the module is loaded into the target process.
- `onPackageLoaded()` – called when the default classloader is ready, before `AppComponentFactory`
  instantiation (API 29+).
- `onPackageReady()` – called after the app classloader is created.
- `onSystemServerStarting()` – called once when system server is starting. This callback replaces
  the first package load phase.

### Error Handling

Framework-level errors are reported via subclasses of `XposedFrameworkError`. In particular,
`HookFailedError` indicates a fatal hook failure that should be reported to the framework
maintainers instead of handled by the module.

---

## All Classes and Interfaces in this package

| Class/Interface | Description |
|---|---|
| `XposedInterface` | Xposed interface for modules to operate on application processes |
| `XposedInterface.Chain` | Interceptor chain for a method or constructor |
| `XposedInterface.CtorInvoker<T>` | Invoker for a constructor |
| `XposedInterface.ExceptionMode` | Exception handling mode for hookers |
| `XposedInterface.HookBuilder` | Builder for configuring a hook |
| `XposedInterface.Hooker` | Hooker for a method or constructor |
| `XposedInterface.HookHandle` | Handle for a hook |
| `XposedInterface.Invoker<T,U>` | Invoker for a method or constructor |
| `XposedInterface.Invoker.Type` | Type of the invoker, determines hook chain invoked |
| `XposedInterface.Invoker.Type.Chain` | Invokes from middle of hook chain |
| `XposedInterface.Invoker.Type.Origin` | Invokes original executable, skipping all hooks |
| `XposedInterfaceWrapper` | Wrapper of XposedInterface used by modules |
| `XposedModule` | Super class which all module entry classes should extend |
| `XposedModuleInterface` | Interface for module initialization |
| `XposedModuleInterface.ModuleLoadedParam` | Info about the process in which module is loaded |
| `XposedModuleInterface.PackageLoadedParam` | Info about the package being loaded |
| `XposedModuleInterface.PackageReadyParam` | Info about the package whose classloader is ready |
| `XposedModuleInterface.SystemServerStartingParam` | Info about system server |

---

# RAW SCRAPE — XposedInterface
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.html

public interface XposedInterface

Xposed interface for modules to operate on application processes.

## Fields

```
static final int API_101
```
Behavior changes: all modules
- Modules cannot be injected into zygote; they are only loaded within the process of the scope.
Behavior changes: Modules targeting 101 or higher
- This is the first API version.

```
static final int LIB_API
```
The API version of this **library**. This is a static value for the framework.
Modules should use `getApiVersion()` to check the API version at runtime.

```
static final long PROP_CAP_SYSTEM
```
The framework has the capability to hook system_server and other system processes.

```
static final long PROP_CAP_REMOTE
```
The framework provides remote preferences and remote files support.

```
static final long PROP_RT_API_PROTECTION
```
The framework disallows accessing Xposed API via reflection or dynamically loaded code.

```
static final int PRIORITY_DEFAULT
```
The default hook priority.

```
static final int PRIORITY_LOWEST
```
Execute at the end of the interception chain.

```
static final int PRIORITY_HIGHEST
```
Execute at the beginning of the interception chain.

## Methods

```java
default int getApiVersion()
```
Gets the runtime Xposed API version. Framework implementations must NOT override this method.

```java
@NonNull String getFrameworkName()
```
Gets the Xposed framework name of current implementation.

```java
@NonNull String getFrameworkVersion()
```
Gets the Xposed framework version of current implementation.

```java
long getFrameworkVersionCode()
```
Gets the Xposed framework version code of current implementation.

```java
long getFrameworkProperties()
```
Gets the Xposed framework properties.
Properties with prefix `PROP_RT_` may change among launches.

```java
@NonNull HookBuilder hook(@NonNull Executable origin)
```
Hook a method / constructor.
- Parameters: `origin` – The executable to be hooked
- Returns: The builder for the hook

```java
@NonNull HookBuilder hookClassInitializer(@NonNull Class<?> origin)
```
Hook the static initializer (`<clinit>`) of a class.
The static initializer is treated as a regular `static void()` method with no parameters.
In the `Chain` passed to the hooker:
- `Chain.getExecutable()` returns a synthetic `Method` representing the static initializer.
- `Chain.getThisObject()` always returns `null`.
- `Chain.getArgs()` returns an empty list.
- `Chain.proceed()` returns `null`.
Note: If the class is already initialized, the hook will never be called.
- Parameters: `origin` – The class whose static initializer is to be hooked
- Returns: The builder for the hook

```java
boolean deoptimize(@NonNull Executable executable)
```
Deoptimizes a method / constructor in case hooked callee is not called because of inline.
By deoptimizing the method, the runtime will fall back to calling all callees without inlining.
For example, when a short hooked method B is invoked by method A, the callback to B is not invoked
after hooking, which may mean A has inlined B inside its method body. To force A to call the hooked
B, you can deoptimize A and then your hook can take effect.
Generally, you need to find all the callers of your hooked callee (you can search all callers by
using DexKit: https://github.com/LuckyPray/DexKit). Otherwise, it would be better to change the
hook point or deoptimize the whole app manually (by simply reinstalling the app without uninstall).
- Parameters: `executable` – The method / constructor to deoptimize
- Returns: Indicate whether the deoptimizing succeeded or not

```java
@NonNull Invoker<?,Method> getInvoker(@NonNull Method method)
```
Get a method invoker for the given method. Invocations through invokers will bypass access checks.
The default type of the invoker is `Invoker.Type.Chain.FULL`.
- Parameters: `method` – The method to get the invoker for
- Returns: The method invoker

```java
@NonNull <T> CtorInvoker<T> getInvoker(@NonNull Constructor<T> constructor)
```
Get a constructor invoker for the given constructor. Invocations through invokers will bypass
access checks. The default type of the invoker is `Invoker.Type.Chain.FULL`.
- Type Parameters: `T` – The type of the constructor
- Parameters: `constructor` – The constructor to get the invoker for
- Returns: The constructor invoker

```java
void log(int priority, @Nullable String tag, @NonNull String msg)
```
Writes a message to the Xposed log.
- Parameters: `priority` – The log priority (see Log); `tag` – The log tag; `msg` – The log message

```java
void log(int priority, @Nullable String tag, @NonNull String msg, @Nullable Throwable tr)
```
Writes a message to the Xposed log.
- Parameters: `tr` – An exception to log

```java
@NonNull ApplicationInfo getModuleApplicationInfo()
```
Gets the application info of the module.

```java
@NonNull SharedPreferences getRemotePreferences(@NonNull String group)
```
Gets remote preferences stored in Xposed framework.
Note that those are read-only in hooked apps.
- Parameters: `group` – Group name
- Returns: The preferences
- Throws: `UnsupportedOperationException` if the framework is embedded

```java
@NonNull String[] listRemoteFiles()
```
List all files in the module's shared data directory.
- Returns: The file list
- Throws: `UnsupportedOperationException` if the framework is embedded

```java
@NonNull ParcelFileDescriptor openRemoteFile(@NonNull String name) throws FileNotFoundException
```
Open a file in the module's shared data directory. The file is opened in read-only mode.
- Parameters: `name` – File name, must not contain path separators and . or ..
- Returns: The file descriptor
- Throws: `FileNotFoundException` if the file does not exist or the path is forbidden
- Throws: `UnsupportedOperationException` if the framework is embedded
