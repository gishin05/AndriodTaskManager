package com.example.taskmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.taskmanager.R
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class IslandOverlayService : LifecycleService(), SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private var overlayView: View? = null
    private var statusBarOverlayView: View? = null
    private val performanceRepo by lazy { PerformanceRepository(this) }

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // Live system stats state
    private val _memStats          = mutableStateOf<MemoryStats?>(null)
    private val _cpuStats          = mutableStateOf<CpuStats?>(null)
    private val _fpsStats          = mutableStateOf<FpsStats?>(null)
    private val _thermalStats      = mutableStateOf<ThermalVoltageStats?>(null)
    private val _networkStats      = mutableStateOf<NetworkSpeedStats?>(null)
    private val _isExpanded        = mutableStateOf(false)
    private val _swipeCount        = mutableStateOf(0)
    private val _showStatusBarMode = mutableStateOf(false)
    private var lastSwipeMs        = 0L

    private var enableOverlay = true

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

        ensureChannel()

        // Start minimal foreground service notification (required by Android OS)
        val silentNotif = buildSilentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TaskManagerApp.OVERLAY_NOTIFICATION_ID,
                silentNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(TaskManagerApp.OVERLAY_NOTIFICATION_ID, silentNotif)
        }

        isFrameCallbackActive = true
        Choreographer.getInstance().postFrameCallback(choreographerCallback)
        startCollecting()

        // Add Game Space edge overlay
        addGameSpaceOverlay()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            updateStatusBarOverlay(true)
        } else if (!_showStatusBarMode.value) {
            updateStatusBarOverlay(false)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP     -> stopSelf()
            ACTION_SET_MODE -> {
                enableOverlay = intent.getBooleanExtra(EXTRA_ENABLE_FLOATING, true)
                if (!enableOverlay) removeOverlay() else if (overlayView == null) addGameSpaceOverlay()
            }
            ACTION_TOGGLE_STATUS_BAR -> {
                val show = intent.getBooleanExtra(EXTRA_SHOW_STATUS_BAR, !_showStatusBarMode.value)
                _showStatusBarMode.value = show
                updateStatusBarOverlay(show)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isFrameCallbackActive = false
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        removeOverlay()
        updateStatusBarOverlay(false)
        super.onDestroy()
    }

    private fun ensureChannel() {
        val silentChannel = NotificationChannel(
            TaskManagerApp.OVERLAY_CHANNEL_ID,
            "System Monitor Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Background service keeper"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(silentChannel)
    }

    private fun buildSilentNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, TaskManagerApp.OVERLAY_CHANNEL_ID)
            .setContentTitle("Task Manager Active")
            .setContentText("Game Space edge handle enabled")
            .setSmallIcon(R.drawable.ic_notification_chart)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    // ────────────────────────────────────────────────────────────────────
    // Game Space Edge Overlay Window (Clean layout switching, 0 middle glitch!)
    // ────────────────────────────────────────────────────────────────────

    private fun addGameSpaceOverlay() {
        if (overlayView != null) return

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        val density = resources.displayMetrics.density
        val initialWidth = if (_isExpanded.value) WindowManager.LayoutParams.MATCH_PARENT else (14 * density).toInt()
        val initialHeight = if (_isExpanded.value) WindowManager.LayoutParams.WRAP_CONTENT else (48 * density).toInt()

        val params = WindowManager.LayoutParams(
            initialWidth,
            initialHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            x = 0
            y = if (_isExpanded.value) 40 else 180
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)
            setContent {
                GameSpaceDockOverlay(
                    memStats          = _memStats.value,
                    cpuStats          = _cpuStats.value,
                    fpsStats          = _fpsStats.value,
                    thermalStats      = _thermalStats.value,
                    netStats          = _networkStats.value,
                    isExpanded        = _isExpanded.value,
                    swipeCount        = _swipeCount.value,
                    isStatusBarActive = _showStatusBarMode.value,
                    onSwipeInward     = { registerSwipeInward() },
                    onCollapse        = { toggleExpand(false) },
                    onToggleStatusBar = {
                        val next = !_showStatusBarMode.value
                        _showStatusBarMode.value = next
                        updateStatusBarOverlay(next)
                    }
                )
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    private fun registerSwipeInward() {
        val now = System.currentTimeMillis()
        if (now - lastSwipeMs < 1500L) {
            // Second swipe detected within 1.5s -> EXPAND GAME SPACE!
            _swipeCount.value = 2
            toggleExpand(true)
            lastSwipeMs = 0L
        } else {
            // First swipe -> show hint visual
            _swipeCount.value = 1
            lastSwipeMs = now
        }
    }

    private fun toggleExpand(expand: Boolean) {
        if (expand) {
            // Expand window layout & state simultaneously
            updateWindowLayout(true)
            _isExpanded.value = true
        } else {
            // Collapse panel state first with lightweight slide out
            _swipeCount.value = 0
            _isExpanded.value = false
            lifecycleScope.launch {
                delay(220L) // Wait for lightweight 200ms slide-out to finish
                if (!_isExpanded.value) {
                    updateWindowLayout(false)
                }
            }
        }
    }

    private fun updateWindowLayout(expanded: Boolean) {
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            val density = resources.displayMetrics.density
            if (expanded) {
                params.width = WindowManager.LayoutParams.MATCH_PARENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                params.x = 0
                params.y = 40
            } else {
                params.width = (14 * density).toInt()
                params.height = (48 * density).toInt()
                params.x = 0
                params.y = 180
            }
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // On-Screen Landscape Status Bar Overlay Mode
    // ────────────────────────────────────────────────────────────────────

    private fun updateStatusBarOverlay(show: Boolean) {
        if (show) {
            if (statusBarOverlayView == null) {
                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                    x = 0; y = 4
                }

                val composeView = ComposeView(this).apply {
                    setViewTreeLifecycleOwner(this@IslandOverlayService)
                    setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)
                    setContent {
                        StatusBarLiveBar(
                            memStats     = _memStats.value,
                            cpuStats     = _cpuStats.value,
                            fpsStats     = _fpsStats.value,
                            thermalStats = _thermalStats.value,
                            netStats     = _networkStats.value,
                        )
                    }
                }
                statusBarOverlayView = composeView
                try { windowManager.addView(composeView, params) } catch (_: Exception) {}
            }
        } else {
            statusBarOverlayView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
                statusBarOverlayView = null
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Data collection
    // ────────────────────────────────────────────────────────────────────

    private fun startCollecting() {
        lifecycleScope.launch {
            performanceRepo.performanceFlow(1000L).collect { snap ->
                _memStats.value     = snap.memory
                _cpuStats.value     = snap.cpu
                _fpsStats.value     = snap.fps
                _thermalStats.value = snap.thermal
                _networkStats.value = snap.networkSpeed
            }
        }
    }

    companion object {
        const val ACTION_STOP              = "com.example.taskmanager.STOP_OVERLAY"
        const val ACTION_SET_MODE          = "com.example.taskmanager.SET_MODE"
        const val ACTION_TOGGLE_STATUS_BAR = "com.example.taskmanager.TOGGLE_STATUS_BAR"
        const val EXTRA_ENABLE_FLOATING    = "enable_floating"
        const val EXTRA_SHOW_STATUS_BAR    = "show_status_bar"

        fun start(context: Context, enableFloating: Boolean = true) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_SET_MODE
                putExtra(EXTRA_ENABLE_FLOATING, enableFloating)
            }
            context.startForegroundService(intent)
        }

        fun toggleStatusBarMode(context: Context, show: Boolean) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_TOGGLE_STATUS_BAR
                putExtra(EXTRA_SHOW_STATUS_BAR, show)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Game Space Edge Dock & Dashboard Compose Overlay (Lightweight & Smooth)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameSpaceDockOverlay(
    memStats: MemoryStats?,
    cpuStats: CpuStats?,
    fpsStats: FpsStats?,
    thermalStats: ThermalVoltageStats?,
    netStats: NetworkSpeedStats?,
    isExpanded: Boolean,
    swipeCount: Int,
    isStatusBarActive: Boolean,
    onSwipeInward: () -> Unit,
    onCollapse: () -> Unit,
    onToggleStatusBar: () -> Unit,
) {
    TaskManagerTheme {
        Box(
            modifier = if (isExpanded) Modifier.fillMaxWidth() else Modifier.wrapContentSize(),
            contentAlignment = Alignment.TopStart
        ) {
            if (isExpanded) {
                // Expanded HUD Panel with lightweight horizontal slide animation
                AnimatedVisibility(
                    visible = true,
                    enter = slideInHorizontally(
                        animationSpec = tween(220, easing = LinearOutSlowInEasing),
                        initialOffsetX = { -it }
                    ) + fadeIn(tween(180)),
                    exit = slideOutHorizontally(
                        animationSpec = tween(200, easing = FastOutLinearInEasing),
                        targetOffsetX = { -it }
                    ) + fadeOut(tween(150))
                ) {
                    GameSpaceDashboardPanel(
                        memStats          = memStats,
                        cpuStats          = cpuStats,
                        fpsStats          = fpsStats,
                        thermalStats      = thermalStats,
                        netStats          = netStats,
                        isStatusBarActive = isStatusBarActive,
                        onCollapse        = onCollapse,
                        onToggleStatusBar = onToggleStatusBar,
                    )
                }
            } else {
                // Collapsed Edge Handle anchored strictly to left edge (x = 0)
                GameSpaceEdgeHandle(
                    swipeCount    = swipeCount,
                    onSwipeInward = onSwipeInward,
                )
            }
        }
    }
}

/**
 * The ultra compact Edge Handle that sits on the left screen border.
 * Dragging right or double swiping right triggers onSwipeInward.
 */
@Composable
private fun GameSpaceEdgeHandle(
    swipeCount: Int,
    onSwipeInward: () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.85f),
        label = "HandleDragOffset"
    )

    val handleGlowColor = if (swipeCount > 0) ComposeColor(0xFF10B981) else AccentViolet

    Box(
        modifier = Modifier
            .offset(x = animatedOffsetX.dp)
            .graphicsLayer { shadowElevation = 4f }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 16f) {
                            onSwipeInward()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount / 2.5f).coerceIn(0f, 50f)
                        if (dragAmount > 10f) {
                            onSwipeInward()
                        }
                    }
                )
            }
            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        ComposeColor(0xCC000000),
                        handleGlowColor.copy(alpha = 0.9f)
                    )
                )
            )
            .clickable { onSwipeInward() }
            .padding(horizontal = 3.dp, vertical = 6.dp)
    ) {
        // Ultra thin vertical glowing bar (No icon)
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(handleGlowColor)
        )
    }
}

