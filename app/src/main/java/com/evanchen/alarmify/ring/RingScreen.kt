package com.evanchen.alarmify.ring

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import com.evanchen.alarmify.data.AlarmRepository
import com.evanchen.alarmify.ui.Numeric
import com.evanchen.alarmify.ui.Palette
import com.evanchen.alarmify.ui.formatClock
import com.evanchen.alarmify.ui.pressable
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
    val (time, suffix) = formatClock(DateFormat.is24HourFormat(context), t.hour, t.minute)

    val anim = rememberInfiniteTransition(label = "ring")
    val pulse by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse")
    val breathe by anim.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "breathe")

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1446), Color(0xFF5B2A86), Color(0xFFFF7E6B), Color(0xFFFFC46B))))
    ) {
        // The sun coming up behind everything.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.5f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x66FFE2B8), Color.Transparent), center = c, radius = size.width * 0.9f * breathe),
                radius = size.width * 0.9f * breathe, center = c,
            )
            // Three rings rippling outward.
            for (i in 0 until 3) {
                val p = (pulse + i / 3f) % 1f
                drawCircle(
                    Color.White.copy(alpha = (1f - p) * 0.35f),
                    radius = size.width * (0.16f + 0.3f * p), center = c,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            drawCircle(Color.White.copy(alpha = 0.18f), radius = size.width * 0.15f * breathe, center = c)
        }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                t.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp, fontWeight = FontWeight.Medium,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Thin, letterSpacing = (-3).sp, style = Numeric)
                if (suffix.isNotEmpty()) {
                    Text(suffix, color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp, bottom = 18.dp))
                }
            }
            Text(
                alarm.label.ifBlank { if (alarm.quick) "Quick alarm" else "Rise and shine" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.Alarm, null, tint = Color.White,
                modifier = Modifier.size(56.dp).graphicsLayer {
                    // A little ring-ring wobble.
                    rotationZ = kotlin.math.sin(pulse * 2 * Math.PI.toFloat() * 6) * 12f
                },
            )
            Spacer(Modifier.weight(1f))

            // Snooze is the big, easy target for a sleepy thumb…
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(CircleShape)
                    .background(Color(0x55120E33))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    .pressable(onSnooze),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Snooze, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("Snooze ${AlarmRepository.SNOOZE_MINUTES} min", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            // …and stopping takes a deliberate slide, so you don't turn it off in your sleep.
            SlideToStop(onStop)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SlideToStop(onStop: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val thumb = 64.dp
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val sh by shimmer.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "sh")

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(CircleShape)
            .background(Color(0x55120E33))
            .semantics {
                contentDescription = "Slide to stop alarm"
                onClick("Stop alarm") { onStop(); true }
            },
    ) {
        val maxPx = with(LocalDensity.current) { (maxWidth - thumb - 12.dp).toPx() }
        val progress = (drag.value / maxPx).coerceIn(0f, 1f)

        Text(
            "slide to stop",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(1f - progress)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // A glint sweeping across the words, nudging you which way to go.
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0x99FFC46B), Color.Transparent),
                            startX = size.width * (sh - 0.3f), endX = size.width * (sh + 0.3f),
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                    )
                },
        )
        Box(
            Modifier
                .padding(6.dp)
                .offset { IntOffset(drag.value.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Palette.Dawn, Palette.Gold)))
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (drag.value > maxPx * 0.8f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    drag.animateTo(maxPx, tween(120))
                                    onStop()
                                } else {
                                    drag.animateTo(0f, spring(dampingRatio = 0.6f))
                                }
                            }
                        },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { drag.snapTo((drag.value + delta).coerceIn(0f, maxPx)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (progress > 0.8f) Icons.Rounded.AlarmOff else Icons.AutoMirrored.Rounded.ArrowForward,
                null, tint = Palette.Night0,
            )
        }
    }
}

