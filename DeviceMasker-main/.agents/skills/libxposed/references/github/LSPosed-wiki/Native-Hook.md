To use the native hook, you have to know how to write an NDK project first.

### How Native Hook Works

Native Hook can help you hook native functions of an app. Whenever a new native library loaded, LSPosed will invoke a callback of the module and the module can then perform hooks on this native library.

### Header File

To use Native hook, you have to first create a header file with the following content:
```c++

typedef int (*HookFunType)(void *func, void *replace, void **backup);

typedef int (*UnhookFunType)(void *func);

typedef void (*NativeOnModuleLoaded)(const char *name, void *handle);

typedef struct {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
} NativeAPIEntries;

typedef NativeOnModuleLoaded (*NativeInit)(const NativeAPIEntries *entries);
```

### Entries

After creating the header file, you can then create a source file with the entry function:
```c++
extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries);
```

Notice the name `native_init` has to be kept and exported, so we have to use the attribute `[[gnu::visibility("default")]] [[gnu::used]]` for this purpose. `extern "C"` is to make the name `native_init` to be in c style.

The `NativeAPIEntries` object contains necessary util functions for you to hook (Do not try to modify its content. It will crash.). Currently, they are `hook_fun` and `unhook_func`, which can be used to hook and unhook functions respectively. The return value of the function is the callback function which will be called once a library is loaded by `dlopen`. We will talk about this callback function later.

You can perform hooks on system libraries in `native_init`.


### Callback

The callback function should be in type `NativeOnModuleLoaded`. It will be called by LSPosed each time a library is loaded. It receives two parameters: `name` is the name of the loaded library (e.g. `/xxx/libtarget.so`); `handle` is the handle of the library which you can use to `dlsym`. You can perform `dlsym` of this library to get the pointer of the target function can call the `hook_func`.

### JNIEnv Hooks

You may try to hook some JNIEnv functions like `GetMethodId`. Since the function is in `libart.so` which you cannot easily `dlopen`, you can try to create `JNI_OnLoad` to let Android System give you a `JavaVm` and then get a `JNIEnv` once your library is loaded. Then you can get the JNIEnv functions by the `JNIEnv` object.

### Tell Entries to Framework

Naturally, like Java hook, you have to tell which libraries of your module contain `native_init`. You can do this by placing the name of your libraries to the `assets/native_init` file of your apk. Then you have to manually `System.loadLibrary` your module once your module is loaded.


### Simple Example
Here is a simple example which use native hook:

assets/native_init
```
libexample.so
```

assets/xposed_init
```
org.lsposed.example.MainHook
```

AndroidManifest.xml
```xml
<application ...
    android:multiArch="true"
    android:extractNativeLibs="false"
>
```

MainHook.kt
```kotlin
package org.lsposed.example

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "org.lsposed.target") {
            try {
                System.loadLibrary("example")
            } catch (e: Throwable) {
                LogUtil.e(e)
            }
        }
    }
}
```

example.cc
```c++
static HookFunType hook_func = nullptr;

int (*backup)();

int fake() {
    return backup() + 1;
}

FILE *(*backup_fopen)(const char *filename, const char *mode);

FILE *fake_fopen(const char *filename, const char *mode) {
    if (strstr(filename, "banned")) return nullptr;
    return backup_fopen(filename, mode);
}

jclass (*backup_FindClass)(JNIEnv *env, const char *name);
jclass fake_FindClass(JNIEnv *env, const char *name)
{
    if(!strcmp(name, "dalvik/system/BaseDexClassLoader"))
        return nullptr;
    return backup_FindClass(env, name);
}

void on_library_loaded(const char *name, void *handle) {
    // hooks on `libtarget.so`
    if (std::string(name).ends_with("libtarget.so")) {
        void *target = dlsym(handle, "target_fun");
        hook_func(target, (void *) fake, (void **) &backup);
    }
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
jint JNI_OnLoad(JavaVM *jvm, void*) {
    JNIEnv *env = nullptr;
    jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    hook_func((void *)env->functions->FindClass, (void *)fake_FindClass, (void **)&backup_FindClass);
    return JNI_VERSION_1_6;
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]]
NativeOnModuleLoaded native_init(const NativeAPIEntries *entries) {
    hook_func = entries->hook_func;
    // system hooks
    hook_func((void*) fopen, (void*) fake_fopen, (void**) &backup_fopen);
    return on_library_loaded;
}
```
