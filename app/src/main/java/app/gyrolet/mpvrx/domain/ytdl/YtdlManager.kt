package app.gyrolet.mpvrx.domain.ytdl

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Scanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class YtdlManager(
  private val context: Context,
  private val httpClient: OkHttpClient,
) {
  private val ytdlDir = File(context.filesDir, "ytdl")
  private val pythonZip = File(ytdlDir, "python3.zip")
  private val setupScript = File(ytdlDir, "setup.py")
  private val wrapperScript = File(ytdlDir, "wrapper")
  private val ytdlBin = File(context.filesDir, "youtube-dl")

  companion object {
    private const val TAG = "YtdlManager"
  }

  fun initialize(): Boolean {
    if (!ytdlDir.exists() && !ytdlDir.mkdirs()) return false

    // Copy python3.zip from assets if not exists
    if (!pythonZip.exists()) {
      copyAsset("ytdl/python3.zip", pythonZip)
    }

    // Copy cacert.pem from assets (required for https)
    val caCert = File(context.filesDir, "cacert.pem")
    if (!caCert.exists()) {
      copyAsset("cacert.pem", caCert)
    }

    // Copy setup.py from assets
    if (!setupScript.exists()) {
      copyAsset("ytdl/setup.py", setupScript)
    }

    // Copy wrapper from assets
    if (!wrapperScript.exists()) {
      copyAsset("ytdl/wrapper", wrapperScript)
    }

    setupEnvironment()
    return true
  }

  fun getIsInstalled(): Boolean {
    return ytdlBin.exists() && ytdlBin.length() > 0
  }

  suspend fun runInstaller(onLog: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val pythonBin = getYtdlPath() ?: throw IOException("Python interpreter not found")
      
      // Update wrapper with absolute path to python interpreter for SDK 29+
      val wrapperContent = context.assets.open("ytdl/wrapper").bufferedReader().use { it.readText() }
        .replace("./python3", pythonBin)
      wrapperScript.writeText(wrapperContent)

      // setup.py logic expects to be run from ytdlDir or have correct relative paths
      // The wrapper sets PYTHONHOME=. and PYTHONPATH=./python3.zip
      // So we should run it from ytdlDir.

      val process = ProcessBuilder(
        "/system/bin/sh",
        wrapperScript.absolutePath,
        setupScript.absolutePath
      )
        .directory(ytdlDir)
        .redirectErrorStream(true)
        .start()

      val scanner = Scanner(process.inputStream)
      while (scanner.hasNextLine()) {
        val line = scanner.nextLine()
        Log.d(TAG, "Installer: $line")
        onLog(line)
      }

      val exitCode = process.waitFor()
      if (exitCode != 0) throw IOException("Installer failed with exit code $exitCode")
    }
  }

  private fun copyAsset(assetPath: String, destFile: File): Boolean {
    try {
      context.assets.open(assetPath).use { input ->
        FileOutputStream(destFile).use { output ->
          input.copyTo(output)
        }
      }
      return true
    } catch (e: IOException) {
      Log.e(TAG, "Failed to copy asset $assetPath", e)
      return false
    }
  }

  private fun setupEnvironment() {
    try {
      val pythonHome = ytdlDir.absolutePath
      val pythonPath = pythonZip.absolutePath
      val caCert = File(context.filesDir, "cacert.pem").absolutePath

      Os.setenv("PYTHONHOME", pythonHome, true)
      Os.setenv("PYTHONPATH", pythonPath, true)
      Os.setenv("SSL_CERT_FILE", caCert, true)
      
      val tmpDir = File(context.cacheDir, "ytdl_tmp")
      tmpDir.mkdirs()
      Os.setenv("TMPDIR", tmpDir.absolutePath, true)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to set up environment variables", e)
    }
  }

  fun getYtdlPath(): String? {
    val libDir = context.applicationInfo.nativeLibraryDir
    val pythonBin = File(libDir, "libpython3.so")
    if (pythonBin.exists()) {
        return pythonBin.absolutePath
    }
    
    File(libDir).listFiles()?.forEach { file ->
        if (file.name.contains("python") && file.name.endsWith(".so")) {
            return file.absolutePath
        }
    }
    return null
  }
  
  fun getYtdlBinPath(): String {
      return ytdlBin.absolutePath
  }
}
