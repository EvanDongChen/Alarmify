package com.evanchen.alarmify.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Click with a light haptic tick and a slight fade while held. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    haptic: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.55f else 1f, tween(90), label = "press")
    val haptics = LocalHapticFeedback.current
    return this
        .graphicsLayer { this.alpha = alpha }
        .clickable(source, indication = null, enabled = enabled, role = role) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** "6:30" in serif with a small "am" beside it. */
@Composable
fun TimeText(
    hour: Int,
    minute: Int,
    is24: Boolean,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Night.Fg,
) {
    val (time, suffix) = formatClock(is24, hour, minute)
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(time, style = Clock.copy(fontSize = size, letterSpacing = (-0.02f * size.value).sp, color = color))
        if (suffix.isNotEmpty()) {
            Text(
                suffix.lowercase(),
                style = MaterialTheme.typography.labelLarge.copy(color = color, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(start = 5.dp, bottom = (size.value * 0.17f).dp),
            )
        }
    }
}

enum class ButtonKind { Filled, Accent, Outline }

/** A plain, flat button: ink, red, or outlined. */
@Composable
fun PaperButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Filled,
    height: Dp = 56.dp,
    onClick: () -> Unit,
) {
    val (bg, fg) = when (kind) {
        ButtonKind.Filled -> Night.Fg to Night.Bg
        ButtonKind.Accent -> Night.Accent to Night.Bg
        ButtonKind.Outline -> Night.Bg to Night.Fg
    }
    Box(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(bg)
            .then(if (kind == ButtonKind.Outline) Modifier.border(1.5.dp, Night.Fg, CircleShape) else Modifier)
            .pressable(onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(color = fg, fontSize = 16.sp), maxLines = 1)
    }
}

/** Outlined chip; filled with ink when selected. */
@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Night.Fg else Color.Transparent, tween(150), label = "chipBg")
    val fg by animateColorAsState(if (selected) Night.Bg else Night.Fg, tween(150), label = "chipFg")
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Night.Fg else Night.Rule, CircleShape)
            .pressable(onClick)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(horizontal = 6.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(color = fg), maxLines = 1, softWrap = false)
    }
}

/** A small toggle: ink when on, an empty outline when off. */
@Composable
fun PaperSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String = "Alarm") {
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, tween(160), label = "knob")
    val track by animateColorAsState(if (checked) Night.Fg else Color.Transparent, tween(160), label = "track")
    val dot by animateColorAsState(if (checked) Night.Bg else Night.FgFaint, tween(160), label = "dot")
    var pops by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    Box(
        Modifier
            .bubbleBurst(pops)
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.5.dp, if (checked) Night.Fg else Night.FgFaint, CircleShape)
            .pressable({ if (!checked) pops++; onCheckedChange(!checked) }, role = Role.Switch)
            .semantics {
                contentDescription = description
                stateDescription = if (checked) "On" else "Off"
            },
    ) {
        Box(
            Modifier
                .padding(5.dp)
                .offset(x = knob)
                .size(18.dp)
                .clip(CircleShape)
                .background(dot),
        )
    }
}

/** Round day toggles for the editor. */
@Composable
fun DayToggles(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 1..7) {
            val on = d in days
            val bg by animateColorAsState(if (on) Night.Fg else Color.Transparent, tween(150), label = "day")
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (on) Night.Fg else Night.Rule, CircleShape)
                    .pressable({ onChange(if (on) days - d else days + d) }, role = Role.Checkbox)
                    .semantics {
                        contentDescription = java.time.DayOfWeek.of(d).name.lowercase().replaceFirstChar { it.uppercase() }
                        stateDescription = if (on) "Repeats" else "Off"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(dayLetter(d), style = MaterialTheme.typography.labelLarge.copy(color = if (on) Night.Bg else Night.Fg))
            }
        }
    }
}

/** A plain notice, ruled in red, for something that would stop alarms ringing. */
@Composable
fun Notice(title: String, body: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(40.dp).background(Night.Accent))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp))
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Text("$action →", style = MaterialTheme.typography.labelLarge.copy(color = Night.Accent))
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier) = HorizontalDivider(modifier, thickness = 1.dp, color = Night.Rule)

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall)
}
