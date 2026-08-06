package com.example.taskmanager.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.taskmanager.data.model.AppInfo
import com.example.taskmanager.ui.components.AppRow
import com.example.taskmanager.ui.components.PermissionPrompt
import com.example.taskmanager.theme.*

@Composable
fun ProcessesScreen(modifier: Modifier = Modifier) {
    val vm: ProcessesViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.recheckUsageAccess() }

    if (!state.hasUsageAccess) {
        PermissionPrompt(
            icon = Icons.Default.Visibility,
            title = "Usage Access Required",
            description = "Grant 'Usage Access' so Task Manager can show recently active apps.\n\nSettings → Apps → Special app access → Usage access",
            buttonText = "Open Settings",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Process Category Tabs ──────────────────────────────────────
        ProcessTabs(
            activeTab = state.activeTab,
            userCount = state.userApps.size,
            backgroundCount = state.backgroundApps.size,
            systemCount = state.systemApps.size,
            onTabChange = vm::onTabChange,
        )

        Spacer(Modifier.height(8.dp))

        // ── Search + Sort bar ──────────────────────────────────────────
        SearchSortBar(
            query = state.query,
            sortOrder = state.sortOrder,
            showSystem = state.showSystemApps,
            activeTab = state.activeTab,
            onQueryChange = vm::onQueryChange,
            onSortChange = vm::onSortChange,
            onToggleSystem = vm::toggleSystemApps,
        )

        Spacer(Modifier.height(8.dp))

        // ── Summary chips ──────────────────────────────────────────────
        val active = state.filteredApps.count { it.memoryCategory.name == "ACTIVE" }
        val boot   = state.filteredApps.count { it.hasBootReceiver }
        if (state.filteredApps.isNotEmpty()) {
            ProcessSummaryRow(
                total    = state.filteredApps.size,
                active   = active,
                boot     = boot,
                tab      = state.activeTab,
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── List ───────────────────────────────────────────────────────
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = AccentViolet)
            }
        } else if (state.filteredApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when (state.activeTab) {
                            ProcessTab.USER       -> Icons.Default.Apps
                            ProcessTab.BACKGROUND -> Icons.Default.Settings
                            ProcessTab.SYSTEM     -> Icons.Default.Memory
                        },
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when (state.activeTab) {
                            ProcessTab.USER       -> "No user apps found"
                            ProcessTab.BACKGROUND -> "No background processes running"
                            ProcessTab.SYSTEM     -> "No system processes found"
                        },
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(state.filteredApps, key = { it.packageName + it.processCategory.name }) { app ->
                    AppRow(
                        app = app,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Process Category Tabs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProcessTabs(
    activeTab: ProcessTab,
    userCount: Int,
    backgroundCount: Int,
    systemCount: Int,
    onTabChange: (ProcessTab) -> Unit,
) {
    data class TabSpec(val tab: ProcessTab, val label: String, val count: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

    val tabs = listOf(
        TabSpec(ProcessTab.USER,       "Processes",   userCount,       Icons.Default.Apps,    AccentViolet),
        TabSpec(ProcessTab.BACKGROUND, "Background",  backgroundCount, Icons.Default.Settings, AccentCyan),
        TabSpec(ProcessTab.SYSTEM,     "System",      systemCount,     Icons.Default.Memory,   WarningAmber),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { spec ->
            val isActive = activeTab == spec.tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) spec.color.copy(alpha = 0.18f) else SurfaceContainer)
                    .then(
                        if (isActive) Modifier else Modifier
                    ),
            ) {
                Surface(
                    onClick = { onTabChange(spec.tab) },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            spec.icon,
                            contentDescription = null,
                            tint = if (isActive) spec.color else TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            spec.label,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) spec.color else TextMuted,
                        )
                        if (spec.count > 0) {
                            Text(
                                "${spec.count}",
                                fontSize = 10.sp,
                                color = if (isActive) spec.color else TextMuted.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.5f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(spec.color)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search + Sort
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchSortBar(
    query: String,
    sortOrder: SortOrder,
    showSystem: Boolean,
    activeTab: ProcessTab,
    onQueryChange: (String) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleSystem: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search apps…", color = TextMuted) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentViolet,
                unfocusedBorderColor = SurfaceBorder,
                cursorColor = AccentViolet,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceContainer,
                unfocusedContainerColor = SurfaceContainer,
            ),
            shape = RoundedCornerShape(12.dp),
        )

        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Icon(Icons.Default.Sort, null, tint = AccentVioletLight)
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                containerColor = SurfaceElevated,
            ) {
                SortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                order.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                color = if (order == sortOrder) AccentVioletLight else TextPrimary,
                            )
                        },
                        onClick = { onSortChange(order); showSortMenu = false },
                    )
                }
            }
        }

        // Only show system toggle on USER tab
        if (activeTab == ProcessTab.USER) {
            IconButton(onClick = onToggleSystem) {
                Icon(
                    if (showSystem) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showSystem) "Hide system apps" else "Show system apps",
                    tint = if (showSystem) AccentCyan else TextMuted,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProcessSummaryRow(total: Int, active: Int, boot: Int, tab: ProcessTab) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val countLabel = when (tab) {
            ProcessTab.USER       -> "$total Apps"
            ProcessTab.BACKGROUND -> "$total Services"
            ProcessTab.SYSTEM     -> "$total Packages"
        }
        val countIcon = when (tab) {
            ProcessTab.USER       -> Icons.Default.Apps
            ProcessTab.BACKGROUND -> Icons.Default.Settings
            ProcessTab.SYSTEM     -> Icons.Default.Memory
        }
        val countColor = when (tab) {
            ProcessTab.USER       -> AccentViolet
            ProcessTab.BACKGROUND -> AccentCyan
            ProcessTab.SYSTEM     -> WarningAmber
        }
        SummaryChip(countLabel, countIcon, countColor, Modifier.weight(1f))
        SummaryChip("$active Running", Icons.Default.RadioButtonChecked, AccentCyan, Modifier.weight(1f))
        SummaryChip("$boot Boot", Icons.Default.RestartAlt, WarningAmber, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}
