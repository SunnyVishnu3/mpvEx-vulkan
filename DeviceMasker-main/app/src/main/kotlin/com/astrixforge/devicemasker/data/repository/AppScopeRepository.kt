package com.astrixforge.devicemasker.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.astrixforge.devicemasker.data.models.InstalledApp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppScopeRepository
@JvmOverloads
constructor(
    private val context: Context,
    private val packageManager: PackageManager = context.packageManager,
) : IAppScopeRepository {

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    override val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _scopedAppMetadata = MutableStateFlow<Map<String, InstalledApp>>(emptyMap())
    override val scopedAppMetadata: StateFlow<Map<String, InstalledApp>> =
        _scopedAppMetadata.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val cacheMutex = Mutex()
    private val isCacheValid = AtomicBoolean(false)
    private val scopedCache = mutableMapOf<String, InstalledApp>()

    override suspend fun loadApps(forceRefresh: Boolean) {
        cacheMutex.withLock {
            if (isCacheValid.get() && !forceRefresh) return

            _isLoading.value = true

            val apps = withContext(Dispatchers.IO) { queryInstalledApps() }

            _installedApps.value = apps
            isCacheValid.set(true)
            _isLoading.value = false
        }
    }

    override suspend fun loadScopedApps(packageNames: Set<String>, forceRefresh: Boolean) {
        val userPackages = packageNames.filterNotTo(linkedSetOf()) { it in DEFAULT_SCOPE_PACKAGES }
        val missing =
            cacheMutex.withLock {
                if (forceRefresh) {
                    userPackages
                } else {
                    userPackages.filterNotTo(linkedSetOf()) { it in scopedCache }
                }
            }
        val resolved =
            if (missing.isEmpty()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    missing.mapNotNull { packageName ->
                        resolveInstalledApp(packageName, includeVersion = false)
                    }
                }
            }

        cacheMutex.withLock {
            resolved.forEach { app -> scopedCache[app.packageName] = app }
            _scopedAppMetadata.value =
                userPackages
                    .mapNotNull { packageName ->
                        scopedCache[packageName]?.let { packageName to it }
                    }
                    .toMap()
        }
    }

    override suspend fun getInstalledApps(
        includeSystem: Boolean,
        refreshCache: Boolean,
    ): List<InstalledApp> {
        if (refreshCache || !isCacheValid.get()) {
            loadApps(refreshCache)
        }
        return if (includeSystem) {
            _installedApps.value
        } else {
            _installedApps.value.filter { !it.isSystemApp }
        }
    }

    private fun queryInstalledApps(): List<InstalledApp> {
        val flags = PackageManager.GET_META_DATA

        return packageManager
            .getInstalledApplications(flags)
            .mapNotNull { appInfo -> createInstalledApp(appInfo) }
            .sortedBy { it.label.lowercase() }
    }

    private fun resolveInstalledApp(packageName: String, includeVersion: Boolean): InstalledApp? {
        return try {
            createInstalledApp(
                appInfo = packageManager.getApplicationInfo(packageName, 0),
                includeVersion = includeVersion,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun createInstalledApp(
        appInfo: ApplicationInfo,
        includeVersion: Boolean = true,
    ): InstalledApp? {
        return try {
            val label = packageManager.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val versionName =
                if (includeVersion) {
                    try {
                        packageManager.getPackageInfo(appInfo.packageName, 0).versionName ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    ""
                }

            InstalledApp(
                packageName = appInfo.packageName,
                label = label,
                isSystemApp = isSystem,
                versionName = versionName,
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun invalidateCache() {
        isCacheValid.set(false)
        scopedCache.clear()
        _scopedAppMetadata.value = emptyMap()
    }

    private companion object {
        val DEFAULT_SCOPE_PACKAGES = setOf("android", "system")
    }
}
