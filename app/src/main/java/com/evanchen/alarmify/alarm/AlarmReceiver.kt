package com.evanchen.alarmify.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evanchen.alarmify.ring.AlarmService

/** AlarmManager fires this at ring time; it hands straight off to the foreground service that rings. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id < 0) return
        context.startForegroundService(AlarmService.startIntent(context, id))
    }
}
