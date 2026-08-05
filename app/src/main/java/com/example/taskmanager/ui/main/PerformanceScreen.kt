package com.example.taskmanager.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.ui.components.SparklineChart
import com.example.taskmanager.ui.components.StatBar
import com.example.taskmanager.ui.components.StatCard
import com.example.taskmanager.theme.*
import kotlin.math.roundToInt

@Composable
fun PerformanceScreen(modifier: Modifier = Modifier) {
    val vm: PerformanceViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snap = state.snapshot

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── RAM Card ──
        StatCard(
            title = "Memory (RAM)",
            subtitle = if (snap != null) "${snap.memory.usedRamMb} MB used of ${snap.memory.totalRamMb} MB" else "Loading…",
        ) {
            if (snap != null) {
                StatBar(
                    label = "RAM",
                    value = snap.memory.usedPercent,
                    usedLabel = "${snap.memory.usedRamMb} MB",
                    totalLabel = "${snap.memory.totalRamMb} MB",
                    color = RamColor,
                )
                Spacer(Modifier.height(14.dp))
                SparklineChart(
                    samples = state.ramHistory,
                    color = RamColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MemoryStatItem("Available", "${snap.memory.availableRamMb} MB", AccentCyan)
                    if (snap.memory.lowMemory) {
                        MemoryStatItem("⚠ Low Memory", "Yes", DangerRed)
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                    CircularProgressIndicator(color = RamColor, modifier = Modifier.size(28.dp))
                }
            }
        }

        // ── CPU Card ──
        StatCard(
            title = "CPU",
            subtitle = if (snap != null) "${snap.cpu.coreCount} cores · ${if (snap.cpu.available) "Live data" else "Estimated"}" else "Loading…",
        ) {
            if (snap != null) {
                StatBar(
                    label = "CPU",
                    value = snap.cpu.totalPercent,
                    usedLabel = "${(snap.cpu.totalPercent * 100).roundToInt()}%",
                    totalLabel = "100%",
                    color = CpuColor,
                )
                Spacer(Modifier.height(14.dp))
                SparklineChart(
                    samples = snap.cpu.historyPercent,
                    color = CpuColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                if (!snap.cpu.available) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ /proc/stat is restricted on this device. Showing estimated data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                    )
                }
            } else {
                Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                    CircularProgressIndicator(color = CpuColor, modifier = Modifier.size(28.dp))
                }
            }
        }

        // ── Storage Card ──
        StatCard(
            title = "Storage",
            subtitle = if (snap != null) "${"%.1f".format(snap.storage.freeGb)} GB free of ${"%.1f".format(snap.storage.totalGb)} GB" else "Loading…",
        ) {
            if (snap != null) {
                StatBar(
                    label = "Storage",
                    value = snap.storage.usedPercent,
                    usedLabel = "${"%.1f".format(snap.storage.usedGb)} GB",
                    totalLabel = "${"%.1f".format(snap.storage.totalGb)} GB",
                    color = StorageColor,
                    height = 14.dp,
                )
            } else {
                Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                    CircularProgressIndicator(color = StorageColor, modifier = Modifier.size(28.dp))
                }
            }
        }

        // ── FPS Card ──
        StatCard(
            title = "Display & FPS",
            subtitle = if (snap != null) "${snap.fps.refreshRate.roundToInt()} Hz refresh rate" else "Loading…",
        ) {
            if (snap != null) {
                StatBar(
                    label = "FPS",
                    value = if (snap.fps.refreshRate > 0) snap.fps.currentFps / snap.fps.refreshRate else 0f,
                    usedLabel = "${snap.fps.currentFps} fps",
                    totalLabel = "${snap.fps.refreshRate.roundToInt()} Hz",
                    color = CpuColor,
                )
                Spacer(Modifier.height(14.dp))
                val fpsFloats = snap.fps.history.map { it.toFloat() / snap.fps.refreshRate.coerceAtLeast(1f) }
                if (fpsFloats.size >= 2) {
                    SparklineChart(
                        samples = fpsFloats,
                        color = CpuColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MemoryStatItem("Current", "${snap.fps.currentFps} fps", CpuColor)
                    MemoryStatItem("Max", "${snap.fps.refreshRate.roundToInt()} Hz", AccentCyan)
                    val fpsColor = when {
                        snap.fps.currentFps < snap.fps.refreshRate / 2 -> DangerRed
                        snap.fps.currentFps < snap.fps.refreshRate * 0.75f -> WarningAmber
                        else -> SuccessGreen
                    }
                    MemoryStatItem("Status", if (snap.fps.currentFps >= snap.fps.refreshRate * 0.9f) "Smooth" else "Dropping", fpsColor)
                }
            } else {
                Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                    CircularProgressIndicator(color = CpuColor, modifier = Modifier.size(28.dp))
                }
            }
        }

        // Bottom spacing for nav bar
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun MemoryStatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, fontSize = 13.sp, color = color)
    }
}
