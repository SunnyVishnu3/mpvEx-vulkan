# RAW SCRAPE — io.github.libxposed.api.error package
# Source: https://libxposed.github.io/api/io/github/libxposed/api/error/package-summary.html
# Scraped: 2025-05-03

## Exception Classes

| Class | Description |
|---|---|
| `HookFailedError` | Thrown to indicate that a hook failed due to framework internal error |
| `XposedFrameworkError` | Thrown to indicate that the Xposed framework function is broken |

---

# RAW SCRAPE — XposedFrameworkError
# Source: https://libxposed.github.io/api/io/github/libxposed/api/error/XposedFrameworkError.html

Hierarchy:
Object → Throwable → Error → XposedFrameworkError

Direct Known Subclasses: `HookFailedError`

public class XposedFrameworkError
extends Error
implements Serializable

Thrown to indicate that the Xposed framework function is broken.

## Constructors

```java
public XposedFrameworkError(String message)
public XposedFrameworkError(String message, Throwable cause)
public XposedFrameworkError(Throwable cause)
```

---

# RAW SCRAPE — HookFailedError
# Source: https://libxposed.github.io/api/io/github/libxposed/api/error/HookFailedError.html

Hierarchy:
Object → Throwable → Error → XposedFrameworkError → HookFailedError

public class HookFailedError
extends XposedFrameworkError
implements Serializable

Thrown to indicate that a hook failed due to framework internal error.

Design Note: This inherits from `Error` rather than `RuntimeException` because hook failures are
considered fatal framework bugs. Module developers **should not** attempt to catch this error to
provide fallbacks. Instead, please report the issue to the framework maintainers so it can be
fixed at the root.

## Constructors

```java
public HookFailedError(String message)
public HookFailedError(String message, Throwable cause)
public HookFailedError(Throwable cause)
```
