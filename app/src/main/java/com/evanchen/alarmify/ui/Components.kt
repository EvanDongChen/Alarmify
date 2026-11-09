package com.evanchen.alarmify.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Squishes a little while pressed, springs back on release. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role = Role.Button,
    haptic: Boolean = true,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    return this
        .scale(scale)
        .clickable(source, indication = null, enabled = enabled, role = role) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = Palette.Glass,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(tint)
            .border(1.dp, Palette.GlassBorder, shape),
        content = content,
    )
}

/** Big gradient call-to-action. */
@Composable
fun GradientButton(
    text: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    glow: Color = Palette.Dawn,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(height)
            .shadow(18.dp, CircleShape, ambientColor = glow, spotColor = glow)
            .clip(CircleShape)
            .background(brush)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
            Text(text, color = Palette.Night0, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

/** Soft pill chip that lights up when selected. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Palette.Dawn,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) accent else Palette.Glass, label = "pillBg")
    val fg by animateColorAsState(if (selected) Palette.Night0 else Palette.TextDim, label = "pillFg")
    Box(
        modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else Palette.GlassBorder, CircleShape)
            .pressable(onClick)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** A custom on/off switch: a sun that slides into place and glows when on. */
@Composable
fun GlowSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, accent: Brush = Palette.dawnBrush) {
    val knob by animateDpAsState(
        if (checked) 24.dp else 0.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "knob",
    )
    val trackAlpha by animateFloatAsState(if (checked) 1f else 0f, label = "track")
    Box(
        Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
            .pressable({ onCheckedChange(!checked) }, role = Role.Switch)
            .semantics {
                stateDescription = if (checked) "On" else "Off"
                contentDescription = "Alarm switch"
            },
    ) {
        Box(Modifier.matchParentSize().clip(CircleShape).background(accent, alpha = trackAlpha))
        Box(
            Modifier
                .padding(4.dp)
                .offset(x = knob)
                .size(24.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(lerp(Palette.TextDim, Color.White, trackAlpha)),
        )
    }
}

/** M T W T F S S — the days an alarm repeats on. */
@Composable
fun DayDots(days: Set<Int>, active: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in 1..7) {
            val on = d in days
            Text(
                dayLetter(d),
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    on && active -> Palette.Gold
                    on -> Palette.TextDim
                    else -> Palette.TextFaint.copy(alpha = 0.6f)
                },
            )
        }
    }
}

/** Round day toggles for the editor. */
@Composable
fun DayToggles(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (d in 1..7) {
            val on = d in days
            val bg by animateColorAsState(if (on) Palette.Dawn else Palette.Glass, label = "day")
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (on) Color.Transparent else Palette.GlassBorder, CircleShape)
                    .pressable({ onChange(if (on) days - d else days + d) }, role = Role.Checkbox)
                    .semantics {
                        contentDescription = java.time.DayOfWeek.of(d).name.lowercase().replaceFirstChar { it.uppercase() }
                        stateDescription = if (on) "Repeats" else "Off"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(dayLetter(d), color = if (on) Palette.Night0 else Palette.TextDim, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A friendly card that explains what's missing for alarms to ring, and fixes it in one tap. */
@Composable
fun FixCard(title: String, body: String, action: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), tint = Palette.Rose.copy(alpha = 0.12f)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Palette.Rose.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.NotificationsActive, null, tint = Palette.Rose) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = Palette.Text, fontWeight = FontWeight.SemiBold)
                Text(body, color = Palette.TextDim, fontSize = 13.sp)
            }
            Box(
                Modifier.clip(CircleShape).background(Palette.Rose).pressable(onClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(action, color = Palette.Night0, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
}

/** Sky colours for a time of day, used behind the "next alarm" hero. */
fun skyFor(hour: Int): List<Color> = when (hour) {
    in 4..6 -> listOf(Color(0xFF3B2A7A), Color(0xFFFF7E6B), Color(0xFFFFC46B)) // dawn
    in 7..10 -> listOf(Color(0xFF2E6BD8), Color(0xFF66B7FF), Color(0xFFFFD48A)) // morning
    in 11..15 -> listOf(Color(0xFF1F7AE0), Color(0xFF56C3FF), Color(0xFF9EE6FF)) // day
    in 16..19 -> listOf(Color(0xFF4A2A8C), Color(0xFFFF5F8F), Color(0xFFFF9E5B)) // sunset
    else -> listOf(Color(0xFF0E0C2A), Color(0xFF2B2170), Color(0xFF5B3BA8)) // night
}

@Composable
fun RowScope.Grow() = Spacer(Modifier.weight(1f))
