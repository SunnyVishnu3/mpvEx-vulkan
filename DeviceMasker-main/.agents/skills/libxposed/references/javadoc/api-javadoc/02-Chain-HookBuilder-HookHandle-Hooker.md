# RAW SCRAPE — XposedInterface.Chain
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Chain.html
# Scraped: 2025-05-03

public static interface XposedInterface.Chain

Interceptor chain for a method or constructor.
Chain objects cannot be shared among threads or reused after `XposedInterface.Hooker.intercept(Chain)` ends.

## Methods

```java
@NonNull Executable getExecutable()
```
Gets the method / constructor being hooked.

```java
Object getThisObject()
```
Gets the `this` pointer for the call, or `null` for static methods.

```java
@NonNull List<Object> getArgs()
```
Gets the arguments. The returned list is **immutable**. If you want to change the arguments, you
should call `proceed(Object[])` or `proceedWith(Object, Object[])` with the new arguments.

```java
Object getArg(int index) throws IndexOutOfBoundsException, ClassCastException
```
Gets the argument at the given index.
- Parameters: `index` – The argument index
- Returns: The argument at the given index
- Throws: `IndexOutOfBoundsException` if index is out of bounds
- Throws: `ClassCastException` if the argument cannot be cast to the expected type

```java
Object proceed() throws Throwable
```
Proceeds to the next interceptor in the chain with the same arguments and `this` pointer.
- Returns: The result returned from next interceptor or the original executable if current
  interceptor is the last one in the chain. For void methods and constructors, always returns `null`.
- Throws: `Throwable` if any interceptor or the original executable throws an exception

```java
Object proceed(@NonNull Object[] args) throws Throwable
```
Proceeds to the next interceptor in the chain with the given arguments and the same `this` pointer.
- Parameters: `args` – The arguments used for the call
- Returns: Same as `proceed()`. For void methods and constructors, always returns `null`.
- Throws: `Throwable` if any interceptor or the original executable throws an exception

```java
Object proceedWith(@NonNull Object thisObject) throws Throwable
```
Proceeds to the next interceptor in the chain with the same arguments and given `this` pointer.
Static method interceptors should not call this.
- Parameters: `thisObject` – The `this` pointer for the call
- Returns: Same as `proceed()`. For void methods and constructors, always returns `null`.
- Throws: `Throwable` if any interceptor or the original executable throws an exception

```java
Object proceedWith(@NonNull Object thisObject, @NonNull Object[] args) throws Throwable
```
Proceeds to the next interceptor in the chain with the given arguments and `this` pointer.
Static method interceptors should not call this.
- Parameters: `thisObject` – The `this` pointer for the call; `args` – The arguments used for the call
- Returns: Same as `proceed()`. For void methods and constructors, always returns `null`.
- Throws: `Throwable` if any interceptor or the original executable throws an exception

---

# RAW SCRAPE — XposedInterface.HookBuilder
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.HookBuilder.html

public static interface XposedInterface.HookBuilder

Builder for configuring a hook.

## Methods

```java
HookBuilder setPriority(int priority)
```
Sets the priority of the hook. Hooks with higher priority will be called before hooks with lower
priority. The default priority is `XposedInterface.PRIORITY_DEFAULT`.
- Parameters: `priority` – The priority of the hook
- Returns: The builder itself for chaining

```java
HookBuilder setExceptionMode(@NonNull ExceptionMode mode)
```
Sets the exception handling mode for the hook. The default mode is `ExceptionMode.DEFAULT`.
- Parameters: `mode` – The exception handling mode
- Returns: The builder itself for chaining

```java
@NonNull HookHandle intercept(@NonNull Hooker hooker)
```
Sets the hooker for the method / constructor and builds the hook.
- Parameters: `hooker` – The hooker object
- Returns: The handle for the hook
- Throws: `IllegalArgumentException` if origin is framework internal or `Constructor.newInstance()`,
  or hooker is invalid
- Throws: `HookFailedError` if hook fails due to framework internal error

---

# RAW SCRAPE — XposedInterface.HookHandle
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.HookHandle.html

public static interface XposedInterface.HookHandle

Handle for a hook.

## Methods

```java
@NonNull Executable getExecutable()
```
Gets the method / constructor being hooked.

```java
void unhook()
```
Cancels the hook. This method is idempotent. It is safe to call this method multiple times.

---

# RAW SCRAPE — XposedInterface.Hooker
# Source: https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Hooker.html

public static interface XposedInterface.Hooker

Hooker for a method or constructor.

## Methods

```java
Object intercept(@NonNull Chain chain) throws Throwable
```
Intercepts a method / constructor call.
- Parameters: `chain` – The interceptor chain for the call
- Returns: The result to be returned from the interceptor. If the hooker does not want to change
  the result, it should call `chain.proceed()` and return its result.
  For void methods and constructors, the return value is ignored by the framework.
- Throws: `Throwable` – Throw any exception from the interceptor. The exception will propagate to
  the caller if not caught by any interceptor.
