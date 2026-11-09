package com.evanchen.alarmify.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.evanchen.alarmify.ui.AlarmifyTheme

/** Full-screen ringing UI, shown over the lock screen. Closes itself as soon as nothing is ringing. */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Always dark, so the bar icons are always light.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AlarmifyTheme {
                val alarm by AlarmService.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(alarm) { if (alarm == null) finish() }
                alarm?.let { a ->
                    RingScreen(
                        alarm = a,
                        onSnooze = { AlarmService.snooze(this, a.id) },
                        onStop = { AlarmService.dismiss(this, a.id) },
                    )
                }
            }
        }
    }

    // Back shouldn't silently leave an alarm ringing behind the lock screen.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit
}
