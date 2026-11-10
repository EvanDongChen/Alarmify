package com.evanchen.alarmify.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evanchen.alarmify.ring.AlarmService

/** AlarmManager fires this at ring time; it hands straight off to the foreground service that rings. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val placeId = intent.getIntExtra(EXTRA_PLACE_ID, -1)
        if (placeId > 0) {
            AlarmService.startPlace(
                context, placeId,
                intent.getStringExtra(EXTRA_PLACE_LABEL).orEmpty(),
                intent.getBooleanExtra(EXTRA_PLACE_VIBRATE, true),
            )
            return
        }
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id < 0) return
        context.startForegroundService(AlarmService.startIntent(context, id))
    }

    companion object {
        const val EXTRA_PLACE_ID = "place_id"
        const val EXTRA_PLACE_LABEL = "place_label"
        const val EXTRA_PLACE_VIBRATE = "place_vibrate"
    }
}
