package com.example.taskmanager.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.data.model.AppInfo
import com.example.taskmanager.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StartupUiState(
    val bootApps: List<AppInfo> = emptyList(),
    val hasUsageAccess: Boolean = false,
    val isLoading: Boolean = true,
)

class StartupViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val _state = MutableStateFlow(StartupUiState())
    val state: StateFlow<StartupUiState> = _state.asStateFlow()

    init {
        val hasAccess = repo.hasUsageAccess()
        _state.update { it.copy(hasUsageAccess = hasAccess) }
        load()
    }

    fun reload() {
        val hasAccess = repo.hasUsageAccess()
        _state.update { it.copy(hasUsageAccess = hasAccess) }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            repo.appsFlow(30_000L).collect { apps ->
                val bootApps = apps.filter { it.hasBootReceiver }.sortedBy { it.appName }
                _state.update { it.copy(bootApps = bootApps, isLoading = false) }
            }
        }
    }
}
