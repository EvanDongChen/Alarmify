package com.evanchen.alarmify.ui

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * A slow clock (seconds) for background motion: the water, light rays, bubbles and fish.
 *
 * Battery-minded on purpose:
 * - it ticks [fps] times a second instead of every display frame (a Pixel refreshes up to 120 times a second,
 *   and motion this slow looks the same at 20);
 * - it only runs while the screen is visible, so nothing wakes up in the background;
 * - with Battery Saver on, or animations turned off in system settings, it stands still and the scene is drawn once.
 *
 * Read it inside a Canvas/draw lambda so a tick only redraws, never recomposes.
 */
@Composable
fun rememberAmbientTime(): State<Float> = LocalAmbientTime.current

/** One clock per screen, shared by everything on it, so their ticks land on the same frames. */
val LocalAmbientTime = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

@Composable
internal fun ambientClock(fps: Int = 20): State<Float> {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(0f, lifecycle, fps) {
        val start = System.nanoTime()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var still = reduceMotion(context)
            var tick = 0
            while (true) {
                if (tick++ % (fps * 2) == 0) still = reduceMotion(context) // re-check every couple of seconds
                if (!still) value = (System.nanoTime() - start) / 1e9f
                delay(1000L / fps)
            }
        }
    }
}

/** Battery Saver on, or animations switched off in the system's developer/accessibility settings. */
fun reduceMotion(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return power?.isPowerSaveMode == true || scale == 0f
}
