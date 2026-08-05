package com.example.taskmanager.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taskmanager.service.IslandOverlayService
import com.example.taskmanager.theme.AccentViolet
import com.example.taskmanager.theme.Background
import com.example.taskmanager.theme.SurfaceContainer
import com.example.taskmanager.theme.SurfaceElevated
import com.example.taskmanager.theme.TextMuted
import com.example.taskmanager.theme.TextPrimary

enum class MainTab(val title: String, val icon: ImageVector) {
    PROCESSES("Processes", Icons.Default.Apps),
    PERFORMANCE("Performance", Icons.Default.Speed),
    STARTUP("Startup", Icons.Default.Autorenew),
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(MainTab.PROCESSES) }
    var serviceActive by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        IslandOverlayService.start(context, enableFloating = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Task Manager",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                )
                Text(
                    text = "Android System Monitor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }

            // Live Service Toggle Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (serviceActive) AccentViolet else SurfaceElevated)
                    .clickable {
                        serviceActive = !serviceActive
                        if (serviceActive) {
                            IslandOverlayService.start(context, enableFloating = true)
                        } else {
                            IslandOverlayService.stop(context)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        if (serviceActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (serviceActive) "Live ON" else "Live OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }

        // Mode Info Banner when Active
        if (serviceActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Portrait: Native HyperOS Dynamic Island  •  Landscape: Status Bar Mode",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
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
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AccentViolet else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) TextPrimary else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) TextPrimary else TextMuted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Tab Content ──
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                MainTab.PROCESSES -> ProcessesScreen()
                MainTab.PERFORMANCE -> PerformanceScreen()
                MainTab.STARTUP -> StartupScreen()
            }
        }
    }
}
