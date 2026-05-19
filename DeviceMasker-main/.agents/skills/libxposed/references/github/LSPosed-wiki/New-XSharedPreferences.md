## New XSharedPreferences

Since LSPosed API 93, a new `XSharedPreferences` is provided targeted for modules with sdk > 27. Modules developers can enable this new `XSharedPreferences` support by modifying the meta-data of your modules to set `xposedminversion` to 93 or above, or add `xposedsharedprefs` meta-data.


### For the module
When enabled, LSPosed will automatically hook `ContextImpl.getPreferencesDir()` so that your `Context.getSharedPreferences` and `PreferenceFragment` could work without further modification. Also, LSPosed hooks `ContextImpl.checkMode(int)` so that you can easily get a world readable shared preference by passing `Context.MODE_WORLD_READABLE` to `Context.getSharedPreferences`.

If you are using `xposedsharedprefs` meta-data to indicate your module will use this new feature, you probably want to ensure this feature is working properly. The following code snippet will do the magic:
<details>
<summary>Java</summary>

```java
SharedPreferences pref;
try {
    pref = context.getSharedPreferences(MY_PREF_NAME, Context.MODE_WORLD_READABLE);
} catch (SecurityException ignored) {
    // The new XSharedPreferences is not enabled or module's not loading
    pref = null; // other fallback, if any
}
```
</details>
<details>
<summary>Kotlin</summary>

```kotlin
val pref = try {
    context.getSharedPreferences(MY_PREF_NAME, Context.MODE_WORLD_READABLE)
} catch (e: SecurityException) {
    // The new XSharedPreferences is not enabled or module's not loading
    null // other fallback, if any
}
```
</details>

Notice that LSPosed will help you to make the preference world readable if and only if you are getting preference in mode `Context.MODE_WORLD_READABLE`.

### For the hooked app

To read the preference from the hooked app, you can simply use `XSharedPreferences(String packageName)` or `XSharedPreferences(String packageName, String prefFileName)` to retrieve the preference. Notice that you cannot use the `XSharedPreferences(File prefFile)` because the preference file is stored in a random directory.

You should check the permission before you read anything from the module. You should also load the preference if you need it. Try to split the preference file to multiple files for different purposes. Here's an example:

<details>
<summary>Java</summary>

```java
public class XposedInit implements IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

    private static XSharedPreferences getPref(String path) {
        XSharedPreferences pref = new XSharedPreferences(BuildConfig.APPLICATION_ID, path);
        return pref.getFile().canRead() ? pref : null;
    }

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) {
        XSharedPreferences pref = getPref("zygote_conf");
        if (pref != null) {
            // do things with it
        } else {
            Log.e(TAG, "Cannot load pref for zygote properly");
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XSharedPreferences pref;
        switch (lpparam.packageName) {
            case PACKAGE_NAME_A:
                pref = getPref("a_conf");
                if (pref != null) {
                    // do things with it for A
                } else {
                    Log.e(TAG, "Cannot load pref for A properly");
                }
                break;
            case PACKAGE_NAME_B:
                pref = getPref("b_conf");
                if (pref != null) {
                    // do things with it for B
                } else {
                    Log.e(TAG, "Cannot load pref for B properly");
                }
                break;
            case BuildConfig.APPLICATION_ID:
                // hook myself
                break;
            default:
                // skip
        }
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) {
        // similar to handleLoadPackage
    }
}
```
</details>
<details>
<summary>Kotlin</summary>

```kotlin
class XposedInit : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

    companion object {
        fun getPref(path: String) : XSharedPreferences? {
            val pref = XSharedPreferences(BuildConfig.APPLICATION_ID, path)
            return if(pref.file.canRead()) pref else null
        }

        // lazy loads when needed
        val prefForZygote by lazy { getPref("zygote_conf") }

        // lazy loads when needed
        val prefForA by lazy { getPref("a_conf") }

        // lazy loads when needed
        val prefForB by lazy { getPref("b_conf") }
    }

    override fun initZygote(startupParam: StartupParam) {
        prefForZygote?.let {
            // do things with it
        } ?: Log.e(TAG, "Cannot load pref for zygote properly")
    }


    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        when(lpparam.packageName) {
            PACKAGE_NAME_A -> {
                prefForA?.let {
                    // do things with it for A
                } ?: Log.e(TAG, "Cannot load pref for A properly")
            }

            PACKAGE_NAME_B -> {
                prefForB?.let {
                    // do things with it for B
                } ?: Log.e(TAG, "Cannot load pref for B properly")

            }

            BuildConfig.APPLICATION_ID -> {
                // hook myself
            }

            default -> {
                // skip
            }
        }
    }

    override fun handleInitPackageResources(resparam: XC_InitPackageResources.InitPackageResourcesParam) {
        // similar to handleLoadPackage
    }
}
```
</details>

### Preference change listener in a hooked process
It is possible to register preference change listener within a hooked process to listen for changes of the preferences of a module.
Call `registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)` on a XSharedPreferences instance passing
reference to your listener. XSharedPreferences will start watching for changes on the physical file that belongs to given XSharedPreferences instance and notify specified listener every time change on the preference file happens; allowing module running within a hooked process to react on the changes of the module preferences.

**Note that by design it is not possible to determine which particular preference changed and thus preference key in listener's callback invocation will always be null.**
Typical scenario would thus be to call `reload()` on the XSharedPrefereces instance to get a fresh set of the preferences after the change happened.

To stop watching for preference changes call `unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener)` passing a reference to the previously registered listener.