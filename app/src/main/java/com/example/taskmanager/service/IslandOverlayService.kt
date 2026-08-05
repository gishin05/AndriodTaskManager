package com.example.taskmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
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
import androidx.compose.ui.graphics.Color as ComposeColor
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
    private val _memStats     = mutableStateOf<MemoryStats?>(null)
    private val _cpuStats     = mutableStateOf<CpuStats?>(null)
    private val _fpsStats     = mutableStateOf<FpsStats?>(null)
    private val _thermalStats = mutableStateOf<ThermalVoltageStats?>(null)
    private val _networkStats = mutableStateOf<NetworkSpeedStats?>(null)
    private val _activeApps   = mutableStateOf(0)
    private val _isLandscape  = mutableStateOf(false)
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

        ensureChannels()

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        _isLandscape.value = isLandscape

        // Start foreground immediately to satisfy service requirements
        val initialNotif = buildBaseNotification("Task Manager Active", "Collecting system metrics…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TaskManagerApp.OVERLAY_NOTIFICATION_ID,
                initialNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(TaskManagerApp.OVERLAY_NOTIFICATION_ID, initialNotif)
        }

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
            ACTION_STOP             -> stopSelf()
            ACTION_SET_MODE         -> {
                enableOverlay = intent.getBooleanExtra(EXTRA_ENABLE_FLOATING, true)
                updateOverlayForOrientation(_isLandscape.value)
            }
            ACTION_UPDATE_APP_COUNT -> _activeApps.value = intent.getIntExtra(EXTRA_APP_COUNT, 0)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isFrameCallbackActive = false
        Choreographer.getInstance().removeFrameCallback(choreographerCallback)
        removeOverlay()
        super.onDestroy()
    }

    // ────────────────────────────────────────────────────────────────────
    // Notification channels
    // ────────────────────────────────────────────────────────────────────

    private fun ensureChannels() {
        // Live Updates channel – IMPORTANCE_DEFAULT required for promotion
        val liveChannel = NotificationChannel(
            LIVE_UPDATE_CHANNEL_ID,
            "Live System Monitor",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description     = "Real-time CPU, RAM and thermal data in the status bar"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(liveChannel)

        // Silent service-keeping channel
        val silentChannel = NotificationChannel(
            TaskManagerApp.OVERLAY_CHANNEL_ID,
            "System Monitor Service",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description  = "Background service notification"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(silentChannel)
    }

    // ────────────────────────────────────────────────────────────────────
    // Notification builders
    // ────────────────────────────────────────────────────────────────────

    private fun buildBaseNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, TaskManagerApp.OVERLAY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_chart)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    /**
     * Posts the promoted Live Update notification.
     * Strategy (layered, most-capable first):
     *   1. Android 16 (API 36)  → Notification.ProgressStyle + EXTRA_REQUEST_PROMOTED_ONGOING
     *                              Works on ALL Android 16 phones regardless of OEM (Pixel, Samsung, Xiaomi, etc.)
     *   2. HyperOS / MIUI       → HyperIsland Toolkit bundle extras
     *   3. Fallback             → Standard NotificationCompat with a progress bar
     */
    private fun updateLiveNotification(
        ramPct: Float,
        cpuPct: Float,
        fps: Int,
        tempC: Float,
        downKbps: Float,
    ) {
        val ramInt  = (ramPct * 100).roundToInt().coerceIn(0, 100)
        val cpuInt  = (cpuPct * 100).roundToInt().coerceIn(0, 100)
        val speedText = formatSpeed(downKbps)
        val ramBar  = buildBar(ramPct)
        val cpuBar  = buildBar(cpuPct)
        val title   = "CPU ${cpuInt}%  ·  RAM ${ramInt}%  ·  ${fps} FPS"
        val text    = "$ramBar RAM   $cpuBar CPU   ${"+%.1f".format(tempC)}°C   ↓$speedText"

        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val liveIcon = IconUtils.createMetricBitmap("$cpuInt%", cpuPct)

        // ── Tier 1: Android 16 Promoted Ongoing (works on all OEMs on API 36+) ──
        if (Build.VERSION.SDK_INT >= 36) {
            postAndroid16LiveUpdate(
                cpuInt, ramInt, fps, tempC, speedText, title, text, openIntent, liveIcon
            )
            return
        }

        // ── Tier 2: HyperOS / MIUI with HyperIsland Toolkit ──
        if (isHyperOS() || isMiui()) {
            postHyperIslandNotification(cpuInt, ramInt, fps, tempC, speedText, title, text, openIntent, liveIcon)
            return
        }

        // ── Tier 3: Standard fallback (all other Android 8–15 devices) ──
        postFallbackNotification(cpuInt, ramInt, title, text, openIntent, liveIcon)
    }

    @Suppress("DEPRECATION")
    private fun postAndroid16LiveUpdate(
        cpuInt: Int, ramInt: Int, fps: Int, tempC: Float, speedText: String,
        title: String, text: String,
        openIntent: PendingIntent, liveIcon: android.graphics.Bitmap
    ) {
        // Build ProgressStyle for the Live Update chip
        val progressStyle = Notification.ProgressStyle()
            .apply {
                progress = cpuInt            // CPU % drives the ring/bar
            }

        val notification = Notification.Builder(this, LIVE_UPDATE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("System Monitor")
            .setSmallIcon(R.drawable.ic_notification_chart)
            .setLargeIcon(liveIcon)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setStyle(progressStyle)
            .apply {
                // Request promotion to the Live Update chip in the status bar.
                // The constant is defined as "android.app.extra.REQUEST_PROMOTED_ONGOING" in API 36.
                extras.putBoolean("android.app.extra.REQUEST_PROMOTED_ONGOING", true)
            }
            .build()

        notificationManager.notify(LIVE_UPDATE_NOTIFICATION_ID, notification)
    }

    private fun postHyperIslandNotification(
        cpuInt: Int, ramInt: Int, fps: Int, tempC: Float, speedText: String,
        title: String, text: String,
        openIntent: PendingIntent, liveIcon: android.graphics.Bitmap
    ) {
        val hyperExtras = try {
            HyperIslandNotification.Builder(this, "sys_monitor", "upload_progress")
                .setProgressBar(
                    cpuInt,
                    title,
                    text,
                    "#FFFFFF", "#AAAAAA", "#10B981", "#1A1A2E",
                    "ic_menu_info_details"
                )
                .buildCustomExtras()
        } catch (e: Exception) {
            Bundle()
        }

        val notif = NotificationCompat.Builder(this, LIVE_UPDATE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("System Monitor")
            .setSmallIcon(R.drawable.ic_notification_chart)
            .setLargeIcon(liveIcon)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(hyperExtras)
            .build()

        notificationManager.notify(LIVE_UPDATE_NOTIFICATION_ID, notif)
    }

    private fun postFallbackNotification(
        cpuInt: Int, ramInt: Int,
        title: String, text: String,
        openIntent: PendingIntent, liveIcon: android.graphics.Bitmap
    ) {
        val notif = NotificationCompat.Builder(this, LIVE_UPDATE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText("System Monitor")
            .setSmallIcon(R.drawable.ic_notification_chart)
            .setLargeIcon(liveIcon)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, cpuInt, false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(LIVE_UPDATE_NOTIFICATION_ID, notif)
    }

    // ────────────────────────────────────────────────────────────────────
    // OEM detection helpers
    // ────────────────────────────────────────────────────────────────────

    private fun getSystemProperty(key: String): String? = try {
        val c = Class.forName("android.os.SystemProperties")
        c.getMethod("get", String::class.java).invoke(null, key) as? String
    } catch (_: Exception) { null }

    private fun isHyperOS(): Boolean {
        val uiVersion = getSystemProperty("ro.miui.ui.version.name")
        val modDevice = getSystemProperty("ro.product.mod_device")
        if (!uiVersion.isNullOrBlank() || !modDevice.isNullOrBlank()) return true
        val manu = Build.MANUFACTURER.lowercase()
        return manu.contains("xiaomi") || manu.contains("poco") || manu.contains("redmi")
    }

    private fun isMiui(): Boolean {
        val code = getSystemProperty("ro.miui.ui.version.code")
        return !code.isNullOrBlank()
    }

    // ────────────────────────────────────────────────────────────────────
    // Overlay (landscape status bar mode)
    // ────────────────────────────────────────────────────────────────────

    private fun updateOverlayForOrientation(isLandscape: Boolean) {
        _isLandscape.value = isLandscape
        if (!enableOverlay) { removeOverlay(); return }

        if (isLandscape) {
            if (overlayView == null) addLandscapeOverlay()
            else {
                overlayView?.let {
                    val params = it.layoutParams as WindowManager.LayoutParams
                    params.width = WindowManager.LayoutParams.MATCH_PARENT
                    params.y = 4
                    try { windowManager.updateViewLayout(it, params) } catch (_: Exception) {}
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

                updateLiveNotification(
                    ramPct   = snap.memory.usedPercent,
                    cpuPct   = snap.cpu.totalPercent,
                    fps      = snap.fps.currentFps,
                    tempC    = snap.thermal.batteryTempC,
                    downKbps = snap.networkSpeed.downlinkKbps,
                )
            }
        }
    }

    private fun formatSpeed(kbps: Float): String =
        if (kbps > 1024f) "${"+%.1f".format(kbps / 1024f)} MB/s" else "${kbps.roundToInt()} KB/s"

    /** Returns an 8-char ASCII progress bar e.g. "▓▓▓▓▓░░░" */
    private fun buildBar(pct: Float, width: Int = 6): String {
        val filled = (pct.coerceIn(0f, 1f) * width).roundToInt()
        return "▓".repeat(filled) + "░".repeat(width - filled)
    }

    // ────────────────────────────────────────────────────────────────────
    // Companion
    // ────────────────────────────────────────────────────────────────────

    companion object {
        const val ACTION_STOP             = "com.example.taskmanager.STOP_OVERLAY"
        const val ACTION_SET_MODE         = "com.example.taskmanager.SET_MODE"
        const val ACTION_UPDATE_APP_COUNT = "com.example.taskmanager.UPDATE_APP_COUNT"
        const val EXTRA_ENABLE_FLOATING   = "enable_floating"
        const val EXTRA_MODE              = "extra_mode"
        const val EXTRA_APP_COUNT         = "app_count"

        /** Separate channel for the Live Updates notification – needs IMPORTANCE_DEFAULT */
        const val LIVE_UPDATE_CHANNEL_ID      = "live_update_channel"
        const val LIVE_UPDATE_NOTIFICATION_ID = 9002

        fun start(context: Context, enableFloating: Boolean = true) {
            val intent = Intent(context, IslandOverlayService::class.java).apply {
                action = ACTION_SET_MODE
                putExtra(EXTRA_ENABLE_FLOATING, enableFloating)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, IslandOverlayService::class.java).apply { action = ACTION_STOP })
        }
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
    networkStats: NetworkSpeedStats?,
) {
    val ramPct = memStats?.usedPercent ?: 0f
    val cpuPct = cpuStats?.totalPercent ?: 0f
    val fps    = fpsStats?.currentFps ?: 0
    val maxFps = fpsStats?.refreshRate?.roundToInt() ?: 60
    val tempC  = thermalStats?.batteryTempC ?: 0f

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
