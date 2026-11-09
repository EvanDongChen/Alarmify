package com.evanchen.alarmify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.evanchen.alarmify.data.AlarmRepository

class AlarmifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmRepository.init(this)
        val channel = NotificationChannel(CHANNEL_RINGING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shows the alarm when it goes off"
            setSound(null, null) // the service plays the alarm sound itself
            enableVibration(false)
            setBypassDnd(true)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_RINGING = "ringing"
    }
}
