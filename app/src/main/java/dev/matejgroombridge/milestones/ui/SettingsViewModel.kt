package dev.matejgroombridge.milestones.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.matejgroombridge.milestones.data.settings.Settings
import dev.matejgroombridge.milestones.data.settings.SettingsRepository
import dev.matejgroombridge.milestones.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Settings(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAmoled(enabled: Boolean) {
        viewModelScope.launch { repository.setAmoled(enabled) }
    }

    fun setSwipeToNavigate(enabled: Boolean) {
        viewModelScope.launch { repository.setSwipeToNavigate(enabled) }
    }

    fun setCelebrateRecords(enabled: Boolean) {
        viewModelScope.launch { repository.setCelebrateRecords(enabled) }
    }

    /**
     * Toggle Zen mode. When on, the app's UI is locked to a single grid of
     * milestone cards; the only other reachable thing is this setting
     * itself. See [Settings.zenMode] for the full definition.
     */
    fun setZenMode(enabled: Boolean) {
        viewModelScope.launch { repository.setZenMode(enabled) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(SettingsRepository(application.applicationContext))
            }
        }
    }
}
