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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

    // Re-check permission when screen recomposes (e.g. returning from Settings)
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
        // Search + sort bar
        SearchSortBar(
            query = state.query,
            sortOrder = state.sortOrder,
            showSystem = state.showSystemApps,
            onQueryChange = vm::onQueryChange,
            onSortChange = vm::onSortChange,
            onToggleSystem = vm::toggleSystemApps,
        )

        Spacer(Modifier.height(8.dp))

        // Stats summary row
        if (state.apps.isNotEmpty()) {
            ProcessSummaryRow(
                total = state.apps.size,
                active = state.apps.count { it.memoryCategory.name == "ACTIVE" },
                boot = state.apps.count { it.hasBootReceiver },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = AccentViolet)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                items(state.filteredApps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onClick = {
                            // Open system App Info
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

@Composable
private fun SearchSortBar(
    query: String,
    sortOrder: SortOrder,
    showSystem: Boolean,
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
                        text = { Text(order.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, color = if (order == sortOrder) AccentVioletLight else TextPrimary) },
                        onClick = { onSortChange(order); showSortMenu = false },
                    )
                }
            }
        }

        IconButton(onClick = onToggleSystem) {
            Icon(
                if (showSystem) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (showSystem) "Hide system apps" else "Show system apps",
                tint = if (showSystem) AccentCyan else TextMuted,
            )
        }
    }
}

@Composable
private fun ProcessSummaryRow(total: Int, active: Int, boot: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryChip("$total Apps", Icons.Default.Apps, AccentViolet, Modifier.weight(1f))
        SummaryChip("$active Active", Icons.Default.RadioButtonChecked, AccentCyan, Modifier.weight(1f))
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
