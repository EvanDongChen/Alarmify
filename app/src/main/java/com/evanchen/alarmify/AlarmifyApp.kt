package com.evanchen.alarmify

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.evanchen.alarmify.data.AlarmRepository
import com.evanchen.alarmify.data.PlaceRepository

class AlarmifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmRepository.init(this)
        PlaceRepository.init(this)
        val channel = NotificationChannel(CHANNEL_RINGING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shows the alarm when it goes off"
            setSound(null, null) // the service plays the alarm sound itself
            enableVibration(false)
            setBypassDnd(true)
        }
        val trip = NotificationChannel(CHANNEL_TRIP, "Place alarms", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows how far you are while a place alarm is watching"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(channel, trip))
    }

    companion object {
        const val CHANNEL_RINGING = "ringing"
        const val CHANNEL_TRIP = "trip"
    }
}
