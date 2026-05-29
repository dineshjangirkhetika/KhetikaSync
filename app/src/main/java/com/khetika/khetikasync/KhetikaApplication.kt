package com.khetika.khetikasync

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KhetikaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_APPROVALS) != null) return
        val channel = NotificationChannel(
            CHANNEL_APPROVALS,
            "Approval activity",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Pending approvals, decisions, and reminders."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_APPROVALS = "approvals"
    }
}
