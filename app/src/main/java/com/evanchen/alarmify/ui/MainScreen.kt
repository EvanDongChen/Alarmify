package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import com.evanchen.alarmify.data.AlarmRepository
import com.evanchen.alarmify.data.PlaceRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Something that would stop alarms ringing, and how to fix it. */
data class Fix(val title: String, val body: String, val action: String, val onFix: () -> Unit)

@Composable
fun MainScreen(alarms: List<Alarm>, fixes: List<Fix>) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val now by rememberNow()
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var creating by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf(false) }
    var placing by remember { mutableStateOf(false) }
    val places by PlaceRepository.active.collectAsStateWithLifecycle()
    val here by PlaceRepository.here.collectAsStateWithLifecycle()
    var seed by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun toast(message: String, undo: (() -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    fun ringsIn(a: Alarm): String =
        a.nextTrigger()?.let { "Rings in ${formatDurationLong(it - System.currentTimeMillis())}" } ?: "Saved"

    val sorted = alarms.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
    val next = alarms.mapNotNull { a -> a.nextTrigger(now)?.let { a.id to it } }.minByOrNull { it.second }

    // Faintly lighter near the top, like light from the surface.
    val listState = rememberLazyListState()
    // The ring header's height, remembered while it's on screen, so depth keeps counting once it scrolls away.
    var headerPx by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Night.Bg)) {
        OceanBackdrop(Modifier.fillMaxSize(), depth = {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first != null && first.index == 0) headerPx = first.size
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat()
            else headerPx + (listState.firstVisibleItemIndex - 1) * (first?.size ?: 400).toFloat() + listState.firstVisibleItemScrollOffset
        })
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Alarms", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp), modifier = Modifier.weight(1f))
                        Text(
                            todayLabel(now),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    DayRing(
                        alarms = alarms,
                        now = now,
                        next = next?.let { (id, at) -> alarms.firstOrNull { it.id == id }?.let { it to at } },
                        is24 = is24,
                        onAlarmTap = { editing = it },
                        onTimeTap = { h, m -> seed = h to m; creating = true },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    if (alarms.size < 3) {
                        Text(
                            "Tap the dial to set an alarm, or hold and drag.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Night.FgFaint, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        )
                    }
                }
            }
            items(fixes, key = { it.title }) { fix -> Notice(fix.title, fix.body, fix.action, fix.onFix) }
            item { WaveRule() }
            // Place alarms that are watching, above the clock alarms.
            items(places, key = { "place-${it.id}" }) { p ->
                Column(Modifier.animateItem()) {
                    PlaceAlarmCard(p, here) {
                        PlaceRepository.cancel(p.id)
                        toast("Stopped watching for ${p.place.name}") { PlaceRepository.restore(p) }
                    }
                    WaveRule()
                }
            }

            if (sorted.isEmpty()) {
                item { EmptyState() }
            } else {
                items(sorted, key = { it.id }) { alarm ->
                    Column(Modifier.animateItem()) {
                        SwipeToDelete(onDelete = {
                            AlarmRepository.delete(alarm.id)
                            toast("Alarm deleted") { AlarmRepository.upsert(alarm) }
                        }) { swiping ->
                            AlarmRow(
                                alarm, now, is24,
                                solid = swiping,
                                isNext = alarm.id == next?.first,
                                onToggle = { on ->
                                    AlarmRepository.setEnabled(alarm.id, on)
                                    if (on) AlarmRepository.get(alarm.id)?.let { toast(ringsIn(it)) }
                                },
                                onClick = { editing = alarm },
                            )
                        }
                        WaveRule()
                    }
                }
            }
        }

        // Bottom actions, within thumb reach.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.Transparent, 0.3f to Night.Bg))
                .navigationBarsPadding(),
        ) {
            SnackbarHost(snackbar, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { data ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Night.Raised)
                        .border(1.dp, Night.Rule, RoundedCornerShape(12.dp))
                        .padding(start = 18.dp, end = 8.dp).height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Night.Fg),
                        modifier = Modifier.weight(1f),
                    )
                    data.visuals.actionLabel?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(color = Night.Accent),
                            modifier = Modifier.pressable({ data.performAction() }).padding(10.dp),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaperButton("+ Alarm", Modifier.weight(1f), kind = ButtonKind.Outline) { seed = null; creating = true }
                PaperButton("Place", Modifier.weight(1f), kind = ButtonKind.Outline) { placing = true }
                PaperButton("Quick", Modifier.weight(1f), kind = ButtonKind.Accent) { quick = true }
            }
        }
    }

    if (creating || editing != null) {
        AlarmEditor(
            initial = editing,
            newId = { AlarmRepository.newId() },
            seedTime = seed,
            onSave = { a -> AlarmRepository.upsert(a); toast(ringsIn(a)) },
            onDelete = { a ->
                AlarmRepository.delete(a.id)
                toast("Alarm deleted") { AlarmRepository.upsert(a) }
            },
            onDismiss = { creating = false; editing = null; seed = null },
        )
    }

    if (placing) {
        PlaceSheet(
            onStarted = { a -> toast("Watching for ${a.place.name}") },
            onDismiss = { placing = false },
        )
    }

    if (quick) {
        QuickSheet(
            onPick = { at, label ->
                val a = AlarmRepository.addOneTimeAt(at, label)
                toast("Set for ${formatClockAt(is24, at).lowercase()}, in ${formatDurationLong(at - System.currentTimeMillis())}") {
                    AlarmRepository.delete(a.id)
                }
            },
            onDismiss = { quick = false },
        )
    }
}

