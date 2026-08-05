package com.example.taskmanager.data.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val uid: Int,
    val pid: Int,                   // Process ID (0 if not running)
    val lastUsedMs: Long,
    val lastUsedLabel: String,
    val memoryCategory: MemoryCategory,
    val ramPssMb: Long,             // Live PSS RAM memory usage in MB
    val ramLabel: String,           // e.g. "RAM: 145 MB"
    val totalNetworkBytes: Long,    // RX + TX bytes
    val networkSpeedLabel: String,  // e.g. "Net: 1.4 MB/s" or "Net: 0 B"
    val storageBytes: Long,
    val storageLabel: String,
    val isSystemApp: Boolean,
    val hasBootReceiver: Boolean,
    val versionName: String,
    val targetSdkVersion: Int,
)

enum class MemoryCategory(val label: String) {
    ACTIVE("Active"),
    RECENT("Recent"),
    CACHED("Cached"),
    UNKNOWN("Unknown"),
}
