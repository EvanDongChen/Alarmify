package com.evanchen.alarmify.place

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.evanchen.alarmify.AlarmifyApp
import com.evanchen.alarmify.MainActivity
import com.evanchen.alarmify.R
import com.evanchen.alarmify.alarm.AlarmReceiver
import com.evanchen.alarmify.data.LatLon
import com.evanchen.alarmify.data.PlaceAlarm
import com.evanchen.alarmify.data.PlaceRepository
import com.evanchen.alarmify.ring.AlarmService
import com.evanchen.alarmify.ui.formatDistance
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Watches your location while a place alarm is active, like a transit app's trip mode, and rings when you get close.
 *
 * Battery: it asks for fixes rarely and at low power while you're far away, and more often as you approach
 * (see [tierFor]); it stops entirely once nothing is left to watch.
 */
class PlaceTrackingService : Service() {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var tier: Tier? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onFix)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            PlaceRepository.active.value.forEach { PlaceRepository.cancel(it.id) }
            finish()
            return START_NOT_STICKY
        }
        val active = PlaceRepository.active.value
        goForeground(notification(active, null))
        if (active.isEmpty() || !PlaceRepository.hasLocationPermission(this)) { finish(); return START_NOT_STICKY }
        // Start brisk until the first fix tells us how far away we are.
        request(Tier.Near)
        return START_STICKY
    }

    private fun onFix(loc: Location) {
        PlaceRepository.setHere(LatLon(loc.latitude, loc.longitude))
        val active = PlaceRepository.active.value
        if (active.isEmpty()) { finish(); return }

        // Arrived anywhere? Give a little benefit of the doubt for GPS wobble, but not more than 50 m.
        val slack = minOf(loc.accuracy, 50f)
        active.filter { it.place.distanceTo(loc.latitude, loc.longitude) - slack <= it.radiusM }.forEach { arrive(it) }

        val remaining = PlaceRepository.active.value
        if (remaining.isEmpty()) { finish(); return }
        val gap = remaining.minOf { it.place.distanceTo(loc.latitude, loc.longitude) - it.radiusM }
        notify(notification(remaining, loc))
        val next = tierFor(gap)
        if (next != tier) request(next)
    }

    private fun arrive(alarm: PlaceAlarm) {
        PlaceRepository.arrived(alarm.id)
        ringSoon(this, alarm)
    }

    @SuppressLint("MissingPermission") // checked before starting
    private fun request(t: Tier) {
        tier = t
        fused.removeLocationUpdates(callback)
        val req = LocationRequest.Builder(t.priority, t.intervalMs)
            .setMinUpdateIntervalMillis(t.intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        runCatching { fused.requestLocationUpdates(req, callback, Looper.getMainLooper()) }
            .onFailure { finish() }
    }

    private fun finish() {
        runCatching { fused.removeLocationUpdates(callback) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        super.onDestroy()
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notify(n: Notification) =
        runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, n) }

    private fun notification(active: List<PlaceAlarm>, here: Location?): Notification {
        val first = active.firstOrNull()
        val title = when {
            first == null -> "Place alarm"
            active.size == 1 -> "Watching for ${first.place.name}"
            else -> "Watching for ${active.size} places"
        }
        val text = when {
            first == null -> ""
            here == null -> "Finding you… rings within ${formatDistance(first.radiusM.toFloat())}"
            else -> {
                val nearest = active.minBy { it.place.distanceTo(here.latitude, here.longitude) }
                "${formatDistance(nearest.place.distanceTo(here.latitude, here.longitude))} from ${nearest.place.name} · " +
                    "rings within ${formatDistance(nearest.radiusM.toFloat())}"
            }
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PlaceTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, AlarmifyApp.CHANNEL_TRIP)
            .setSmallIcon(R.drawable.ic_stat_place)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setColor(0xFFFF7A5C.toInt())
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    /** How often to look, by how far you still are from the nearest edge of an alarm's circle. */
    enum class Tier(val priority: Int, val intervalMs: Long) {
        VeryFar(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 120_000),
        Far(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000),
        Mid(Priority.PRIORITY_HIGH_ACCURACY, 20_000),
        Close(Priority.PRIORITY_HIGH_ACCURACY, 8_000),
        Near(Priority.PRIORITY_HIGH_ACCURACY, 4_000),
    }

    private fun tierFor(gapM: Float) = when {
        gapM > 20_000 -> Tier.VeryFar
        gapM > 5_000 -> Tier.Far
        gapM > 2_000 -> Tier.Mid
        gapM > 600 -> Tier.Close
        else -> Tier.Near
    }

    companion object {
        private const val NOTIFICATION_ID = 77
        private const val ACTION_STOP = "com.evanchen.alarmify.STOP_TRACKING"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, PlaceTrackingService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaceTrackingService::class.java))
        }

        /**
         * Rings via the system alarm clock half a second from now: an alarm-clock alarm is always allowed to start
         * the ringing service, even with the app in the background.
         */
        fun ringSoon(context: Context, alarm: PlaceAlarm) {
            val am = context.getSystemService(AlarmManager::class.java)
            val op = PendingIntent.getBroadcast(
                context, PLACE_REQUEST_BASE + alarm.id,
                Intent(context, AlarmReceiver::class.java)
                    .putExtra(AlarmReceiver.EXTRA_PLACE_ID, alarm.id)
                    .putExtra(AlarmReceiver.EXTRA_PLACE_LABEL, alarm.place.name)
                    .putExtra(AlarmReceiver.EXTRA_PLACE_VIBRATE, alarm.vibrate),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val show = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 500, show), op) }
                .onFailure { AlarmService.startPlace(context, alarm.id, alarm.place.name, alarm.vibrate) }
        }

        private const val PLACE_REQUEST_BASE = 500_000
    }
}