private fun todayLabel(now: Long): String =
    java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()

@Composable
private fun AlarmRow(
    alarm: Alarm,
    now: Long,
    is24: Boolean,
    isNext: Boolean,
    solid: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val snoozed = (alarm.snoozedUntil ?: 0) > now
    val active = alarm.enabled || snoozed
    val ink by animateColorAsState(if (active) Night.Fg else Night.FgFaint, label = "ink")
    val at = alarm.nextTrigger(now)

    Row(
        Modifier
            .fillMaxWidth()
            // Transparent so the water shows through; solid only while being swiped over the delete layer.
            .background(if (solid) Night.Bg else Color.Transparent)
            .pressable(onClick, haptic = false)
            .padding(vertical = 16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TimeText(alarm.hour, alarm.minute, is24, 56.sp, color = ink)
            val detail = buildAnnotatedString {
                val parts = listOfNotNull(
                    alarm.label.takeIf { it.isNotBlank() },
                    when {
                        snoozed -> "Snoozed until ${formatClockAt(is24, alarm.snoozedUntil!!).lowercase()}"
                        alarm.repeats -> repeatSummary(alarm.days)
                        at != null -> "Once, ${dayPhrase(at)}"
                        else -> "Once"
                    },
                )
                append(parts.joinToString(" · "))
                if (isNext && at != null && at - now < 24 * 3_600_000L) {
                    append(" · ")
                    withStyle(SpanStyle(color = Night.Accent)) { append("in ${formatDuration(at - now)}") }
                }
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium.copy(color = if (active) Night.FgSoft else Night.FgFaint), maxLines = 1)
        }
        Spacer(Modifier.size(12.dp))
        PaperSwitch(alarm.enabled, onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable (swiping: Boolean) -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            Box(Modifier.fillMaxSize().background(Night.Accent), contentAlignment = Alignment.CenterEnd) {
                Text("Delete", style = MaterialTheme.typography.labelLarge.copy(color = Night.Bg), modifier = Modifier.padding(end = 20.dp))
            }
        },
    ) { content(state.dismissDirection == SwipeToDismissBoxValue.EndToStart) }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FishGlyph(Modifier.size(width = 56.dp, height = 30.dp))
        Text(
            "Quiet waters. Tap the dial, or use the buttons below.",
            style = MaterialTheme.typography.bodyMedium.copy(color = Night.FgSoft),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
