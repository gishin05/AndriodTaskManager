package com.example.taskmanager.ui.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanager.service.IslandOverlayService
import com.example.taskmanager.theme.*

private data class NavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

private val tabs = listOf(
    NavTab("Processes", Icons.Filled.Apps, Icons.Outlined.Apps),
    NavTab("Performance", Icons.Filled.Speed, Icons.Outlined.Speed),
    NavTab("Startup", Icons.Filled.RestartAlt, Icons.Outlined.RestartAlt),
)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var serviceActive by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableIntStateOf(IslandOverlayService.MODE_STATUS_BAR) } // 1 = Status Bar, 2 = Floating Island

    Column(modifier = modifier.fillMaxSize().background(Background)) {
        // ── Top header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Task Manager", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
                Text("Android System Monitor", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }

            // Live Service toggle button
            ServiceToggleButton(
                active = serviceActive,
                onClick = {
                    serviceActive = !serviceActive
                    if (serviceActive) {
                        if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                            serviceActive = false
                        } else {
                            IslandOverlayService.start(
                                context,
                                enableFloating = true,
                                mode = selectedMode
                            )
                        }
                    } else {
                        IslandOverlayService.stop(context)
                    }
                }
            )
        }

        // Mode Switcher Bar when Active
        if (serviceActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Status Bar Mode button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedMode == IslandOverlayService.MODE_STATUS_BAR) AccentViolet else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(vertical = 6.dp)
                        .clickable {
                            selectedMode = IslandOverlayService.MODE_STATUS_BAR
                            IslandOverlayService.updateMode(context, enableFloating = true, mode = IslandOverlayService.MODE_STATUS_BAR)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Status Bar Mode", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }

                // Floating Island Mode button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedMode == IslandOverlayService.MODE_FLOATING_ISLAND) AccentViolet else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(vertical = 6.dp)
                        .clickable {
                            selectedMode = IslandOverlayService.MODE_FLOATING_ISLAND
                            IslandOverlayService.updateMode(context, enableFloating = true, mode = IslandOverlayService.MODE_FLOATING_ISLAND)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Floating Island", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ── Tab bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) AccentViolet else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        IconButton(
                            onClick = { selectedTab = index },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = if (selected) TextPrimary else TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            tab.label,
                            fontSize = 9.sp,
                            color = if (selected) TextPrimary else TextMuted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Screen content ──
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (selectedTab) {
                0 -> ProcessesScreen()
                1 -> PerformanceScreen()
                2 -> StartupScreen()
            }
        }
    }
}

@Composable
private fun ServiceToggleButton(active: Boolean, onClick: () -> Unit) {
    val containerColor = if (active) AccentViolet else SurfaceElevated
    val label = if (active) "Live ON" else "Live OFF"
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            if (active) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            null,
            modifier = Modifier.size(16.dp),
            tint = if (active) TextPrimary else TextMuted,
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (active) TextPrimary else TextMuted, fontSize = 12.sp)
    }
}
