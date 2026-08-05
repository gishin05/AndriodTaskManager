package com.example.taskmanager.data.model

data class MemoryStats(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long,
    val usedPercent: Float,         // 0f..1f
    val lowMemory: Boolean,
    val threshold: Long,            // low-memory threshold in bytes
)

data class CpuStats(
    val totalPercent: Float,        // 0f..1f system-wide
    val coreCount: Int,
    val available: Boolean,         // false if /proc/stat is blocked
    val historyPercent: List<Float> // last N samples for sparkline
)

data class StorageStats(
    val totalGb: Float,
    val usedGb: Float,
    val freeGb: Float,
    val usedPercent: Float,
)

data class FpsStats(
    val currentFps: Int,            // live FPS
    val refreshRate: Float,         // device max refresh rate (Hz)
    val history: List<Int>,         // last 60 FPS samples
)

data class PerformanceSnapshot(
    val memory: MemoryStats,
    val cpu: CpuStats,
    val storage: StorageStats,
    val fps: FpsStats,
    val timestamp: Long = System.currentTimeMillis(),
)
