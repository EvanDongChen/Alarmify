package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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

    Box(Modifier.fillMaxSize().background(Paper.Page)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 120.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 28.dp, bottom = 20.dp)) {
                    Text("Alarms", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        nextLine(next?.second, now, is24),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Paper.InkSoft,
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            items(fixes, key = { it.title }) { fix -> Notice(fix.title, fix.body, fix.action, fix.onFix) }
            item { Rule() }

            if (sorted.isEmpty()) {
                item { EmptyState() }
            } else {
                items(sorted, key = { it.id }) { alarm ->
                    Column(Modifier.animateItem()) {
                        SwipeToDelete(onDelete = {
                            AlarmRepository.delete(alarm.id)
                            toast("Alarm deleted") { AlarmRepository.upsert(alarm) }
                        }) {
                            AlarmRow(
                                alarm, now, is24,
                                isNext = alarm.id == next?.first,
                                onToggle = { on ->
                                    AlarmRepository.setEnabled(alarm.id, on)
                                    if (on) AlarmRepository.get(alarm.id)?.let { toast(ringsIn(it)) }
                                },
                                onClick = { editing = alarm },
                            )
                        }
                        Rule()
                    }
                }
            }
        }

        // Bottom actions, within thumb reach.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Paper.Page).navigationBarsPadding(),
        ) {
            SnackbarHost(snackbar, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { data ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Paper.Ink)
                        .padding(start = 18.dp, end = 8.dp).height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        data.visuals.message,
                        style = MaterialTheme.typography.bodyMedium.copy(color = Paper.Page),
                        modifier = Modifier.weight(1f),
                    )
                    data.visuals.actionLabel?.let { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFFFF8A70)),
                            modifier = Modifier.pressable({ data.performAction() }).padding(10.dp),
                        )
                    }
                }
            }
            Rule()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PaperButton("+  New alarm", Modifier.weight(1f), kind = ButtonKind.Outline) { creating = true }
                PaperButton("Quick alarm", Modifier.weight(1f), kind = ButtonKind.Accent) { quick = true }
            }
        }
    }

    if (creating || editing != null) {
        AlarmEditor(
            initial = editing,
            newId = { AlarmRepository.newId() },
            onSave = { a -> AlarmRepository.upsert(a); toast(ringsIn(a)) },
            onDelete = { a ->
                AlarmRepository.delete(a.id)
                toast("Alarm deleted") { AlarmRepository.upsert(a) }
            },
            onDismiss = { creating = false; editing = null },
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

private fun nextLine(at: Long?, now: Long, is24: Boolean): String =
    if (at == null) "Nothing set to ring."
    else "Next ${dayPhrase(at)} at ${formatClockAt(is24, at).lowercase()}, in ${formatDurationLong(at - now)}."

@Composable
private fun AlarmRow(
    alarm: Alarm,
    now: Long,
    is24: Boolean,
    isNext: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val snoozed = (alarm.snoozedUntil ?: 0) > now
    val active = alarm.enabled || snoozed
    val ink by animateColorAsState(if (active) Paper.Ink else Paper.InkFaint, label = "ink")
    val at = alarm.nextTrigger(now)

    Row(
        Modifier
            .fillMaxWidth()
            .background(Paper.Page)
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
                    withStyle(SpanStyle(color = Paper.Red)) { append("in ${formatDuration(at - now)}") }
                }
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium.copy(color = if (active) Paper.InkSoft else Paper.InkFaint), maxLines = 1)
        }
        Spacer(Modifier.size(12.dp))
        PaperSwitch(alarm.enabled, onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection != SwipeToDismissBoxValue.EndToStart) return@SwipeToDismissBox
            Box(Modifier.fillMaxSize().background(Paper.Red), contentAlignment = Alignment.CenterEnd) {
                Text("Delete", style = MaterialTheme.typography.labelLarge.copy(color = Paper.Page), modifier = Modifier.padding(end = 20.dp))
            }
        },
    ) { content() }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Paper.Red))
        Text(
            "No alarms yet.",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            "Make one with New alarm,\nor use Quick alarm for a nap.",
            style = MaterialTheme.typography.bodyMedium.copy(color = Paper.InkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
