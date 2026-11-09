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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val OPTIONS = listOf(1, 5, 10, 15, 30, 60)

/**
 * The quick-alarm popup: six big buttons. Tap one and it's set; the sheet closes and the alarm
 * appears in the list as a one-time alarm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSheet(onPick: (at: Long, label: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val now by rememberNow()

    fun pick(minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        scope.launch { sheet.hide() }.invokeOnCompletion { onPick(at, "Quick, ${durationWords(minutes)}"); onDismiss() }
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
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 16.dp)) {
                Text("Ring me in…", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp), modifier = Modifier.weight(1f))
                FishGlyph(Modifier.size(width = 40.dp, height = 22.dp), color = Night.FgSoft)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OPTIONS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { m ->
                            QuickButton(m, formatClockAt(is24, now + m * 60_000L).lowercase(), Modifier.weight(1f)) { pick(m) }
                        }
                    }
                }
            }
        }
    }
}

/** One big button: "10 min" in large type, and the time it will ring underneath. */
@Composable
private fun QuickButton(minutes: Int, ringsAt: String, modifier: Modifier, onClick: () -> Unit) {
    val (num, unit) = if (minutes < 60) "$minutes" to "min" else "${minutes / 60}" to "hour"
    Column(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Night.Bg)
            .border(1.dp, Night.Rule, RoundedCornerShape(20.dp))
            .pressable({ onClick() }, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = "$num $unit, rings at $ringsAt" }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num, style = Clock.copy(fontSize = 44.sp, letterSpacing = (-0.5).sp))
            Text(
                unit,
                style = MaterialTheme.typography.labelLarge.copy(color = Night.FgSoft),
                modifier = Modifier.padding(start = 5.dp, bottom = 8.dp),
            )
        }
        Text("rings $ringsAt", style = MaterialTheme.typography.bodySmall.copy(color = Night.Accent))
    }
}
