package com.samsung.android.scan3d

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build


class App: Application() {
    override fun onCreate() {
        super.onCreate()
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "location",
                "Locator Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val channel2 = NotificationChannel(
                    "camera",
                    "Camera Channel",
                    NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                notificationManager.createNotificationChannel(channel2)
        }

    }
    
}