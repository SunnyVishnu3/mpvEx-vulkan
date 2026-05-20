package com.astrixforge.devicemasker.xposed.hooker

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.astrixforge.devicemasker.xposed.DualLog
import com.astrixforge.devicemasker.xposed.hooker.callback.stableHooker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.error.XposedFrameworkError

internal object AntiDetectPackageManagerHooks {

    private const val TAG = "AntiDetectHooker"

    @SuppressLint("PrivateApi")
    fun hook(cl: ClassLoader, xi: XposedInterface, hiddenPackages: Set<String>) {
        val pmClass = cl.loadClass("android.app.ApplicationPackageManager")

        hookPackageInfo(pmClass, xi, hiddenPackages)
        hookApplicationInfo(pmClass, xi, hiddenPackages)
        hookInstalledPackages(pmClass, xi, hiddenPackages)
        hookInstalledApplications(pmClass, xi, hiddenPackages)
        hookQueryIntentActivities(pmClass, xi, hiddenPackages)
    }

    private fun hookPackageInfo(
        pmClass: Class<*>,
        xi: XposedInterface,
        hiddenPackages: Set<String>,
    ) {
        try {
            pmClass
                .getDeclaredMethod(
                    "getPackageInfo",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                )
                .also { it.isAccessible = true }
                .let { m ->
                    xi.hook(m)
                        .setExceptionMode(ExceptionMode.PASSTHROUGH)
                        .intercept(
                            stableHooker { chain ->
                                val pkgName = chain.args.firstOrNull() as? String
                                if (pkgName != null && hiddenPackages.matches(pkgName)) {
                                    throw PackageManager.NameNotFoundException(pkgName)
                                }
                                chain.proceed()
                            }
                        )
                    xi.deoptimize(m)
                }
        } catch (_: NoSuchMethodException) {}
    }

    private fun hookApplicationInfo(
        pmClass: Class<*>,
        xi: XposedInterface,
        hiddenPackages: Set<String>,
    ) {
        try {
            pmClass
                .getDeclaredMethod(
                    "getApplicationInfo",
                    String::class.java,
                    Int::class.javaPrimitiveType!!,
                )
                .also { it.isAccessible = true }
                .let { m ->
                    xi.hook(m)
                        .setExceptionMode(ExceptionMode.PASSTHROUGH)
                        .intercept(
                            stableHooker { chain ->
                                val pkgName = chain.args.firstOrNull() as? String
                                if (pkgName != null && hiddenPackages.matches(pkgName)) {
                                    throw PackageManager.NameNotFoundException(pkgName)
                                }
                                chain.proceed()
                            }
                        )
                    xi.deoptimize(m)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "getApplicationInfo hook failed", t)
        }
    }

    private fun hookInstalledPackages(
        pmClass: Class<*>,
        xi: XposedInterface,
        hiddenPackages: Set<String>,
    ) {
        try {
            pmClass
                .getDeclaredMethod("getInstalledPackages", Int::class.javaPrimitiveType!!)
                .also { it.isAccessible = true }
                .let { m ->
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                @Suppress("UNCHECKED_CAST")
                                val packages =
                                    result as? List<PackageInfo> ?: return@stableHooker result
                                packages.filterNot { hiddenPackages.matches(it.packageName) }
                            }
                        )
                    xi.deoptimize(m)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "getInstalledPackages hook failed", t)
        }
    }

    private fun hookInstalledApplications(
        pmClass: Class<*>,
        xi: XposedInterface,
        hiddenPackages: Set<String>,
    ) {
        try {
            pmClass
                .getDeclaredMethod("getInstalledApplications", Int::class.javaPrimitiveType!!)
                .also { it.isAccessible = true }
                .let { m ->
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                @Suppress("UNCHECKED_CAST")
                                val apps =
                                    result as? List<ApplicationInfo> ?: return@stableHooker result
                                apps.filterNot { hiddenPackages.matches(it.packageName) }
                            }
                        )
                    xi.deoptimize(m)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "getInstalledApplications hook failed", t)
        }
    }

    private fun hookQueryIntentActivities(
        pmClass: Class<*>,
        xi: XposedInterface,
        hiddenPackages: Set<String>,
    ) {
        try {
            pmClass
                .getDeclaredMethod(
                    "queryIntentActivities",
                    Intent::class.java,
                    Int::class.javaPrimitiveType!!,
                )
                .also { it.isAccessible = true }
                .let { m ->
                    xi.hook(m)
                        .intercept(
                            stableHooker { chain ->
                                val result = chain.proceed()
                                @Suppress("UNCHECKED_CAST")
                                val infos =
                                    result as? List<ResolveInfo> ?: return@stableHooker result
                                infos.filterNot { info ->
                                    val packageName =
                                        info.activityInfo?.packageName ?: return@filterNot false
                                    hiddenPackages.matches(packageName)
                                }
                            }
                        )
                    xi.deoptimize(m)
                }
        } catch (e: XposedFrameworkError) {
            throw e
        } catch (t: Throwable) {
            DualLog.warn(TAG, "queryIntentActivities hook failed", t)
        }
    }

    private fun Set<String>.matches(packageName: String): Boolean = any {
        packageName.equals(it, ignoreCase = true)
    }
}
