package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import kotlinx.coroutines.launch

/** Bottom sheet for creating or editing an alarm. [initial] null means a new alarm. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditor(
    initial: Alarm?,
    newId: () -> Int,
    onSave: (Alarm) -> Unit,
    onDelete: (Alarm) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // A new alarm starts at the next round half hour, a sensible guess that's one flick from most choices.
    val seed = remember {
        initial ?: run {
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
    val draft = seed.copy(hour = hour, minute = minute, days = days, enabled = true, snoozedUntil = null, quick = false, fireAt = null)
    val next = draft.nextTrigger(now)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = Palette.Sheet,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(width = 40.dp, height = 5.dp).clip(CircleShape).background(Palette.GlassBorder))
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
            // Header
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel", color = Palette.TextDim, fontSize = 16.sp,
                    modifier = Modifier.clip(CircleShape).pressable({ close {} }).padding(8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(if (initial == null) "New alarm" else "Edit alarm", color = Palette.Text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.clip(CircleShape).background(Palette.dawnBrush)
                        .pressable({
                            val id = if (draft.id < 0) newId() else draft.id
                            val saved = draft.copy(id = id, label = label.trim(), vibrate = vibrate)
                            close { onSave(saved) }
                        })
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) { Text("Save", color = Palette.Night0, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }

            // Time wheels
            Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                // The selection band behind the middle row.
                Box(
                    Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Glass),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    if (is24) {
                        WheelPicker(24, hour, { hour = it }, { "%02d".format(it) }, Modifier.width(88.dp), description = "Hour")
                    } else {
                        val h12 = if (hour % 12 == 0) 12 else hour % 12
                        WheelPicker(
                            12, h12 - 1,
                            { idx -> val v = idx + 1; hour = (v % 12) + if (hour >= 12) 12 else 0 },
                            { "${it + 1}" }, Modifier.width(88.dp), description = "Hour",
                        )
                    }
                    Text(":", fontSize = 40.sp, color = Palette.TextDim, fontWeight = FontWeight.Light)
                    WheelPicker(60, minute, { minute = it }, { "%02d".format(it) }, Modifier.width(88.dp), description = "Minute")
                    if (!is24) {
                        WheelPicker(
                            2, if (hour >= 12) 1 else 0,
                            { pm -> hour = (hour % 12) + if (pm == 1) 12 else 0 },
                            { if (it == 0) "AM" else "PM" },
                            Modifier.width(80.dp), cyclic = false, fontSize = 24.sp, description = "AM or PM",
                        )
                    }
                }
            }

            // Live "rings in" readout
            AnimatedContent(
                targetState = next?.let { "${relativeDay(it)} · rings in ${formatDuration(it - now)}" } ?: "",
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 })
                },
                label = "ringsIn",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) { text ->
                Text(text, color = Palette.Gold, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            // Repeat
            SectionLabel("Repeat", Modifier.padding(top = 28.dp, bottom = 12.dp))
            DayToggles(days) { days = it }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Once" to emptySet(), "Weekdays" to WEEKDAYS, "Weekends" to WEEKENDS, "Daily" to EVERY_DAY)
                    .forEach { (name, set) ->
                        Pill(name, days == set, Modifier.weight(1f)) { days = set }
                    }
            }

            // Label
            SectionLabel("Label", Modifier.padding(top = 28.dp, bottom = 12.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                placeholder = { Text("Gym, meds, catch the train…", color = Palette.TextFaint) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Dawn,
                    unfocusedBorderColor = Palette.GlassBorder,
                    focusedContainerColor = Palette.Glass,
                    unfocusedContainerColor = Palette.Glass,
                    cursorColor = Palette.Dawn,
                    focusedTextColor = Palette.Text,
                    unfocusedTextColor = Palette.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            // Vibrate
            GlassCard(Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Vibration, null, tint = Palette.TextDim)
                    Text("Vibrate", color = Palette.Text, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    GlowSwitch(vibrate, { vibrate = it })
                }
            }

            if (initial != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(18.dp))
                        .pressable({ close { onDelete(initial) } }).padding(14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = Palette.Danger)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete alarm", color = Palette.Danger, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
