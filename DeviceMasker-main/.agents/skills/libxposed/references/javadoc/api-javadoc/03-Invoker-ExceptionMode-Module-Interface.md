# RAW SCRAPE — XposedInterface.Invoker
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Invoker.html
# Scraped: 2025-05-03

public static interface XposedInterface.Invoker<T extends XposedInterface.Invoker<T,U>, U extends Executable>

Invoker for a method or constructor. Invocations through invokers will bypass access checks.
All Known Subinterfaces: `XposedInterface.CtorInvoker<T>`

## Methods

```java
T setType(@NonNull Invoker.Type type)
```
Sets the type of the invoker, which determines the hook chain to be invoked.

```java
Object invoke(Object thisObject, Object... args)
    throws InvocationTargetException, IllegalArgumentException, IllegalAccessException
```
Invokes the method (or the constructor as a method) through the hook chain determined by
the invoker's type.
- Parameters: `thisObject` – For non-static calls, the `this` pointer, otherwise `null`; `args` – The arguments
- Returns: The result. For void methods and constructors, always returns `null`.
- Throws: `InvocationTargetException`, `IllegalArgumentException`, `IllegalAccessException`
- See Also: `Method.invoke(Object, Object...)`

```java
Object invokeSpecial(@NonNull Object thisObject, Object... args)
    throws InvocationTargetException, IllegalArgumentException, IllegalAccessException
```
Invokes the special (non-virtual) method (or the constructor as a method) on a given object
instance, similar to the functionality of `CallNonVirtual<type>Method` in JNI, which invokes
an instance (nonstatic) method on a Java object. This method is useful when you need to call
a specific method on an object, bypassing any overridden methods in subclasses and directly
invoking the method defined in the specified class.
This method is useful when you need to call `super.xxx()` in a hooked constructor.
- Parameters: `thisObject` – The `this` pointer (NonNull); `args` – The arguments
- Returns: The result. For void methods and constructors, always returns `null`.
- Throws: `InvocationTargetException`, `IllegalArgumentException`, `IllegalAccessException`

---

# RAW SCRAPE — XposedInterface.Invoker.Type
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Invoker.Type.html

public static sealed interface XposedInterface.Invoker.Type
permits XposedInterface.Invoker.Type.Origin, XposedInterface.Invoker.Type.Chain

Type of the invoker, which determines the hook chain to be invoked.
All Known Implementing Classes: `XposedInterface.Invoker.Type.Chain`, `XposedInterface.Invoker.Type.Origin`

## Fields

```java
static final XposedInterface.Invoker.Type.Origin ORIGIN
```
A convenience constant for `XposedInterface.Invoker.Type.Origin`.

## Nested Classes

- `static final record XposedInterface.Invoker.Type.Chain` – Invokes from middle of hook chain
- `static final record XposedInterface.Invoker.Type.Origin` – Invokes original, skipping all hooks

---

# RAW SCRAPE — XposedInterface.Invoker.Type.Chain
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Invoker.Type.Chain.html

public static record XposedInterface.Invoker.Type.Chain(int maxPriority)
extends Record
implements XposedInterface.Invoker.Type

Invokes the executable starting from the middle of the hook chain, skipping all hooks with priority
higher than the given value.

Record Component: `maxPriority` – The maximum priority of hooks to include in the chain

## Fields

```java
public static final XposedInterface.Invoker.Type.Chain FULL
```
Invoking the executable with full hook chain.

## Constructor

```java
public Chain(int maxPriority)
```
Creates an instance of a `Chain` record class.
- Parameters: `maxPriority` – the value for the maxPriority record component

## Methods

```java
public int maxPriority()
```
Returns the value of the maxPriority record component.

Standard record methods: `toString()`, `hashCode()`, `equals(Object o)`

---

# RAW SCRAPE — XposedInterface.Invoker.Type.Origin
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Invoker.Type.Origin.html

public static record XposedInterface.Invoker.Type.Origin()
extends Record
implements XposedInterface.Invoker.Type

