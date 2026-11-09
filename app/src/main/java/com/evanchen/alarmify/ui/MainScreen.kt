package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import com.evanchen.alarmify.data.AlarmRepository
import kotlinx.coroutines.launch

private enum class Tab { Alarms, Quick }

@Composable
fun MainScreen(alarms: List<Alarm>, fixes: List<Fix>) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    var tab by rememberSaveable { mutableStateOf(Tab.Alarms) }
    // null = closed, Alarm(id = -1) = new alarm, otherwise the one being edited.
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun toast(message: String, undo: (() -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = if (undo != null) "Undo" else null, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    fun ringsIn(a: Alarm) = a.nextTrigger()?.let { "Rings in ${formatDuration(it - System.currentTimeMillis())}" } ?: "Saved"

    val glow by animateColorAsState(
        if (tab == Tab.Alarms) Palette.Dawn else Palette.Mint, tween(700), label = "glow",
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Night0, Palette.Night1)))) {
        // A soft light from over the horizon, warm for alarms and cool for quick ones.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(
                    listOf(glow.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.85f, -size.width * 0.1f), radius = size.width * 1.1f,
                ),
                radius = size.width * 1.1f, center = Offset(size.width * 0.85f, -size.width * 0.1f),
            )
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState == Tab.Quick) 1 else -1
                (fadeIn(tween(250)) + slideInHorizontally(tween(300)) { dir * it / 6 }) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -dir * it / 6 })
            },
            label = "tab",
        ) { t ->
            when (t) {
                Tab.Alarms -> AlarmsScreen(
                    alarms = alarms,
                    fixes = fixes,
                    onToggle = { a, on ->
                        AlarmRepository.setEnabled(a.id, on)
                        if (on) AlarmRepository.get(a.id)?.let { toast(ringsIn(it)) }
                    },
                    onEdit = { editing = it },
                    onDelete = { a ->
                        AlarmRepository.delete(a.id)
                        toast("Alarm deleted") { AlarmRepository.upsert(a) }
                    },
                )
                Tab.Quick -> QuickScreen(
                    alarms = alarms,
                    onStart = { m ->
                        val a = AlarmRepository.addQuick(m)
                        toast("Rings at ${formatClockAt(is24, a.fireAt!!)} · in ${minutesLabel(m)}") { AlarmRepository.delete(a.id) }
                    },
                    onStartAt = { at ->
                        val a = AlarmRepository.addQuickAt(at, "Wake up")
                        toast("${relativeDay(at)} ${formatClockAt(is24, at)} · ${ringsIn(a).lowercase()}") { AlarmRepository.delete(a.id) }
                    },
                    onCancel = { a ->
                        AlarmRepository.delete(a.id)
                        toast("Quick alarm cancelled") { AlarmRepository.upsert(a) }
                    },
                )
            }
        }

        // Floating nav: Alarms · + · Quick
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) {
            SnackbarHost(snackbar, Modifier.padding(horizontal = 20.dp)) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = Palette.Night2,
                    contentColor = Palette.Text,
                    actionColor = glow,
                )
            }
            Spacer(Modifier.height(8.dp))
            NavBar(
                tab = tab,
                accent = glow,
                onTab = { tab = it },
                onAdd = { creating = true },
            )
        }
    }

    if (creating || editing != null) {
        AlarmEditor(
            initial = editing,
            newId = { AlarmRepository.newId() },
            onSave = { a ->
                AlarmRepository.upsert(a)
                tab = Tab.Alarms
                toast(ringsIn(a))
            },
            onDelete = { a ->
                AlarmRepository.delete(a.id)
                toast("Alarm deleted") { AlarmRepository.upsert(a) }
            },
            onDismiss = { creating = false; editing = null },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NavBar(tab: Tab, accent: Color, onTab: (Tab) -> Unit, onAdd: () -> Unit) {
    Row(
        Modifier
            .align(Alignment.CenterHorizontally)
            .shadow(24.dp, CircleShape)
            .clip(CircleShape)
            .background(Palette.Night2.copy(alpha = 0.92f))
            .border(1.dp, Palette.GlassBorder, CircleShape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NavItem("Alarms", Icons.Rounded.Alarm, tab == Tab.Alarms, Palette.Dawn) { onTab(Tab.Alarms) }
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Palette.dawnBrush)
                .pressable(onAdd)
                .semantics { contentDescription = "New alarm" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, tint = Palette.Night0, modifier = Modifier.size(30.dp)) }
        NavItem("Quick", Icons.Rounded.Bolt, tab == Tab.Quick, Palette.Mint) { onTab(Tab.Quick) }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent, label = "navBg")
    val fg by animateColorAsState(if (selected) accent else Palette.TextDim, label = "navFg")
    Row(
        Modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick, role = Role.Tab)
            .semantics { this.selected = selected }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg)
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