/**
 * Full Game Space Dashboard HUD shown when double-swiped open.
 * Swiping back (left) or tapping anywhere collapses it back to the edge handle.
 */
@Composable
private fun GameSpaceDashboardPanel(
    memStats: MemoryStats?,
    cpuStats: CpuStats?,
    fpsStats: FpsStats?,
    thermalStats: ThermalVoltageStats?,
    netStats: NetworkSpeedStats?,
    isStatusBarActive: Boolean,
    onCollapse: () -> Unit,
    onToggleStatusBar: () -> Unit,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 1.0f),
        label = "PanelDragOffset"
    )

    val ramPct    = memStats?.usedPercent ?: 0f
    val cpuPct    = cpuStats?.totalPercent ?: 0f
    val fps       = fpsStats?.currentFps ?: 0
    val engineFps = fpsStats?.engineFps ?: fps
    val maxFps    = fpsStats?.refreshRate?.roundToInt() ?: 60
    val tempC     = thermalStats?.batteryTempC ?: 0f
    val netKbps   = netStats?.downlinkKbps ?: 0f

    val ramColor = when {
        ramPct > 0.85f -> ComposeColor(0xFFEF4444)
        ramPct > 0.65f -> ComposeColor(0xFFF59E0B)
        else           -> AccentViolet
    }
    val cpuColor = when {
        cpuPct > 0.85f -> ComposeColor(0xFFEF4444)
        cpuPct > 0.65f -> ComposeColor(0xFFF59E0B)
        else           -> AccentCyan
    }
    val fpsColor = when {
        fps < maxFps / 2     -> ComposeColor(0xFFEF4444)
        fps < maxFps * 3 / 4 -> ComposeColor(0xFFF59E0B)
        else                 -> ComposeColor(0xFF10B981)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = animatedOffsetX.dp)
            .graphicsLayer { shadowElevation = 8f }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -20f) {
                            onCollapse() // Swipe back left collapses panel!
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount / 2f).coerceAtMost(0f)
                        if (dragAmount < -10f) {
                            onCollapse() // Instant collapse on swipe left back
                        }
                    }
                )
            }
            .clip(RoundedCornerShape(16.dp))
            .background(ComposeColor(0xF50F0D15))
            .border(1.dp, Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)), RoundedCornerShape(16.dp))
            .clickable { onCollapse() } // Tap anywhere on panel backdrop to collapse back to handle
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // Header Row with "On Screen" toggle button placed inside Game Space HUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(AccentViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = AccentViolet,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        "SYSTEM",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ComposeColor.White
                    )
                }

                // Upper Center/Right "On Screen" Status Bar Overlay Button inside Game Space HUD
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isStatusBarActive) AccentCyan else ComposeColor(0xFF1E1B2E))
                        .clickable { onToggleStatusBar() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Tv,
                            contentDescription = null,
                            tint = if (isStatusBarActive) ComposeColor(0xFF0F0D15) else ComposeColor.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "On Screen",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStatusBarActive) ComposeColor(0xFF0F0D15) else ComposeColor.White
                        )
                    }
                }
            }

            // Stats Cards Grid (5 Cards: FPS, CPU, RAM, NET, TEMP)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // FPS Tile
                StatCard(
                    title = "FPS",
                    value = "$fps",
                    subtext = "Eng $engineFps",
                    color = fpsColor,
                    modifier = Modifier.weight(1f)
                )

                // CPU Tile
                StatCard(
                    title = "CPU",
                    value = "${(cpuPct * 100).roundToInt()}%",
                    subtext = "Load",
                    color = cpuColor,
                    modifier = Modifier.weight(1f)
                )

                // RAM Tile
                StatCard(
                    title = "RAM",
                    value = "${(ramPct * 100).roundToInt()}%",
                    subtext = "${memStats?.usedRamMb ?: 0} MB",
                    color = ramColor,
                    modifier = Modifier.weight(1f)
                )

                // NET Speed Tile
                StatCard(
                    title = "NET",
                    value = formatNetSpeed(netKbps),
                    subtext = "Speed",
                    color = AccentCyan,
                    modifier = Modifier.weight(1f)
                )

                // TEMP Tile
                StatCard(
                    title = "TEMP",
                    value = "${"%.1f".format(tempC)}°C",
                    subtext = "Battery",
                    color = ComposeColor(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatNetSpeed(kbps: Float): String {
    return when {
        kbps >= 1024f -> "%.1f M/s".format(kbps / 1024f)
        kbps >= 1f     -> "%.0f K/s".format(kbps)
        else          -> "0 K/s"
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtext: String,
    color: ComposeColor,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0xFF161420))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ComposeColor(0xFF94A3B8))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
        Text(subtext, fontSize = 8.sp, color = ComposeColor(0xFF64748B))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Landscape Status Bar Overlay (non-clickable, display only)
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBarLiveBar(
    memStats: MemoryStats?,
    cpuStats: CpuStats?,
    fpsStats: FpsStats?,
    thermalStats: ThermalVoltageStats?,
    netStats: NetworkSpeedStats?,
) {
    val ramPct  = memStats?.usedPercent ?: 0f
    val cpuPct  = cpuStats?.totalPercent ?: 0f
    val fps     = fpsStats?.currentFps ?: 0
    val maxFps  = fpsStats?.refreshRate?.roundToInt() ?: 60
    val tempC   = thermalStats?.batteryTempC ?: 0f
    val netKbps = netStats?.downlinkKbps ?: 0f

    val ramColor = when {
        ramPct > 0.85f -> ComposeColor(0xFFEF4444)
        ramPct > 0.65f -> ComposeColor(0xFFF59E0B)
        else           -> AccentViolet
    }
    val cpuColor = when {
        cpuPct > 0.85f -> ComposeColor(0xFFEF4444)
        cpuPct > 0.65f -> ComposeColor(0xFFF59E0B)
        else           -> AccentCyan
    }
    val fpsColor = when {
        fps < maxFps / 2     -> ComposeColor(0xFFEF4444)
        fps < maxFps * 3 / 4 -> ComposeColor(0xFFF59E0B)
        else                 -> ComposeColor(0xFF10B981)
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
                    .background(ComposeColor(0xD0111318))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Dot(ramColor); Label("RAM ${(ramPct * 100).roundToInt()}%")
                Sep()
                Dot(cpuColor); Label("CPU ${(cpuPct * 100).roundToInt()}%")
                Sep()
                Dot(AccentCyan); Label("NET ${formatNetSpeed(netKbps)}")
                Sep()
                Label("${"%.1f".format(tempC)}°C", ComposeColor(0xFFF59E0B))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ComposeColor(0xD0111318))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Label("$fps FPS", fpsColor)
                Dot(fpsColor)
            }
        }
    }
}

@Composable
private fun Dot(color: ComposeColor) =
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))

@Composable
private fun Label(text: String, color: ComposeColor = ComposeColor.White) =
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)

@Composable
private fun Sep() =
    Text("•", fontSize = 10.sp, color = ComposeColor(0xFF64748B))
