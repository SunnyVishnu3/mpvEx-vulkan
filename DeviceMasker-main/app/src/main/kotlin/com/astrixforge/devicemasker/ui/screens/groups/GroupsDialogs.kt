package com.astrixforge.devicemasker.ui.screens.groups

import androidx.compose.runtime.Composable
import com.astrixforge.devicemasker.data.models.SpoofGroup
import com.astrixforge.devicemasker.ui.components.dialog.CreateGroupDialog
import com.astrixforge.devicemasker.ui.components.dialog.DeleteGroupDialog
import com.astrixforge.devicemasker.ui.components.dialog.EditGroupDialog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun GroupsDialogs(
    groups: ImmutableList<SpoofGroup>,
    showCreateDialog: Boolean,
    editGroupId: String?,
    deleteGroupId: String?,
    createDialogChanged: (Boolean) -> Unit,
    editGroupChanged: (String?) -> Unit,
    deleteGroupChanged: (String?) -> Unit,
    createGroup: (String, String) -> Unit,
    updateGroup: (SpoofGroup) -> Unit,
    deleteGroup: (String) -> Unit,
) {
    if (showCreateDialog) {
        CreateGroupDialog(
            existingNames = groups.map { it.name }.toImmutableList(),
            onDismiss = { createDialogChanged(false) },
            onCreate = { name, description ->
                createGroup(name, description)
                createDialogChanged(false)
            },
        )
    }

    groups
        .find { it.id == editGroupId }
        ?.let { group ->
            EditGroupDialog(
                group = group,
                onDismiss = { editGroupChanged(null) },
                onSave = { name, description ->
                    updateGroup(
                        group.copy(
                            name = name,
                            description = description,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    editGroupChanged(null)
                },
            )
        }

    groups
        .find { it.id == deleteGroupId }
        ?.let { group ->
            DeleteGroupDialog(
                onDismiss = { deleteGroupChanged(null) },
                onConfirm = {
                    deleteGroup(group.id)
                    deleteGroupChanged(null)
                },
            )
        }
}
