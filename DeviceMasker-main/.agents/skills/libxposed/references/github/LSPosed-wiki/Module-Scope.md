### Background

Module scopes are required in LSPosed to activate a module. Of course, user can select their own scope for a specific module, but it's too advance for a user to determine the real module scope. So in LSPosed, a new meta-data is introduced for module developers to declare its scope.

### Example usage
If a module wants to hook package `com.example.a` and `com.example.b`, in `AndroidManifest.xml`, a meta-data named `xposedscope` should be defined as:
```
<meta-data
    android:name="xposedscope"
    android:resource="@array/example_scope" />
```
And in `array.xml`, a string array named `example_scope` (this name could be arbitrarily changed) should be defined as:
```
<string-array name="example_scope" >
    <item>com.example.a</item>
    <item>com.example.b</item>
</string-array>
```

If no [alternative resources](https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources), can hardcode it:
```
<meta-data
    android:name="xposedscope"
    android:value="com.example.a;com.example.b" />
```

### Advantage
By defining such a meta-data, all apps within this array will be selected by default when the module is enabled in the manager.