Invokes the original executable, skipping all hooks.
(Use the `Invoker.Type.ORIGIN` constant instead of constructing manually.)

## Constructor

```java
public Origin()
```
Creates an instance of an `Origin` record class.

---

# RAW SCRAPE — XposedInterface.ExceptionMode
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.ExceptionMode.html

public static enum XposedInterface.ExceptionMode
extends Enum<XposedInterface.ExceptionMode>
implements Serializable, Comparable<XposedInterface.ExceptionMode>, Constable

Exception handling mode for hookers. This determines how the framework handles exceptions thrown
by hookers. The default mode is `DEFAULT`.

## Enum Constants

### DEFAULT
```
public static final XposedInterface.ExceptionMode DEFAULT
```
Follows the global exception mode configured in `module.prop`. Defaults to `PROTECTIVE` if not specified.

### PROTECTIVE
```
public static final XposedInterface.ExceptionMode PROTECTIVE
```
Any exception thrown by the **hooker** will be caught and logged, and the call will proceed as if
no hook exists. This mode is recommended for most cases, as it can prevent crashes caused by hook errors.
- If the exception is thrown before `Chain.proceed()`, the framework will continue the chain without the hook.
- If the exception is thrown after proceed, the framework will return the value / exception proceeded.
- Exceptions thrown by proceed will always be propagated.

### PASSTHROUGH
```
public static final XposedInterface.ExceptionMode PASSTHROUGH
```
Any exception thrown by the hooker will be propagated to the caller as usual.
Recommended for debugging purposes, as it can help you find and fix errors in your hooks.

## Methods

```java
public static ExceptionMode[] values()
```
Returns an array containing the constants of this enum class, in the order they are declared.

```java
public static ExceptionMode valueOf(String name)
```
Returns the enum constant of this class with the specified name.
- Throws: `IllegalArgumentException` if no constant with the specified name exists

---

# RAW SCRAPE — XposedModule
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedModule.html

public abstract class XposedModule
extends XposedInterfaceWrapper
implements XposedModuleInterface

Super class which all Xposed module entry classes should extend.
Entry classes will be instantiated exactly once for each process. Modules should not do
initialization work before `XposedModuleInterface.onModuleLoaded(ModuleLoadedParam)` is called.

## Constructor

```java
public XposedModule()
```

## Inherited Nested Types (from XposedInterface)
`Chain`, `CtorInvoker<T>`, `ExceptionMode`, `HookBuilder`, `Hooker`, `HookHandle`, `Invoker<T,U>`

## Inherited Nested Types (from XposedModuleInterface)
`ModuleLoadedParam`, `PackageLoadedParam`, `PackageReadyParam`, `SystemServerStartingParam`

## Inherited Fields (from XposedInterface)
`API_101`, `LIB_API`, `PRIORITY_DEFAULT`, `PRIORITY_HIGHEST`, `PRIORITY_LOWEST`,
`PROP_CAP_REMOTE`, `PROP_CAP_SYSTEM`, `PROP_RT_API_PROTECTION`

## Inherited Methods (from XposedInterfaceWrapper — all final)
`attachFramework`, `deoptimize`, `getApiVersion`, `getFrameworkName`, `getFrameworkProperties`,
`getFrameworkVersion`, `getFrameworkVersionCode`, `getInvoker` (×2), `getModuleApplicationInfo`,
`getRemotePreferences`, `hook`, `hookClassInitializer`, `listRemoteFiles`, `log` (×2), `openRemoteFile`

## Inherited Methods (from XposedModuleInterface — default, override these)
`onModuleLoaded`, `onPackageLoaded`, `onPackageReady`, `onSystemServerStarting`

---

# RAW SCRAPE — XposedInterfaceWrapper
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterfaceWrapper.html

public class XposedInterfaceWrapper
extends Object
implements XposedInterface

Wrapper of `XposedInterface` used by modules to shield framework implementation details.
Direct Known Subclasses: `XposedModule`

## Constructor

```java
public XposedInterfaceWrapper()
```

## Methods (all final — cannot be overridden by framework)

