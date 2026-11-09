package com.evanchen.alarmify.ring

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
 * Rings an alarm: plays the alarm sound (fading in; in the headphones if any are connected, else out loud), vibrates, and posts a full-screen notification that
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
            ACTION_START_PLACE -> startPlace(
                id,
                intent.getStringExtra(EXTRA_LABEL).orEmpty(),
                intent.getBooleanExtra(EXTRA_VIBRATE, true),
            )
            ACTION_SNOOZE -> { AlarmRepository.snooze(id); stopRinging() }
            ACTION_DISMISS -> { AlarmRepository.dismiss(id); stopRinging() }
            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun start(id: Int) {
        val alarm = AlarmRepository.get(id)
        // Always go foreground first: startForegroundService requires it even when we bail out.
        goForeground(alarm ?: Alarm(id = id, hour = 0, minute = 0))
        if (alarm == null) { stopRinging(); return }
        AlarmRepository.onFired(id)
        ring(alarm)
    }

    /** A place alarm: you've arrived near somewhere. Rings like any alarm, but there's nothing to snooze. */
    private fun startPlace(placeId: Int, label: String, vibrate: Boolean) {
        val now = java.time.LocalTime.now()
        val alarm = Alarm(id = -placeId, hour = now.hour, minute = now.minute, label = label, vibrate = vibrate)
        goForeground(alarm)
        ring(alarm)
    }

    private fun goForeground(alarm: Alarm) {
        val notification = buildNotification(alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ring(alarm: Alarm) {
        // A new alarm while another is ringing replaces it.
        currentId?.let { if (it != alarm.id) AlarmRepository.dismiss(it) }
        releasePlayback()
        currentId = alarm.id
        _ringing.value = alarm
        showRingScreen() // first, so the screen appears at once even if the sound is slow to load

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alarmify:ringing")
            .apply { acquire(AUTO_SILENCE_MS + 5_000) }

        level = START_LEVEL
        playSound()
        handler.postDelayed(fade, FADE_STEP_MS)
        audio.registerAudioDeviceCallback(deviceWatcher, handler)
        if (alarm.vibrate) vibrate()

        handler.postDelayed(autoSilence, AUTO_SILENCE_MS)
    }

    /**
     * While you're using the phone, Android only shows a full-screen alarm as a banner. With "Display over other
     * apps" allowed we may open the ringing screen ourselves, so it covers whatever you're doing. (Locked or
     * asleep, the notification's full-screen intent opens it either way.)
     */
    private fun showRingScreen() {
        if (!Settings.canDrawOverlays(this)) return
        runCatching {
            startActivity(
                Intent(this, RingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            )
        }
    }

    private val autoSilence = Runnable { currentId?.let { AlarmRepository.dismiss(it) }; stopRinging() }

    // ---- Sound: through headphones when they're connected, otherwise the speaker. ----

    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private var level = START_LEVEL
    private var onHeadphones = false
    private var savedMediaVolume: Int? = null

    /** Gentle wake: start quiet and reach full volume over ~20 seconds. */
    private val fade = object : Runnable {
        override fun run() {
            level = (level + (1f - START_LEVEL) / 20f).coerceAtMost(1f)
            runCatching { player?.setVolume(level, level) }
            if (level < 1f) handler.postDelayed(this, FADE_STEP_MS)
        }
    }

    /** Headphones plugged in or out mid-ring: move the sound over. */
    private val deviceWatcher = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = reroute()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = reroute()
    }

    private fun reroute() {
        if (currentId == null || (headphones() != null) == onHeadphones) return
        stopPlayer()
        playSound()
    }

    private fun headphones(): AudioDeviceInfo? =
        audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type in HEADPHONE_TYPES }

    private fun playSound() {
        val phones = headphones()
        onHeadphones = phones != null
        if (phones != null) ensureMediaAudible() else restoreMediaVolume()
        val candidates = listOfNotNull(
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            player = tryPlayer(uri, phones) ?: continue
            return
        }
    }

    /**
     * Android's alarm stream always rings out loud, even with headphones in, so with headphones connected we
     * play on the media stream and point it at them; on its own it rings the speaker as an alarm.
     */
    private fun tryPlayer(uri: Uri, phones: AudioDeviceInfo?): MediaPlayer? = runCatching {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (phones != null) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM)
                    .setContentType(if (phones != null) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(this@AlarmService, uri)
            if (phones != null) setPreferredDevice(phones)
            isLooping = true
            setVolume(level, level)
            prepare()
            start()
        }
    }.getOrNull()

    /** A muted media volume would make a headphone alarm silent: lift it to match the alarm volume while ringing. */
    private fun ensureMediaAudible() {
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val alarmFrac = audio.getStreamVolume(AudioManager.STREAM_ALARM) /
                audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1).toFloat()
            val target = kotlin.math.ceil(alarmFrac * max).toInt().coerceIn(max / 3, max)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current < target) {
                if (savedMediaVolume == null) savedMediaVolume = current
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        }
    }

    private fun restoreMediaVolume() {
        savedMediaVolume?.let { v -> runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) } }
        savedMediaVolume = null
    }

    private fun stopPlayer() {
        player?.runCatching { stop(); release() }
        player = null
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
        val title = if (alarm.isPlace) "Nearly at ${alarm.label}" else alarm.label.ifBlank { "Alarm" }
        val (time, suffix) = formatClock(this, alarm.hour, alarm.minute)
        return NotificationCompat.Builder(this, AlarmifyApp.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText("$time $suffix".trim())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setColor(0xFFFF7A5C.toInt())
            .setContentIntent(ringScreen)
            .setFullScreenIntent(ringScreen, true)
            .apply {
                // Nothing to snooze once you've arrived somewhere.
                if (!alarm.isPlace) addAction(0, "Snooze ${AlarmRepository.SNOOZE_MINUTES} min", servicePending(this@AlarmService, ACTION_SNOOZE, alarm.id))
            }
            .addAction(0, "Stop", servicePending(this, ACTION_DISMISS, alarm.id))
            .build()
    }

    private fun releasePlayback() {
        handler.removeCallbacksAndMessages(null)
        runCatching { audio.unregisterAudioDeviceCallback(deviceWatcher) }
        stopPlayer()
        restoreMediaVolume()
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
        private const val ACTION_START_PLACE = "com.evanchen.alarmify.START_PLACE"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val EXTRA_ID = "id"
        private const val NOTIFICATION_ID = 42
        private const val AUTO_SILENCE_MS = 10 * 60 * 1000L
        private const val START_LEVEL = 0.15f
        private const val FADE_STEP_MS = 1_000L

        private val HEADPHONE_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }

        private val _ringing = MutableStateFlow<Alarm?>(null)
        /** The alarm ringing right now, if any. */
        val ringing: StateFlow<Alarm?> = _ringing.asStateFlow()

        fun startIntent(context: Context, id: Int) =
            Intent(context, AlarmService::class.java).setAction(ACTION_START).putExtra(EXTRA_ID, id)

        /** Rings for a place alarm ([placeId] > 0); called by the tracker when you get close. */
        fun startPlace(context: Context, placeId: Int, label: String, vibrate: Boolean) =
            context.startForegroundService(
                Intent(context, AlarmService::class.java).setAction(ACTION_START_PLACE)
                    .putExtra(EXTRA_ID, placeId).putExtra(EXTRA_LABEL, label).putExtra(EXTRA_VIBRATE, vibrate),
            )

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
