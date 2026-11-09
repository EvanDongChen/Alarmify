package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val DURATIONS = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
private val WAKE_TIMES = (0 until 8).map { LocalTime.of(5, 30).plusMinutes(30L * it) }

/**
 * The quick-alarm popup. One tap on a duration or a wake-up time sets a one-time alarm and closes the sheet.
 * [onPick] gets the exact instant to ring and a label for the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSheet(onPick: (at: Long, label: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    var custom by remember { mutableIntStateOf(25) }

    fun pick(at: Long, label: String) {
        scope.launch { sheet.hide() }.invokeOnCompletion { onPick(at, label); onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Night.Raised,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Night.Rule))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp)) {
            Text("Quick alarm", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp), modifier = Modifier.padding(top = 12.dp))
            Text(
                "Tap a duration. It rings once, then switches off.",
                style = MaterialTheme.typography.bodyMedium.copy(color = Night.FgSoft),
                modifier = Modifier.padding(top = 2.dp, bottom = 18.dp),
            )

            // A keypad of durations, ruled like a timetable.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Night.Rule, RoundedCornerShape(14.dp))) {
                DURATIONS.chunked(3).forEachIndexed { r, row ->
                    if (r > 0) Rule()
                    Row(Modifier.fillMaxWidth().height(92.dp)) {
                        row.forEachIndexed { c, m ->
                            if (c > 0) Box(Modifier.width(1.dp).height(92.dp).background(Night.Rule))
                            val at = now + m * 60_000L
                            DurationKey(m, formatClockAt(is24, at).lowercase(), Modifier.weight(1f)) {
                                pick(System.currentTimeMillis() + m * 60_000L, "Quick, ${durationWords(m)}")
                            }
                        }
                    }
                }
            }

            // Anything else: step it, then set it.
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton("–", "Five minutes less") { custom = (custom - 5).coerceAtLeast(1) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(durationWords(custom), style = Clock.copy(fontSize = 30.sp))
                    Text(
                        "rings ${formatClockAt(is24, now + custom * 60_000L).lowercase()}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Night.FgFaint),
                    )
                }
                StepButton("+", "Five minutes more") { custom = (custom + 5).coerceAtMost(12 * 60) }
                PaperButton("Set", Modifier.padding(start = 12.dp), kind = ButtonKind.Accent, height = 48.dp) {
                    pick(System.currentTimeMillis() + custom * 60_000L, "Quick, ${durationWords(custom)}")
                }
            }

            SectionLabel("Wake up at", Modifier.padding(top = 24.dp, bottom = 10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WAKE_TIMES) { t ->
                    val at = nextOccurrence(t, now)
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .border(1.dp, Night.Rule, CircleShape)
                            .pressable({ pick(at, "") })
                            .semantics { contentDescription = "Wake up at ${formatClockAt(is24, at)}" }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        TimeText(t.hour, t.minute, is24, 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationKey(minutes: Int, ringsAt: String, modifier: Modifier, onClick: () -> Unit) {
    val (num, unit) = when {
        minutes < 60 -> "$minutes" to "min"
        minutes % 60 == 0 -> "${minutes / 60}" to if (minutes == 60) "hour" else "hours"
        else -> "${minutes / 60}½" to "hours"
    }
    Column(
        modifier
            .height(92.dp)
            .pressable(onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$num $unit, rings $ringsAt" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num, style = Clock.copy(fontSize = 36.sp))
            Text(unit, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 3.dp, bottom = 7.dp))
        }
        Text(ringsAt, style = MaterialTheme.typography.bodySmall.copy(color = Night.FgFaint, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 14.sp))
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(1.dp, Night.Rule, CircleShape)
            .pressable(onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp)) }
}

private fun nextOccurrence(t: LocalTime, now: Long): Long {
    val zone = ZoneId.systemDefault()
    var at = ZonedDateTime.of(LocalDate.now(zone), t, zone)
    if (!at.toInstant().isAfter(Instant.ofEpochMilli(now))) at = at.plusDays(1)
    return at.toInstant().toEpochMilli()
}
