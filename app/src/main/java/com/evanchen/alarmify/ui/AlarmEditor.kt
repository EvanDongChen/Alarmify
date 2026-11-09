package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import kotlinx.coroutines.launch

/** Sheet for creating or editing an alarm. [initial] null means a new alarm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(
    initial: Alarm?,
    newId: () -> Int,
    seedTime: Pair<Int, Int>? = null,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // A new alarm starts at the next round half hour: one flick from most choices.
    val seed = remember {
        initial ?: seedTime?.let { (h, m) -> Alarm(id = -1, hour = h, minute = m) } ?: run {
            val now = java.time.LocalTime.now().plusMinutes(30)
            val m = if (now.minute < 30) 30 else 0
            val h = if (now.minute < 30) now.hour else (now.hour + 1) % 24
            Alarm(id = -1, hour = h, minute = m)
        }
    }
    var hour by remember { mutableIntStateOf(seed.hour) }
    var minute by remember { mutableIntStateOf(seed.minute) }
    var days by remember { mutableStateOf(seed.days) }
    var label by remember { mutableStateOf(seed.label) }
    var vibrate by remember { mutableStateOf(seed.vibrate) }

    fun close(then: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { then(); onDismiss() }
    }

    val now by rememberNow(15_000)
    val draft = seed.copy(hour = hour, minute = minute, days = days, enabled = true, snoozedUntil = null, fireAt = null)
    val next = draft.nextTrigger(now)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Night.Raised,
        shape = WaveTopShape(),
        dragHandle = {
            Box(Modifier.padding(top = 18.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Night.Rule))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (initial == null) "New alarm" else "Edit alarm",
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge.copy(color = Night.FgSoft),
                    modifier = Modifier.pressable({ close {} }).padding(8.dp),
                )
            }

            // Time wheels between two rules.
            Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
                Column {
                    Rule()
                    Spacer(Modifier.height(58.dp))
                    Rule()
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (is24) {
                        WheelPicker(24, hour, { hour = it }, { "%02d".format(it) }, Modifier.width(92.dp), description = "Hour", fontSize = 48.sp)
                    } else {
                        val h12 = if (hour % 12 == 0) 12 else hour % 12
                        WheelPicker(
                            12, h12 - 1,
                            { idx -> val v = idx + 1; hour = (v % 12) + if (hour >= 12) 12 else 0 },
                            { "${it + 1}" }, Modifier.width(92.dp), description = "Hour", fontSize = 48.sp,
                        )
                    }
                    Text(":", style = Clock.copy(fontSize = 44.sp, color = Night.FgFaint))
                    WheelPicker(60, minute, { minute = it }, { "%02d".format(it) }, Modifier.width(92.dp), description = "Minute", fontSize = 48.sp)
                    if (!is24) {
                        WheelPicker(
                            2, if (hour >= 12) 1 else 0,
                            { pm -> hour = (hour % 12) + if (pm == 1) 12 else 0 },
                            { if (it == 0) "am" else "pm" },
                            Modifier.width(72.dp), cyclic = false, fontSize = 20.sp, description = "am or pm",
                            textStyle = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Text(
                next?.let { "Rings ${dayPhrase(it)}, in ${formatDurationLong(it - now)}." } ?: "",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 19.sp, color = Night.FgSoft),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            SectionLabel("Repeat", Modifier.padding(top = 28.dp, bottom = 12.dp))
            DayToggles(days) { days = it }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Once" to emptySet(), "Weekdays" to WEEKDAYS, "Weekends" to WEEKENDS, "Daily" to EVERY_DAY)
                    .forEach { (name, set) ->
                        Chip(name, days == set, Modifier.weight(if (name.length > 5) 1.3f else 1f)) { days = set }
                    }
            }

            SectionLabel("Label", Modifier.padding(top = 28.dp))
            TextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = { Text("Gym, meds, the 7:42 train…", style = MaterialTheme.typography.bodyLarge.copy(color = Night.FgFaint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Night.Fg,
                    unfocusedIndicatorColor = Night.Rule,
                    cursorColor = Night.Accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Vibrate", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                PaperSwitch(vibrate, { vibrate = it }, "Vibrate")
            }

            PaperButton(
                "Save alarm",
                Modifier.fillMaxWidth().padding(top = 28.dp),
            ) {
                val id = if (draft.id < 0) newId() else draft.id
                val saved = draft.copy(id = id, label = label.trim(), vibrate = vibrate)
                close { onSave(saved) }
            }

            if (initial != null) {
                Text(
                    "Delete alarm",
                    style = MaterialTheme.typography.labelLarge.copy(color = Night.Accent),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).pressable({ close { onDelete(initial) } }).padding(14.dp),
                )
            }
        }
    }
}
