package com.example.taskmanager.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.taskmanager.data.model.AppInfo
import com.example.taskmanager.data.model.MemoryCategory
import com.example.taskmanager.theme.*

@Composable
fun AppRow(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceContainer)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon
        AppIcon(drawable = app.icon, modifier = Modifier.size(44.dp))

        Spacer(Modifier.width(12.dp))

        // App info
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = app.lastUsedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                maxLines = 1,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(6.dp))
            // Always show Memory Category, Live RAM PSS, Live Network Data, and Boot badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MemoryBadge(app.memoryCategory)
                RamChip(app.ramLabel, isRunning = app.pid > 0)
                NetworkChip(app.networkSpeedLabel)
                if (app.hasBootReceiver) {
                    BootBadge()
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            drawable.toBitmap(width = 96, height = 96).asImageBitmap()
        }
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(10.dp)))
    } else {
        Box(
            modifier.clip(RoundedCornerShape(10.dp)).background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Android, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun MemoryBadge(category: MemoryCategory) {
    val (color, label) = when (category) {
        MemoryCategory.ACTIVE  -> AccentCyan to "Active"
        MemoryCategory.RECENT  -> WarningAmber to "Recent"
        MemoryCategory.CACHED  -> TextMuted to "Cached"
        MemoryCategory.UNKNOWN -> SurfaceBorder to "—"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 9.sp, color = color)
    }
}

@Composable
fun RamChip(label: String, isRunning: Boolean) {
    val color = if (isRunning) AccentVioletLight else TextMuted
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.Memory, null, tint = color, modifier = Modifier.size(10.dp))
            Text(label, fontSize = 9.sp, color = color, fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun NetworkChip(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AccentCyan.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.Public, null, tint = AccentCyan, modifier = Modifier.size(10.dp))
            Text(label, fontSize = 9.sp, color = AccentCyan)
        }
    }
}

@Composable
fun BootBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(WarningAmber.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text("Boot", fontSize = 9.sp, color = WarningAmber)
    }
}
