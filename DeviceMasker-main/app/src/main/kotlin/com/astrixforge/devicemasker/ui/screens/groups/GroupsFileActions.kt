package com.astrixforge.devicemasker.ui.screens.groups

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberGroupsExportLauncher(
    context: Context,
    scope: CoroutineScope,
    viewModel: GroupsViewModel,
    snackbarHostState: SnackbarHostState,
    messages: GroupsFileMessages,
): ManagedActivityResultLauncher<String, Uri?> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportGroups { result ->
            scope.launch {
                val message =
                    result.fold(
                        onSuccess = { jsonData ->
                            writeGroupsJson(context = context, uri = uri, jsonData = jsonData)
                                .fold(
                                    onSuccess = { messages.exportSuccess },
                                    onFailure = { messages.exportError },
                                )
                        },
                        onFailure = { messages.exportError },
                    )
                snackbarHostState.showSnackbar(message)
            }
        }
    }

@Composable
internal fun rememberGroupsImportLauncher(
    context: Context,
    scope: CoroutineScope,
    viewModel: GroupsViewModel,
    snackbarHostState: SnackbarHostState,
    messages: GroupsFileMessages,
): ManagedActivityResultLauncher<String, Uri?> =
    rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri?
        ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jsonResult = readGroupsJson(context = context, uri = uri)
            if (jsonResult.isNullOrBlank()) {
                snackbarHostState.showSnackbar(messages.importEmpty)
            } else {
                viewModel.importGroups(jsonResult) { success ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (success) messages.importSuccess else messages.importError
                        )
                    }
                }
            }
        }
    }

private suspend fun writeGroupsJson(context: Context, uri: Uri, jsonData: String): Result<Unit> =
    runCatching {
        withContext(Dispatchers.IO) {
            val outputStream = context.contentResolver.openOutputStream(uri)
            checkNotNull(outputStream)
            outputStream.use { it.write(jsonData.toByteArray()) }
        }
    }

private suspend fun readGroupsJson(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
                val inputStream =
                    context.contentResolver.openInputStream(uri) ?: return@runCatching ""
                inputStream.bufferedReader().use { reader -> reader.readText() }
            }
            .getOrNull()
    }
