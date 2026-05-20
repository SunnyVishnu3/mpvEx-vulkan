package com.astrixforge.devicemasker.ui.navigation

import android.app.Application
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.astrixforge.devicemasker.data.ISettingsDataStore
import com.astrixforge.devicemasker.data.repository.ISpoofRepository
import com.astrixforge.devicemasker.ui.screens.diagnostics.DiagnosticsViewModel
import com.astrixforge.devicemasker.ui.screens.groups.GroupsViewModel
import com.astrixforge.devicemasker.ui.screens.groupspoofing.GroupSpoofingViewModel
import com.astrixforge.devicemasker.ui.screens.home.HomeViewModel
import com.astrixforge.devicemasker.ui.screens.logsmonitor.LogsMonitorViewModel
import com.astrixforge.devicemasker.ui.screens.settings.SettingsViewModel

internal fun homeViewModelFactory(repository: ISpoofRepository) = viewModelFactory {
    initializer { HomeViewModel(repository, savedStateHandle = createSavedStateHandle()) }
}

internal fun settingsViewModelFactory(application: Application, settingsStore: ISettingsDataStore) =
    viewModelFactory {
        initializer { SettingsViewModel(application, settingsStore) }
    }

internal fun groupSpoofingViewModelFactory(repository: ISpoofRepository, groupId: String) =
    viewModelFactory {
        initializer { GroupSpoofingViewModel(repository, groupId, createSavedStateHandle()) }
    }

internal fun groupsViewModelFactory(repository: ISpoofRepository) = viewModelFactory {
    initializer { GroupsViewModel(repository, createSavedStateHandle()) }
}

internal fun diagnosticsViewModelFactory(application: Application, repository: ISpoofRepository) =
    viewModelFactory {
        initializer {
            DiagnosticsViewModel(
                application,
                repository,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }

internal fun logsMonitorViewModelFactory(application: Application) = viewModelFactory {
    initializer { LogsMonitorViewModel(application = application) }
}
