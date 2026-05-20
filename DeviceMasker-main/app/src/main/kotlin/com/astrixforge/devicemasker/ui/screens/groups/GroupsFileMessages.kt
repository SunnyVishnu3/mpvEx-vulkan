package com.astrixforge.devicemasker.ui.screens.groups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.astrixforge.devicemasker.R

internal data class GroupsFileMessages(
    val exportSuccess: String,
    val exportError: String,
    val importEmpty: String,
    val importSuccess: String,
    val importError: String,
)

@Composable
internal fun rememberGroupsFileMessages(): GroupsFileMessages {
    val exportSuccess = stringResource(R.string.group_export_success)
    val exportError = stringResource(R.string.group_export_error)
    val importEmpty = stringResource(R.string.group_import_empty_error)
    val importSuccess = stringResource(R.string.group_import_success)
    val importError = stringResource(R.string.group_import_error)

    return remember(exportSuccess, exportError, importEmpty, importSuccess, importError) {
        GroupsFileMessages(
            exportSuccess = exportSuccess,
            exportError = exportError,
            importEmpty = importEmpty,
            importSuccess = importSuccess,
            importError = importError,
        )
    }
}
