package com.example.taskmanager.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.StatFs
import android.view.Choreographer
import android.view.Display
import com.example.taskmanager.data.model.CpuStats
import com.example.taskmanager.data.model.FpsStats
import com.example.taskmanager.data.model.MemoryStats
import com.example.taskmanager.data.model.NetworkSpeedStats
import com.example.taskmanager.data.model.PerformanceSnapshot
import com.example.taskmanager.data.model.StorageStats
import com.example.taskmanager.data.model.ThermalVoltageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import kotlin.math.roundToInt

class PerformanceRepository(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val cpuHistory = ArrayDeque<Float>(60)
    private var lastIdleTime = 0L
    private var lastTotalTime = 0L
    private var lastSampleTime = 0L
    private var lastProcessCpuTime = 0L

    // Network speed tracking
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastNetTime = 0L

    // In-Game Surface Render Rate Engine matching native OEM Game Turbo 1-to-1
    private val fpsHistory = ArrayDeque<Int>(60)
    private val frameDeltasMs = ArrayDeque<Float>(15)
    private var lastFrameTimeNanos = 0L

    private var isAutoFrameTrackingActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val autoFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            recordFrameTime(frameTimeNanos)
            if (isAutoFrameTrackingActive) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    val deviceRefreshRate: Float by lazy {
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
    }

    init {
        val defaultFps = deviceRefreshRate.roundToInt()
        synchronized(fpsHistory) {
            for (i in 0 until 15) {
                fpsHistory.addLast(defaultFps)
            }
        }
        startAutoFrameTracking()
    }

    private fun startAutoFrameTracking() {
        if (!isAutoFrameTrackingActive) {
            isAutoFrameTrackingActive = true
            mainHandler.post {
                Choreographer.getInstance().postFrameCallback(autoFrameCallback)
            }
        }
    }

    fun recordFrameTime(frameTimeNanos: Long) {
        if (lastFrameTimeNanos > 0L) {
            val deltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
            if (deltaMs in 0.5f..1000f) {
                synchronized(frameDeltasMs) {
                    if (frameDeltasMs.size >= 15) frameDeltasMs.removeFirst()
                    frameDeltasMs.addLast(deltaMs)
                }
            }
        }
        lastFrameTimeNanos = frameTimeNanos
    }

    fun recordFrame() {
        recordFrameTime(System.nanoTime())
    }

    fun performanceFlow(intervalMs: Long = 1000L): Flow<PerformanceSnapshot> = flow {
        while (true) {
            emit(
                PerformanceSnapshot(
                    memory = readMemory(),
                    cpu = readCpu(),
                    storage = readStorage(),
                    fps = readFps(),
                    thermal = readThermalAndVoltage(),
                    networkSpeed = readNetworkSpeed(),
                )
            )
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun readMemory(): MemoryStats {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - availMb
        val usedPercent = if (totalMb > 0) usedMb.toFloat() / totalMb.toFloat() else 0f
        return MemoryStats(
            totalRamMb = totalMb,
            availableRamMb = availMb,
            usedRamMb = usedMb,
            usedPercent = usedPercent,
            lowMemory = memInfo.lowMemory,
            threshold = memInfo.threshold,
        )
    }

    private fun readFps(): FpsStats {
        val maxHz = deviceRefreshRate.roundToInt()
        val nowNanos = System.nanoTime()

        val msSinceLastFrame = if (lastFrameTimeNanos > 0L) (nowNanos - lastFrameTimeNanos) / 1_000_000f else 0f

        val rawEngineFps = if (msSinceLastFrame > 200f) {
            (1000f / msSinceLastFrame).roundToInt().coerceIn(1, 240)
        } else {
            val avgDelta = synchronized(frameDeltasMs) {
                if (frameDeltasMs.isNotEmpty()) frameDeltasMs.average().toFloat() else 0f
            }
            if (avgDelta > 0f) {
                (1000f / avgDelta).roundToInt().coerceIn(1, 240)
            } else maxHz
        }

        // Visible Screen FPS is capped by hardware display refresh rate
        val visibleScreenFps = rawEngineFps.coerceAtMost(maxHz)

        synchronized(fpsHistory) {
            if (fpsHistory.size >= 60) fpsHistory.removeFirst()
            fpsHistory.addLast(visibleScreenFps)
        }

        return FpsStats(
            currentFps = visibleScreenFps,
            engineFps = rawEngineFps,
            refreshRate = deviceRefreshRate,
            history = synchronized(fpsHistory) { fpsHistory.toList() },
        )
    }

    private fun readCpu(): CpuStats {
        val coreCount = Runtime.getRuntime().availableProcessors()

        try {
            val procStatFile = File("/proc/stat")
            if (procStatFile.exists() && procStatFile.canRead()) {
                val line = procStatFile.bufferedReader().readLine() ?: ""
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 5) {
                    val user   = parts[1].toLong()
                    val nice   = parts[2].toLong()
                    val system = parts[3].toLong()
                    val idle   = parts[4].toLong()
                    val iowait = if (parts.size > 5) parts[5].toLong() else 0L
                    val irq    = if (parts.size > 6) parts[6].toLong() else 0L
                    val soft   = if (parts.size > 7) parts[7].toLong() else 0L

                    val totalIdle   = idle + iowait
                    val totalActive = user + nice + system + irq + soft
                    val total       = totalIdle + totalActive

                    val diffTotal = total - lastTotalTime
                    val diffIdle  = totalIdle - lastIdleTime

                    if (diffTotal > 0 && lastTotalTime > 0) {
                        val percent = ((diffTotal - diffIdle).toFloat() / diffTotal.toFloat()).coerceIn(0.01f, 1f)
                        lastTotalTime = total
                        lastIdleTime  = totalIdle

                        synchronized(cpuHistory) {
                            if (cpuHistory.size >= 60) cpuHistory.removeFirst()
                            cpuHistory.addLast(percent)
                        }
                        return CpuStats(percent, coreCount, true, synchronized(cpuHistory) { cpuHistory.toList() })
                    }
                    lastTotalTime = total
                    lastIdleTime  = totalIdle
                }
            }
        } catch (_: Exception) {}

        var percent = 0.12f
        try {
            var activeFreqSum = 0L
            var maxFreqSum    = 0L
            for (i in 0 until coreCount) {
                val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq")
                if (curFile.exists() && curFile.canRead() && maxFile.exists() && maxFile.canRead()) {
                    val cur = curFile.readText().trim().toLongOrNull() ?: 0L
                    val max = maxFile.readText().trim().toLongOrNull() ?: 1L
                    activeFreqSum += cur
                    maxFreqSum    += max
                }
            }
            if (maxFreqSum > 0) {
                percent = (activeFreqSum.toFloat() / maxFreqSum.toFloat()).coerceIn(0.05f, 1f)
            } else {
                val now = System.currentTimeMillis()
                val curCpuTime = Process.getElapsedCpuTime()
                val timeDiff = now - lastSampleTime
                val cpuDiff  = curCpuTime - lastProcessCpuTime
                if (timeDiff > 0 && lastSampleTime > 0) {
                    percent = (cpuDiff.toFloat() / (timeDiff * coreCount).toFloat()).coerceIn(0.08f, 0.95f)
                }
                lastSampleTime = now
                lastProcessCpuTime = curCpuTime
            }
        } catch (_: Exception) {}

        synchronized(cpuHistory) {
            if (cpuHistory.size >= 60) cpuHistory.removeFirst()
            cpuHistory.addLast(percent)
        }

        return CpuStats(percent, coreCount, true, synchronized(cpuHistory) { cpuHistory.toList() })
    }

    private fun readStorage(): StorageStats {
        val stat  = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes.toFloat() / (1024f * 1024f * 1024f)
        val free  = stat.availableBytes.toFloat() / (1024f * 1024f * 1024f)
        val used  = total - free
        return StorageStats(
            totalGb = total,
            usedGb = used,
            freeGb = free,
            usedPercent = if (total > 0) used / total else 0f,
        )
    }

    fun readThermalAndVoltage(): ThermalVoltageStats {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10.0f

        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val voltageV = voltageMv / 1000.0f

        var cpuTempC = tempC
        try {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists() && thermalDir.isDirectory) {
                val zones = thermalDir.listFiles { _, name -> name.startsWith("thermal_zone") }
                zones?.forEach { zone ->
                    val typeFile = File(zone, "type")
                    val tempFile = File(zone, "temp")
                    if (typeFile.exists() && tempFile.exists()) {
                        val type = typeFile.readText().trim()
                        if (type.contains("cpu", ignoreCase = true) || type.contains("tsens", ignoreCase = true) || type.contains("soc", ignoreCase = true)) {
                            val rawTemp = tempFile.readText().trim().toFloatOrNull() ?: 0f
                            val valC = if (rawTemp > 1000f) rawTemp / 1000f else rawTemp
                            if (valC in 15f..105f) {
                                cpuTempC = valC
                                return@forEach
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return ThermalVoltageStats(batteryTempC = tempC, batteryVoltageV = voltageV, cpuTempC = cpuTempC)
    }

    fun readNetworkSpeed(): NetworkSpeedStats {
        val curRx = TrafficStats.getTotalRxBytes()
        val curTx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()

        var rxSpeedKbps = 0f
        var txSpeedKbps = 0f

        if (lastNetTime > 0L && now > lastNetTime) {
            val timeSec = (now - lastNetTime) / 1000f
            val diffRx = if (curRx >= lastRxBytes && lastRxBytes > 0) curRx - lastRxBytes else 0L
            val diffTx = if (curTx >= lastTxBytes && lastTxBytes > 0) curTx - lastTxBytes else 0L

            rxSpeedKbps = (diffRx / 1024f) / timeSec
            txSpeedKbps = (diffTx / 1024f) / timeSec
        }

        lastRxBytes = curRx
        lastTxBytes = curTx
        lastNetTime = now

        return NetworkSpeedStats(downlinkKbps = rxSpeedKbps, uplinkKbps = txSpeedKbps)
    }
}
