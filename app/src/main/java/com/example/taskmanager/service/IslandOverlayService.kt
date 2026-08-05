package com.example.taskmanager.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.taskmanager.MainActivity
import com.example.taskmanager.TaskManagerApp
import com.example.taskmanager.data.model.CpuStats
import com.example.taskmanager.data.model.FpsStats
import com.example.taskmanager.data.model.MemoryStats
import com.example.taskmanager.data.model.NetworkSpeedStats
import com.example.taskmanager.data.model.ThermalVoltageStats
import com.example.taskmanager.data.repository.PerformanceRepository
import com.example.taskmanager.theme.AccentCyan
import com.example.taskmanager.theme.AccentViolet
import com.example.taskmanager.theme.TaskManagerTheme
import com.example.taskmanager.util.IconUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class WindowSnapPosition {
    CENTER,
    LEFT_EDGE,
    RIGHT_EDGE
}

class IslandOverlayService : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var overlayView: View? = null
    private val performanceRepo by lazy { PerformanceRepository(this) }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // Live system stats state
    private val _memStats   = mutableStateOf<MemoryStats?>(null)
    private val _cpuStats   = mutableStateOf<CpuStats?>(null)
    private val _fpsStats   = mutableStateOf<FpsStats?>(null)
    private val _thermalStats = mutableStateOf<ThermalVoltageStats?>(null)
    private val _networkStats = mutableStateOf<NetworkSpeedStats?>(null)
    private val _activeApps = mutableStateOf(0)
    private val _expanded   = mutableStateOf(false)
    private val _isSideDocked = mutableStateOf(false)
    private val _isLandscapeState = mutableStateOf(false)
    private var enableFloatingOverlay = true

    private var windowPosition = WindowSnapPosition.CENTER

    private var isFrameCallbackActive = false
    private val choreographerCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isFrameCallbackActive) {
                performanceRepo.recordFrameTime(frameTimeNanos)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onCreate() {
        savedStateRegistryController.performRestore(null)
        super.onCreate()
        windowManager       = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        _isLandscapeState.value = isLandscape

        startForegroundWithNotification(
            title = "Task Manager System Service",
            text  = "Hardware Polling Active (Privileged Priv-App)",
            subText = "HyperOS Native System Overlay"
        )

        isFrameCallbackActive = true
        Choreographer.getInstance().postFrameCallback(choreographerCallback)
        startCollecting()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateOverlayForOrientation(isLandscape)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_SET_MODE -> {
                enableFloatingOverlay = intent.getBooleanExtra(EXTRA_ENABLE_FLOATING, true)
                if (enableFloatingOverlay) {
                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    if (overlayView == null) addOverlay(isLandscape) else updateOverlayForOrientation(isLandscape)
                } else {
                    removeOverlay()
                }
            }
            ACTION_UPDATE_APP_COUNT -> {
                _activeApps.value = intent.getIntExtra(EXTRA_APP_COUNT, 0)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isFrameCallbackActive = false
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        removeOverlay()
        super.onDestroy()
    }

    private fun startForegroundWithNotification(title: String, text: String, subText: String) {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val hyperOsExtras = Bundle().apply {
            putBoolean("miui.show_in_statusbar", true)
            putBoolean("extra_show_in_statusbar", true)
            putInt("miui.status_bar_notification_style", 1)
        }

        val notification: Notification = NotificationCompat.Builder(this, TaskManagerApp.OVERLAY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(hyperOsExtras)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TaskManagerApp.OVERLAY_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(TaskManagerApp.OVERLAY_NOTIFICATION_ID, notification)
        }
    }

    private fun updateSystemNotification(
        ramPct: Float,
        cpuPct: Float,
        fps: Int,
        tempC: Float,
        downKbps: Float,
        activeAppsCount: Int
    ) {
        val ramInt = (ramPct * 100).roundToInt()
        val cpuInt = (cpuPct * 100).roundToInt()
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val liveIconBitmap = IconUtils.createMetricBitmap("$ramInt%", ramPct)

        val hyperOsExtras = Bundle().apply {
            putBoolean("miui.show_in_statusbar", true)
            putBoolean("extra_show_in_statusbar", true)
            putInt("miui.status_bar_notification_style", 1)
        }

        val speedText = if (downKbps > 1024f) "${"%.1f".format(downKbps / 1024f)}MB/s" else "${downKbps.roundToInt()}KB/s"

        val notification: Notification = NotificationCompat.Builder(this, TaskManagerApp.OVERLAY_CHANNEL_ID)
            .setContentTitle("RAM $ramInt% • CPU $cpuInt% • $fps FPS")
            .setContentText("${"%.1f".format(tempC)}°C • ↓$speedText • System Priv-App")
            .setSubText("HyperOS System Monitor")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setLargeIcon(liveIconBitmap)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(hyperOsExtras)
            .build()

        notificationManager.notify(TaskManagerApp.OVERLAY_NOTIFICATION_ID, notification)
    }

    private fun moveWindowX(deltaX: Int) {
        if (_isSideDocked.value || _isLandscapeState.value) return
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.x += deltaX
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun handleSwipeLeft() {
        if (_isLandscapeState.value) return
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            when (windowPosition) {
                WindowSnapPosition.CENTER -> {
                    windowPosition = WindowSnapPosition.LEFT_EDGE
                    params.x = -(screenWidthPx / 3)
                }
                WindowSnapPosition.LEFT_EDGE -> {
                    _isSideDocked.value = true
                    params.x = -(screenWidthPx / 2 - 12)
                }
                WindowSnapPosition.RIGHT_EDGE -> {
                    windowPosition = WindowSnapPosition.CENTER
                    params.x = 0
                }
            }
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun handleSwipeRight() {
        if (_isLandscapeState.value) return
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            when (windowPosition) {
                WindowSnapPosition.CENTER -> {
                    windowPosition = WindowSnapPosition.RIGHT_EDGE
                    params.x = (screenWidthPx / 3)
                }
                WindowSnapPosition.RIGHT_EDGE -> {
                    _isSideDocked.value = true
                    params.x = (screenWidthPx / 2 - 12)
                }
                WindowSnapPosition.LEFT_EDGE -> {
                    windowPosition = WindowSnapPosition.CENTER
                    params.x = 0
                }
            }
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun restoreFromSideDock() {
        _isSideDocked.value = false
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            windowPosition = WindowSnapPosition.CENTER
            params.x = 0
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun addOverlay(isLandscape: Boolean) {
        if (!enableFloatingOverlay) return
        _isLandscapeState.value = isLandscape

        val flags = if (isLandscape) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }

        val params = WindowManager.LayoutParams(
            if (isLandscape) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            x = 0
            y = 4
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)

            setContent {
                val currentLandscape = _isLandscapeState.value
                if (currentLandscape) {
                    // ── Landscape Mode: Status Bar (Non-clickable display only!) ──
                    StatusBarLiveBar(
                        memStats = _memStats.value,
                        cpuStats = _cpuStats.value,
                        fpsStats = _fpsStats.value,
                        thermalStats = _thermalStats.value,
                        networkStats = _networkStats.value,
                    )
                } else {
                    // ── Portrait Mode: Dynamic Floating Island ──
                    DynamicIslandPill(
                        memStats   = _memStats.value,
                        cpuStats   = _cpuStats.value,
                        fpsStats   = _fpsStats.value,
                        thermalStats = _thermalStats.value,
                        networkStats = _networkStats.value,
                        activeApps = _activeApps.value,
                        expanded   = _expanded.value,
                        isSideDocked = _isSideDocked.value,
                        onToggleExpand = { _expanded.value = !_expanded.value },
                        onMoveWindow = { dx -> moveWindowX(dx) },
                        onSwipeLeft  = { handleSwipeLeft() },
                        onSwipeRight = { handleSwipeRight() },
                        onRestoreFromDock = { restoreFromSideDock() },
                        onOpenApp = {
                            val intent = Intent(this@IslandOverlayService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(intent)
                        }
                    )
                }
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    private fun updateOverlayForOrientation(isLandscape: Boolean) {
        _isLandscapeState.value = isLandscape
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            if (isLandscape) {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.y = 4
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.x = 0
                params.y = 4
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
            windowPosition = WindowSnapPosition.CENTER
            _isSideDocked.value = false
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun startCollecting() {
        lifecycleScope.launch {
            performanceRepo.performanceFlow(1000L).collect { snap ->
                _memStats.value = snap.memory
                _cpuStats.value = snap.cpu
                _fpsStats.value = snap.fps
                _thermalStats.value = snap.thermal
                _networkStats.value = snap.networkSpeed

                updateSystemNotification(
                    ramPct = snap.memory.usedPercent,
                    cpuPct = snap.cpu.totalPercent,
                    fps = snap.fps.currentFps,
                    tempC = snap.thermal.batteryTempC,
                    downKbps = snap.networkSpeed.downlinkKbps,
                    activeAppsCount = _activeApps.value
                )
            }
        }
    }

    companion object {
        const val ACTION_STOP             = "com.example.taskmanager.STOP_OVERLAY"
        const val ACTION_SET_MODE         = "com.example.taskmanager.SET_MODE"
        const val ACTION_UPDATE_APP_COUNT = "com.example.taskmanager.UPDATE_APP_COUNT"
        const val EXTRA_ENABLE_FLOATING   = "enable_floating"
        const val EXTRA_MODE              = "extra_mode"
        const val EXTRA_APP_COUNT         = "app_count"

        const val MODE_STATUS_BAR  = 1
        const val MODE_FLOATING_ISLAND = 2

        fun start(context: Context, enableFloating: Boolean = true, mode: Int = MODE_FLOATING_ISLAND) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_SET_MODE
                putExtra(EXTRA_ENABLE_FLOATING, enableFloating)
                putExtra(EXTRA_MODE, mode)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun updateMode(context: Context, enableFloating: Boolean, mode: Int) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_SET_MODE
                putExtra(EXTRA_ENABLE_FLOATING, enableFloating)
                putExtra(EXTRA_MODE, mode)
            }
            context.startService(intent)
        }
    }
}

/**
 * Landscape Status Bar Mode (Non-clickable, display only!)
 */
@Composable
private fun StatusBarLiveBar(
    memStats: MemoryStats?,
    cpuStats: CpuStats?,
    fpsStats: FpsStats?,
    thermalStats: ThermalVoltageStats?,
    networkStats: NetworkSpeedStats?,
) {
    val ramPct = memStats?.usedPercent ?: 0f
    val cpuPct = cpuStats?.totalPercent ?: 0f
    val fps    = fpsStats?.currentFps ?: 0
    val maxFps = fpsStats?.refreshRate?.roundToInt() ?: 60
    val tempC  = thermalStats?.batteryTempC ?: 0f

    val ramColor = when {
        ramPct > 0.85f -> Color(0xFFEF4444)
        ramPct > 0.65f -> Color(0xFFF59E0B)
        else           -> AccentViolet
    }
    val cpuColor = when {
        cpuPct > 0.85f -> Color(0xFFEF4444)
        cpuPct > 0.65f -> Color(0xFFF59E0B)
        else           -> AccentCyan
    }
    val fpsColor = when {
        fps < maxFps / 2       -> Color(0xFFEF4444)
        fps < maxFps * 3 / 4   -> Color(0xFFF59E0B)
        else                   -> Color(0xFF10B981)
    }

    TaskManagerTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xD0111318))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(ramColor))
                    Text(
                        text = "RAM ${(ramPct * 100).roundToInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text("•", fontSize = 10.sp, color = Color(0xFF64748B))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(cpuColor))
                    Text(
                        text = "CPU ${(cpuPct * 100).roundToInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text("•", fontSize = 10.sp, color = Color(0xFF64748B))

                Text(
                    text = "${"%.1f".format(tempC)}°C",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xD0111318))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${fps} FPS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = fpsColor
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(fpsColor))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
// Portrait Mode Dynamic Island Composable
// ─────────────────────────────────────────────────────────
@Composable
private fun DynamicIslandPill(
    memStats: MemoryStats?,
    cpuStats: CpuStats?,
    fpsStats: FpsStats?,
    thermalStats: ThermalVoltageStats?,
    networkStats: NetworkSpeedStats?,
    activeApps: Int,
    expanded: Boolean,
    isSideDocked: Boolean,
    onToggleExpand: () -> Unit,
    onMoveWindow: (Int) -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onRestoreFromDock: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val ramPct = memStats?.usedPercent ?: 0f
    val cpuPct = cpuStats?.totalPercent ?: 0f
    val fps    = fpsStats?.currentFps ?: 0
    val maxFps = fpsStats?.refreshRate?.roundToInt() ?: 60
    val tempC  = thermalStats?.batteryTempC ?: 0f
    val voltV  = thermalStats?.batteryVoltageV ?: 0f
    val downKbps = networkStats?.downlinkKbps ?: 0f

    val ramColor = when {
        ramPct > 0.85f -> Color(0xFFEF4444)
        ramPct > 0.65f -> Color(0xFFF59E0B)
        else           -> AccentViolet
    }
    val fpsColor = when {
        fps < maxFps / 2       -> Color(0xFFEF4444)
        fps < maxFps * 3 / 4   -> Color(0xFFF59E0B)
        else                   -> AccentCyan
    }

    TaskManagerTheme {
        if (isSideDocked) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(Color(0xD00D0F14))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onRestoreFromDock() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(AccentCyan, AccentViolet))
                        )
                )
            }
        } else {
            val targetWidth by animateDpAsState(
                if (expanded) 320.dp else 142.dp,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "island_w"
            )
            val targetHeight by animateDpAsState(
                if (expanded) 140.dp else 26.dp,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "island_h"
            )
            val cornerRadius by animateDpAsState(
                if (expanded) 28.dp else 13.dp,
                spring(Spring.DampingRatioMediumBouncy),
                label = "island_r"
            )

            Box(
                modifier = Modifier
                    .width(targetWidth)
                    .height(targetHeight)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xF00D0F14))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggleExpand() }
                        )
                    }
                    .pointerInput(Unit) {
                        var accumulatedX = 0f
                        detectDragGestures(
                            onDragStart = { accumulatedX = 0f },
                            onDragEnd = {
                                if (accumulatedX < -40f) {
                                    onSwipeLeft()
                                } else if (accumulatedX > 40f) {
                                    onSwipeRight()
                                }
                            },
                            onDragCancel = { accumulatedX = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedX += dragAmount.x
                                onMoveWindow(dragAmount.x.toInt())
                            }
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!expanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(ramColor))
                            Text(
                                text = "${(ramPct * 100).roundToInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text("•", fontSize = 9.sp, color = Color(0xFF64748B))
                            Text(
                                text = "C ${(cpuPct * 100).roundToInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "${fps}fps",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = fpsColor
                            )
                            Box(Modifier.size(5.dp).clip(CircleShape).background(fpsColor))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(AccentViolet))
                                Text("Task Manager", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (activeApps > 0) "$activeApps apps" else "Priv-App", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onOpenApp() }
                                        .padding(4.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Memory, null, tint = ramColor, modifier = Modifier.size(14.dp))
                            Text("RAM", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            val animRam by animateFloatAsState(ramPct, tween(400), label = "ram_bar")
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E2128))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(animRam)
                                        .fillMaxHeight()
                                        .background(Brush.horizontalGradient(listOf(ramColor.copy(0.7f), ramColor)))
                                )
                            }
                            Text(
                                "${(ramPct * 100).roundToInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Thermostat, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                                Text(
                                    "${"%.1f".format(tempC)}°C",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                                Text(
                                    "(${"%.2f".format(voltV)}V)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Speed, null, tint = fpsColor, modifier = Modifier.size(13.dp))
                                Text(
                                    "$fps / ${maxFps}Hz",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = fpsColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
