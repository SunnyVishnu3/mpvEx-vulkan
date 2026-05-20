package com.astrixforge.devicemasker.verifier

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

internal object PackageVisibilityProbe {
    private val packagesToCheck =
        listOf(
            "com.astrixforge.devicemasker",
            "org.lsposed.manager",
            "com.topjohnwu.magisk",
            "flar2.devcheck",
        )

    fun capture(context: Context): JSONObject {
        val manager = context.packageManager
        val results = JSONArray()
        packagesToCheck.forEach { packageName ->
            results.put(
                JSONObject()
                    .put("packageName", packageName)
                    .put("getPackageInfoVisible", isPackageInfoVisible(manager, packageName))
                    .put(
                        "getApplicationInfoVisible",
                        isApplicationInfoVisible(manager, packageName),
                    )
            )
        }
        return JSONObject().put("packages", results)
    }

    private fun isPackageInfoVisible(manager: PackageManager, packageName: String): Boolean =
        runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION") manager.getPackageInfo(packageName, 0)
                }
            }
            .isSuccess

    private fun isApplicationInfoVisible(manager: PackageManager, packageName: String): Boolean =
        runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION") manager.getApplicationInfo(packageName, 0)
                }
            }
            .isSuccess
}
