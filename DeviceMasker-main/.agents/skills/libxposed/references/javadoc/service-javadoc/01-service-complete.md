# RAW SCRAPE — io.github.libxposed.service package
# Source: https://libxposed.github.io/service/io/github/libxposed/service/package-summary.html
# Scraped: 2025-05-03

## Interfaces

| Interface | Description |
|---|---|
| `XposedServiceHelper.OnServiceListener` | Callback interface for Xposed service |
| `XposedService.OnScopeEventListener` | Callback interface for module scope request |

## Classes

| Class | Description |
|---|---|
| `XposedServiceHelper` | Helper to register listener and receive framework service |
| `XposedProvider` | ContentProvider implementation for framework ↔ module IPC |
| `RemotePreferences` | SharedPreferences implementation backed by framework storage |
| `RemotePreferences.Editor` | Editor for RemotePreferences |
| `XposedService.ServiceException` | Exception for service errors |
| `XposedService` | The main framework service object |

---

# RAW SCRAPE — XposedService
# Source: https://libxposed.github.io/service/io/github/libxposed/service/XposedService.html

public final class XposedService

## Nested Classes

```
public final class XposedService.ServiceException
public interface XposedService.OnScopeEventListener
```

## Fields (same as XposedInterface)

```java
public static final long PROP_CAP_SYSTEM
public static final long PROP_CAP_REMOTE
public static final long PROP_RT_API_PROTECTION
```

## Methods

```java
int getApiVersion()
```
Get the Xposed API version of current implementation.

```java
String getFrameworkName()
```
Get the Xposed framework name of current implementation.

```java
String getFrameworkVersion()
```
Get the Xposed framework version of current implementation.

```java
long getFrameworkVersionCode()
```
Get the Xposed framework version code of current implementation.

```java
long getFrameworkProperties()
```
Gets the Xposed framework properties. Properties with prefix `PROP_RT_` may change among launches.

```java
List<String> getScope()
```
Get the application scope of current module.
Returns: Module scope

```java
void requestScope(List<String> packages, XposedService.OnScopeEventListener callback)
```
Request to add a new app to the module scope.
- Parameters: `packages` – Packages to be added; `callback` – Callback for completion or error

```java
void removeScope(List<String> packages)
```
Remove an app from the module scope.
- Parameters: `packages` – Packages to be removed

```java
synchronized SharedPreferences getRemotePreferences(String group)
```
Get remote preferences from Xposed framework. If the group does not exist, it will be created.
- Parameters: `group` – Group name
- Returns: The preferences

```java
synchronized void deleteRemotePreferences(String group)
```
Delete a group of remote preferences.
- Parameters: `group` – Group name

```java
Array<String> listRemoteFiles()
```
List all files in the module's shared data directory.
Returns: The file list

```java
ParcelFileDescriptor openRemoteFile(String name)
```
Open a file in the module's shared data directory. The file will be created if not exists.
- Parameters: `name` – File name, must not contain path separators and .
- Returns: The file descriptor

```java
boolean deleteRemoteFile(String name)
```
Delete a file in the module's shared data directory.
- Parameters: `name` – File name, must not contain path separators and .
- Returns: `true` if successful, `false` if the file does not exist

---

# RAW SCRAPE — XposedServiceHelper
# Source: https://libxposed.github.io/service/io/github/libxposed/service/XposedServiceHelper.html

public final class XposedServiceHelper

## Nested Interface

```
public interface XposedServiceHelper.OnServiceListener
```
Callback interface for Xposed service.

## Constructor

```java
XposedServiceHelper()
```

## Methods

```java
static void registerListener(XposedServiceHelper.OnServiceListener listener)
```
Register a ServiceListener to receive service binders from Xposed frameworks.
**This method should only be called once.**
- Parameters: `listener` – Listener to register

---

# RAW SCRAPE — XposedServiceHelper.OnServiceListener
# Source: https://libxposed.github.io/service/io/github/libxposed/service/XposedServiceHelper.OnServiceListener.html

public interface XposedServiceHelper.OnServiceListener

Callback interface for Xposed service.

## Methods

```java
abstract void onServiceBind(XposedService service)
```
Callback when the service is connected.
**This method could be called multiple times if multiple Xposed frameworks exist.**
- Parameters: `service` – Service instance

```java
abstract void onServiceDied(XposedService service)
```
Callback when the service is dead.
- Parameters: `service` – Service instance

---

# RAW SCRAPE — XposedService.OnScopeEventListener
# Source: https://libxposed.github.io/service/io/github/libxposed/service/XposedService.OnScopeEventListener.html

public interface XposedService.OnScopeEventListener

Callback interface for module scope request.

## Methods

```java
void onScopeRequestApproved(List<String> approved)
```
Callback when the request is approved.
- Parameters: `approved` – Approved packages for the request

```java
void onScopeRequestFailed(String message)
```
Callback when the request is failed.
- Parameters: `message` – Error message

---

# RAW SCRAPE — XposedProvider
# Source: https://libxposed.github.io/service/io/github/libxposed/service/XposedProvider.html

public final class XposedProvider

ContentProvider implementation used internally for framework ↔ module app IPC.
Do not call any methods directly — just declare it in AndroidManifest.xml.

## Constructor

```java
XposedProvider()
```

## Methods (ContentProvider overrides — internal use only)

```java
boolean onCreate()
Cursor query(Uri uri, Array<String> projection, String selection, Array<String> selectionArgs, String sortOrder)
String getType(Uri uri)
Uri insert(Uri uri, ContentValues values)
int delete(Uri uri, String selection, Array<String> selectionArgs)
int update(Uri uri, ContentValues values, String selection, Array<String> selectionArgs)
Bundle call(String method, String arg, Bundle extras)
```

Manifest declaration required:
```xml
<provider
    android:name="io.github.libxposed.service.XposedProvider"
    android:authorities="${applicationId}.xposed"
    android:exported="true" />
```

---

# RAW SCRAPE — RemotePreferences
# Source: https://libxposed.github.io/service/io/github/libxposed/service/RemotePreferences.html

public final class RemotePreferences

SharedPreferences implementation backed by Xposed framework storage.
Obtained via `XposedService.getRemotePreferences(String group)`.

## Nested Class

```
public class RemotePreferences.Editor
```

## Methods (SharedPreferences implementation)

```java
Map<String, out Object> getAll()
String getString(String key, String defValue)
Set<String> getStringSet(String key, Set<String> defValues)
int getInt(String key, int defValue)
long getLong(String key, long defValue)
float getFloat(String key, float defValue)
boolean getBoolean(String key, boolean defValue)
boolean contains(String key)
void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)
RemotePreferences.Editor edit()
```

---

# RAW SCRAPE — RemotePreferences.Editor
# Source: https://libxposed.github.io/service/io/github/libxposed/service/RemotePreferences.Editor.html

public class RemotePreferences.Editor

## Constructor

```java
RemotePreferences.Editor()
```

## Methods (SharedPreferences.Editor implementation)

```java
Editor putString(String key, String value)
Editor putStringSet(String key, Set<String> values)
Editor putInt(String key, int value)
Editor putLong(String key, long value)
Editor putFloat(String key, float value)
Editor putBoolean(String key, boolean value)
Editor remove(String key)
Editor clear()
boolean commit()
void apply()
```
