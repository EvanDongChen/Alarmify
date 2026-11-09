package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val STEPS = listOf(1, 5, 10, 15, 30, 60)
private const val MAX_MINUTES = 24 * 60

/**
 * The quick-alarm popup. Tap the +time buttons to build up a total (tap one twice to add it twice),
 * then Set alarm saves it as a one-time alarm that rings after that long.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSheet(onPick: (at: Long, label: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    var total by remember { mutableIntStateOf(0) }
    val taps = remember { mutableStateMapOf<Int, Int>() }
    var saves by remember { mutableIntStateOf(0) }

    fun save() {
        if (total == 0) return
        saves++
        val minutes = total
        val at = System.currentTimeMillis() + minutes * 60_000L
        scope.launch { kotlinx.coroutines.delay(220); sheet.hide() }
            .invokeOnCompletion { onPick(at, "Quick, ${durationWords(minutes)}"); onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Night.Raised,
        shape = WaveTopShape(),
        dragHandle = {
            Box(Modifier.padding(top = 18.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Night.Rule))
        },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, start = 4.dp)) {
                Text("Quick alarm", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp), modifier = Modifier.weight(1f))
                FishGlyph(Modifier.size(width = 40.dp, height = 22.dp), color = Night.FgSoft)
            }

            // The running total.
            Column(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = total,
                    transitionSpec = {
                        (fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 3 }) togetherWith
                            (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 3 })
                    },
                    label = "total",
                ) { m -> Text(totalText(m), style = Clock.copy(fontSize = 72.sp, letterSpacing = (-1).sp)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (total == 0) "Tap the times below to add them up"
                        else "rings ${formatClockAt(is24, now + total * 60_000L).lowercase()}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = if (total == 0) Night.FgSoft else Night.Accent),
                    )
                    if (total > 0) {
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.labelLarge.copy(color = Night.FgSoft),
                            modifier = Modifier.padding(start = 10.dp).clip(CircleShape)
                                .pressable({ total = 0; taps.clear() })
                                .border(1.dp, Night.Rule, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // +time buttons.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                STEPS.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { m ->
                            AddButton(m, taps[m] ?: 0, Modifier.weight(1f)) {
                                if (total + m <= MAX_MINUTES) {
                                    total += m
                                    taps[m] = (taps[m] ?: 0) + 1
                                }
                            }
                        }
                    }
                }
            }

            // Save.
            val ready = total > 0
            val bg by animateColorAsState(if (ready) Night.Accent else Night.Rule, label = "saveBg")
            val fg by animateColorAsState(if (ready) Night.Bg else Night.FgFaint, label = "saveFg")
            Box(
                Modifier
                    .padding(top = 18.dp)
                    .bubbleBurst(saves)
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .pressable({ save() }, enabled = ready)
                    .semantics { contentDescription = if (ready) "Set alarm for ${durationWords(total)} from now" else "Set alarm, add time first" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (ready) "Set alarm" else "Add time to set",
                    style = MaterialTheme.typography.labelLarge.copy(color = fg, fontSize = 17.sp),
                )
            }
        }
    }
}

/** "1h 15m" with serif numbers and small units; "0m" when empty. */
private fun totalText(m: Int) = buildAnnotatedString {
    val unit = SpanStyle(fontFamily = Sans, fontSize = 22.sp, color = Night.FgSoft, letterSpacing = 0.sp)
    fun part(n: Int, u: String) { append("$n"); withStyle(unit) { append(u) } }
    when {
        m >= 60 && m % 60 == 0 -> part(m / 60, "h")
        m >= 60 -> { part(m / 60, "h "); part(m % 60, "m") }
        else -> part(m, "m")
    }
}

/** "+10 min", with a small ×2 badge once it's been added more than once. */
@Composable
private fun AddButton(minutes: Int, count: Int, modifier: Modifier, onClick: () -> Unit) {
    var pops by remember { mutableIntStateOf(0) }
    val (num, unit) = if (minutes < 60) "$minutes" to "min" else "${minutes / 60}" to "hour"
    val border by animateColorAsState(if (count > 0) Night.Accent.copy(alpha = 0.6f) else Night.Rule, label = "addBorder")
    Box(
        modifier
            .bubbleBurst(pops)
            .height(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Night.Bg)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .pressable({ pops++; onClick() }, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = "Add $num $unit" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Night.FgSoft, fontFamily = Sans, fontSize = 20.sp)) { append("+") }
                append(num)
                withStyle(SpanStyle(color = Night.FgSoft, fontFamily = Sans, fontSize = 14.sp)) { append(" $unit") }
            },
            style = Clock.copy(fontSize = 32.sp),
            textAlign = TextAlign.Center,
        )
        if (count > 1) {
            Text(
                "×$count",
                style = MaterialTheme.typography.labelMedium.copy(color = Night.Accent),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp),
            )
        }
    }
}

