package com.astrixforge.devicemasker.verifier

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

internal object CrashProbe {
    fun capture(): JSONObject =
        JSONObject()
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("previewSdkInt", Build.VERSION.PREVIEW_SDK_INT)
            .put("codename", Build.VERSION.CODENAME)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("vmVersion", System.getProperty("java.vm.version"))
            .put("vmName", System.getProperty("java.vm.name"))
}
