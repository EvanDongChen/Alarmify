package com.evanchen.alarmify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evanchen.alarmify.data.AlarmRepository
import com.evanchen.alarmify.ring.AlarmService
import com.evanchen.alarmify.ring.RingActivity
import com.evanchen.alarmify.ui.AlarmifyTheme
import com.evanchen.alarmify.ui.Fix
import com.evanchen.alarmify.ui.MainScreen

class MainActivity : ComponentActivity() {
    private var fixes by mutableStateOf<List<Fix>>(emptyList())

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshFixes()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark, so the bar icons are always light.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Whenever an alarm starts ringing while the app is on screen (or the app is opened while one rings),
        // go straight to the full ringing screen; the heads-up banner alone is easy to lose.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AlarmService.ringing.collect { alarm ->
                    if (alarm != null) {
                        startActivity(Intent(this@MainActivity, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        }
        setContent {
            AlarmifyTheme {
                val alarms by AlarmRepository.alarms.collectAsStateWithLifecycle()
                MainScreen(alarms, fixes)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshFixes()
    }

    /** Everything that would stop an alarm ringing loudly over the lock screen, each with a one-tap fix. */
    private fun refreshFixes() {
        val list = mutableListOf<Fix>()
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            list += Fix("Allow notifications", "Alarms need them to show when ringing.", "Allow") {
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            list += Fix("Allow exact alarms", "So alarms ring on the dot, not minutes late.", "Allow") {
                openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            list += Fix("Allow full screen", "So alarms wake the screen when it's locked.", "Allow") {
                openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            list += Fix("Show alarms over other apps", "So an alarm fills the screen even while you're using your phone.", "Allow") {
                openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
        fixes = list
        // Permissions may have just been granted: re-plan with exact alarms if we can now.
        AlarmRepository.rescheduleAll()
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}
