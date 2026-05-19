@file:Suppress("FunctionNaming")

package com.astrixforge.devicemasker.ui.components.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.R
import com.astrixforge.devicemasker.data.models.SpoofGroup
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Dialog for creating a new group. */
@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    existingNames: ImmutableList<String> = persistentListOf(),
    onCreate: (name: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val nameExists = existingNames.any { it.equals(name.trim(), ignoreCase = true) }
    val isValid = name.isNotBlank() && !nameExists

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
        title = { Text(stringResource(id = R.string.group_create_new)) },
        text = {
            Column {
                val maxNameLength = 12
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= maxNameLength) name = it },
                    label = { Text(stringResource(id = R.string.group_name_hint)) },
                    placeholder = { Text(stringResource(id = R.string.group_name_example)) },
                    singleLine = true,
                    isError = nameExists,
                    supportingText = {
                        if (nameExists) {
                            Text(
                                text = stringResource(id = R.string.group_name_exists),
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            Text(
                                stringResource(
                                    id = R.string.group_name_length,
                                    name.length,
                                    maxNameLength,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(id = R.string.group_description_hint)) },
                    placeholder = { Text(stringResource(id = R.string.group_description_example)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), description.trim()) }, enabled = isValid) {
                Text(stringResource(id = R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.action_cancel)) }
        },
    )
}

/** Dialog for editing a group. */
@Composable
fun EditGroupDialog(
    group: SpoofGroup,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSave: (name: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    var description by remember { mutableStateOf(group.description) }
    val isValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(imageVector = Icons.Default.Groups, contentDescription = null) },
        title = { Text(stringResource(id = R.string.group_edit_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.group_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(id = R.string.group_description_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), description.trim()) }, enabled = isValid) {
                Text(stringResource(id = R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.action_cancel)) }
        },
    )
}

/** Dialog for confirming group deletion. */
@Composable
fun DeleteGroupDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(id = R.string.group_delete_dialog_title)) },
        text = {
            Text(
                stringResource(id = R.string.group_delete_confirm) +
                    " " +
                    stringResource(id = R.string.group_delete_warning)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(id = R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.action_cancel)) }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CreateGroupDialogPreview() {
    DeviceMaskerTheme { CreateGroupDialog(onDismiss = {}, onCreate = { _, _ -> }) }
}
