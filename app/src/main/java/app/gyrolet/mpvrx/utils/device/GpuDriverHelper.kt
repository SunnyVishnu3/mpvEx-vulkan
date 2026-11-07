/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.utils.device

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import app.gyrolet.mpvrx.preferences.DecoderPreferences
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object GpuDriverHelper {
  private const val TAG = "GpuDriverHelper"
  const val META_JSON_FILENAME = "meta.json"

  fun driverStoragePath(context: Context): String =
    File(context.filesDir, "gpu_drivers").apply { if (!exists()) mkdirs() }.canonicalPath + File.separator

  fun driverInstallationPath(context: Context): String =
    File(context.filesDir, "gpu_driver").apply { if (!exists()) mkdirs() }.canonicalPath + File.separator

  fun hookLibPath(context: Context): String =
    context.applicationInfo.nativeLibraryDir.removeSuffix(File.separator) + File.separator

  fun fileRedirectionPath(context: Context): String? =
    context.getExternalFilesDir(null)?.let {
      File(it, "gpu/vk_file_redirect").apply { if (!exists()) mkdirs() }.canonicalPath + File.separator
    }

  /**
   * Adreno Tools only supports 64-bit ARM (arm64-v8a) and Android 9+ (API 28+).
   */
  fun supportsCustomDriverLoading(): Boolean {
    val isArm64 = Build.SUPPORTED_ABIS.contains("arm64-v8a")
    val isSupportedApi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    return isArm64 && isSupportedApi
  }

  fun isCustomDriverActive(context: Context): Boolean {
    val prefs = getPreferences(context)
    if (!prefs.useCustomGpuDriver.get()) return false
    val libraryName = prefs.customDriverLibraryName.get()
    if (libraryName.isBlank()) return false
    val driverFile = File(driverInstallationPath(context), libraryName)
    return driverFile.exists() && driverFile.length() > 0
  }

  fun forceEnableVulkan(context: Context): Boolean =
    getPreferences(context).forceEnableVulkan.get()

  fun getInstalledDriverMetadata(context: Context): GpuDriverMetadata {
    val metaFile = File(driverInstallationPath(context), META_JSON_FILENAME)
    return if (metaFile.exists()) GpuDriverMetadata(metaFile) else GpuDriverMetadata()
  }

  fun getAvailableDrivers(context: Context): List<Pair<File, GpuDriverMetadata>> {
    val dir = File(driverStoragePath(context))
    val files = dir.listFiles { file -> file.extension.equals("zip", ignoreCase = true) } ?: return emptyList()
    return files.mapNotNull { file ->
      val meta = getMetadataFromZip(file)
      if (meta.isValid) file to meta else null
    }.sortedBy { it.second.name ?: "" }
  }

  fun getMetadataFromZip(zipFile: File): GpuDriverMetadata {
    if (!zipFile.exists() || zipFile.length() == 0L) return GpuDriverMetadata()
    try {
      ZipFile(zipFile).use { zf ->
        val entries = zf.entries()
        while (entries.hasMoreElements()) {
          val entry = entries.nextElement()
          if (!entry.isDirectory && entry.name.substringAfterLast("/").equals(META_JSON_FILENAME, ignoreCase = true)) {
            zf.getInputStream(entry).use { inputStream ->
              return GpuDriverMetadata(inputStream, entry.size)
            }
          }
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to read metadata from zip: ${zipFile.name}", e)
    }
    return GpuDriverMetadata()
  }

  fun installDefaultDriver(context: Context) {
    try {
      File(driverInstallationPath(context)).deleteRecursively()
      val prefs = getPreferences(context)
      prefs.useCustomGpuDriver.set(false)
      prefs.customDriverPath.set("")
      prefs.customDriverName.set("")
      prefs.customDriverLibraryName.set("")
      Log.i(TAG, "Reverted to default system GPU driver")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to clear custom driver", e)
    }
  }

  fun importAndInstallDriver(context: Context, uri: Uri): Result<GpuDriverMetadata> = runCatching {
    val tempFile = File(context.cacheDir, "driver_temp_${System.currentTimeMillis()}.zip")
    try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
          input.copyTo(output)
        }
      } ?: error("Failed to open input stream for URI: $uri")

      val metadata = getMetadataFromZip(tempFile)
      if (!metadata.isValid) {
        error("Invalid GPU driver package: missing or invalid $META_JSON_FILENAME")
      }
      if (metadata.minApi > Build.VERSION.SDK_INT) {
        error("Driver requires Android API ${metadata.minApi}, but current is ${Build.VERSION.SDK_INT}")
      }

      // Store in permanent driver storage
      val fileName = (metadata.name ?: "driver").replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".zip"
      val permanentFile = File(driverStoragePath(context), fileName)
      tempFile.copyTo(permanentFile, overwrite = true)

      // Unpack into installation directory
      installDriverFromFile(context, permanentFile, metadata)
      metadata
    } finally {
      tempFile.delete()
    }
  }

  fun installDriverFromFile(context: Context, zipFile: File, metadata: GpuDriverMetadata? = null): Result<GpuDriverMetadata> = runCatching {
    val meta = metadata ?: getMetadataFromZip(zipFile)
    if (!meta.isValid) {
      error("Driver zip has invalid metadata: ${zipFile.name}")
    }

    val installDir = File(driverInstallationPath(context))
    installDir.deleteRecursively()
    installDir.mkdirs()

    unzipToDirectory(zipFile, installDir)

    // Verify driver soname exists
    val libFile = File(installDir, meta.libraryName!!)
    if (!libFile.exists()) {
      error("Driver library ${meta.libraryName} not found in zip")
    }

    // Update preferences
    val prefs = getPreferences(context)
    prefs.customDriverPath.set(zipFile.canonicalPath)
    prefs.customDriverName.set(meta.name ?: zipFile.nameWithoutExtension)
    prefs.customDriverLibraryName.set(meta.libraryName!!)
    prefs.useCustomGpuDriver.set(true)
    prefs.useVulkan.set(true)
    prefs.gpuNext.set(true)

    Log.i(TAG, "Custom GPU driver installed: ${meta.name} (${meta.version}) -> ${meta.libraryName}")
    meta
  }

  fun deleteDriver(context: Context, zipFile: File): Boolean {
    val prefs = getPreferences(context)
    if (prefs.customDriverPath.get() == zipFile.canonicalPath) {
      installDefaultDriver(context)
    }
    return zipFile.delete()
  }

  @Throws(SecurityException::class)
  private fun unzipToDirectory(zipFile: File, destDir: File) {
    val destCanonicalPath = destDir.canonicalPath
    ZipFile(zipFile).use { zf ->
      val entries = zf.entries()
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val entryFile = File(destDir, entry.name)

        // Protect against Zip Slip vulnerability
        if (!entryFile.canonicalPath.startsWith(destCanonicalPath + File.separator) &&
          entryFile.canonicalPath != destCanonicalPath
        ) {
          throw SecurityException("Zip Slip exploit detected: ${entry.name}")
        }

        if (entry.isDirectory) {
          entryFile.mkdirs()
        } else {
          entryFile.parentFile?.mkdirs()
          zf.getInputStream(entry).use { input ->
            FileOutputStream(entryFile).use { output ->
              input.copyTo(output)
            }
          }
        }
      }
    }
  }

  fun applyDriverToMpv(context: Context) {
    if (!isCustomDriverActive(context)) {
      Log.i(TAG, "Custom GPU driver not active, resetting MPV to default system driver")
      invokeMpvSetCustomGpuDriver(null, null, null, null)
      return
    }
    val prefs = getPreferences(context)
    val libraryName = prefs.customDriverLibraryName.get()
    val driverDir = driverInstallationPath(context)
    val hookDir = hookLibPath(context)
    val redirectDir = fileRedirectionPath(context)
    val debugFlags = prefs.turnipDebugFlags.get()

    Log.i(TAG, "Applying custom GPU driver to MPV: $libraryName from $driverDir (hookDir: $hookDir)")

    // Apply Turnip debug flags if set
    if (debugFlags.isNotBlank()) {
      runCatching {
        val mpvLibClass = `is`.xyz.mpv.MPVLib::class.java
        val receiver = runCatching { mpvLibClass.getField("INSTANCE").get(null) }.getOrElse { `is`.xyz.mpv.MPVLib }
        val setEnvMethod = mpvLibClass.getMethod("setFreedrenoEnv", String::class.java, String::class.java)
        val envResult = setEnvMethod.invoke(receiver, "TU_DEBUG", debugFlags)
        Log.i(TAG, "setFreedrenoEnv(TU_DEBUG, $debugFlags) result: $envResult")
      }.onFailure {
        Log.w(TAG, "Failed to set Freedreno env: ${it.message}")
      }
    }

    // Call setCustomGpuDriver on MPVLib
    val success = invokeMpvSetCustomGpuDriver(hookDir, driverDir, libraryName, redirectDir)
    Log.i(TAG, "invokeMpvSetCustomGpuDriver result: $success")
  }

  private fun invokeMpvSetCustomGpuDriver(
    hookLibDir: String?,
    customDriverDir: String?,
    customDriverName: String?,
    fileRedirectDir: String?
  ): Boolean {
    return runCatching {
      val mpvLibClass = `is`.xyz.mpv.MPVLib::class.java
      val receiver = runCatching { mpvLibClass.getField("INSTANCE").get(null) }.getOrElse { `is`.xyz.mpv.MPVLib }
      val method = mpvLibClass.getMethod(
        "setCustomGpuDriver",
        String::class.java,
        String::class.java,
        String::class.java,
        String::class.java
      )
      val result = method.invoke(receiver, hookLibDir, customDriverDir, customDriverName, fileRedirectDir)
      (result as? Boolean) ?: false
    }.onFailure {
      Log.w(TAG, "setCustomGpuDriver invocation failed: ${it.message}", it)
    }.getOrDefault(false)
  }

  private fun getPreferences(context: Context): DecoderPreferences {
    return runCatching {
      org.koin.core.context.GlobalContext.get().get<DecoderPreferences>()
    }.getOrElse {
      DecoderPreferences(app.gyrolet.mpvrx.preferences.preference.AndroidPreferenceStore(context.applicationContext))
    }
  }
}
