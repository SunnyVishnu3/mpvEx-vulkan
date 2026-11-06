/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.preferences

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.preferences.DecoderPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.ui.preferences.components.SwitchPreference
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import app.gyrolet.mpvrx.utils.device.GpuDriverHelper
import app.gyrolet.mpvrx.utils.device.GpuDriverMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object GpuDriverManagerScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backstack = LocalBackStack.current
    val preferences = koinInject<DecoderPreferences>()
    val coroutineScope = rememberCoroutineScope()

    val useCustomDriver by preferences.useCustomGpuDriver.collectAsState()
    val activeDriverPath by preferences.customDriverPath.collectAsState()
    val activeDriverName by preferences.customDriverName.collectAsState()
    val activeDriverLib by preferences.customDriverLibraryName.collectAsState()
    val forceEnableVulkan by preferences.forceEnableVulkan.collectAsState()
    val turnipFlags by preferences.turnipDebugFlags.collectAsState()

    var availableDrivers by remember { mutableStateOf(GpuDriverHelper.getAvailableDrivers(context)) }

    fun refreshDrivers() {
      availableDrivers = GpuDriverHelper.getAvailableDrivers(context)
    }

    val launcher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        coroutineScope.launch {
          val result = withContext(Dispatchers.IO) {
            GpuDriverHelper.importAndInstallDriver(context, uri)
          }
          result.onSuccess { meta ->
            Toast.makeText(context, "Installed: ${meta.name ?: "Custom Driver"}", Toast.LENGTH_SHORT).show()
            refreshDrivers()
          }.onFailure { error ->
            Toast.makeText(context, "Install failed: ${error.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    Scaffold(
      contentWindowInsets = settingsContentWindowInsets(),
      topBar = {
        SettingsTopAppBar(
          title = stringResource(R.string.pref_gpu_driver_manager_title),
          onBack = { backstack.popSafely() },
        )
      },
    ) { padding ->
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        item {
          Spacer(modifier = Modifier.height(4.dp))
          // Current Driver Card
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
              containerColor = if (useCustomDriver) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant
              }
            )
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Text(
                text = "Active GPU Driver",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (useCustomDriver) activeDriverName.ifBlank { "Custom Driver" } else "System Default Driver",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              if (useCustomDriver && activeDriverLib.isNotBlank()) {
                Text(
                  text = "Library: $activeDriverLib",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.outline
                )
              }
              Spacer(modifier = Modifier.height(12.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                  Text(stringResource(R.string.pref_install_driver))
                }
                if (useCustomDriver) {
                  OutlinedButton(onClick = {
                    GpuDriverHelper.installDefaultDriver(context)
                    refreshDrivers()
                  }) {
                    Text(stringResource(R.string.pref_uninstall_driver))
                  }
                }
              }
            }
          }
        }

        item {
          // Bypasses & Overrides section
          Text(
            text = "Compatibility & Overrides",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(8.dp))
          SwitchPreference(
            value = forceEnableVulkan,
            onValueChange = { preferences.forceEnableVulkan.set(it) },
            title = { Text(stringResource(R.string.pref_force_vulkan_override_title)) },
            summary = { Text(stringResource(R.string.pref_force_vulkan_override_summary)) }
          )
        }

        item {
          // Freedreno / Turnip Environment Variables
          Text(
            text = "Driver Configuration",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.height(8.dp))
          var flagsText by remember(turnipFlags) { mutableStateOf(turnipFlags) }
          OutlinedTextField(
            value = flagsText,
            onValueChange = {
              flagsText = it
              preferences.turnipDebugFlags.set(it)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.pref_turnip_debug_flags_title)) },
            supportingText = { Text(stringResource(R.string.pref_turnip_debug_flags_summary)) },
            singleLine = true
          )
        }

        item {
          Text(
            text = "Installed Custom Drivers (${availableDrivers.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
          )
        }

        if (availableDrivers.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.pref_no_drivers_installed),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.outline,
              modifier = Modifier.padding(vertical = 12.dp)
            )
          }
        } else {
          items(availableDrivers) { (file, meta) ->
            val isCurrent = useCustomDriver && activeDriverPath == file.canonicalPath
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(
                containerColor = if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
              )
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = meta.name ?: file.nameWithoutExtension,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "Version: ${meta.version ?: "Unknown"} | Vendor: ${meta.vendor ?: "Unknown"}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline
                    )
                    if (!meta.author.isNullOrBlank()) {
                      Text(
                        text = "Author: ${meta.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                      )
                    }
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  if (!isCurrent) {
                    Button(onClick = {
                      coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                          GpuDriverHelper.installDriverFromFile(context, file, meta)
                        }
                        refreshDrivers()
                      }
                    }) {
                      Text("Activate")
                    }
                  } else {
                    OutlinedButton(onClick = {}, enabled = false) {
                      Text("Active")
                    }
                  }
                  OutlinedButton(onClick = {
                    GpuDriverHelper.deleteDriver(context, file)
                    refreshDrivers()
                  }) {
                    Text("Delete")
                  }
                }
              }
            }
          }
        }

        item {
          Spacer(modifier = Modifier.height(16.dp))
        }
      }
    }
  }
}

