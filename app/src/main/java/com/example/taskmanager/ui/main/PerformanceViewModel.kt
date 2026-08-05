package com.example.taskmanager.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskmanager.data.model.PerformanceSnapshot
import com.example.taskmanager.data.repository.PerformanceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PerformanceUiState(
    val snapshot: PerformanceSnapshot? = null,
    val ramHistory: List<Float> = emptyList(),  // 0f..1f, last 60 samples
)

class PerformanceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PerformanceRepository(app)
    private val _state = MutableStateFlow(PerformanceUiState())
    val state: StateFlow<PerformanceUiState> = _state.asStateFlow()

    private val ramHistory = ArrayDeque<Float>(60)

    init {
        viewModelScope.launch {
            repo.performanceFlow(1000L).collect { snap ->
                if (ramHistory.size >= 60) ramHistory.removeFirst()
                ramHistory.addLast(snap.memory.usedPercent)
                _state.update { it.copy(snapshot = snap, ramHistory = ramHistory.toList()) }
            }
        }
    }
}
