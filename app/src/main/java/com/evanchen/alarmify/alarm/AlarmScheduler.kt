package com.evanchen.alarmify.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.evanchen.alarmify.MainActivity
import com.evanchen.alarmify.data.Alarm

object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"

    /** Makes AlarmManager match [alarms]: schedules each one's next ring, cancels those that won't ring. */
    fun sync(context: Context, alarms: List<Alarm>) {
        val now = System.currentTimeMillis()
        alarms.forEach { alarm ->
            val at = alarm.nextTrigger(now)
            if (at != null) schedule(context, alarm.id, at) else cancel(context, alarm.id)
        }
    }

    private fun schedule(context: Context, id: Int, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = ringIntent(context, id, PendingIntent.FLAG_UPDATE_CURRENT)!!
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            // setAlarmClock rings exactly on time even in Doze, and shows the alarm icon in the status bar.
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    fun cancel(context: Context, id: Int) {
        val pi = ringIntent(context, id, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    private fun ringIntent(context: Context, id: Int, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, id),
            PendingIntent.FLAG_IMMUTABLE or flag,
        )
}
