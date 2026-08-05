package com.example.taskmanager.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.data.model.AppInfo
import com.example.taskmanager.data.repository.AppRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder { LAST_USED, NAME, MEMORY, NETWORK, SIZE }

data class ProcessesUiState(
    val apps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val query: String = "",
    val sortOrder: SortOrder = SortOrder.LAST_USED,
    val showSystemApps: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val isLoading: Boolean = true,
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
        viewModelScope.launch {
            repo.appsFlow(3000L).collect { apps ->
                _state.update { s ->
                    s.copy(
                        apps = apps,
                        filteredApps = filter(apps, s.query, s.sortOrder, s.showSystemApps),
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { s ->
            s.copy(query = q, filteredApps = filter(s.apps, q, s.sortOrder, s.showSystemApps))
        }
    }

    fun onSortChange(order: SortOrder) {
        _state.update { s ->
            s.copy(sortOrder = order, filteredApps = filter(s.apps, s.query, order, s.showSystemApps))
        }
    }

    fun toggleSystemApps() {
        _state.update { s ->
            val show = !s.showSystemApps
            s.copy(showSystemApps = show, filteredApps = filter(s.apps, s.query, s.sortOrder, show))
        }
    }

    private fun filter(apps: List<AppInfo>, q: String, sort: SortOrder, showSystem: Boolean): List<AppInfo> {
        var result = if (showSystem) apps else apps.filter { !it.isSystemApp }
        if (q.isNotBlank()) result = result.filter { it.appName.contains(q, true) || it.packageName.contains(q, true) }
        return when (sort) {
            SortOrder.LAST_USED -> result.sortedByDescending { it.lastUsedMs }
            SortOrder.NAME      -> result.sortedBy { it.appName }
            SortOrder.MEMORY    -> result.sortedBy { it.memoryCategory.ordinal }
            SortOrder.NETWORK   -> result.sortedByDescending { it.totalNetworkBytes }
            SortOrder.SIZE      -> result.sortedByDescending { it.storageBytes }
        }
    }
}
