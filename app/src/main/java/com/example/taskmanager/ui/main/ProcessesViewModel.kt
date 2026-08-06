package com.example.taskmanager.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.data.model.AppInfo
import com.example.taskmanager.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder { LAST_USED, NAME, MEMORY, NETWORK, SIZE }
enum class ProcessTab { USER, BACKGROUND, SYSTEM }

data class ProcessesUiState(
    val userApps: List<AppInfo> = emptyList(),
    val backgroundApps: List<AppInfo> = emptyList(),
    val systemApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.LAST_USED,
    val showSystemApps: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val isLoading: Boolean = true,
    val activeTab: ProcessTab = ProcessTab.USER,
)

class ProcessesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val _state = MutableStateFlow(ProcessesUiState())
    val state: StateFlow<ProcessesUiState> = _state.asStateFlow()

    init {
        val hasAccess = repo.hasUsageAccess()
        _state.update { it.copy(hasUsageAccess = hasAccess) }
        if (hasAccess) startCollecting()
    }

    fun recheckUsageAccess() {
        val hasAccess = repo.hasUsageAccess()
        _state.update { it.copy(hasUsageAccess = hasAccess) }
        if (hasAccess) startCollecting()
    }

    private fun startCollecting() {
        // Collect user apps
        viewModelScope.launch {
            repo.appsFlow(3000L).collect { apps ->
                _state.update { s ->
                    val updated = s.copy(userApps = apps, isLoading = false)
                    updated.copy(filteredApps = tabApps(updated))
                }
            }
        }
        // Collect background processes
        viewModelScope.launch {
            repo.backgroundFlow(3000L).collect { apps ->
                _state.update { s ->
                    val updated = s.copy(backgroundApps = apps)
                    updated.copy(filteredApps = tabApps(updated))
                }
            }
        }
        // Collect system apps
        viewModelScope.launch {
            repo.systemFlow(3000L).collect { apps ->
                _state.update { s ->
                    val updated = s.copy(systemApps = apps)
                    updated.copy(filteredApps = tabApps(updated))
                }
            }
        }
    }

    fun onTabChange(tab: ProcessTab) {
        _state.update { s ->
            val updated = s.copy(activeTab = tab, query = "")
            updated.copy(filteredApps = tabApps(updated))
        }
    }

    fun onQueryChange(q: String) {
        _state.update { s ->
            val updated = s.copy(query = q)
            updated.copy(filteredApps = tabApps(updated))
        }
    }

    fun onSortChange(order: SortOrder) {
        _state.update { s ->
            val updated = s.copy(sortOrder = order)
            updated.copy(filteredApps = tabApps(updated))
        }
    }

    fun toggleSystemApps() {
        _state.update { s ->
            val updated = s.copy(showSystemApps = !s.showSystemApps)
            updated.copy(filteredApps = tabApps(updated))
        }
    }

    private fun tabApps(s: ProcessesUiState): List<AppInfo> {
        val base = when (s.activeTab) {
            ProcessTab.USER       -> s.userApps
            ProcessTab.BACKGROUND -> s.backgroundApps
            ProcessTab.SYSTEM     -> s.systemApps
        }
        var result = if (s.activeTab == ProcessTab.USER && !s.showSystemApps) {
            base.filter { !it.isSystemApp }
        } else base
        if (s.query.isNotBlank()) {
            result = result.filter { it.appName.contains(s.query, true) || it.packageName.contains(s.query, true) }
        }
        return when (s.sortOrder) {
            SortOrder.LAST_USED -> result.sortedByDescending { it.lastUsedMs }
            SortOrder.NAME      -> result.sortedBy { it.appName }
            SortOrder.MEMORY    -> result.sortedByDescending { it.ramPssMb }
            SortOrder.NETWORK   -> result.sortedByDescending { it.totalNetworkBytes }
            SortOrder.SIZE      -> result.sortedByDescending { it.storageBytes }
        }
    }
}
