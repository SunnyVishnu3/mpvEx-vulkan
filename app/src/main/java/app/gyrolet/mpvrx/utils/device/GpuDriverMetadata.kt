/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.utils.device

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream

data class GpuDriverMetadata(
  var name: String? = null,
  var description: String? = null,
  var author: String? = null,
  var vendor: String? = null,
  var version: String? = null,
  var minApi: Int = 0,
  var libraryName: String? = null,
) {
  constructor(metadataFile: File) : this() {
    if (metadataFile.length() > MAX_META_SIZE_BYTES || !metadataFile.exists()) return
    runCatching {
      parseJson(metadataFile.readText())
    }
  }

  constructor(metadataStream: InputStream, size: Long) : this() {
    if (size > MAX_META_SIZE_BYTES) return
    runCatching {
      val text = metadataStream.bufferedReader().use { it.readText() }
      parseJson(text)
    }
  }

  constructor(jsonString: String) : this() {
    runCatching {
      parseJson(jsonString)
    }
  }

  private fun parseJson(jsonString: String) {
    try {
      val json = JSONObject(jsonString)
      name = json.optString("name").takeIf { it.isNotBlank() }
      description = json.optString("description").takeIf { it.isNotBlank() }
      author = json.optString("author").takeIf { it.isNotBlank() }
      vendor = json.optString("vendor").takeIf { it.isNotBlank() }
      version = json.optString("driverVersion").takeIf { it.isNotBlank() }
      minApi = json.optInt("minApi", 0)
      libraryName = json.optString("libraryName").takeIf { it.isNotBlank() }
    } catch (_: JSONException) {
    }
  }

  val isValid: Boolean
    get() = !name.isNullOrBlank() && !libraryName.isNullOrBlank()

  companion object {
    private const val MAX_META_SIZE_BYTES = 500_000
  }
}

