This is a brief document about developing Xposed Modules using modern Xposed API.

### API Changes
Compared to the legacy XposedBridge APIs, the modern API has the following differences:
1. Java entry now uses `META-INF/xposed/java_init.list` instead of `assets/xposed_init`; native entry now uses `META-INF/xposed/native_init.list`. Create a file in `src/main/resources/META-INF`, and Gradle will automatically package files into your APK.
2. Modern API does not use metadata anymore as well. Module name uses the `android:label` resource; module description uses the `android:description` resource; scope list uses `META-INF/xposed/scope.list` (one line for one package name); module configuration uses `META-INF/xposed/module.prop` (in format of [Java properties](https://docs.oracle.com/javase/7/docs/api/java/util/Properties.html)).
3. Java entry should now implement `io.github.libxposed.api.XposedModule`. Note that `XposedModule` no longer receives `XposedInterface` and `ModuleLoadedParam` in its constructor; the framework calls `attachFramework(XposedInterface)` automatically. Modules **should not** perform initialization before `onModuleLoaded()` is called.
4. Hook APIs use an **OkHttp-style interceptor chain** model. Modules implement typed `Hooker<T>` interfaces with an `intercept(Chain<T> chain)` method. Hooking methods now return `HookBuilder` for configuring priority and exception mode. We no longer provide interfaces like `XposedHelpers` in the framework anymore. But we will offer official libraries for a more friendly development kit. See [libxposed/helper](https://github.com/libxposed/helper) for this developing library.
5. You can now deoptimize a specific method (accepting an `Executable` parameter) to bypass method inline (especially when hooking System Framework). We also introduce useful APIs through the **Invoker system**: use `getInvoker(Method)` or `getInvoker(Constructor)` to obtain an invoker, which provides `invokeSpecial` and `newInstanceSpecial` as methods on the invoker objects.
6. Resource hooks are removed. Resource hooks are hard to maintain and caused many problems previously, so it will not be supported.
7. You can communicate to the Xposed framework now. With the help of this feature, you can **dynamically request scope**, **share SharedPreferences or blob file** across your module and hooked app, **check framework's name and version**, and more... To achieve this, you should register an Xposed service listener in your module, and once your module app is launched, the Xposed framework will send you a service to communicate with the framework. See [libxposed/service](https://github.com/libxposed/service) for more details. As a result, **module apps are no longer hooked by themselves**.

Module configuration uses entries as following:
|Name|Format|Optional|Meaning|
|:-|:-|:-|:-|
|minApiVersion|int|No|Indicates the minimal Xposed API version required by the module|
|targetApiVersion|int|No|Indicates the target Xposed API version required by the module|
|staticScope|boolean|Yes|Indicates whether users should not apply the module on any other app out of scope|

Comparison among content sharing APIs:
|Name|API|Supported|Storage Location|Change Listener|Large Content|
|:-:|:-:|:-:|:-|:-:|:-:|
|[New XSharedPreferences](https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences)|Legacy(ext)|❌ Since v2.1.0|/data/misc/\<random\>/prefs/\<module\>|❌|❌|
|[XSharedPreferences](https://api.xposed.info/reference/de/robv/android/xposed/XSharedPreferences.html)|Legacy|✅ Since v2.0.0|Module apps' internal storage|❌|❌|
|Remote Preferences|Modern|✅ Since v1.9.0|LSPosed database|✅|❌|
|Remote Files|Modern|✅ Since v1.9.0|/data/adb/lspd/modules/\<user\>/\<module\>|❌|✅|
