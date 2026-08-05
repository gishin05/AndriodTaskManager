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
            "Island Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent channel for the Island overlay service"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val OVERLAY_CHANNEL_ID = "island_overlay_channel"
        const val OVERLAY_NOTIFICATION_ID = 1001
    }
}
