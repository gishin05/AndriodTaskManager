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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    private val _isLandscapeState = mutableStateOf(false)
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

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        _isLandscapeState.value = isLandscape

        startForegroundWithNotification(
            title = "Task Manager System Service",
            text  = "Connecting to Native HyperIsland Toolkit...",
            subText = "HyperOS Native System Monitor"
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
                enableOverlay = intent.getBooleanExtra(EXTRA_ENABLE_FLOATING, true)
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                updateOverlayForOrientation(isLandscape)
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

        val notification: Notification = NotificationCompat.Builder(this, TaskManagerApp.OVERLAY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
        val speedText = if (downKbps > 1024f) "${"%.1f".format(downKbps / 1024f)}MB/s" else "${downKbps.roundToInt()}KB/s"

        // ── Use D4vidDf HyperIsland Toolkit! ──
        val hyperIslandExtras = HyperIslandNotification.Builder(this, "sys_monitor_id", "upload_progress")
            .setProgressBar(
                cpuInt, 
                "System Monitor ($fps FPS | $speedText)", 
                "CPU: $cpuInt% | RAM: $ramInt% | Temp: ${"%.1f".format(tempC)}°C", 
                "#FFFFFF", 
                "#AAAAAA", 
                "#10B981", 
                "#333333", 
                "ic_menu_info_details"
            )
            .buildCustomExtras()

        val combinedExtras = Bundle().apply {
            putBoolean("miui.show_in_statusbar", true)
            putBoolean("extra_show_in_statusbar", true)
            putInt("miui.status_bar_notification_style", 1)
            putBoolean("android.live_update", true)
            putAll(hyperIslandExtras) // Inject the actual Toolkit payload!
        }

        val notification: Notification = NotificationCompat.Builder(this, TaskManagerApp.OVERLAY_CHANNEL_ID)
            .setContentTitle("RAM $ramInt% • CPU $cpuInt% • $fps FPS")
            .setContentText("${"%.1f".format(tempC)}°C • ↓$speedText • Toolkit Active")
            .setSubText("System Monitor")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setLargeIcon(liveIconBitmap)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(combinedExtras)
            .build()

        notificationManager.notify(TaskManagerApp.OVERLAY_NOTIFICATION_ID, notification)
    }

    private fun updateOverlayForOrientation(isLandscape: Boolean) {
        _isLandscapeState.value = isLandscape
        if (!enableOverlay) {
            removeOverlay()
            return
        }

        if (isLandscape) {
            if (overlayView == null) {
                addLandscapeOverlay()
            } else {
                overlayView?.let { view ->
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                    params.y = 4
                    params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                }
            }
        } else {
            removeOverlay()
        }
    }

    private fun addLandscapeOverlay() {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
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
            x = 0
            y = 4
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@IslandOverlayService)
            setViewTreeSavedStateRegistryOwner(this@IslandOverlayService)

            setContent {
                StatusBarLiveBar(
                    memStats = _memStats.value,
                    cpuStats = _cpuStats.value,
                    fpsStats = _fpsStats.value,
                    thermalStats = _thermalStats.value,
                    networkStats = _networkStats.value,
                )
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
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

        fun start(context: Context, enableFloating: Boolean = true, mode: Int = 1) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_SET_MODE
                putExtra(EXTRA_ENABLE_FLOATING, enableFloating)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, IslandOverlayService::class.java).apply { action = ACTION_STOP }
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
