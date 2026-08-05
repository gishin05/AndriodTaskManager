package com.example.taskmanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class TaskManagerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            OVERLAY_CHANNEL_ID,
            "HyperOS Dynamic Island Live Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Persistent channel for HyperOS Native Island Live Updates"
            setShowBadge(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val OVERLAY_CHANNEL_ID = "island_overlay_channel"
        const val OVERLAY_NOTIFICATION_ID = 1001
    }
}
