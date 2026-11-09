package com.evanchen.alarmify.ring

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.evanchen.alarmify.AlarmifyApp
import com.evanchen.alarmify.R
import com.evanchen.alarmify.data.Alarm
import com.evanchen.alarmify.data.AlarmRepository
import com.evanchen.alarmify.ui.formatClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Rings an alarm: plays the alarm sound (fading in), vibrates, and posts a full-screen notification that
 * opens [RingActivity] over the lock screen. Stops itself on dismiss, snooze, or after [AUTO_SILENCE_MS].
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var currentId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_START -> start(id)
            ACTION_SNOOZE -> { AlarmRepository.snooze(id); stopRinging() }
            ACTION_DISMISS -> { AlarmRepository.dismiss(id); stopRinging() }
            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun start(id: Int) {
        val alarm = AlarmRepository.get(id)
        // Always go foreground first: startForegroundService requires it even when we bail out.
        val notification = buildNotification(alarm ?: Alarm(id = id, hour = 0, minute = 0))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (alarm == null) { stopRinging(); return }

        // A new alarm while another is ringing replaces it.
        currentId?.let { if (it != id) AlarmRepository.dismiss(it) }
        releasePlayback()
        currentId = id
        AlarmRepository.onFired(id)
        _ringing.value = alarm

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmify:ringing")
            .apply { acquire(AUTO_SILENCE_MS + 5_000) }

        playSound()
        if (alarm.vibrate) vibrate()

        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ AlarmRepository.dismiss(id); stopRinging() }, AUTO_SILENCE_MS)
    }

    private fun playSound() {
        val candidates = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = tryPlayer(uri) ?: continue
            player = mp
            fadeIn(mp)
            return
        }
    }

    private fun tryPlayer(uri: Uri): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            setVolume(0.15f, 0.15f)
            prepare()
            start()
        }
    }.getOrNull()

    /** Gentle wake: start quiet and reach full volume over ~20 seconds. */
    private fun fadeIn(mp: MediaPlayer) {
        val steps = 20
        for (i in 1..steps) {
            handler.postDelayed({
                val v = 0.15f + 0.85f * (i / steps.toFloat())
                runCatching { if (player === mp) mp.setVolume(v, v) }
            }, i * 1_000L)
        }
    }

    private fun vibrate() {
        val vibrator = vibrator() ?: return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 1200), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun buildNotification(alarm: Alarm): Notification {
        val ringScreen = PendingIntent.getActivity(
            this, alarm.id,
            Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = alarm.label.ifBlank { "Alarm" }
        val (time, suffix) = formatClock(this, alarm.hour, alarm.minute)
        return NotificationCompat.Builder(this, AlarmifyApp.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText("$time $suffix".trim())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setColor(0xFFC8371D.toInt())
            .setContentIntent(ringScreen)
            .setFullScreenIntent(ringScreen, true)
            .addAction(0, "Snooze ${AlarmRepository.SNOOZE_MINUTES} min", servicePending(this, ACTION_SNOOZE, alarm.id))
            .addAction(0, "Stop", servicePending(this, ACTION_DISMISS, alarm.id))
            .build()
    }

    private fun releasePlayback() {
        handler.removeCallbacksAndMessages(null)
        player?.runCatching { stop(); release() }
        player = null
        vibrator()?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopRinging() {
        releasePlayback()
        currentId = null
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayback()
        _ringing.value = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.evanchen.alarmify.START"
        private const val ACTION_SNOOZE = "com.evanchen.alarmify.SNOOZE"
        private const val ACTION_DISMISS = "com.evanchen.alarmify.DISMISS"
        private const val EXTRA_ID = "id"
        private const val NOTIFICATION_ID = 42
        private const val AUTO_SILENCE_MS = 10 * 60 * 1000L

        private val _ringing = MutableStateFlow<Alarm?>(null)
        /** The alarm ringing right now, if any. */
        val ringing: StateFlow<Alarm?> = _ringing.asStateFlow()

        fun startIntent(context: Context, id: Int) =
            Intent(context, AlarmService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)

        fun snooze(context: Context, id: Int) =
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE).putExtra(EXTRA_ID, id))

        fun dismiss(context: Context, id: Int) =
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ID, id))

        private fun servicePending(context: Context, action: String, id: Int): PendingIntent =
            PendingIntent.getService(
                context, id * 10 + action.length,
                Intent(context, AlarmService::class.java).setAction(action).putExtra(EXTRA_ID, id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
