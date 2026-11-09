package com.evanchen.alarmify.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

/**
 * An iOS-style drum: flick to spin, it snaps to the nearest value and ticks under your thumb.
 * [cyclic] wheels wrap around (59 → 00).
 */
@Composable
fun WheelPicker(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
    cyclic: Boolean = true,
    itemHeight: Dp = 56.dp,
    visible: Int = 5,
    fontSize: TextUnit = 40.sp,
    description: String = "",
) {
    val half = visible / 2
    // Non-cyclic wheels get blank rows above and below so the first and last values can reach the middle.
    val total = if (cyclic) count * 2_000 else count + half * 2
    val startFirst = if (cyclic) (total / 2) - (total / 2) % count + selected - half else selected
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startFirst)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val haptics = LocalHapticFeedback.current
    val currentOnSelected by rememberUpdatedState(onSelected)

    val centered by remember {
        derivedStateOf {
            state.firstVisibleItemIndex + half + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
        }
    }
    fun valueAt(row: Int) = if (cyclic) row % count else row - half

    LaunchedEffect(state) {
        snapshotFlow { centered }.distinctUntilChanged().drop(1).collect { row ->
            val v = valueAt(row)
            if (v in 0 until count) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnSelected(v)
            }
        }
    }

    Box(
        modifier
            .height(itemHeight * visible)
            .semantics { contentDescription = "$description ${label(selected)}" },
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(total) { row ->
                val v = valueAt(row)
                Box(
                    Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Fade and shrink with distance from the middle, like a drum turning away.
                            val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == row }
                            val center = state.layoutInfo.viewportSize.height / 2f
                            val dist = if (info == null) half.toFloat()
                            else abs(info.offset + info.size / 2f - center) / itemPx
                            val t = (dist / (half + 0.5f)).coerceIn(0f, 1f)
                            alpha = 1f - 0.8f * t
                            scaleX = 1f - 0.25f * t
                            scaleY = scaleX
                            rotationX = (if (info != null && info.offset + info.size / 2f < center) 1 else -1) * 40f * t
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (v in 0 until count) {
                        Text(
                            label(v),
                            fontSize = fontSize,
                            fontWeight = FontWeight.Light,
                            color = Palette.Text,
                            style = Numeric,
                        )
                    }
                }
            }
        }
    }
}