```java
public final void attachFramework(@NonNull XposedInterface base)
```
Attaches the framework interface to the module. Modules should NEVER call this method.

All other methods delegate to the base and are `final`:
`getApiVersion()`, `getFrameworkName()`, `getFrameworkVersion()`, `getFrameworkVersionCode()`,
`getFrameworkProperties()`, `hook(Executable)`, `hookClassInitializer(Class)`,
`deoptimize(Executable)`, `getInvoker(Method)`, `getInvoker(Constructor)`,
`log(int, String, String)`, `log(int, String, String, Throwable)`,
`getRemotePreferences(String)`, `getModuleApplicationInfo()`, `listRemoteFiles()`, `openRemoteFile(String)`

---

# RAW SCRAPE — XposedModuleInterface
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.html

public interface XposedModuleInterface

Interface for module initialization.
All Known Implementing Classes: `XposedModule`

## Methods (all default — override only what you need)

```java
default void onModuleLoaded(@NonNull ModuleLoadedParam param)
```
Gets notified when the module is loaded into the target process.
This callback is guaranteed to be called exactly once for a process.

```java
@RequiresApi(29)
default void onPackageLoaded(@NonNull PackageLoadedParam param)
```
Gets notified when a package is loaded into the app process. This is the time when the default
classloader is ready but before the instantiation of custom `AppComponentFactory`.
This callback could be invoked multiple times for the same process on each package.

```java
default void onPackageReady(@NonNull PackageReadyParam param)
```
Gets notified when custom `AppComponentFactory` has instantiated the app classloader and is ready
to create `Activity` and `Service`.
This callback could be invoked multiple times for the same process on each package.

```java
default void onSystemServerStarting(@NonNull SystemServerStartingParam param)
```
Gets notified when system server is ready to start critical services.

---

# RAW SCRAPE — ModuleLoadedParam
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.ModuleLoadedParam.html

public static interface XposedModuleInterface.ModuleLoadedParam

Wraps information about the process in which the module is loaded.
This information only indicates the state at the time of loading and will not be updated.

## Methods

```java
boolean isSystemServer()
```
Returns whether the current process is system server.
Returns: `true` if the current process is system server.

```java
@NonNull String getProcessName()
```
Gets the process name.
Returns: The process name.

---

# RAW SCRAPE — PackageLoadedParam
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.PackageLoadedParam.html

public static interface XposedModuleInterface.PackageLoadedParam
All Known Subinterfaces: `PackageReadyParam`

Wraps information about the package being loaded.

## Methods

```java
@NonNull String getPackageName()
```
Gets the package name of the current package.

```java
@NonNull ApplicationInfo getApplicationInfo()
```
Gets the ApplicationInfo of the current package.

```java
boolean isFirstPackage()
```
Returns whether this is the first and main package loaded in the app process.
Returns: `true` if this is the first package.

```java
@RequiresApi(29)
@NonNull ClassLoader getDefaultClassLoader()
```
Gets the default classloader of the current package. This is the classloader that loads the app's
code, resources and custom `AppComponentFactory`.

---

# RAW SCRAPE — PackageReadyParam
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.PackageReadyParam.html

public static interface XposedModuleInterface.PackageReadyParam
extends PackageLoadedParam

Wraps information about the package whose classloader is ready.
This information only indicates the state at the time of loading and will not be updated.

Inherits all of `PackageLoadedParam`. Additionally:

## Methods

```java
@NonNull ClassLoader getClassLoader()
```
Gets the classloader of the current package. It may be different from `getDefaultClassLoader()` if
the package has a custom `AppComponentFactory` that creates a different classloader.

```java
@RequiresApi(28)
@NonNull AppComponentFactory getAppComponentFactory()
```
Gets the `AppComponentFactory` of the current package.

---

# RAW SCRAPE — SystemServerStartingParam
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.SystemServerStartingParam.html

public static interface XposedModuleInterface.SystemServerStartingParam

Wraps information about system server.
This information only indicates the state at the time of loading and will not be updated.

## Methods

```java
@NonNull ClassLoader getClassLoader()
```
Gets the class loader of system server.
