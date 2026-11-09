package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

@Composable
fun AlarmsScreen(
    alarms: List<Alarm>,
    fixes: List<Fix>,
    onToggle: (Alarm, Boolean) -> Unit,
    onEdit: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val now by rememberNow()
    val regular = alarms.filterNot { it.quick }.sortedWith(compareBy({ it.hour }, { it.minute }))
    // The hero counts down to the soonest ring of any alarm, quick ones included.
    val next = alarms.mapNotNull { a -> a.nextTrigger(now)?.let { a to it } }.minByOrNull { it.second }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 20.dp, bottom = 8.dp)) {
                val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
                Text(greeting(hour), color = Palette.TextDim, fontSize = 15.sp)
                Text("Alarms", style = MaterialTheme.typography.headlineLarge, color = Palette.Text)
            }
        }
        items(fixes, key = { it.title }) { fix -> FixCard(fix.title, fix.body, fix.action, fix.onFix) }
        item { NextAlarmHero(next?.first, next?.second, now, is24) }

        if (regular.isEmpty()) {
            item { EmptyAlarms() }
        } else {
            item { SectionLabel("Your alarms", Modifier.padding(top = 12.dp, start = 4.dp)) }
            items(regular, key = { it.id }) { alarm ->
                SwipeToDelete(onDelete = { onDelete(alarm) }, modifier = Modifier.animateItem()) {
                    AlarmCard(alarm, now, is24, onToggle = { onToggle(alarm, it) }, onClick = { onEdit(alarm) })
                }
            }
        }
    }
}

/** The big card at the top: when you're next getting up, painted in that hour's sky. */
@Composable
private fun NextAlarmHero(alarm: Alarm?, at: Long?, now: Long, is24: Boolean) {
    val hour = at?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).hour } ?: -1
    val sky = if (at == null) listOf(Palette.Night2, Palette.Night1, Palette.Night2) else skyFor(hour)
    val c0 by animateColorAsState(sky[0], label = "sky0")
    val c1 by animateColorAsState(sky[1], label = "sky1")
    val c2 by animateColorAsState(sky[2], label = "sky2")
    val isNight = at != null && (hour >= 20 || hour < 4)

    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(c0, c1, c2), start = Offset.Zero, end = Offset(900f, 700f)))
            .semantics(mergeDescendants = true) {},
    ) {
        if (isNight || at == null) Stars()
        // The sun (or moon) sitting on the horizon of the card.
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * 0.42f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)),
                    center = Offset(size.width * 0.82f, size.height * 1.02f), radius = r * 1.8f,
                ),
                radius = r * 1.8f, center = Offset(size.width * 0.82f, size.height * 1.02f),
            )
            drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.55f, center = Offset(size.width * 0.82f, size.height * 1.02f))
        }
        Column(Modifier.padding(24.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (at == null) "NOTHING SCHEDULED" else "NEXT ALARM",
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
            )
            AnimatedContent(
                targetState = at?.let { "in ${formatDuration(it - now)}" } ?: "Sleep in",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "heroIn",
            ) { text ->
                Text(text, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, style = Numeric)
            }
            Text(
                if (alarm == null || at == null) "Tap + to set an alarm, or try Quick."
                else listOfNotNull(
                    relativeDay(at),
                    formatClockAt(is24, at),
                    alarm.label.ifBlank { if (alarm.quick) "Quick alarm" else null },
                ).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun Stars() {
    val stars = remember { List(28) { Triple(Random.nextFloat(), Random.nextFloat() * 0.7f, Random.nextFloat()) } }
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (x, y, s) ->
            drawCircle(Color.White.copy(alpha = 0.25f + 0.5f * s), radius = 1f + 2f * s, center = Offset(x * size.width, y * size.height))
        }
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, now: Long, is24: Boolean, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val active = alarm.enabled || (alarm.snoozedUntil ?: 0) > now
    val dim by animateFloatAsState(if (active) 1f else 0.45f, label = "dim")
    val (time, suffix) = formatClock(is24, alarm.hour, alarm.minute)
    val next = alarm.nextTrigger(now)

    GlassCard(
        Modifier.fillMaxWidth().pressable(onClick, haptic = false)
            .clip(RoundedCornerShape(28.dp)).background(Palette.Night1),
        tint = if (active) Color(0x14FFFFFF) else Palette.Glass,
    ) {
        Row(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(dim)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, fontSize = 46.sp, fontWeight = FontWeight.Light, color = Palette.Text, letterSpacing = (-1).sp, style = Numeric)
                    if (suffix.isNotEmpty()) {
                        Text(suffix, fontSize = 16.sp, color = Palette.TextDim, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, bottom = 9.dp))
                    }
                }
                val sub = listOfNotNull(
                    alarm.label.takeIf { it.isNotBlank() },
                    when {
                        (alarm.snoozedUntil ?: 0) > now -> "Snoozed until ${formatClockAt(is24, alarm.snoozedUntil!!)}"
                        !alarm.repeats && next != null -> relativeDay(next)
                        else -> repeatSummary(alarm.days)
                    },
                ).joinToString("  ·  ")
                Text(sub, color = Palette.TextDim, fontSize = 14.sp, maxLines = 1)
                if (alarm.repeats) DayDots(alarm.days, active, Modifier.padding(top = 8.dp))
            }
            GlowSwitch(alarm.enabled, onToggle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Only paint the delete layer mid-swipe; the glass card would otherwise show it through.
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            val progress = state.progress
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                    .background(Palette.Danger.copy(alpha = (0.25f + progress).coerceAtMost(0.9f))),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(Modifier.padding(end = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color.White)
                }
            }
        },
    ) { content() }
}

@Composable
private fun EmptyAlarms() {
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp).semantics(mergeDescendants = true) { contentDescription = "No alarms yet" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(Palette.Glass),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Bedtime, null, tint = Palette.Lavender, modifier = Modifier.size(40.dp)) }
        Spacer(Modifier.height(16.dp))
        Text("No alarms yet", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Tap the + below to make your first one.", color = Palette.TextDim, fontSize = 14.sp)
    }
}

/** Something that would stop alarms ringing, and how to fix it. */
data class Fix(val title: String, val body: String, val action: String, val onFix: () -> Unit)
