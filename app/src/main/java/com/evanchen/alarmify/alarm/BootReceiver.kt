package com.evanchen.alarmify.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evanchen.alarmify.data.AlarmRepository

/** AlarmManager forgets everything on reboot (and should re-plan when the clock or time zone changes). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmRepository.init(context)
        AlarmRepository.rescheduleAll()
    }
}
