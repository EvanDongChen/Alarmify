package com.evanchen.alarmify.ring

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import com.evanchen.alarmify.data.AlarmRepository
import com.evanchen.alarmify.ui.ButtonKind
import com.evanchen.alarmify.ui.Paper
import com.evanchen.alarmify.ui.PaperButton
import com.evanchen.alarmify.ui.Serif
import com.evanchen.alarmify.ui.TimeText
import com.evanchen.alarmify.ui.rememberNow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun RingScreen(alarm: Alarm, onSnooze: () -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val now by rememberNow()
    val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper.Page)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        Text(
            t.format(DateTimeFormatter.ofPattern("EEEE d MMMM")).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(color = Paper.InkSoft),
        )
        TimeText(t.hour, t.minute, DateFormat.is24HourFormat(context), 112.sp, Modifier.padding(top = 4.dp))
        Text(
            alarm.label.ifBlank { "Time to get up." },
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 26.sp),
        )

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Pulse() }

        PaperButton(
            "Snooze ${AlarmRepository.SNOOZE_MINUTES} min",
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Outline,
            height = 72.dp,
            onClick = onSnooze,
        )
        Spacer(Modifier.height(14.dp))
        SlideToStop(onStop)
    }
}

/** A red dot with one ring spreading from it, like a bell being struck. */
@Composable
private fun Pulse() {
    val anim = rememberInfiniteTransition(label = "pulse")
    val p by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p")
    Canvas(Modifier.size(200.dp)) {
        val c = center
        for (i in 0 until 2) {
            val q = (p + i * 0.5f) % 1f
            drawCircle(
                Paper.Red.copy(alpha = (1f - q) * 0.6f),
                radius = 14.dp.toPx() + q * size.minDimension / 2.2f,
                center = c,
                style = Stroke(1.5.dp.toPx()),
            )
        }
        drawCircle(Paper.Red, radius = 14.dp.toPx(), center = c)
    }
}

@Composable
private fun SlideToStop(onStop: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val thumb = 60.dp

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(CircleShape)
            .background(Paper.Ink)
            .semantics {
                contentDescription = "Slide to stop alarm"
                onClick("Stop alarm") { onStop(); true }
            },
    ) {
        val maxPx = with(LocalDensity.current) { (maxWidth - thumb - 12.dp).toPx() }
        val progress = (drag.value / maxPx).coerceIn(0f, 1f)

        Text(
            "Slide to stop  →",
            style = MaterialTheme.typography.labelLarge.copy(color = Paper.Page, fontSize = 17.sp),
            modifier = Modifier.align(Alignment.Center).alpha(1f - progress),
        )
        Box(
            Modifier
                .padding(6.dp)
                .offset { IntOffset(drag.value.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(Paper.Red)
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (drag.value > maxPx * 0.8f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    drag.animateTo(maxPx, tween(120))
                                    onStop()
                                } else {
                                    drag.animateTo(0f, spring(dampingRatio = 0.7f))
                                }
                            }
                        },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { drag.snapTo((drag.value + delta).coerceIn(0f, maxPx)) }
                    }
                },
        )
    }
}
